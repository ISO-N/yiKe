package com.kariscode.yike.data.sync

import com.kariscode.yike.data.local.db.entity.SyncChangeEntity
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewRecord
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.SyncedAppSettings
import com.kariscode.yike.domain.model.StreakAchievementUnlock
import com.kariscode.yike.domain.model.ThemeMode
import kotlinx.serialization.Serializable

/**
 * 传输层变更信封显式携带元数据与实体载荷，是为了让预览、冲突检测和真正应用都围绕同一份协议对象工作。
 */
@Serializable
data class SyncChangePayload(
    val seq: Long,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val summary: String,
    val payloadJson: String?,
    val payloadHash: String,
    val modifiedAt: Long
)

/**
 * 设置同步载荷与 AppSettings 主模型分离，是为了避免把 schemaVersion、备份时间等本地元数据错误带入跨设备同步。
 */
@Serializable
data class SyncSettingsPayload(
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val themeMode: String,
    val streakAchievementUnlocks: List<SyncStreakAchievementUnlockPayload> = emptyList()
)

/**
 * 解锁记录进入同步载荷，是为了让成就进度能够跨设备一致而不是依赖每台设备本地派生。
 */
@Serializable
data class SyncStreakAchievementUnlockPayload(
    val id: String,
    val unlockedAt: Long
)

/**
 * Deck 载荷保持与领域字段接近，是为了让同步应用阶段可直接重建实体，而不必再次依赖备份模型。
 */
@Serializable
data class SyncDeckPayload(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val intervalStepCount: Int,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Card 载荷保留 deckId，是为了让远端应用在 upsert 时仍能维护父子关系。
 */
@Serializable
data class SyncCardPayload(
    val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Question 载荷完整保留调度字段，是为了让双向同步后不需要重新计算 due/stage，从而保持用户真实学习进度。
 */
@Serializable
data class SyncQuestionPayload(
    val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tags: List<String>,
    val status: String,
    val stageIndex: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * ReviewRecord 作为追加型历史事件同步，是为了让多端评分历史能汇合，而不是只有最终题目状态收敛。
 */
@Serializable
data class SyncReviewRecordPayload(
    val id: String,
    val questionId: String,
    val rating: String,
    val oldStageIndex: Int,
    val newStageIndex: Int,
    val oldDueAt: Long,
    val newDueAt: Long,
    val reviewedAt: Long,
    val responseTimeMs: Long?,
    val note: String
)

/**
 * 领域设置转为同步载荷的映射集中在一处，是为了让协议层始终只看到明确允许跨设备传播的字段。
 */
fun SyncedAppSettings.toPayload(): SyncSettingsPayload = SyncSettingsPayload(
    dailyReminderEnabled = dailyReminderEnabled,
    dailyReminderHour = dailyReminderHour,
    dailyReminderMinute = dailyReminderMinute,
    themeMode = themeMode.storageValue,
    streakAchievementUnlocks = streakAchievementUnlocks.map { unlock ->
        SyncStreakAchievementUnlockPayload(
            id = unlock.achievementId,
            unlockedAt = unlock.unlockedAtEpochMillis
        )
    }
)

/**
 * Deck 的同步映射集中后，journal 和网络应用就能共享同一份字段口径，不会出现一边漏 tags 一边保留 tags 的情况。
 */
fun Deck.toPayload(): SyncDeckPayload = SyncDeckPayload(
    id = id,
    name = name,
    description = description,
    tags = tags,
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Card 的同步载荷只保留真正需要跨端重建的字段，是为了让协议对象保持紧凑并易于校验。
 */
fun Card.toPayload(): SyncCardPayload = SyncCardPayload(
    id = id,
    deckId = deckId,
    title = title,
    description = description,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Question 的调度字段直接进入同步载荷，是为了保证双向同步后的待复习口径能立即收敛。
 */
fun Question.toPayload(): SyncQuestionPayload = SyncQuestionPayload(
    id = id,
    cardId = cardId,
    prompt = prompt,
    answer = answer,
    tags = tags,
    status = status.storageValue,
    stageIndex = stageIndex,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * ReviewRecord 转为同步载荷时保留评分名称，是为了让远端仍能按原始评分语义还原历史。
 */
fun ReviewRecord.toPayload(): SyncReviewRecordPayload = SyncReviewRecordPayload(
    id = id,
    questionId = questionId,
    rating = rating.name,
    oldStageIndex = oldStageIndex,
    newStageIndex = newStageIndex,
    oldDueAt = oldDueAt,
    newDueAt = newDueAt,
    reviewedAt = reviewedAt,
    responseTimeMs = responseTimeMs,
    note = note
)

/**
 * 同步设置从载荷回到领域模型时统一解析主题值，是为了把协议字符串与领域枚举之间的边界固定在单点。
 */
fun SyncSettingsPayload.toDomain(): SyncedAppSettings = SyncedAppSettings(
    dailyReminderEnabled = dailyReminderEnabled,
    dailyReminderHour = dailyReminderHour,
    dailyReminderMinute = dailyReminderMinute,
    themeMode = ThemeMode.fromStorageValue(themeMode),
    streakAchievementUnlocks = streakAchievementUnlocks.map { payload ->
        StreakAchievementUnlock(
            achievementId = payload.id,
            unlockedAtEpochMillis = payload.unlockedAt
        )
    }
)

/**
 * Deck 从同步载荷回到领域模型，是为了让“协议对象 -> domain -> entity”的链路保持一致，
 * 从而避免远端应用阶段出现一处手写映射、一处复用仓储映射导致的字段口径漂移。
 */
fun SyncDeckPayload.toDomain(): Deck = Deck(
    id = id,
    name = name,
    description = description,
    tags = tags,
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Card 从同步载荷回到领域模型后再写库，是为了让数据层只维护一种写入入口，
 * 便于未来调整 Room schema 或字段默认值时集中收敛影响范围。
 */
fun SyncCardPayload.toDomain(): Card = Card(
    id = id,
    deckId = deckId,
    title = title,
    description = description,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Question 状态字符串到领域枚举的恢复放在载荷层，是为了让协议应用逻辑保持“拿到模型就写库”的简单骨架。
 */
fun SyncQuestionPayload.toDomain(): Question = Question(
    id = id,
    cardId = cardId,
    prompt = prompt,
    answer = answer,
    tags = tags,
    status = QuestionStatus.fromStorageValue(status),
    stageIndex = stageIndex,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * ReviewRecord 的评分恢复走统一枚举解析，是为了避免远端应用阶段再直接依赖硬编码评分字符串集合。
 */
fun SyncReviewRecordPayload.toDomain(): ReviewRecord = ReviewRecord(
    id = id,
    questionId = questionId,
    rating = ReviewRating.valueOf(rating),
    oldStageIndex = oldStageIndex,
    newStageIndex = newStageIndex,
    oldDueAt = oldDueAt,
    newDueAt = newDueAt,
    reviewedAt = reviewedAt,
    responseTimeMs = responseTimeMs,
    note = note
)

/**
 * 统一把领域类型转成协议枚举名称，是为了让 journal、网络和冲突页共用同一套实体标识。
 */
fun SyncEntityType.storageValue(): String = name

/**
 * 统一把操作枚举转成字符串，是为了让本地 journal 和远端协议以完全相同的值表示 upsert/delete。
 */
fun SyncChangeOperation.storageValue(): String = name

/**
 * journal 实体转为协议载荷后，客户端和服务端就能围绕同一份 DTO 进行增量传输。
 */
fun SyncChangeEntity.toPayload(): SyncChangePayload = SyncChangePayload(
    seq = seq,
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    summary = summary,
    payloadJson = payloadJson,
    payloadHash = payloadHash,
    modifiedAt = modifiedAt
)
