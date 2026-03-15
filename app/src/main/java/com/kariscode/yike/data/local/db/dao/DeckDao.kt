package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kariscode.yike.data.local.db.entity.DeckEntity
import kotlinx.coroutines.flow.Flow

/**
 * 列表聚合查询的返回类型，用于减少 N+1 查询并保持统计口径集中在数据库侧。
 */
data class DeckSummaryRow(
    val id: String,
    val name: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val cardCount: Int,
    val questionCount: Int,
    val dueQuestionCount: Int
)

/**
 * DAO 是 data 层对数据库的最小抽象边界；把查询集中在 DAO 中，
 * 能避免 Repository 或 UseCase 里手写 SQL 片段导致统计口径不一致。
 */
@Dao
interface DeckDao {
    /**
     * 首版使用 Upsert 以降低“创建/编辑”分支复杂度，
     * 这样 UI 只需提交最终表单即可，不必先判断是否已存在再分支调用 insert/update。
     */
    @Upsert
    suspend fun upsert(deck: DeckEntity): Long

    /**
     * 提供 Flow 版本是为了后续首页/列表能在数据变更时自动刷新，
     * 避免首版在多个页面手动触发 reload 造成状态不一致。
     */
    @Query("SELECT * FROM deck WHERE archived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveDecks(): Flow<List<DeckEntity>>

    /**
     * 备份导出需要完整读取全部卡组（包括已归档项），
     * 否则恢复后会丢失用户显式保留的历史内容。
     */
    @Query("SELECT * FROM deck ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun listAll(): List<DeckEntity>

    /**
     * 通过一次聚合查询返回列表所需的基础统计，避免在 UI 层逐条查询导致性能与口径问题。
     */
    @Query(
        """
        SELECT
            d.id AS id,
            d.name AS name,
            d.description AS description,
            d.archived AS archived,
            d.sortOrder AS sortOrder,
            d.createdAt AS createdAt,
            d.updatedAt AS updatedAt,
            COUNT(DISTINCT c.id) AS cardCount,
            COUNT(DISTINCT q.id) AS questionCount,
            COUNT(DISTINCT CASE WHEN q.dueAt <= :nowEpochMillis THEN q.id END) AS dueQuestionCount
        FROM deck d
        LEFT JOIN card c ON c.deckId = d.id AND c.archived = 0
        LEFT JOIN question q ON q.cardId = c.id AND q.status = :activeStatus
        WHERE d.archived = 0
        GROUP BY d.id
        ORDER BY d.sortOrder ASC, d.createdAt ASC
        """
    )
    fun observeActiveDeckSummaries(activeStatus: String, nowEpochMillis: Long): Flow<List<DeckSummaryRow>>

    /**
     * 首页最近卡组只需要一小段快照，数据库侧限量可以避免先构造完整列表再让上层截断。
     */
    @Query(
        """
        SELECT
            d.id AS id,
            d.name AS name,
            d.description AS description,
            d.archived AS archived,
            d.sortOrder AS sortOrder,
            d.createdAt AS createdAt,
            d.updatedAt AS updatedAt,
            COUNT(DISTINCT c.id) AS cardCount,
            COUNT(DISTINCT q.id) AS questionCount,
            COUNT(DISTINCT CASE WHEN q.dueAt <= :nowEpochMillis THEN q.id END) AS dueQuestionCount
        FROM deck d
        LEFT JOIN card c ON c.deckId = d.id AND c.archived = 0
        LEFT JOIN question q ON q.cardId = c.id AND q.status = :activeStatus
        WHERE d.archived = 0
        GROUP BY d.id
        ORDER BY d.sortOrder ASC, d.createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun listRecentActiveDeckSummaries(
        activeStatus: String,
        nowEpochMillis: Long,
        limit: Int
    ): List<DeckSummaryRow>

    @Query("SELECT * FROM deck WHERE id = :deckId LIMIT 1")
    suspend fun findById(deckId: String): DeckEntity?

    /**
     * 恢复导入需要批量重建卡组层级，因此 DAO 必须提供批量 upsert 入口，
     * 以避免在事务中逐条写入增加失败窗口。
     */
    @Upsert
    suspend fun upsertAll(decks: List<DeckEntity>): List<Long>

    @Query("UPDATE deck SET archived = :archived, updatedAt = :updatedAt WHERE id = :deckId")
    suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long): Int

    /**
     * 按 id 直接删除可避免 Repository 先额外读取一次实体，
     * 同时仍然保留“找不到记录时静默无操作”的数据库语义。
     */
    @Query("DELETE FROM deck WHERE id = :deckId")
    suspend fun deleteById(deckId: String): Int

    /**
     * 全量覆盖恢复前必须先清空旧数据，
     * 这样才能保证“恢复后结果完全等于备份内容”的语义成立。
     */
    @Query("DELETE FROM deck")
    suspend fun clearAll(): Int
}
