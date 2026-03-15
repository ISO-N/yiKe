package com.kariscode.yike.data.mapper

import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewRecord
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * 映射层的存在是为了隔离 Room 字段与 domain 语义，
 * 这样后续调整索引字段或序列化细节时，不会把变化直接传播到业务与 UI。
 */
object RoomMappers {
    private val json: Json = Json { ignoreUnknownKeys = true }
    private val tagsSerializer = ListSerializer(String.serializer())

    fun DeckEntity.toDomain(): Deck = Deck(
        id = id,
        name = name,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Deck.toEntity(): DeckEntity = DeckEntity(
        id = id,
        name = name,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun CardEntity.toDomain(): Card = Card(
        id = id,
        deckId = deckId,
        title = title,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Card.toEntity(): CardEntity = CardEntity(
        id = id,
        deckId = deckId,
        title = title,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun QuestionEntity.toDomain(): Question = Question(
        id = id,
        cardId = cardId,
        prompt = prompt,
        answer = answer,
        tags = decodeTags(tagsJson),
        status = decodeStatus(status),
        stageIndex = stageIndex,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun Question.toEntity(): QuestionEntity = QuestionEntity(
        id = id,
        cardId = cardId,
        prompt = prompt,
        answer = answer,
        tagsJson = encodeTags(tags),
        status = encodeStatus(status),
        stageIndex = stageIndex,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun ReviewRecordEntity.toDomain(): ReviewRecord = ReviewRecord(
        id = id,
        questionId = questionId,
        rating = decodeRating(rating),
        oldStageIndex = oldStageIndex,
        newStageIndex = newStageIndex,
        oldDueAt = oldDueAt,
        newDueAt = newDueAt,
        reviewedAt = reviewedAt,
        responseTimeMs = responseTimeMs,
        note = note
    )

    fun ReviewRecord.toEntity(): ReviewRecordEntity = ReviewRecordEntity(
        id = id,
        questionId = questionId,
        rating = rating.name,
        oldStageIndex = oldStageIndex,
        newStageIndex = newStageIndex,
        oldDueAt = oldDueAt,
        newDueAt = newDueAt,
        reviewedAt = reviewedAt,
        responseTimeMs = responseTimeMs,
        note = note
    )

    private fun encodeStatus(status: QuestionStatus): String = when (status) {
        QuestionStatus.ACTIVE -> QuestionEntity.STATUS_ACTIVE
        QuestionStatus.ARCHIVED -> QuestionEntity.STATUS_ARCHIVED
    }

    private fun decodeStatus(status: String): QuestionStatus = when (status) {
        QuestionEntity.STATUS_ARCHIVED -> QuestionStatus.ARCHIVED
        else -> QuestionStatus.ACTIVE
    }

    private fun decodeRating(rating: String): ReviewRating = runCatching {
        ReviewRating.valueOf(rating)
    }.getOrElse {
        ReviewRating.AGAIN
    }

    private fun encodeTags(tags: List<String>): String = json.encodeToString(
        serializer = tagsSerializer,
        value = tags
    )

    private fun decodeTags(tagsJson: String): List<String> = runCatching {
        json.decodeFromString(
            deserializer = tagsSerializer,
            string = tagsJson
        )
    }.getOrElse { emptyList() }
}
