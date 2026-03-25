package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.coroutine.parallel3
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 搜索页筛选元数据独立成用例，是为了让标签、卡组和卡片候选始终按同一轮快照加载，避免筛选项彼此错位。
 */
class GetQuestionSearchMetadataUseCase(
    private val studyInsightsRepository: StudyInsightsRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository
) {
    /**
     * 搜索页初始化只关心“有哪些可选条件”，因此这里直接返回统一快照，减少 ViewModel 对多仓储并发细节的感知。
     */
    suspend operator fun invoke(
        selectedDeckId: String?,
        tagLimit: Int
    ): QuestionSearchMetadataSnapshot {
        val (tags, decks, cards) = parallel3(
            first = { studyInsightsRepository.listAvailableTags(limit = tagLimit) },
            second = { deckRepository.listActiveDecks() },
            third = {
                if (selectedDeckId == null) {
                    emptyList()
                } else {
                    cardRepository.listActiveCards(selectedDeckId)
                }
            }
        )
        return QuestionSearchMetadataSnapshot(
            tags = tags,
            decks = decks,
            cards = cards
        )
    }
}

/**
 * 搜索元数据快照统一承载筛选候选，是为了让调用方只处理映射到 UI 的工作，而不再重复组织查询结果。
 */
data class QuestionSearchMetadataSnapshot(
    val tags: List<String>,
    val decks: List<Deck>,
    val cards: List<Card>
)

