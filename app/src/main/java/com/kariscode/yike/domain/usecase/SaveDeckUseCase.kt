package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.repository.DeckRepository

/**
 * 卡组保存进入用例层后，ID 与时间戳的生成策略就不会散落在多个页面入口里各自维护。
 */
class SaveDeckUseCase(
    private val deckRepository: DeckRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 保存时统一补齐主键和时间语义，并在编辑时保留创建时间、排序和归档状态，
     * 是为了让“更新元信息”不会意外把既有卡组生命周期重新写成一次新建。
     */
    suspend operator fun invoke(request: DeckSaveRequest): DeckSaveResult {
        val now = timeProvider.nowEpochMillis()
        val existingDeck = request.deckId?.let { deckId ->
            deckRepository.findById(deckId)
        }
        val savedDeck = Deck(
            id = existingDeck?.id ?: EntityIds.newDeckId(),
            name = request.name,
            description = request.description,
            tags = request.tags,
            intervalStepCount = request.intervalStepCount,
            archived = existingDeck?.archived ?: false,
            sortOrder = existingDeck?.sortOrder ?: 0,
            createdAt = existingDeck?.createdAt ?: now,
            updatedAt = now
        )
        deckRepository.upsert(savedDeck)
        return if (existingDeck == null) {
            DeckSaveResult.Created(savedDeck)
        } else {
            DeckSaveResult.Updated(savedDeck)
        }
    }
}

/**
 * 卡组保存结果显式建模后，页面和日志都可以直接依赖语义结果而不是通过空 id 猜测发生了什么。
 */
sealed interface DeckSaveResult {
    data class Created(val deck: Deck) : DeckSaveResult
    data class Updated(val deck: Deck) : DeckSaveResult
}

/**
 * 卡组保存请求显式建模后，ViewModel 只需要关心表单合法性，不必继续拼装领域对象。
 */
data class DeckSaveRequest(
    val deckId: String?,
    val name: String,
    val description: String,
    val tags: List<String>,
    val intervalStepCount: Int
)
