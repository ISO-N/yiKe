package com.kariscode.yike.domain.model

/**
 * Deck 是内容层级的顶层单位；将其建模在 domain 层可以确保“归档/排序/命名”等规则
 * 不被数据库字段或 UI 展示细节反向驱动，从而保持业务语义稳定。
 */
data class Deck(
    val id: String,
    val name: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

