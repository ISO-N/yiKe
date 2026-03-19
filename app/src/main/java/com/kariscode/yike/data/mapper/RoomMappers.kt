package com.kariscode.yike.data.mapper

import com.kariscode.yike.data.local.db.dao.ArchivedCardSummaryRow
import com.kariscode.yike.data.local.db.dao.CardSummaryRow
import com.kariscode.yike.data.local.db.dao.DeckSummaryRow
import com.kariscode.yike.data.local.db.dao.QuestionContextRow
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewRecord

/**
 * 映射层的存在是为了隔离 Room 字段与 domain 语义，
 * 这样后续调整索引字段或序列化细节时，不会把变化直接传播到业务与 UI。
 */

/**
 * 复用同一套字段组装入口，是为了让 Deck 映射在实体行、聚合行与后续扩展字段时保持口径一致。
 */
private fun deckDomainFrom(
    id: String,
    name: String,
    description: String,
    tagsJson: String,
    intervalStepCount: Int,
    archived: Boolean,
    sortOrder: Int,
    createdAt: Long,
    updatedAt: Long
): Deck = Deck(
    id = id,
    name = name,
    description = description,
    tags = decodeTags(tagsJson),
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Card 字段在多处聚合查询会复用，集中构造器能避免一旦字段新增就要在多处同步补齐。
 */
private fun cardDomainFrom(
    id: String,
    deckId: String,
    title: String,
    description: String,
    archived: Boolean,
    sortOrder: Int,
    createdAt: Long,
    updatedAt: Long
): Card = Card(
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
 * Question 组装在搜索/预览/复习多个入口共享，统一入口能降低不同 SQL Row 对字段默认值的漂移风险。
 */
private fun questionDomainFrom(
    id: String,
    cardId: String,
    prompt: String,
    answer: String,
    tagsJson: String,
    status: String,
    stageIndex: Int,
    dueAt: Long,
    lastReviewedAt: Long?,
    reviewCount: Int,
    lapseCount: Int,
    createdAt: Long,
    updatedAt: Long
): Question = Question(
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

/**
 * 让数据层调用点直接 `entity.toDomain()`，是为了消除 `RoomMappers.run { ... }` 的样板噪音，
 * 让仓储代码更聚焦于“查什么/写什么”而不是“如何把 mapper 引入作用域”。
 */
fun DeckEntity.toDomain(): Deck = deckDomainFrom(
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
 * 映射回 Entity 时统一走同一套 tags 编码策略，是为了避免不同写入口产生不兼容 JSON 格式。
 */
fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    name = name,
    description = description,
    tagsJson = encodeTags(tags),
    intervalStepCount = intervalStepCount,
    archived = archived,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Card 的实体到领域映射保持无副作用，是为了让缓存/同步等后续替换数据源时仍能复用同一语义。
 */
fun CardEntity.toDomain(): Card = cardDomainFrom(
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
 * Card 写回 Entity 的映射集中后，上层就不必理解 Room 字段约束（例如列名、默认值等）。
 */
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

/**
 * Question 的映射统一放在数据边界，是为了保证 status/tags 的容错语义不会散落在多个仓储中。
 */
fun QuestionEntity.toDomain(): Question = questionDomainFrom(
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

/**
 * Question 持久化边界只暴露领域模型，是为了让调度/校验逻辑在 domain 层闭环而不泄露 Entity 细节。
 */
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

/**
 * ReviewRecord 的评分字段在历史数据中可能不完整，容错解码集中在此处能避免读库全量失败。
 */
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

/**
 * ReviewRecord 写入仍使用枚举 name，是为了让备份/同步/统计在同一套评分字符串上对齐。
 */
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
    deck = deckDomainFrom(
        id = id,
        name = name,
        description = description,
        tagsJson = tagsJson,
        intervalStepCount = intervalStepCount,
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
    card = cardDomainFrom(
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
    card = cardDomainFrom(
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
    question = questionDomainFrom(
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
