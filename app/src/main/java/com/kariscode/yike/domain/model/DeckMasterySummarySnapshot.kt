package com.kariscode.yike.domain.model

/**
 * 卡组熟练度摘要作为独立领域模型存在，是为了让仓储、用例和页面都围绕同一份聚合结果协作，
 * 避免“仓储返回原始题目列表、用例再二次统计”的临时协议继续扩散。
 */
data class DeckMasterySummarySnapshot(
    val totalQuestions: Int,
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int
)
