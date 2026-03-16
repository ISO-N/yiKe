package com.kariscode.yike.data.reminder

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.reminder.ReminderTimeCalculator
import com.kariscode.yike.domain.repository.AppSettingsRepository
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * ReminderScheduler 统一负责“取消旧任务 + 计算下一次时间 + 注册新任务”，
 * 从而把设置变更、应用启动和恢复备份后的提醒重建口径固定在一处。
 */
class ReminderScheduler(
    private val workManager: WorkManager,
    private val appSettingsRepository: AppSettingsRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 从仓储读取最新设置后再调度，能确保应用初始化或广播恢复时不依赖页面状态。
     */
    suspend fun syncReminderFromRepository() {
        val settings = appSettingsRepository.getSettings()
        syncReminder(settings)
    }

    /**
     * 关闭提醒时必须先取消唯一任务，
     * 这样旧任务不会在用户已经关闭提醒后继续发送通知。
     */
    fun cancelReminder() {
        workManager.cancelUniqueWork(ReminderConstants.UNIQUE_WORK_NAME)
    }

    /**
     * 重新调度前始终先取消旧任务，是为了保证时间修改后只存在一个有效提醒。
     */
    fun syncReminder(settings: AppSettings) {
        cancelReminder()
        if (!settings.dailyReminderEnabled) return

        val now = timeProvider.nowEpochMillis()
        val triggerAt = ReminderTimeCalculator.computeNextTriggerAt(
            nowEpochMillis = now,
            reminderHour = settings.dailyReminderHour,
            reminderMinute = settings.dailyReminderMinute,
            zoneId = ZoneId.systemDefault()
        )
        val initialDelay = (triggerAt - now).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderCheckWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .addTag(ReminderConstants.UNIQUE_WORK_NAME)
            .build()

        workManager.enqueueUniqueWork(
            ReminderConstants.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
