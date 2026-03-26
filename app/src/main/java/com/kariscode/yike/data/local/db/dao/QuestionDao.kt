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
 * CSV 导出只需要题目与所属层级的少量字段，单独定义行模型是为了避免复用更重的上下文行，
 * 同时把“导出只包含活跃内容”这一过滤口径固定在数据库边界。
 */
data class CsvQuestionExportRow(
    val deckName: String,
    val cardTitle: String,
    val cardDescription: String,
    val prompt: String,
    val answer: String,
    val tagsJson: String,
    val stageIndex: Int,
    val dueAt: Long
)

/**
 * 熟练度摘要行把卡片页需要的四档统计一次性聚合出来，
 * 是为了避免上层为了几个数字先拉整批题目再重复执行领域级分类。
 */
data class DeckMasterySummaryRow(
    val totalQuestions: Int,
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int
)

/**
 * 标签聚合行把“标签去重 + 使用次数”下推到 SQL，是为了避免应用层把所有 tagsJson 全量加载进内存后再解析聚合，
 * 从而在题库规模变大时仍能保持稳定的内存占用与查询延迟。
 */
data class TagUsageRow(
    val tag: String,
    val usageCount: Int
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
     * 导出 CSV 的查询直接在数据库层过滤归档与非活跃状态，
     * 是为了确保导出范围与应用内“活跃内容口径”保持一致，不把回收站/归档内容带出。
     */
    @Query(
        """
        SELECT
            d.name AS deckName,
            c.title AS cardTitle,
            c.description AS cardDescription,
            q.prompt AS prompt,
            q.answer AS answer,
            q.tagsJson AS tagsJson,
            q.stageIndex AS stageIndex,
            q.dueAt AS dueAt
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
        ORDER BY d.sortOrder ASC, d.createdAt ASC, d.id ASC, c.sortOrder ASC, c.createdAt ASC, c.id ASC, q.createdAt ASC, q.id ASC
        """
    )
    suspend fun listCsvExportRows(activeStatus: String): List<CsvQuestionExportRow>

    /**
     * 标签候选由数据库层直接聚合，是为了让“常用标签”只读取需要的前 N 项，
     * 避免应用层先把所有 tagsJson 拉到内存后再解码、去重、计数。
     */
    @Query(
        """
        SELECT
            je.value AS tag,
            COUNT(*) AS usageCount
        FROM question q
        JOIN json_each(q.tagsJson) je
        WHERE q.status = :activeStatus
          AND TRIM(je.value) <> ''
        GROUP BY je.value
        ORDER BY usageCount DESC, LOWER(je.value) ASC
        LIMIT :limit
        """
    )
    suspend fun listTagUsages(activeStatus: String, limit: Int): List<TagUsageRow>

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
     * 卡组熟练度摘要直接在数据库完成分类计数，是为了让卡片页拿到稳定数字时不必额外装载题目上下文。
     */
    @Query(
        """
        SELECT
            COUNT(q.id) AS totalQuestions,
            SUM(CASE WHEN q.reviewCount <= 0 THEN 1 ELSE 0 END) AS newCount,
            SUM(
                CASE
                    WHEN q.reviewCount > 0
                     AND MIN(MAX(q.stageIndex, 0), 7) >= 6
                     AND q.lapseCount = 0
                    THEN 1
                    ELSE 0
                END
            ) AS masteredCount,
            SUM(
                CASE
                    WHEN q.reviewCount > 0
                     AND MIN(MAX(q.stageIndex, 0), 7) >= 3
                     AND q.lapseCount <= 1
                     AND NOT (
                        MIN(MAX(q.stageIndex, 0), 7) >= 6
                        AND q.lapseCount = 0
                     )
                    THEN 1
                    ELSE 0
                END
            ) AS familiarCount,
            SUM(
                CASE
                    WHEN q.reviewCount > 0
                     AND NOT (
                        MIN(MAX(q.stageIndex, 0), 7) >= 3
                        AND q.lapseCount <= 1
                     )
                    THEN 1
                    ELSE 0
                END
            ) AS learningCount
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND c.deckId = :deckId
          AND q.status = :activeStatus
        """
    )
    suspend fun getDeckMasterySummary(deckId: String, activeStatus: String): DeckMasterySummaryRow

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
     * 未来到期预测只需要 dueAt 时间戳序列，因此在 DAO 层直接返回 Long 列表，
     * 既能减少实体装载开销，也能让上层按本地日期自由分桶。
     */
    @Query(
        """
        SELECT q.dueAt
        FROM question q
        JOIN card c ON c.id = q.cardId
        JOIN deck d ON d.id = c.deckId
        WHERE d.archived = 0
          AND c.archived = 0
          AND q.status = :activeStatus
          AND q.dueAt >= :startEpochMillis
          AND q.dueAt < :endEpochMillis
        ORDER BY q.dueAt ASC
        """
    )
    suspend fun listUpcomingDueAts(
        activeStatus: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<Long>

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
     * 启动期索引补建按分页拉取题目，是为了避免把整张题目表一次性装进内存后再做分词。
     */
    @Query("SELECT * FROM question ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun listPage(limit: Int, offset: Int): List<QuestionEntity>

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
