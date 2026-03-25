package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.repository.CardRepository

/**
 * 卡片保存进入用例层后，卡片页就不需要再负责 ID、时间戳与领域对象拼装。
 */
class SaveCardUseCase(
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 保存统一补齐主键和时间戳，并在更新时保留既有创建时间与排序信息，
     * 是为了让页面拿到显式结果后既能区分“新建/编辑”，又不会把历史元数据在编辑时冲掉。
     */
    suspend operator fun invoke(request: CardSaveRequest): CardSaveResult {
        val now = timeProvider.nowEpochMillis()
        val existingCard = request.cardId?.let { cardId ->
            cardRepository.findById(cardId)
        }
        val savedCard = Card(
            id = existingCard?.id ?: EntityIds.newCardId(),
            deckId = request.deckId,
            title = request.title,
            description = request.description,
            archived = existingCard?.archived ?: false,
            sortOrder = existingCard?.sortOrder ?: 0,
            createdAt = existingCard?.createdAt ?: now,
            updatedAt = now
        )
        cardRepository.upsert(savedCard)
        return if (existingCard == null) {
            CardSaveResult.Created(savedCard)
        } else {
            CardSaveResult.Updated(savedCard)
        }
    }
}

/**
 * 保存结果显式区分新建与更新，是为了让上层反馈和后续审计逻辑不再从 `cardId == null` 反推业务结果。
 */
sealed interface CardSaveResult {
    data class Created(val card: Card) : CardSaveResult
    data class Updated(val card: Card) : CardSaveResult
}

/**
 * 卡片保存请求显式建模后，ViewModel 只需要做表单校验，不必再直接组装领域实体。
 */
data class CardSaveRequest(
    val cardId: String?,
    val deckId: String,
    val title: String,
    val description: String
)
