package com.kariscode.yike.data.backup

import com.kariscode.yike.core.domain.time.TimeTextFormatter
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.mapper.decodeTags
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.ReviewRecord

/**
 * 备份导出映射从 BackupService 中抽离，是为了让服务类只保留“读快照、写文件、事务恢复”三条主线，
 * 同时把字段级别的序列化规则收口到独立文件，避免后续扩展字段时在服务类里滚雪球式膨胀。
 */

/**
 * 统一把设置映射为固定 `HH:mm` 文本，是为了让备份文件结构稳定且便于人工阅读。
 */
internal fun AppSettings.toBackupReminderTime(): String =
    TimeTextFormatter.formatHourMinute(
        hour = dailyReminderHour,
        minute = dailyReminderMinute
    )

/**
 * Deck 到备份模型的映射收敛到单点后，字段调整时就不必同时追踪导出主流程里的长内联表达式。
 */
internal fun DeckEntity.toBackup(): BackupDeck = BackupDeck(
    id = id,
    name = name,
    description = description,
    tags = decodeTags(tagsJson),
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = BackupJson.formatEpochMillis(createdAt),
    updatedAt = BackupJson.formatEpochMillis(updatedAt)
)

/**
 * Card 的备份映射独立出来，是为了让层级字段与时间字段的导出规则保持易读且可复用。
 */
internal fun CardEntity.toBackup(): BackupCard = BackupCard(
    id = id,
    deckId = deckId,
    title = title,
    description = description,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = BackupJson.formatEpochMillis(createdAt),
    updatedAt = BackupJson.formatEpochMillis(updatedAt)
)

/**
 * Question 映射集中在单点后，可以把状态与时间字段的转换规则固定住，
 * 避免导出路径未来增删字段时遗漏调度相关数据。
 */
internal fun QuestionEntity.toBackup(): BackupQuestion {
    val domainQuestion = toDomain()
    return domainQuestion.toBackup()
}

/**
 * ReviewRecord 的备份映射抽出来后，导出主流程只保留“读哪些表”的骨架，更容易检查事务语义。
 */
internal fun ReviewRecordEntity.toBackup(): BackupReviewRecord {
    val domainRecord = toDomain()
    return domainRecord.toBackup()
}

/**
 * 领域问题转换成备份模型，是为了让状态枚举与字符串字段的边界集中在一处维护。
 */
internal fun Question.toBackup(): BackupQuestion = BackupQuestion(
    id = id,
    cardId = cardId,
    prompt = prompt,
    answer = answer,
    tags = tags,
    status = status.storageValue,
    stageIndex = stageIndex,
    dueAt = BackupJson.formatEpochMillis(dueAt),
    lastReviewedAt = lastReviewedAt?.let(BackupJson::formatEpochMillis),
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = BackupJson.formatEpochMillis(createdAt),
    updatedAt = BackupJson.formatEpochMillis(updatedAt)
)

/**
 * 领域复习记录转换为备份模型时保留原始评分链路，
 * 这样恢复后仍能重建用户真实的复习历史。
 */
internal fun ReviewRecord.toBackup(): BackupReviewRecord = BackupReviewRecord(
    id = id,
    questionId = questionId,
    rating = rating.name,
    oldStageIndex = oldStageIndex,
    newStageIndex = newStageIndex,
    oldDueAt = BackupJson.formatEpochMillis(oldDueAt),
    newDueAt = BackupJson.formatEpochMillis(newDueAt),
    reviewedAt = BackupJson.formatEpochMillis(reviewedAt),
    responseTimeMs = responseTimeMs,
    note = note
)


