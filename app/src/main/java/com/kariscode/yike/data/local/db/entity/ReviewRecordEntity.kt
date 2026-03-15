package com.kariscode.yike.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * ReviewRecordEntity 以“旧值 + 新值”的方式落库，是为了在备份恢复或故障排查时能重建调度链路，
 * 否则只保存最终 stage/due 会丢失当时的评分上下文。
 */
@Entity(
    tableName = "review_record",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["questionId"], name = "review_record_questionId_idx"),
        Index(value = ["reviewedAt"], name = "review_record_reviewedAt_idx"),
        Index(value = ["questionId", "reviewedAt"], name = "review_record_question_reviewedAt_idx")
    ]
)
data class ReviewRecordEntity(
    @PrimaryKey val id: String,
    val questionId: String,
    val rating: String,
    val oldStageIndex: Int,
    val newStageIndex: Int,
    val oldDueAt: Long,
    val newDueAt: Long,
    val reviewedAt: Long,
    val responseTimeMs: Long?,
    val note: String
)

