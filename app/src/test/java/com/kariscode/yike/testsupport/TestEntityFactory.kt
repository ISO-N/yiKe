package com.kariscode.yike.testsupport

import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity

/**
 * 仓储测试常用的实体构造器统一收口，是为了让用例只覆盖自己真正关心的字段差异，
 * 而不是在每个测试文件里反复拼装同一份最小合法实体。
 */
fun testDeckEntity(
    id: String,
    name: String,
    description: String = "",
    tagsJson: String = "[]",
    intervalStepCount: Int = 8,
    archived: Boolean = false,
    sortOrder: Int = 0,
    createdAt: Long = 1L,
    updatedAt: Long = 1L
): DeckEntity = DeckEntity(
    id = id,
    name = name,
    description = description,
    tagsJson = tagsJson,
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * 卡片实体默认值保持和仓储测试的高频场景一致，是为了让断言聚焦在 id、归属层级和元数据差异上。
 */
fun testCardEntity(
    id: String,
    deckId: String,
    title: String = id,
    description: String = "",
    archived: Boolean = false,
    sortOrder: Int = 0,
    createdAt: Long = 1L,
    updatedAt: Long = 1L
): CardEntity = CardEntity(
    id = id,
    deckId = deckId,
    title = title,
    description = description,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * 问题实体工厂补齐调度字段默认值，是为了让仓储测试在只改一两个字段时仍然能生成合法领域输入。
 */
fun testQuestionEntity(
    id: String,
    cardId: String,
    prompt: String = id,
    answer: String = "",
    tagsJson: String = "[]",
    status: String = QuestionEntity.STATUS_ACTIVE,
    stageIndex: Int = 0,
    dueAt: Long = 1_000L,
    lastReviewedAt: Long? = null,
    reviewCount: Int = 0,
    lapseCount: Int = 0,
    createdAt: Long = 1L,
    updatedAt: Long = 1L
): QuestionEntity = QuestionEntity(
    id = id,
    cardId = cardId,
    prompt = prompt,
    answer = answer,
    tagsJson = tagsJson,
    status = status,
    stageIndex = stageIndex,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
