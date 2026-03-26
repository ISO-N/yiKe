package com.kariscode.yike.feature.card

import com.kariscode.yike.core.ui.message.SuccessMessages
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
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * CardListViewModelMutationTest 锁定卡片页写操作的状态回写边界，
 * 避免后续继续简化时又把删除成功后的 UI 更新混回实际仓储 action 里。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardListViewModelMutationTest {
    /**
     * 删除成功后应在 action 结束后统一清理确认态并回写成功提示，
     * 这样 ViewModel 的副作用执行和状态更新才能继续保持两段式边界。
     */
    @Test
    fun onConfirmDelete_deletesCardAndUpdatesUiStateAfterMutationSucceeds() = runTest {
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
            val item = CardSummary(
                card = Card(
                    id = "card_1",
                    deckId = "deck_1",
                    title = "极限",
                    description = "",
                    archived = false,
                    sortOrder = 0,
                    createdAt = 1L,
                    updatedAt = 1L
                ),
                questionCount = 3,
                dueQuestionCount = 1
            )
            cardRepository.activeSummariesFlow.value = listOf(item)
            val viewModel = CardListViewModel(
                deckId = "deck_1",
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = FixedTimeProvider(nowEpochMillis = 100L)
            )
            advanceUntilIdle()

            viewModel.onDeleteCardClick(item)
            viewModel.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("card_1"), cardRepository.deletedCardIds)
            assertNull(viewModel.uiState.value.pendingDelete)
            assertEquals(SuccessMessages.DELETED, viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
