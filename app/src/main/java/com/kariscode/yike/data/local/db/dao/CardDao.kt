package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kariscode.yike.data.local.db.entity.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 卡片列表聚合查询的返回类型，用于把“题目数量”等统计口径固定在数据库层。
 */
data class CardSummaryRow(
    val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val questionCount: Int,
    val dueQuestionCount: Int
)

/**
 * 回收站里的卡片需要保留所属卡组上下文，
 * 这样用户才能判断恢复动作应该回到哪里。
 */
data class ArchivedCardSummaryRow(
    val id: String,
    val deckId: String,
    val deckName: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val questionCount: Int,
    val dueQuestionCount: Int
)

/**
 * CardDao 提供以 deckId 维度的查询，是为了让内容管理流保持“按层级加载”的稳定形态，
 * 这样 UI 只持有路由参数即可重建页面状态，避免跨页面传递大对象。
 */
@Dao
interface CardDao {
    @Upsert
    suspend fun upsert(card: CardEntity): Long

    @Query("SELECT * FROM card WHERE deckId = :deckId AND archived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveCards(deckId: String): Flow<List<CardEntity>>

    /**
     * 一次性筛选初始化直接读快照即可，避免搜索页为了拿候选列表额外建立长期订阅。
     */
    @Query("SELECT * FROM card WHERE deckId = :deckId AND archived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listActiveCards(deckId: String): List<CardEntity>

    /**
     * 备份导出需要保留全部卡片层级，包括已归档项，
     * 否则恢复后会破坏用户原本的管理状态。
     */
    @Query("SELECT * FROM card ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listAll(): List<CardEntity>

    /**
     * 聚合查询用于卡片列表的基础统计展示，避免在 UI 层为每张卡片单独查询问题数量造成 N+1 问题。
     */
    @Query(
        """
        SELECT
            c.id AS id,
            c.deckId AS deckId,
            c.title AS title,
            c.description AS description,
            c.archived AS archived,
            c.sortOrder AS sortOrder,
            c.createdAt AS createdAt,
            c.updatedAt AS updatedAt,
            COUNT(DISTINCT q.id) AS questionCount,
            COUNT(DISTINCT CASE WHEN q.dueAt <= :nowEpochMillis THEN q.id END) AS dueQuestionCount
        FROM card c
        LEFT JOIN question q ON q.cardId = c.id AND q.status = :activeStatus
        WHERE c.deckId = :deckId AND c.archived = 0
        GROUP BY c.id
        ORDER BY c.sortOrder ASC, c.createdAt ASC
        """
    )
    fun observeActiveCardSummaries(
        deckId: String,
        activeStatus: String,
        nowEpochMillis: Long
    ): Flow<List<CardSummaryRow>>

    /**
     * 回收站读取已归档卡片时把卡组名称一并带出，
     * 可以避免 UI 为了展示上下文再为每条卡片单独补查询。
     */
    @Query(
        """
        SELECT
            c.id AS id,
            c.deckId AS deckId,
            d.name AS deckName,
            c.title AS title,
            c.description AS description,
            c.archived AS archived,
            c.sortOrder AS sortOrder,
            c.createdAt AS createdAt,
            c.updatedAt AS updatedAt,
            COUNT(DISTINCT q.id) AS questionCount,
            COUNT(DISTINCT CASE WHEN q.dueAt <= :nowEpochMillis THEN q.id END) AS dueQuestionCount
        FROM card c
        INNER JOIN deck d ON d.id = c.deckId
        LEFT JOIN question q ON q.cardId = c.id AND q.status = :activeStatus
        WHERE c.archived = 1
        GROUP BY c.id
        ORDER BY c.updatedAt DESC, c.createdAt DESC
        """
    )
    fun observeArchivedCardSummaries(
        activeStatus: String,
        nowEpochMillis: Long
    ): Flow<List<ArchivedCardSummaryRow>>

    @Query("SELECT * FROM card WHERE id = :cardId LIMIT 1")
    suspend fun findById(cardId: String): CardEntity?

    /**
     * 恢复导入需要一次性重建卡片层级，因此提供批量写入口可缩小事务边界内的复杂度。
     */
    @Upsert
    suspend fun upsertAll(cards: List<CardEntity>): List<Long>

    @Query("UPDATE card SET archived = :archived, updatedAt = :updatedAt WHERE id = :cardId")
    suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long): Int

    /**
     * 按 id 删除能减少无意义的预读取，
     * 并继续让级联约束作为唯一的数据清理入口生效。
     */
    @Query("DELETE FROM card WHERE id = :cardId")
    suspend fun deleteById(cardId: String): Int

    /**
     * 清空卡片表是全量覆盖恢复的前置步骤，
     * 否则旧卡片会与新备份内容混杂，破坏恢复结果可预测性。
     */
    @Query("DELETE FROM card")
    suspend fun clearAll(): Int
}
