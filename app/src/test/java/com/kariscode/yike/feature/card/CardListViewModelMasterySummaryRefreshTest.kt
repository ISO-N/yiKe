package com.kariscode.yike.feature.card

import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.testsupport.FakeCardRepository
import com.kariscode.yike.testsupport.FakeDeckRepository
import com.kariscode.yike.testsupport.FakeStudyInsightsRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CardListViewModelMasterySummaryRefreshTest 锁定“何时重算熟练度摘要”的触发条件，
 * 是为了在优化去重逻辑后仍能保证复习评分等高频状态变化会刷新摘要，而编辑标题等低价值变化不会反复触发查询。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardListViewModelMasterySummaryRefreshTest {
    /**
     * 标题编辑会让 CardSummary 变化，但不应触发熟练度检索；
     * 而 dueQuestionCount 变化通常意味着复习评分推进了 dueAt 和熟练度状态，因此必须触发一次重算。
     */
    @Test
    fun observeCardSummaries_titleChangeSkipsMasteryRefresh_butDueCountChangeTriggersRefresh() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val deckRepository = FakeDeckRepository().apply {
                deckById["deck_1"] = Deck(
                    id = "deck_1",
                    name = "数学",
                    description = "",
                    tags = emptyList(),
                    intervalStepCount = 8,
                    archived = false,
                    sortOrder = 0,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            }
            val cardRepository = FakeCardRepository()
            val studyInsightsRepository = FakeStudyInsightsRepository()
            val timeProvider = FixedTimeProvider(nowEpochMillis = 100L)

            val baseCard = Card(
                id = "card_1",
                deckId = "deck_1",
                title = "集合",
                description = "",
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )

            cardRepository.activeSummariesFlow.value = listOf(
                CardSummary(
                    card = baseCard,
                    questionCount = 10,
                    dueQuestionCount = 0
                )
            )

            CardListViewModel(
                deckId = "deck_1",
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                studyInsightsRepository = studyInsightsRepository,
                timeProvider = timeProvider
            )
            advanceUntilIdle()
            assertEquals(1, studyInsightsRepository.deckMasteryRequests.size)

            cardRepository.activeSummariesFlow.value = listOf(
                CardSummary(
                    card = baseCard.copy(title = "集合（已编辑）", updatedAt = 2L),
                    questionCount = 10,
                    dueQuestionCount = 0
                )
            )
            advanceUntilIdle()
            assertEquals(1, studyInsightsRepository.deckMasteryRequests.size)

            cardRepository.activeSummariesFlow.value = listOf(
                CardSummary(
                    card = baseCard.copy(title = "集合（已编辑）", updatedAt = 2L),
                    questionCount = 10,
                    dueQuestionCount = 1
                )
            )
            advanceUntilIdle()
            assertEquals(2, studyInsightsRepository.deckMasteryRequests.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

