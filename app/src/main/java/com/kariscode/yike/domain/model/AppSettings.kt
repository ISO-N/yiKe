package com.kariscode.yike.domain.model

/**
 * AppSettings 代表全局配置的业务语义，而不是某个存储实现的字段集合；
 * 把它放在 domain 层可以保证 UI/UseCase 不依赖 DataStore key 细节，从而便于未来迁移存储方案。
 */
data class AppSettings(
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val schemaVersion: Int,
    val backupLastAt: Long?,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val streakAchievementUnlocks: List<StreakAchievementUnlock> = emptyList()
)

