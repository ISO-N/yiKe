package com.kariscode.yike.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kariscode.yike.app.YikeApplication

/**
 * Worker 只依赖本地仓储与通知辅助组件，
 * 这样即使应用页面未打开，也能按本地数据状态独立完成提醒检查。
 */
class ReminderCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    /**
     * 到点后只在确实存在待复习内容时发送通知，
     * 并在结束前无论是否发送都重建下一次提醒，避免提醒链路中断。
     */
    override suspend fun doWork(): Result {
        val container = (applicationContext as YikeApplication).container
        return runCatching {
            container.reminderCheckRunner.run()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
