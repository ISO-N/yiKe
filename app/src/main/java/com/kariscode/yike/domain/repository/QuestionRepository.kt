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
     * 单对象读取用于评分提交等场景精确定位问题，避免在 UI 层缓存过期对象；
     * 当问题不存在时返回 null，让调用方显式决定是报错、跳转还是跳过。
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
     * 复习队列只需要下一张卡片的路由目标，直接返回卡片 id 可以避免先拉全量问题再在内存分组；
     * 如果当前没有到期题目则返回 null，调用方可据此切到“今日已完成”状态。
     */
    suspend fun findNextDueCardId(nowEpochMillis: Long): String?

    /**
     * 首页与提醒需要“卡片数 + 问题数”的概览统计；
     * 将其作为仓储能力可以保证统计口径一致并避免上层通过拉全量列表再计数。
     */
    suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary

    /**
     * 删除问题应同时级联清理复习记录；仓储提供以 ID 删除的语义接口以减少上层对实体的依赖。
     */
    suspend fun delete(questionId: String)

    /**
     * 编辑页批量保存时提供集合删除能力，可以把多次往返压缩成一次写操作并缩短保存窗口。
     */
    suspend fun deleteAll(questionIds: Collection<String>)
}
