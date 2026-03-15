package com.kariscode.yike.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * QuestionEntity 存放调度字段（stage/due 等）是为了让“今日待复习”查询只依赖本地数据即可完成，
 * 避免在 UI 层临时计算导致不同页面对 due 条件的理解不一致。
 */
@Entity(
    tableName = "question",
    foreignKeys = [
        ForeignKey(
            entity = CardEntity::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardId"], name = "question_cardId_idx"),
        Index(value = ["dueAt"], name = "question_due_idx"),
        Index(value = ["status", "dueAt"], name = "question_status_due_idx"),
        Index(value = ["cardId", "status", "dueAt"], name = "question_card_status_due_idx")
    ]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tagsJson: String,
    val status: String,
    val stageIndex: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val STATUS_ACTIVE: String = "active"
        const val STATUS_ARCHIVED: String = "archived"
    }
}

