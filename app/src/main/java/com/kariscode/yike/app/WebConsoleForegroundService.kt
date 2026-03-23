package com.kariscode.yike.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.ServiceCompat
import com.kariscode.yike.R
import com.kariscode.yike.domain.model.WebConsoleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 网页后台前台服务单独存在，是为了让手机锁屏后仍能继续提供局域网访问，
 * 同时把常驻通知和服务生命周期从普通页面状态里解耦出来。
 */
class WebConsoleForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * 首次创建时就建立渠道并开始观察状态，是为了让启动中、运行中和失败态都能及时反映到常驻通知。
     */
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        serviceScope.launch {
            applicationContainer().webConsoleRepository.observeState().collectLatest { state ->
                updateNotification(state)
            }
        }
    }

    /**
     * 启停与访问码刷新都通过显式 action 驱动，是为了让服务行为保持可追踪且便于后续从通知按钮复用。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForegroundStarted()
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    applicationContainer().webConsoleRepository.stopServer()
                    stopSelf()
                }
            }

            ACTION_REFRESH_ACCESS_CODE -> {
                serviceScope.launch {
                    applicationContainer().webConsoleRepository.refreshAccessCode()
                }
            }

            else -> {
                serviceScope.launch {
                    applicationContainer().webConsoleRepository.startServer()
                }
            }
        }
        return START_STICKY
    }

    /**
     * 当前服务只负责前台常驻，不提供绑定接口，是为了把交互入口继续收口在页面和通知动作上。
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 服务销毁时主动停止网页后台，是为了避免前台服务被系统回收后端口仍残留在不可见状态。
     */
    override fun onDestroy() {
        serviceScope.launch {
            applicationContainer().webConsoleRepository.stopServer()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * 启动早期先给出占位通知，是为了满足前台服务时限要求并避免“还没启动完成就被系统判定违规”。
     */
    private fun ensureForegroundStarted() {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(
                title = "网页后台准备中",
                content = "正在初始化服务与访问地址。"
            ),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
    }

    /**
     * 通知文案围绕状态模型生成，是为了让手机锁屏后的排障信息仍和页面看到的事实一致。
     */
    private fun updateNotification(state: WebConsoleState) {
        val recommendedAddress = state.addresses.firstOrNull { it.isRecommended } ?: state.addresses.firstOrNull()
        val title = when {
            state.isStarting -> "网页后台准备中"
            state.isRunning -> "网页后台运行中"
            state.lastError != null -> "网页后台启动失败"
            else -> "网页后台已停止"
        }
        val content = when {
            state.isStarting -> "正在准备端口与访问码。"
            state.isRunning -> recommendedAddress?.url ?: "正在等待可用地址。"
            state.lastError != null -> state.lastError
            else -> "已停止对局域网提供访问。"
        }
        val notification = buildNotification(title = title, content = content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 通知集中构造可以保证启动、运行和失败态共享同一套动作入口，避免文案与按钮逐渐分叉。
     */
    private fun buildNotification(title: String, content: String): Notification {
        val stopIntent = createIntent(context = this, action = ACTION_STOP)
        val refreshIntent = createIntent(context = this, action = ACTION_REFRESH_ACCESS_CODE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_web_console)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "刷新访问码", android.app.PendingIntent.getService(this, 2, refreshIntent, android.app.PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, "停止服务", android.app.PendingIntent.getService(this, 1, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    /**
     * 渠道幂等创建后，服务重启和进程恢复都不需要再次判断系统里是否已有同名渠道。
     */
    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "网页后台",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "用于保持局域网网页后台在锁屏后继续可访问。"
            }
        )
    }

    /**
     * Application 容器读取收口后，服务不需要自己维护额外的单例查找逻辑。
     */
    private fun applicationContainer(): AppContainer = (application as YikeApplication).container

    companion object {
        private const val CHANNEL_ID = "web_console_service"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_START = "com.kariscode.yike.action.WEB_CONSOLE_START"
        private const val ACTION_STOP = "com.kariscode.yike.action.WEB_CONSOLE_STOP"
        private const val ACTION_REFRESH_ACCESS_CODE = "com.kariscode.yike.action.WEB_CONSOLE_REFRESH_ACCESS_CODE"

        /**
         * 启动服务使用 ContextCompat 包装，是为了兼容 Android 8+ 的前台服务启动要求。
         */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, createIntent(context, ACTION_START))
        }

        /**
         * 停止动作也走同一服务入口，是为了让页面按钮和通知按钮复用完全一致的生命周期语义。
         */
        fun stop(context: Context) {
            context.startService(createIntent(context, ACTION_STOP))
        }

        /**
         * 访问码刷新单独暴露，是为了让手机页不必直接操作仓储就能触发服务内动作。
         */
        fun refreshAccessCode(context: Context) {
            context.startService(createIntent(context, ACTION_REFRESH_ACCESS_CODE))
        }

        /**
         * 显式 action Intent 统一在单点构造，是为了避免页面和通知各自手写字符串常量后逐渐漂移。
         */
        private fun createIntent(context: Context, action: String): Intent =
            Intent(context, WebConsoleForegroundService::class.java).apply {
                this.action = action
            }
    }
}
