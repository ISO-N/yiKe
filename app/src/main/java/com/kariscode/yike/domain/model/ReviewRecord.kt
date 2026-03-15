package com.kariscode.yike.domain.model

/**
 * ReviewRecord 记录每次评分的“旧值 -> 新值”变化链路，
 * 这是为了在备份恢复、问题排查以及未来统计扩展时可还原调度行为，而不是只保留最终状态。
 */
data class ReviewRecord(
    val id: String,
    val questionId: String,
    val rating: ReviewRating,
    val oldStageIndex: Int,
    val newStageIndex: Int,
    val oldDueAt: Long,
    val newDueAt: Long,
    val reviewedAt: Long,
    val responseTimeMs: Long?,
    val note: String
)

