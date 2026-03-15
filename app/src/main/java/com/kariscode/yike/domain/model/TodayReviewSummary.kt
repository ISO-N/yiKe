package com.kariscode.yike.domain.model

/**
 * 首页需要的待复习概览以 domain 模型承载，目的是让 UI 不依赖具体 SQL 聚合写法，
 * 同时确保提醒 Worker 与首页使用同一套统计口径。
 */
data class TodayReviewSummary(
    val dueCardCount: Int,
    val dueQuestionCount: Int
)

