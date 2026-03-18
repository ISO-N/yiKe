package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.data.mapper.toEntity
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow

/**
 * Card 的读写实现收敛在 Repository 中，能保证归档/删除等行为通过同一通路触发，
 * 并使后续需要在写入后刷新统计/重建提醒时更易扩展。
 */
class OfflineCardRepository(
    private val cardDao: CardDao,
    private val dispatchers: AppDispatchers,
    private val timeProvider: TimeProvider,
    private val syncChangeRecorder: LanSyncChangeRecorder
) : CardRepository {
    /**
     * 观察式查询能让卡片列表在新增/归档后自动刷新，避免页面状态手动同步。
     */
    override fun observeActiveCards(deckId: String): Flow<List<Card>> =
        cardDao.observeActiveCards(deckId).mapEach { entity ->
            entity.toDomain()
        }

    /**
     * 卡片快照读取用于短生命周期筛选场景，是为了避免搜索初始化时创建后立即取消的订阅。
     */
    override suspend fun listActiveCards(deckId: String): List<Card> = dispatchers.onIo {
        cardDao.listActiveCards(deckId).map { entity ->
            entity.toDomain()
        }
    }

    /**
     * 通过聚合查询提供列表统计，避免 UI 层逐项查询带来的性能与口径风险。
     */
    override fun observeActiveCardSummaries(deckId: String, nowEpochMillis: Long): Flow<List<CardSummary>> =
        cardDao.observeActiveCardSummaries(
            deckId = deckId,
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis
        )
            .mapEach { row -> row.toDomain() }

    /**
     * 已归档卡片的读取单独走带卡组名称的聚合查询，是为了让回收站直接具备恢复所需上下文。
     */
    override fun observeArchivedCardSummaries(nowEpochMillis: Long): Flow<List<ArchivedCardSummary>> =
        cardDao.observeArchivedCardSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis
        ).mapEach { row -> row.toDomain() }

    /**
     * cardId 查询用于编辑页/复习页基于路由参数重建内容。
     */
    override suspend fun findById(cardId: String): Card? = dispatchers.onIo {
        cardDao.findById(cardId).mapNullable { entity ->
            entity.toDomain()
        }
    }

    /**
     * Upsert 统一创建与编辑行为，减少上层分支并确保写入口径一致。
     */
    override suspend fun upsert(card: Card) = dispatchers.onIo {
        cardDao.upsert(card.toEntity())
        syncChangeRecorder.recordCardUpsert(card)
        Unit
    }

    /**
     * 归档通过字段更新完成，便于默认列表过滤并降低误删风险。
     */
    override suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long) =
        dispatchers.onIo {
            val current = cardDao.findById(cardId)?.let { entity ->
                entity.toDomain()
            }
            cardDao.setArchived(cardId = cardId, archived = archived, updatedAt = updatedAt)
            val updatedCard = current?.copy(archived = archived, updatedAt = updatedAt)
            if (updatedCard != null) {
                syncChangeRecorder.recordCardUpsert(updatedCard)
            }
            Unit
        }

    /**
     * 删除依赖级联约束清理下层数据，因此直接按 id 触发 DAO 删除就足够，
     * 不需要为了同一条删除语义先额外读取实体。
     */
    override suspend fun delete(cardId: String) = dispatchers.onIo {
        val current = cardDao.findById(cardId)?.let { entity ->
            entity.toDomain()
        }
        cardDao.deleteById(cardId)
        syncChangeRecorder.recordDelete(
            entityType = SyncEntityType.CARD,
            entityId = cardId,
            summary = current?.title ?: cardId,
            modifiedAt = current?.updatedAt ?: timeProvider.nowEpochMillis()
        )
        Unit
    }
}
