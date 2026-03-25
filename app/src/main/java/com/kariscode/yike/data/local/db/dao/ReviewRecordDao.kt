package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * 统计摘要行把评分分布和平均耗时一次性聚合出来，
 * 是为了让统计页避免在内存里重复扫描历史记录。
 */
data class ReviewAnalyticsRow(
    val totalReviews: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val averageResponseTimeMs: Double?
)

/**
 * 卡组聚合统计行单独建模，是为了让统计页可以直接给出“先处理哪个卡组”的建议依据。
 */
data class DeckReviewAnalyticsRow(
    val deckId: String,
    val deckName: String,
    val reviewCount: Int,
    val againCount: Int,
    val averageResponseTimeMs: Double?
)

/**
 * 遗忘曲线聚合行只保留 stage 维度的 AGAIN/总量，是为了让上层自由决定如何绘图（折线/柱状），
 * 同时确保口径始终来自同一条 SQL 聚合而不是散落的内存计算。
 */
data class StageAgainRatioRow(
    val stageIndex: Int,
    val reviewCount: Int,
    val againCount: Int
)

/**
 * ReviewRecord 只允许追加写入，不允许编辑，
 * 这样才能保证复习历史可追溯，且备份恢复后的历史不会被 UI 操作意外篡改。
 */
@Dao
interface ReviewRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ReviewRecordEntity): Long

    @Query("SELECT * FROM review_record WHERE questionId = :questionId ORDER BY reviewedAt DESC")
    fun observeByQuestion(questionId: String): Flow<List<ReviewRecordEntity>>

    /**
     * 时间戳序列留给上层按本地时区计算连续学习天数，
     * 可以避免数据库侧以 UTC 切天后和用户感知日期不一致。
     */
    @Query(
        """
        SELECT rr.reviewedAt
        FROM review_record rr
        JOIN question q ON q.id = rr.questionId
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE (:startEpochMillis IS NULL OR rr.reviewedAt >= :startEpochMillis)
          AND d.archived = 0
          AND c.archived = 0
        ORDER BY rr.reviewedAt DESC
        """
    )
    suspend fun listReviewTimestamps(startEpochMillis: Long?): List<Long>

    /**
     * 统计摘要只聚合未归档层级的历史，是为了让“当前活跃内容的学习效果”与内容管理默认口径一致。
     */
    @Query(
        """
        SELECT
            COUNT(rr.id) AS totalReviews,
            COALESCE(SUM(CASE WHEN rr.rating = 'AGAIN' THEN 1 ELSE 0 END), 0) AS againCount,
            COALESCE(SUM(CASE WHEN rr.rating = 'HARD' THEN 1 ELSE 0 END), 0) AS hardCount,
            COALESCE(SUM(CASE WHEN rr.rating = 'GOOD' THEN 1 ELSE 0 END), 0) AS goodCount,
            COALESCE(SUM(CASE WHEN rr.rating = 'EASY' THEN 1 ELSE 0 END), 0) AS easyCount,
            AVG(rr.responseTimeMs) AS averageResponseTimeMs
        FROM review_record rr
        JOIN question q ON q.id = rr.questionId
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND (:startEpochMillis IS NULL OR rr.reviewedAt >= :startEpochMillis)
        """
    )
    suspend fun getReviewAnalytics(startEpochMillis: Long?): ReviewAnalyticsRow

    /**
     * 卡组拆分统计下推到数据库，是为了避免统计页先拉全量记录再在内存里做多层分组。
     */
    @Query(
        """
        SELECT
            d.id AS deckId,
            d.name AS deckName,
            COUNT(rr.id) AS reviewCount,
            COALESCE(SUM(CASE WHEN rr.rating = 'AGAIN' THEN 1 ELSE 0 END), 0) AS againCount,
            AVG(rr.responseTimeMs) AS averageResponseTimeMs
        FROM review_record rr
        JOIN question q ON q.id = rr.questionId
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND (:startEpochMillis IS NULL OR rr.reviewedAt >= :startEpochMillis)
        GROUP BY d.id
        ORDER BY reviewCount DESC, deckName ASC
        """
    )
    suspend fun listDeckReviewAnalytics(startEpochMillis: Long?): List<DeckReviewAnalyticsRow>

    /**
     * 遗忘曲线按“评分时的旧 stage”分组，是为了贴近用户当时的记忆状态；
     * 如果按 newStageIndex 分组，会把 AGAIN 这种“打回去”的评分映射到更低 stage，反而掩盖问题来源。
     */
    @Query(
        """
        SELECT
            rr.oldStageIndex AS stageIndex,
            COUNT(rr.id) AS reviewCount,
            COALESCE(SUM(CASE WHEN rr.rating = 'AGAIN' THEN 1 ELSE 0 END), 0) AS againCount
        FROM review_record rr
        JOIN question q ON q.id = rr.questionId
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND (:startEpochMillis IS NULL OR rr.reviewedAt >= :startEpochMillis)
        GROUP BY rr.oldStageIndex
        ORDER BY rr.oldStageIndex ASC
        """
    )
    suspend fun listStageAgainRatios(startEpochMillis: Long?): List<StageAgainRatioRow>

    /**
     * 备份导出必须保留完整历史记录，
     * 否则恢复后会丢失评分链路，影响调度排查与后续统计扩展。
     */
    @Query("SELECT * FROM review_record ORDER BY reviewedAt ASC")
    suspend fun listAll(): List<ReviewRecordEntity>

    /**
     * 恢复导入需要批量重建复习历史，因此必须提供批量写入口以减少事务中的样板代码。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ReviewRecordEntity>): List<Long>

    /**
     * 清空复习记录是全量覆盖恢复的必要前置动作，
     * 否则旧历史会与新备份混杂，破坏“恢复结果完全等于备份”的语义。
     */
    @Query("DELETE FROM review_record")
    suspend fun clearAll(): Int
}
