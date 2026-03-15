package com.kariscode.yike.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 将 Room Entity 与 domain 模型分离的原因是：数据库字段需要为查询/索引服务，
 * 而 domain 模型需要为业务语义服务；两者强耦合会让任意一方的演进牵连另一方。
 */
@Entity(
    tableName = "deck",
    indices = [
        Index(value = ["name"], name = "deck_name_idx"),
        Index(value = ["archived", "sortOrder", "createdAt"], name = "deck_archived_sort_idx")
    ]
)
data class DeckEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)

