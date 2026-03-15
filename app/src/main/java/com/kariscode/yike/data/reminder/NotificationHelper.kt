package com.kariscode.yike.data.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kariscode.yike.R
import com.kariscode.yike.app.MainActivity

/**
 * 通知辅助组件把渠道、权限判断与通知构建集中起来，
 * 这样 Worker 与应用初始化都能复用同一套通知语义，避免文案和渠道配置漂移。
 */
class NotificationHelper(
    private val context: Context
) {
    /**
     * 渠道创建需要幂等执行，集中在此处可以让应用启动与 Worker 发送前都安全调用。
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ReminderConstants.CHANNEL_ID,
            ReminderConstants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
    }

    /**
     * Android 13+ 需要显式检查通知权限，
     * 这样才能做到“权限未开时不崩溃，同时不阻断核心复习功能”。
     */
    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 通知点击回首页而不是直接进某张卡片，是为了让用户在数据可能已变化的情况下仍能回到稳定入口。
     */
    fun showDailyReminder(dueCardCount: Int, dueQuestionCount: Int) {
        ensureChannel()
        if (!canPostNotifications()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = "今天还有 $dueCardCount 张卡片、$dueQuestionCount 个问题待复习"
        val notification = NotificationCompat.Builder(context, ReminderConstants.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("忆刻提醒你复习")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(ReminderConstants.NOTIFICATION_ID, notification)
    }
}
