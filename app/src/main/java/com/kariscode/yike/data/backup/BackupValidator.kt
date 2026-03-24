package com.kariscode.yike.data.backup

import com.kariscode.yike.core.time.TimeTextFormatter
import com.kariscode.yike.data.sync.LanSyncJson
import com.kariscode.yike.data.sync.SyncCardPayload
import com.kariscode.yike.data.sync.SyncDeckPayload
import com.kariscode.yike.data.sync.SyncQuestionPayload
import com.kariscode.yike.data.sync.SyncReviewRecordPayload
import com.kariscode.yike.data.sync.SyncSettingsPayload
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1

/**
 * 备份校验器把版本、字段和引用关系的检查集中起来，
 * 是为了在真正覆盖本地数据前尽早拦截无效文件，守住恢复安全边界。
 */
class BackupValidator {
    /**
     * 校验阶段直接返回面向用户的明确原因，是为了让恢复失败时能快速定位是版本、字段还是引用关系出了问题。
     */
    fun validate(document: BackupDocument): Result<Unit> = runCatching {
        require(document.app.backupVersion == BackupConstants.BACKUP_VERSION) {
            "备份版本不兼容，当前仅支持版本 ${BackupConstants.BACKUP_VERSION}"
        }
        requireValidEpoch(document.app.exportedAt, "app.exportedAt")
        when (document.app.kind) {
            BackupDocumentKind.FULL -> validateFull(document)
            BackupDocumentKind.INCREMENTAL -> validateIncremental(document)
        }
    }

    /**
     * 完整备份必须携带完整快照，是为了确保“恢复”继续保持一份文件即可独立重建的语义。
     */
    private fun validateFull(document: BackupDocument) {
        val payload = requireNotNull(document.full) { "完整备份缺少 full 数据段" }
        require(document.incremental == null) { "完整备份不应包含增量数据段" }
        validateSettings(payload.settings)

        val deckIds = payload.decks.map { deck ->
            require(deck.id.isNotBlank()) { "deck.id 不能为空" }
            require(deck.name.isNotBlank()) { "deck.name 不能为空" }
            require(
                deck.intervalStepCount in
                    ReviewSchedulerV1.MIN_INTERVAL_STEP_COUNT..ReviewSchedulerV1.MAX_INTERVAL_STEP_COUNT
            ) {
                "deck.intervalStepCount 超出支持范围"
            }
            requireValidEpoch(deck.createdAt, "deck.createdAt")
            requireValidEpoch(deck.updatedAt, "deck.updatedAt")
            deck.id
        }.toSet()

        val cardIds = payload.cards.map { card ->
            require(card.id.isNotBlank()) { "card.id 不能为空" }
            require(card.title.isNotBlank()) { "card.title 不能为空" }
            require(card.deckId in deckIds) { "card.deckId 找不到对应卡组：${card.deckId}" }
            requireValidEpoch(card.createdAt, "card.createdAt")
            requireValidEpoch(card.updatedAt, "card.updatedAt")
            card.id
        }.toSet()

        val questionIds = payload.questions.map { question ->
            require(question.id.isNotBlank()) { "question.id 不能为空" }
            require(question.prompt.isNotBlank()) { "question.prompt 不能为空" }
            require(question.cardId in cardIds) { "question.cardId 找不到对应卡片：${question.cardId}" }
            require(
                QuestionStatus.entries.any { status -> status.storageValue == question.status }
            ) {
                "question.status 不受支持：${question.status}"
            }
            requireStageIndexInRange(question.stageIndex, "question.stageIndex")
            requireValidEpoch(question.dueAt, "question.dueAt")
            question.lastReviewedAt?.let { requireValidEpoch(it, "question.lastReviewedAt") }
            requireValidEpoch(question.createdAt, "question.createdAt")
            requireValidEpoch(question.updatedAt, "question.updatedAt")
            question.id
        }.toSet()

        payload.reviewRecords.forEach { record ->
            require(record.questionId in questionIds) { "reviewRecord.questionId 找不到对应题目：${record.questionId}" }
            require(ReviewRating.entries.any { rating -> rating.name == record.rating }) {
                "reviewRecord.rating 不受支持：${record.rating}"
            }
            requireStageIndexInRange(record.oldStageIndex, "reviewRecord.oldStageIndex")
            requireStageIndexInRange(record.newStageIndex, "reviewRecord.newStageIndex")
            requireValidEpoch(record.oldDueAt, "reviewRecord.oldDueAt")
            requireValidEpoch(record.newDueAt, "reviewRecord.newDueAt")
            requireValidEpoch(record.reviewedAt, "reviewRecord.reviewedAt")
        }
    }

    /**
     * 增量备份必须显式带出基线时间和可解析变更，是为了保证它能在匹配基线上被稳定重放。
     */
    private fun validateIncremental(document: BackupDocument) {
        val payload = requireNotNull(document.incremental) { "增量备份缺少 incremental 数据段" }
        require(document.full == null) { "增量备份不应包含完整快照数据段" }
        requireValidEpoch(payload.baseBackupAt, "incremental.baseBackupAt")
        payload.changes.forEachIndexed { index, change ->
            require(change.seq > 0L) { "incremental.changes[$index].seq 必须大于 0" }
            require(change.entityId.isNotBlank()) { "incremental.changes[$index].entityId 不能为空" }
            require(change.entityType in SyncEntityType.entries.map { it.name }) {
                "incremental.changes[$index].entityType 不受支持：${change.entityType}"
            }
            require(change.operation in SyncChangeOperation.entries.map { it.name }) {
                "incremental.changes[$index].operation 不受支持：${change.operation}"
            }
            require(change.payloadHash.isNotBlank()) { "incremental.changes[$index].payloadHash 不能为空" }
            require(change.modifiedAt >= 0L) { "incremental.changes[$index].modifiedAt 不能为负数" }
            validateIncrementalPayload(index = index, change = change)
        }
    }

    /**
     * 设置写回后会马上参与提醒调度，因此完整备份里的时间与主题值必须在校验阶段就验证通过。
     */
    private fun validateSettings(settings: BackupSettings) {
        parseReminderTime(settings.dailyReminderTime)
        settings.backupLastAt?.let { requireValidEpoch(it, "settings.backupLastAt") }
        require(
            ThemeMode.entries.any { mode -> mode.storageValue == settings.themeMode }
        ) {
            "settings.themeMode 不受支持：${settings.themeMode}"
        }
    }

    /**
     * 增量变更沿用同步协议载荷解析，是为了保证“同步能应用的变更，增量备份也能应用”。
     */
    private fun validateIncrementalPayload(index: Int, change: com.kariscode.yike.data.sync.SyncChangePayload) {
        if (change.operation == SyncChangeOperation.DELETE.name) {
            return
        }
        val payloadJson = requireNotNull(change.payloadJson) {
            "incremental.changes[$index].payloadJson 不能为空"
        }
        when (change.entityType) {
            SyncEntityType.SETTINGS.name ->
                LanSyncJson.json.decodeFromString(SyncSettingsPayload.serializer(), payloadJson)

            SyncEntityType.DECK.name ->
                LanSyncJson.json.decodeFromString(SyncDeckPayload.serializer(), payloadJson)

            SyncEntityType.CARD.name ->
                LanSyncJson.json.decodeFromString(SyncCardPayload.serializer(), payloadJson)

            SyncEntityType.QUESTION.name ->
                LanSyncJson.json.decodeFromString(SyncQuestionPayload.serializer(), payloadJson)

            SyncEntityType.REVIEW_RECORD.name ->
                LanSyncJson.json.decodeFromString(SyncReviewRecordPayload.serializer(), payloadJson)
        }
    }

    /**
     * 提醒时间在恢复后会立即参与调度，因此必须在校验阶段就保证格式正确。
     */
    private fun parseReminderTime(value: String) {
        runCatching { TimeTextFormatter.parseHourMinute(value) }
            .getOrElse { throw IllegalArgumentException("settings.dailyReminderTime 格式无效：$value") }
    }

    /**
     * 复习阶段范围统一通过单点校验，是为了让题目与复习记录共享同一条约束来源，
     * 避免后续扩展阶段数时漏改其中一处。
     */
    private fun requireStageIndexInRange(value: Int, fieldName: String) {
        require(value in 0..ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex) {
            "$fieldName 超出支持范围"
        }
    }

    /**
     * 时间字段统一收口解析，是为了让错误文案能直接指出具体是哪一个字段损坏。
     */
    private fun requireValidEpoch(value: String, fieldName: String) {
        runCatching { BackupJson.parseEpochMillis(value) }
            .getOrElse { throw IllegalArgumentException("$fieldName 不是合法时间：$value") }
    }
}
