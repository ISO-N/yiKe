package com.kariscode.yike.domain.model

/**
 * 连续学习成就枚举化，是为了让“规则（需要多少天）”与“展示文案/徽章 ID”保持一一对应，
 * 从而在 DataStore 持久化、备份恢复和局域网同步三条链路里都能共享同一套稳定的语义定义。
 */
enum class StreakAchievement(
    val id: String,
    val requiredDays: Int,
    val title: String
) {
    FIRST_SPARK(id = "first_spark", requiredDays = 3, title = "初露头角"),
    PERSISTENCE(id = "persistence", requiredDays = 7, title = "持之以恒"),
    HABIT(id = "habit", requiredDays = 30, title = "习惯养成"),
    MASTER(id = "master", requiredDays = 100, title = "记忆大师");

    companion object {
        /**
         * 成就 ID 与枚举的映射集中在这里，是为了让存储层只依赖稳定字符串，
         * 而不把枚举名称暴露到序列化协议里，避免后续重命名破坏兼容。
         */
        fun fromId(id: String): StreakAchievement? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 解锁记录持久化需要保留“解锁了哪个徽章以及何时解锁”，
 * 这样成就就不是纯派生 UI，而是可以被备份、恢复与同步的用户进度。
 */
data class StreakAchievementUnlock(
    val achievementId: String,
    val unlockedAtEpochMillis: Long
)

/**
 * 最高徽章用于首页 Hero 聚焦展示，因此提供单点计算入口避免 UI 自己推导排序规则。
 */
fun List<StreakAchievementUnlock>.highestUnlockedAchievement(): StreakAchievement? =
    mapNotNull { unlock -> StreakAchievement.fromId(unlock.achievementId) }
        .maxByOrNull { achievement -> achievement.requiredDays }

/**
 * 根据 streakDays 补齐需要解锁的徽章列表，是为了让解锁动作具备幂等性，
 * 并确保多次刷新或多端同步回放时不会产生重复记录。
 */
fun List<StreakAchievementUnlock>.unlockForStreakDays(
    streakDays: Int,
    nowEpochMillis: Long
): List<StreakAchievementUnlock> {
    if (streakDays <= 0) return this
    val unlockedIds = asSequence().map { it.achievementId }.toSet()
    val newlyUnlocked = StreakAchievement.entries
        .filter { achievement -> achievement.requiredDays <= streakDays }
        .filterNot { achievement -> achievement.id in unlockedIds }
        .map { achievement -> StreakAchievementUnlock(achievementId = achievement.id, unlockedAtEpochMillis = nowEpochMillis) }

    if (newlyUnlocked.isEmpty()) return this
    return this + newlyUnlocked
}

