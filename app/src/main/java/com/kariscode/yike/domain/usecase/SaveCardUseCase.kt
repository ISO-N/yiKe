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
     * 保存统一补齐主键和更新时间，是为了让新建与编辑都走同一套内容落库语义。
     */
    suspend operator fun invoke(request: CardSaveRequest) {
        val now = timeProvider.nowEpochMillis()
        cardRepository.upsert(
            Card(
                id = request.cardId ?: EntityIds.newCardId(),
                deckId = request.deckId,
                title = request.title,
                description = request.description,
                archived = false,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }
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
