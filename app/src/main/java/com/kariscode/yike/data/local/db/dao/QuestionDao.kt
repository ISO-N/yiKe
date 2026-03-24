package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 首页与提醒使用的聚合统计返回类型，用于把统计口径固定在数据库层，避免上层再二次计数造成偏差。
 */
data class TodayReviewSummaryRow(
    val dueCardCount: Int,
    val dueQuestionCount: Int
)

/**
 * 题目上下文行把问题和所属层级一并返回，是为了让搜索与预览页面避免再次做 N+1 层级查询。
 */
data class QuestionContextRow(
    val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tagsJson: String,
    val status: String,
    val stageIndex: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deckId: String,
    val deckName: String,
    val cardTitle: String
)

/**
 * QuestionDao 的查询需要显式包含 status 与 due 条件，
 * 这是为了保证“归档不出现”和“今日到期口径”在全应用范围内一致。
 */
@Dao
interface QuestionDao {
    @Upsert
    suspend fun upsertAll(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM question WHERE cardId = :cardId ORDER BY createdAt ASC")
    fun observeQuestionsByCard(cardId: String): Flow<List<QuestionEntity>>

    /**
     * 编辑页在进入和保存后只需要一个即时快照，直接查询可避免为单次读取走完整观察链路。
     */
    @Query("SELECT * FROM question WHERE cardId = :cardId ORDER BY createdAt ASC")
    suspend fun listByCard(cardId: String): List<QuestionEntity>

    @Query("SELECT * FROM question WHERE id = :questionId LIMIT 1")
    suspend fun findById(questionId: String): QuestionEntity?

    /**
     * 批量删除前需要先取回摘要信息时，直接按 id 集合查询可避免 Repository 反复单条 findById。
     */
    @Query("SELECT * FROM question WHERE id IN (:questionIds)")
    suspend fun listByIds(questionIds: List<String>): List<QuestionEntity>

    /**
     * 评分事务需要知道题目所属卡组的调度上限，
     * 在 DAO 层直接关联查询可以避免仓储为了一次提交再拼多跳读取链路。
     */
    @Query(
        """
        SELECT d.intervalStepCount
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE q.id = :questionId
        LIMIT 1
        """
    )
    suspend fun findDeckIntervalStepCountByQuestionId(questionId: String): Int?

    /**
     * 搜索与今日预览都需要题目附带所属卡组/卡片信息，
     * 因此在 DAO 层直接完成关联能避免上层反复拼接层级数据。
     */
    @Query(
        """
        SELECT
            q.id AS id,
            q.cardId AS cardId,
            q.prompt AS prompt,
            q.answer AS answer,
            q.tagsJson AS tagsJson,
            q.status AS status,
            q.stageIndex AS stageIndex,
            q.dueAt AS dueAt,
            q.lastReviewedAt AS lastReviewedAt,
            q.reviewCount AS reviewCount,
            q.lapseCount AS lapseCount,
            q.createdAt AS createdAt,
            q.updatedAt AS updatedAt,
            c.deckId AS deckId,
            d.name AS deckName,
            c.title AS cardTitle
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND (:status IS NULL OR q.status = :status)
          AND (:deckId IS NULL OR c.deckId = :deckId)
          AND (:cardId IS NULL OR q.cardId = :cardId)
          AND (:maxDueAt IS NULL OR q.dueAt <= :maxDueAt)
          AND (:includeAllQuestionIds = 1 OR q.id IN (:questionIds))
          AND (
            :keyword IS NULL OR :keyword = ''
            OR (
                q.prompt LIKE '%' || :keyword || '%'
                OR q.answer LIKE '%' || :keyword || '%'
            )
          )
          AND (
            :tagKeyword IS NULL OR :tagKeyword = ''
            OR q.tagsJson LIKE '%' || :tagKeyword || '%'
          )
        ORDER BY q.dueAt ASC, q.updatedAt DESC, q.createdAt ASC
        """
    )
    suspend fun listQuestionContexts(
        keyword: String?,
        tagKeyword: String?,
        status: String?,
        deckId: String?,
        cardId: String?,
        maxDueAt: Long?,
        includeAllQuestionIds: Boolean,
        questionIds: List<String>
    ): List<QuestionContextRow>

    /**
     * 标签候选直接读取原始 JSON 字段，是为了让仓储层统一负责解析与去重策略，
     * 避免 SQL 为了拆 JSON 引入超出当前需求的复杂度。
     */
    @Query("SELECT tagsJson FROM question WHERE status = :activeStatus")
    suspend fun listTagsJson(activeStatus: String): List<String>

    /**
     * 练习模式必须忽略 due，只按未归档层级、active 状态与用户选择范围读取，
     * 这样才能从数据库层面保证“主动练习不受正式调度限制”。
     */
    @Query(
        """
        SELECT
            q.id AS id,
            q.cardId AS cardId,
            q.prompt AS prompt,
            q.answer AS answer,
            q.tagsJson AS tagsJson,
            q.status AS status,
            q.stageIndex AS stageIndex,
            q.dueAt AS dueAt,
            q.lastReviewedAt AS lastReviewedAt,
            q.reviewCount AS reviewCount,
            q.lapseCount AS lapseCount,
            q.createdAt AS createdAt,
            q.updatedAt AS updatedAt,
            c.deckId AS deckId,
            d.name AS deckName,
            c.title AS cardTitle
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND (:includeAllDecks = 1 OR c.deckId IN (:deckIds))
          AND (:includeAllCards = 1 OR q.cardId IN (:cardIds))
          AND (:includeAllQuestions = 1 OR q.id IN (:questionIds))
        ORDER BY d.sortOrder ASC, d.createdAt ASC, d.id ASC, c.sortOrder ASC, c.createdAt ASC, c.id ASC, q.createdAt ASC, q.id ASC
        """
    )
    suspend fun listPracticeQuestionContexts(
        activeStatus: String,
        includeAllDecks: Boolean,
        deckIds: List<String>,
        includeAllCards: Boolean,
        cardIds: List<String>,
        includeAllQuestions: Boolean,
        questionIds: List<String>
    ): List<QuestionContextRow>

    /**
     * 复习页需要只加载当前卡片本轮到期的问题，
     * 这样才能满足“按卡片组织并按问题逐题推进”的交互约束。
     */
    @Query(
        """
        SELECT q.* FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE q.cardId = :cardId
          AND d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND q.dueAt <= :nowEpochMillis
        ORDER BY q.dueAt ASC, q.createdAt ASC
        """
    )
    suspend fun listDueQuestionsByCard(
        cardId: String,
        activeStatus: String,
        nowEpochMillis: Long
    ): List<QuestionEntity>

    /**
     * due 查询必须排除已归档的卡片与卡组，否则内容管理归档会与复习/提醒口径冲突。
     */
    @Query(
        """
        SELECT q.* FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND q.dueAt <= :nowEpochMillis
        """
    )
    suspend fun listDueQuestions(activeStatus: String, nowEpochMillis: Long): List<QuestionEntity>

    /**
     * 队列页把“下一张卡片”判断下推到数据库，可以避免把全部到期问题加载到内存后再做分组筛选。
     */
    @Query(
        """
        SELECT c.id
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND q.dueAt <= :nowEpochMillis
        GROUP BY c.id
        ORDER BY MIN(q.dueAt) ASC, MIN(q.createdAt) ASC
        LIMIT 1
        """
    )
    suspend fun findNextDueCardId(activeStatus: String, nowEpochMillis: Long): String?

    /**
     * 首页概览统计一次性返回卡片数与问题数，避免上层通过“拉全量 due 列表再 distinct 计数”的低效做法。
     */
    @Query(
        """
        SELECT
            COUNT(DISTINCT c.id) AS dueCardCount,
            COUNT(DISTINCT q.id) AS dueQuestionCount
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND q.dueAt <= :nowEpochMillis
        """
    )
    suspend fun getTodayReviewSummary(activeStatus: String, nowEpochMillis: Long): TodayReviewSummaryRow

    /**
     * 备份导出需要读取完整问题集合，
     * 以保证调度字段与归档状态能原样进入导出文件。
     */
    @Query("SELECT * FROM question ORDER BY createdAt ASC")
    suspend fun listAll(): List<QuestionEntity>

    /**
     * 直接按 id 删除可以把“对象不存在时无操作”的判定留给数据库，
     * 从而避免 Repository 为了删除再做一次额外读取。
     */
    @Query("DELETE FROM question WHERE id = :questionId")
    suspend fun deleteById(questionId: String): Int

    /**
     * 多选删除由数据库直接处理 id 集合，可以避免编辑页为同一次保存反复进出 DAO。
     */
    @Query("DELETE FROM question WHERE id IN (:questionIds)")
    suspend fun deleteByIds(questionIds: Collection<String>): Int

    /**
     * 清空问题表是恢复流程的必要步骤，
     * 否则旧问题可能继续参与 due 查询，破坏恢复后的结果一致性。
     */
    @Query("DELETE FROM question")
    suspend fun clearAll(): Int
}
