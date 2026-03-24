package com.kariscode.yike.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application 负责启动全局对象图并保留现有容器入口，
 * 是为了在切到 Koin 后仍让 Activity、Worker 与 Service 继续沿用统一的应用级依赖边界。
 */
class YikeApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 以懒加载方式创建容器可避免在测试/预览等场景提前触发 IO 初始化，
     * 同时保证全局依赖在整个进程内复用。
     */
    val container: AppContainer by lazy { AppContainer() }

    /**
     * 应用启动时主动校准提醒与通知渠道，能降低“重启后提醒失效”或“首次 Worker 无渠道”的系统级风险。
     */
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@YikeApplication)
            modules(yikeModules)
        }
        container.notificationHelper.ensureChannel()
        applicationScope.launch {
            container.reminderScheduler.syncReminderFromRepository()
        }
    }
}
