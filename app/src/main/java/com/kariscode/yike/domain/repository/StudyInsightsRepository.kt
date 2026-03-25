package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.DeckMasterySummarySnapshot
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot

/**
 * 学习洞察仓储把统计、搜索和预览查询收口在一起，
 * 是为了避免多个页面分别拼接相似 SQL，最终让筛选与统计口径产生分叉。
 */
interface StudyInsightsRepository {
    /**
     * 搜索查询直接返回带上下文的问题结果，
     * 这样页面拿到结果后就能立刻渲染“题目属于哪个卡组/卡片”的定位信息。
     */
    suspend fun searchQuestionContexts(filters: QuestionQueryFilters): List<QuestionContext>

    /**
     * 今日预览只关心当前已经到期的问题集合，
     * 因此单独提供 due 查询可以避免预览页和搜索页复用时互相夹带无关筛选条件。
     */
    suspend fun listDueQuestionContexts(nowEpochMillis: Long): List<QuestionContext>

    /**
     * 标签候选统一来自真实题库，是为了让筛选控件只暴露当前真正可用的标签而不是静态示例。
     */
    suspend fun listAvailableTags(limit: Int): List<String>

    /**
     * 卡片页只需要卡组级熟练度摘要时，直接返回聚合结果可以避免先拉整批题目再在用例层二次计数。
     */
    suspend fun getDeckMasterySummary(deckId: String): DeckMasterySummarySnapshot

    /**
     * 统计页的主指标聚合在仓储层计算，是为了保证 AGAIN 比例、平均耗时和卡组拆分都共享一致口径。
     */
    suspend fun getReviewAnalytics(startEpochMillis: Long?): ReviewAnalyticsSnapshot

    /**
     * 连续学习天数需要基于真实复习时间戳按本地日期计算，
     * 因此提供时间序列本身比只返回数据库侧天数更便于上层按时区处理。
     */
    suspend fun listReviewTimestamps(startEpochMillis: Long?): List<Long>
}
