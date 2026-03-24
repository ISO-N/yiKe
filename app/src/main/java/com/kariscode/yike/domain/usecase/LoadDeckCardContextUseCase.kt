package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.repository.DeckRepository

/**
 * 卡片页顶部上下文通过用例读取后，ViewModel 不需要再直接依赖卡组仓储的细节接口。
 */
class LoadDeckCardContextUseCase(
    private val deckRepository: DeckRepository
) {
    /**
     * 只读取 deck 元信息，是为了让标题和返回上下文复用最小必要数据，而不是额外拉取无关列表。
     */
    suspend operator fun invoke(deckId: String): Deck? = deckRepository.findById(deckId)
}
