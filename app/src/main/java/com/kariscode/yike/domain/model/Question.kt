package com.kariscode.yike.domain.model

/**
 * Question 是调度与复习的最小单位；将调度字段（stage/due/count 等）放在同一模型中，
 * 是为了让“评分一次即移出本轮”等规则有统一的数据承载，而不是散落在多张表或 UI 状态里。
 */
data class Question(
    val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tags: List<String>,
    val status: QuestionStatus,
    val stageIndex: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

