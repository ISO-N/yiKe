package com.kariscode.yike.domain.model

/**
 * Card 作为 Deck 下的知识块承载问题集合；独立建模的原因是复习流以“按卡片组织”推进，
 * 如果只在 UI 层临时拼装卡片概念，会导致复习队列与内容管理出现语义不一致。
 */
data class Card(
    val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

