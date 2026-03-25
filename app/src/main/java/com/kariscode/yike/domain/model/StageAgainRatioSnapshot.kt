package com.kariscode.yike.domain.model

/**
 * 遗忘曲线以“按 stage 分组的 AGAIN 比例”表达，是为了在不引入复杂建模的情况下，
 * 让用户直观看到哪些阶段更容易遗忘，从而决定是否需要调整复习节奏或拆分内容。
 */
data class StageAgainRatioSnapshot(
    val stageIndex: Int,
    val reviewCount: Int,
    val againCount: Int
)

