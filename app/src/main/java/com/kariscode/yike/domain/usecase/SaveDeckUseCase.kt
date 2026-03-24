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
     * 保存时统一补齐主键和时间语义，是为了保证新建与编辑都沿用同一套落库规则。
     */
    suspend operator fun invoke(request: DeckSaveRequest) {
        val now = timeProvider.nowEpochMillis()
        deckRepository.upsert(
            Deck(
                id = request.deckId ?: EntityIds.newDeckId(),
                name = request.name,
                description = request.description,
                tags = request.tags,
                intervalStepCount = request.intervalStepCount,
                archived = false,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now
            )
        )
    }
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
