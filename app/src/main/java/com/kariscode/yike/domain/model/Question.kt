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
) {
    /**
     * 题目一旦从数据层进入领域模型，就应满足最基本的不变式，
     * 否则后续调度、统计和备份都会把脏值继续扩散。
     */
    init {
        require(stageIndex >= 0) { "stageIndex 不能为负数" }
        require(reviewCount >= 0) { "reviewCount 不能为负数" }
        require(lapseCount >= 0) { "lapseCount 不能为负数" }
        require(updatedAt >= createdAt) { "updatedAt 不能早于 createdAt" }
        require(lastReviewedAt == null || lastReviewedAt >= 0L) { "lastReviewedAt 不能为负数" }
        require(dueAt >= 0L) { "dueAt 不能为负数" }
    }
}

