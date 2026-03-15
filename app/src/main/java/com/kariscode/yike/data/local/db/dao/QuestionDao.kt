package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
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
 * QuestionDao 的查询需要显式包含 status 与 due 条件，
 * 这是为了保证“归档不出现”和“今日到期口径”在全应用范围内一致。
 */
@Dao
interface QuestionDao {
    @Upsert
    suspend fun upsertAll(questions: List<QuestionEntity>): List<Long>

    @Query("SELECT * FROM question WHERE cardId = :cardId ORDER BY createdAt ASC")
    fun observeQuestionsByCard(cardId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM question WHERE id = :questionId LIMIT 1")
    suspend fun findById(questionId: String): QuestionEntity?

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

    @Delete
    suspend fun delete(question: QuestionEntity): Int

    /**
     * 清空问题表是恢复流程的必要步骤，
     * 否则旧问题可能继续参与 due 查询，破坏恢复后的结果一致性。
     */
    @Query("DELETE FROM question")
    suspend fun clearAll(): Int
}
