package com.kariscode.yike.data.settings

import com.kariscode.yike.domain.model.StreakAchievementUnlock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 解锁记录在 DataStore 中以 JSON 字符串保存，
 * 把编码/解码集中到 settings 包内，是为了确保写入、读取、备份恢复和同步回放看到的都是同一套格式语义。
 */
private val streakAchievementJson: Json = Json { ignoreUnknownKeys = true }

@Serializable
private data class StreakAchievementUnlockPayload(
    val id: String,
    val unlockedAt: Long
)

private val payloadSerializer = ListSerializer(StreakAchievementUnlockPayload.serializer())

/**
 * 将领域解锁记录转为稳定 JSON，是为了让 DataStore 只承担持久化职责而不暴露复杂结构类型。
 */
internal fun encodeStreakAchievementUnlocks(
    unlocks: List<StreakAchievementUnlock>
): String = streakAchievementJson.encodeToString(
    serializer = payloadSerializer,
    value = unlocks.map { unlock ->
        StreakAchievementUnlockPayload(
            id = unlock.achievementId,
            unlockedAt = unlock.unlockedAtEpochMillis
        )
    }
)

/**
 * 解码时对异常回退为空列表，是为了让单条坏数据不会让整个设置读盘失败，
 * 同时把容错边界固定在单点便于后续排查。
 */
internal fun decodeStreakAchievementUnlocks(
    unlocksJson: String
): List<StreakAchievementUnlock> = runCatching {
    streakAchievementJson.decodeFromString(
        deserializer = payloadSerializer,
        string = unlocksJson
    ).map { payload ->
        StreakAchievementUnlock(
            achievementId = payload.id,
            unlockedAtEpochMillis = payload.unlockedAt
        )
    }
}.getOrElse { emptyList() }

