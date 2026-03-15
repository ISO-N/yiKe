package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.CardSummaryRow
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.RoomMappers
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Card 的读写实现收敛在 Repository 中，能保证归档/删除等行为通过同一通路触发，
 * 并使后续需要在写入后刷新统计/重建提醒时更易扩展。
 */
class OfflineCardRepository(
    private val cardDao: CardDao,
    private val dispatchers: AppDispatchers
) : CardRepository {
    /**
     * 观察式查询能让卡片列表在新增/归档后自动刷新，避免页面状态手动同步。
     */
    override fun observeActiveCards(deckId: String): Flow<List<Card>> =
        cardDao.observeActiveCards(deckId).map { list -> list.map { entity -> RoomMappers.run { entity.toDomain() } } }

    /**
     * 通过聚合查询提供列表统计，避免 UI 层逐项查询带来的性能与口径风险。
     */
    override fun observeActiveCardSummaries(deckId: String): Flow<List<CardSummary>> =
        cardDao.observeActiveCardSummaries(deckId = deckId, activeStatus = QuestionEntity.STATUS_ACTIVE)
            .map { list -> list.map(::toCardSummary) }

    /**
     * cardId 查询用于编辑页/复习页基于路由参数重建内容。
     */
    override suspend fun findById(cardId: String): Card? = withContext(dispatchers.io) {
        cardDao.findById(cardId)?.let { entity -> RoomMappers.run { entity.toDomain() } }
    }

    /**
     * Upsert 统一创建与编辑行为，减少上层分支并确保写入口径一致。
     */
    override suspend fun upsert(card: Card) = withContext(dispatchers.io) {
        cardDao.upsert(RoomMappers.run { card.toEntity() })
        Unit
    }

    /**
     * 归档通过字段更新完成，便于默认列表过滤并降低误删风险。
     */
    override suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long) =
        withContext(dispatchers.io) {
            cardDao.setArchived(cardId = cardId, archived = archived, updatedAt = updatedAt)
            Unit
        }

    /**
     * 删除依赖级联约束清理下层数据，因此必须通过 DAO 触发而不是手写多表删除。
     */
    override suspend fun delete(cardId: String) = withContext(dispatchers.io) {
        val entity = cardDao.findById(cardId) ?: return@withContext
        cardDao.delete(entity)
        Unit
    }

    /**
     * CardSummary 的转换在 data 层完成，能避免 UI 层理解数据库聚合细节，
     * 并为后续扩展更多统计字段提供稳定扩展点。
     */
    private fun toCardSummary(row: CardSummaryRow): CardSummary = CardSummary(
        card = Card(
            id = row.id,
            deckId = row.deckId,
            title = row.title,
            description = row.description,
            archived = row.archived,
            sortOrder = row.sortOrder,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt
        ),
        questionCount = row.questionCount
    )
}
