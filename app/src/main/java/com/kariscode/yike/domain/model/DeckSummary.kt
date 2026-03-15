package com.kariscode.yike.domain.model

/**
 * 列表页需要的基础聚合信息应以 domain 模型承载，原因是：
 * - UI 不应依赖 SQL 聚合字段
 * - 统计口径变更时不应影响页面参数结构
 */
data class DeckSummary(
    val deck: Deck,
    val cardCount: Int,
    val questionCount: Int,
    val dueQuestionCount: Int
)

