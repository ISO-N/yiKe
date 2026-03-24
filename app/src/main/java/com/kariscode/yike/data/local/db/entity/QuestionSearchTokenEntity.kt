package com.kariscode.yike.data.local.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * 问题搜索 token 表把正文拆成可索引的短 token，
 * 是为了在保持原始 LIKE 语义不变的前提下先缩小候选集，降低全文扫描成本。
 */
@Entity(
    tableName = "question_search_token",
    primaryKeys = ["questionId", "token"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["questionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["token"], name = "question_search_token_idx"),
        Index(value = ["questionId"], name = "question_search_question_idx")
    ]
)
data class QuestionSearchTokenEntity(
    val questionId: String,
    val token: String
)
