package com.kariscode.yike.domain.model

/**
 * 回收站里的卡片需要同时展示所属卡组名称，
 * 因此单独建模摘要可以避免页面为补上下文再额外发起查询。
 */
data class ArchivedCardSummary(
    val card: Card,
    val deckName: String,
    val questionCount: Int,
    val dueQuestionCount: Int
)
