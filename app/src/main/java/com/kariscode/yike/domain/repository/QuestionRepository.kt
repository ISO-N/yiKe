package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.TodayReviewSummary
import kotlinx.coroutines.flow.Flow

/**
 * QuestionRepository 用于统一“问题编辑/待复习查询/备份导出”等入口的读取口径，
 * 防止不同功能各自拼装查询条件，最终导致 due 与归档规则不一致。
 */
interface QuestionRepository {
    /**
     * 编辑页需要以 cardId 观察问题列表，确保新增/删除后 UI 能即时反映并避免状态分叉。
     */
    fun observeQuestionsByCard(cardId: String): Flow<List<Question>>

    /**
     * 单对象读取用于评分提交等场景精确定位问题，避免在 UI 层缓存过期对象。
     */
    suspend fun findById(questionId: String): Question?

    /**
     * 编辑页重新载入只需要一个稳定快照，提供直接查询可避免为一次性读取建立额外 Flow 订阅。
     */
    suspend fun listByCard(cardId: String): List<Question>

    /**
     * 批量 upsert 能让编辑页一次性保存多问题，避免逐条写入造成中途失败的半成功状态。
     */
    suspend fun upsertAll(questions: List<Question>)

    /**
     * due 查询集中在仓储接口，能保证首页统计、提醒 Worker 与复习队列使用一致口径。
     */
    suspend fun listDueQuestions(nowEpochMillis: Long): List<Question>

    /**
     * 首页与提醒需要“卡片数 + 问题数”的概览统计；
     * 将其作为仓储能力可以保证统计口径一致并避免上层通过拉全量列表再计数。
     */
    suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary

    /**
     * 删除问题应同时级联清理复习记录；仓储提供以 ID 删除的语义接口以减少上层对实体的依赖。
     */
    suspend fun delete(questionId: String)
}
