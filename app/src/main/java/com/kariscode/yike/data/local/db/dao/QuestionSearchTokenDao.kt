package com.kariscode.yike.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kariscode.yike.data.local.db.entity.QuestionSearchTokenEntity

/**
 * 搜索 token DAO 只负责覆盖式写入和基线统计，
 * 是为了让索引维护保持简单可重建，而不引入额外状态机。
 */
@Dao
interface QuestionSearchTokenDao {
    /**
     * 同一题目每次重建 token 时都先清旧再写新，是为了让索引永远只反映当前正文状态。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tokens: List<QuestionSearchTokenEntity>)

    /**
     * 单题覆盖式重建需要先删旧 token，才能避免正文变短后残留已经失效的片段。
     */
    @Query("DELETE FROM question_search_token WHERE questionId = :questionId")
    suspend fun deleteByQuestionId(questionId: String)

    /**
     * 全量重建时清空旧索引，是为了避免历史脏 token 混入新一轮搜索候选集。
     */
    @Query("DELETE FROM question_search_token")
    suspend fun clearAll()

    /**
     * 先按 token 命中取回原始索引行，再由仓储层聚合筛掉不完整命中的题目，
     * 是为了优先依赖最稳定的等值查询，避开 Room 对复杂聚合 SQL 的处理边界。
     */
    @Query("SELECT * FROM question_search_token WHERE token IN (:tokens)")
    suspend fun listByTokens(tokens: List<String>): List<QuestionSearchTokenEntity>

    /**
     * 用索引覆盖题目数和真实题目数比对，可以快速判断现有安装是否需要启动期回填。
     */
    @Query("SELECT COUNT(DISTINCT questionId) FROM question_search_token")
    suspend fun countIndexedQuestions(): Int
}
