package com.kariscode.yike.data.sync

import androidx.room.withTransaction
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.mapper.toEntity
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.data.settings.DataStoreAppSettingsRepository
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.mergeSyncedSettings
import com.kariscode.yike.domain.repository.AppSettingsRepository

/**
 * 远端变更应用器把落库顺序、设置合并与提醒重建集中起来，
 * 这样仓储测试可以直接围绕“收到这批变更后本地会变成什么”来断言。
 */
class LanSyncChangeApplier(
    private val database: YikeDatabase,
    private val appSettingsRepository: AppSettingsRepository,
    private val reminderScheduler: ReminderSyncScheduler,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val questionDao: QuestionDao,
    private val reviewRecordDao: ReviewRecordDao,
    private val conflictResolver: LanSyncConflictResolver
) {
    /**
     * 变更进入本地前先按实体类型与操作压缩和排序，确保外键与 settings 语义都能稳定落地。
     */
    suspend fun applyIncomingChanges(changes: List<SyncChangePayload>): Long {
        if (changes.isEmpty()) {
            return 0L
        }
        val compressed = conflictResolver.compressChanges(changes)
        val latestRemoteSeq = compressed.maxOfOrNull { it.seq } ?: 0L
        val settingsPayload = compressed
            .lastOrNull { it.entityType == SyncEntityType.SETTINGS.name && it.operation == SyncChangeOperation.UPSERT.name }
            ?.payloadJson
            ?.let { payloadJson ->
                LanSyncJson.json.decodeFromString(SyncSettingsPayload.serializer(), payloadJson)
            }

        val deckUpserts = compressed.filterType(SyncEntityType.DECK, SyncChangeOperation.UPSERT).map { change ->
            val payload = LanSyncJson.json.decodeFromString(SyncDeckPayload.serializer(), change.payloadJson.orEmpty())
            payload.toDomain().toEntity()
        }
        val cardUpserts = compressed.filterType(SyncEntityType.CARD, SyncChangeOperation.UPSERT).map { change ->
            val payload = LanSyncJson.json.decodeFromString(SyncCardPayload.serializer(), change.payloadJson.orEmpty())
            payload.toDomain().toEntity()
        }
        val questionUpserts = compressed.filterType(SyncEntityType.QUESTION, SyncChangeOperation.UPSERT).map { change ->
            val payload = LanSyncJson.json.decodeFromString(SyncQuestionPayload.serializer(), change.payloadJson.orEmpty())
            payload.toDomain().toEntity()
        }
        val reviewRecordUpserts = compressed.filterType(SyncEntityType.REVIEW_RECORD, SyncChangeOperation.UPSERT).map { change ->
            val payload = LanSyncJson.json.decodeFromString(SyncReviewRecordPayload.serializer(), change.payloadJson.orEmpty())
            payload.toDomain().toEntity()
        }
        val questionDeletes = compressed.filterType(SyncEntityType.QUESTION, SyncChangeOperation.DELETE).map { it.entityId }
        val cardDeletes = compressed.filterType(SyncEntityType.CARD, SyncChangeOperation.DELETE).map { it.entityId }
        val deckDeletes = compressed.filterType(SyncEntityType.DECK, SyncChangeOperation.DELETE).map { it.entityId }

        database.withTransaction {
            if (deckUpserts.isNotEmpty()) deckDao.upsertAll(deckUpserts)
            if (cardUpserts.isNotEmpty()) cardDao.upsertAll(cardUpserts)
            if (questionUpserts.isNotEmpty()) questionDao.upsertAll(questionUpserts)
            if (reviewRecordUpserts.isNotEmpty()) reviewRecordDao.insertAll(reviewRecordUpserts)
            questionDeletes.forEach { questionId -> questionDao.deleteById(questionId) }
            cardDeletes.forEach { cardId -> cardDao.deleteById(cardId) }
            deckDeletes.forEach { deckId -> deckDao.deleteById(deckId) }
        }
        if (settingsPayload != null) {
            val currentSettings = appSettingsRepository.getSettings()
            val mergedSettings = currentSettings.mergeSyncedSettings(settingsPayload.toDomain())
            applySyncedSettingsWithoutRecording(mergedSettings)
            reminderScheduler.syncReminder(mergedSettings)
        }
        return latestRemoteSeq
    }

    /**
     * 同步设置时优先绕过本地 journal，避免收到远端设置后再次记录为本地变更。
     */
    private suspend fun applySyncedSettingsWithoutRecording(settings: AppSettings) {
        val settingsRepository = appSettingsRepository
        if (settingsRepository is DataStoreAppSettingsRepository) {
            settingsRepository.applySyncedSettingsWithoutRecording(settings)
        } else {
            settingsRepository.setSettings(settings)
        }
    }

    /**
     * 过滤特定类型和操作的帮助函数收敛在此处，让应用顺序保持可读。
     */
    private fun List<SyncChangePayload>.filterType(
        entityType: SyncEntityType,
        operation: SyncChangeOperation
    ): List<SyncChangePayload> = filter { change ->
        change.entityType == entityType.name && change.operation == operation.name
    }
}
