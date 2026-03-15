package com.kariscode.yike.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * CardEntity 通过外键与级联删除约束，确保删除上层 Deck 时不会留下孤儿数据；
 * 这是离线应用中保持数据一致性最重要的基础之一。
 */
@Entity(
    tableName = "card",
    foreignKeys = [
        ForeignKey(
            entity = DeckEntity::class,
            parentColumns = ["id"],
            childColumns = ["deckId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["deckId"], name = "card_deckId_idx"),
        Index(value = ["deckId", "archived", "sortOrder", "createdAt"], name = "card_deck_archived_sort_idx")
    ]
)
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

