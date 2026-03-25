package com.kariscode.yike.domain.model

/**
 * 只把用户真正期望跨设备一致的设置抽成同步模型，
 * 是为了把提醒、主题等业务偏好纳入双向同步，同时继续把 schema/备份元数据留在本机维护。
 */
data class SyncedAppSettings(
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val themeMode: ThemeMode,
    val streakAchievementUnlocks: List<StreakAchievementUnlock> = emptyList()
)

/**
 * 从 AppSettings 投影同步设置的转换放在 domain 层，是为了让同步协议不依赖具体存储结构中有哪些本地技术字段。
 */
fun AppSettings.toSyncedAppSettings(): SyncedAppSettings = SyncedAppSettings(
    dailyReminderEnabled = dailyReminderEnabled,
    dailyReminderHour = dailyReminderHour,
    dailyReminderMinute = dailyReminderMinute,
    themeMode = themeMode,
    streakAchievementUnlocks = streakAchievementUnlocks
)

/**
 * 把同步设置回填到完整设置快照时只覆盖跨设备字段，
 * 是为了让本地 schemaVersion、最近备份时间等元数据继续由本机负责。
 */
fun AppSettings.mergeSyncedSettings(settings: SyncedAppSettings): AppSettings = copy(
    dailyReminderEnabled = settings.dailyReminderEnabled,
    dailyReminderHour = settings.dailyReminderHour,
    dailyReminderMinute = settings.dailyReminderMinute,
    themeMode = settings.themeMode,
    streakAchievementUnlocks = settings.streakAchievementUnlocks
)
