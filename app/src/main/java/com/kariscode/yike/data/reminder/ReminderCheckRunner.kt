package com.kariscode.yike.data.reminder

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.QuestionRepository

/**
 * ReminderCheckRunner 把“读设置、查到期数量、发通知、续约下一次”收敛为单一执行单元，
 * 这样 Worker 可以保持极薄，核心业务路径也能在 JVM 测试中稳定覆盖。
 */
class ReminderCheckRunner(
    private val appSettingsRepository: AppSettingsRepository,
    private val questionRepository: QuestionRepository,
    private val reminderNotifier: ReminderNotifier,
    private val reminderScheduler: ReminderSyncScheduler,
    private val timeProvider: TimeProvider
) {
    /**
     * 到点检查后无论是否发通知都续约下一次提醒，从而避免提醒链路中断。
     */
    suspend fun run() {
        val settings = appSettingsRepository.getSettings()
        if (settings.dailyReminderEnabled) {
            val summary = questionRepository.getTodayReviewSummary(timeProvider.nowEpochMillis())
            if (summary.dueQuestionCount > 0) {
                reminderNotifier.showDailyReminder(
                    dueCardCount = summary.dueCardCount,
                    dueQuestionCount = summary.dueQuestionCount
                )
            }
        }
        reminderScheduler.syncReminder(settings)
    }
}
