package com.kariscode.yike.data.reminder

/**
 * 提醒相关常量集中定义，是为了让 WorkManager 唯一任务名、通知渠道与通知 ID 保持稳定，
 * 避免后续修改其中一个值时遗留旧任务或旧渠道。
 */
object ReminderConstants {
    const val UNIQUE_WORK_NAME: String = "daily_review_reminder_work"
    const val CHANNEL_ID: String = "daily_review_reminder"
    const val CHANNEL_NAME: String = "每日复习提醒"
    const val NOTIFICATION_ID: Int = 1001
}
