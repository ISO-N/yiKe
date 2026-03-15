package com.kariscode.yike.domain.model

/**
 * CardSummary 用于承载卡片列表中的基础统计，
 * 这样后续增加“今日到期数量”等字段时只需扩展模型而不破坏 UI 与 data 的边界。
 */
data class CardSummary(
    val card: Card,
    val questionCount: Int
)

