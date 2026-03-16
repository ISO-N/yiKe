package com.kariscode.yike.data.mapper

import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewRecord
import com.kariscode.yike.data.local.db.dao.CardSummaryRow
import com.kariscode.yike.data.local.db.dao.ArchivedCardSummaryRow
import com.kariscode.yike.data.local.db.dao.DeckSummaryRow
import com.kariscode.yike.data.local.db.dao.QuestionContextRow
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
        tags = decodeQuestionTags(tagsJson),
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

    /**
     * 聚合行在 mapper 层还原成领域摘要，可避免 Repository 重复理解 SQL 别名与层级字段。
     */
    fun DeckSummaryRow.toDomain(): DeckSummary = DeckSummary(
        deck = Deck(
            id = id,
            name = name,
            description = description,
            archived = archived,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        ),
        cardCount = cardCount,
        questionCount = questionCount,
        dueQuestionCount = dueQuestionCount
    )

    /**
     * 卡片摘要行与普通实体字段相近但不完全一致，把转换集中可避免列表查询继续散落手写构造。
     */
    fun CardSummaryRow.toDomain(): CardSummary = CardSummary(
        card = Card(
            id = id,
            deckId = deckId,
            title = title,
            description = description,
            archived = archived,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        ),
        questionCount = questionCount,
        dueQuestionCount = dueQuestionCount
    )

    /**
     * 回收站行额外携带卡组名称，因此单独映射能避免页面层理解 SQL 别名字段。
     */
    fun ArchivedCardSummaryRow.toDomain(): ArchivedCardSummary = ArchivedCardSummary(
        card = Card(
            id = id,
            deckId = deckId,
            title = title,
            description = description,
            archived = archived,
            sortOrder = sortOrder,
            createdAt = createdAt,
            updatedAt = updatedAt
        ),
        deckName = deckName,
        questionCount = questionCount,
        dueQuestionCount = dueQuestionCount
    )

    /**
     * 搜索与预览共用的上下文行统一映射成领域模型，是为了让熟练度筛选继续只依赖同一份 Question 语义。
     */
    fun QuestionContextRow.toDomain(): QuestionContext = QuestionContext(
        question = Question(
            id = id,
            cardId = cardId,
            prompt = prompt,
            answer = answer,
            tags = decodeQuestionTags(tagsJson),
            status = decodeStatus(status),
            stageIndex = stageIndex,
            dueAt = dueAt,
            lastReviewedAt = lastReviewedAt,
            reviewCount = reviewCount,
            lapseCount = lapseCount,
            createdAt = createdAt,
            updatedAt = updatedAt
        ),
        deckId = deckId,
        deckName = deckName,
        cardTitle = cardTitle
    )

    /**
     * Room 状态字段统一走领域枚举自带的存储值，是为了让持久化边界只维护一套状态编码规则。
     */
    private fun encodeStatus(status: QuestionStatus): String = status.storageValue

    /**
     * 反向解析收口到领域模型后，数据层就不需要为同一套默认值语义重复维护多个 `when` 分支。
     */
    private fun decodeStatus(status: String): QuestionStatus = QuestionStatus.fromStorageValue(status)

    /**
     * 评分字符串保持宽松兜底，是为了让历史备份或异常脏数据不会因为单条记录失效而中断整个读取流程。
     */
    private fun decodeRating(rating: String): ReviewRating = runCatching {
        ReviewRating.valueOf(rating)
    }.getOrElse {
        ReviewRating.AGAIN
    }

    /**
     * 标签编码继续集中在映射层，是为了让数据库字段格式调整时不必让仓储和页面感知 JSON 细节。
     */
    private fun encodeTags(tags: List<String>): String = json.encodeToString(
        serializer = tagsSerializer,
        value = tags
    )

    /**
     * 标签解码规则对搜索候选、洞察统计和实体映射都必须保持一致，
     * 因此暴露单一入口可以避免不同仓储各自演化出不同的容错语义。
     */
    fun decodeQuestionTags(tagsJson: String): List<String> = runCatching {
        json.decodeFromString(
            deserializer = tagsSerializer,
            string = tagsJson
        )
    }.getOrElse { emptyList() }

    /**
     * 标签解码失败时回退空列表，是为了保证单条题目脏数据不会让整个列表查询失去可用性。
     */
    private fun decodeTags(tagsJson: String): List<String> = decodeQuestionTags(tagsJson)
}
