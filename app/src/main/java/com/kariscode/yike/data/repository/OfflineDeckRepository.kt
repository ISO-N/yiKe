package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.DeckSummaryRow
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.RoomMappers
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 离线优先首版直接以 Room 作为 Repository 的数据源，
 * 这样可以先把“正确的分层与口径”跑通，再考虑未来同步或缓存策略。
 */
class OfflineDeckRepository(
    private val deckDao: DeckDao,
    private val dispatchers: AppDispatchers
) : DeckRepository {
    /**
     * 直接映射 Flow 能让 UI 不触碰 Entity，从而保持 Room 细节不外泄。
     */
    override fun observeActiveDecks(): Flow<List<Deck>> =
        deckDao.observeActiveDecks().map { list ->
            list.map { entity -> RoomMappers.run { entity.toDomain() } }
        }

    /**
     * 快照读取与订阅读取共享同一映射逻辑，是为了让搜索页拿到的数据口径与列表页保持一致。
     */
    override suspend fun listActiveDecks(): List<Deck> = withContext(dispatchers.io) {
        deckDao.listActiveDecks().map { entity ->
            RoomMappers.run { entity.toDomain() }
        }
    }

    /**
     * 通过数据库聚合流提供统计信息，能让列表页在数据变更时稳定刷新且避免 N+1 查询。
     */
    override fun observeActiveDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> =
        deckDao.observeActiveDeckSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis
        )
            .map { list -> list.map(::toDeckSummary) }

    /**
     * 首页走限量快照查询可把“只展示少量入口”的意图下推到数据层，减少无意义的聚合结果构建。
     */
    override suspend fun listRecentActiveDeckSummaries(
        nowEpochMillis: Long,
        limit: Int
    ): List<DeckSummary> = withContext(dispatchers.io) {
        deckDao.listRecentActiveDeckSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis,
            limit = limit
        ).map(::toDeckSummary)
    }

    /**
     * IO 查询放在 dispatchers.io 上执行，避免在主线程触发磁盘读写导致卡顿。
     */
    override suspend fun findById(deckId: String): Deck? = withContext(dispatchers.io) {
        deckDao.findById(deckId)?.let { entity ->
            RoomMappers.run { entity.toDomain() }
        }
    }

    /**
     * Upsert 后不返回值是为了让上层用例以显式的领域模型作为唯一来源，避免依赖 rowId。
     */
    override suspend fun upsert(deck: Deck) = withContext(dispatchers.io) {
        deckDao.upsert(RoomMappers.run { deck.toEntity() })
        Unit
    }

    /**
     * 归档作为轻量写入操作仍需在 IO 线程执行，避免在列表交互时阻塞 UI。
     */
    override suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long) =
        withContext(dispatchers.io) {
            deckDao.setArchived(deckId = deckId, archived = archived, updatedAt = updatedAt)
            Unit
        }

    /**
     * 直接按 id 删除可减少一次无意义读取，
     * 同时仍保留“记录不存在时静默无操作”的容错语义。
     */
    override suspend fun delete(deckId: String) = withContext(dispatchers.io) {
        deckDao.deleteById(deckId)
        Unit
    }

    /**
     * 将聚合行转换为 domain 模型是为了让上层完全不依赖 SQL 别名与聚合字段命名，
     * 从而保持统计口径变更时的影响面可控。
     */
    private fun toDeckSummary(row: DeckSummaryRow): DeckSummary = DeckSummary(
        deck = Deck(
            id = row.id,
            name = row.name,
            description = row.description,
            archived = row.archived,
            sortOrder = row.sortOrder,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt
        ),
        cardCount = row.cardCount,
        questionCount = row.questionCount,
        dueQuestionCount = row.dueQuestionCount
    )
}
