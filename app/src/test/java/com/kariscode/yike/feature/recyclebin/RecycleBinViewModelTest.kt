package com.kariscode.yike.feature.recyclebin

import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.testsupport.FakeCardRepository
import com.kariscode.yike.testsupport.FakeDeckRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RecycleBinViewModel 测试用于锁定恢复和彻底删除两类高风险操作，
 * 避免回收站交互在后续整理时悄悄失去确认与反馈语义。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecycleBinViewModelTest {

    /**
     * 首次订阅必须同时拿到归档卡组和归档卡片，
     * 否则回收站会出现只显示一半内容的误导状态。
     */
    @Test
    fun init_collectsArchivedDecksAndCards() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val deckRepository = FakeDeckRepository().apply {
                archivedSummariesFlow.value = listOf(deckSummary(deckId = "deck_1"))
            }
            val cardRepository = FakeCardRepository().apply {
                archivedSummariesFlow.value = listOf(archivedCardSummary(cardId = "card_1"))
            }

            val viewModel = createViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository
            )
            advanceUntilIdle()

            assertEquals(listOf("deck_1"), viewModel.uiState.value.archivedDecks.map { it.deck.id })
            assertEquals(listOf("card_1"), viewModel.uiState.value.archivedCards.map { it.card.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 恢复卡组必须写入归档状态切换并给出成功提示，
     * 否则用户在回收站执行恢复后无法判断操作是否生效。
     */
    @Test
    fun onRestoreDeckClick_updatesArchivedStateAndShowsMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val deckRepository = FakeDeckRepository()
            val viewModel = createViewModel(deckRepository = deckRepository)
            val item = deckSummary(deckId = "deck_restore")
            advanceUntilIdle()

            viewModel.onRestoreDeckClick(item)
            advanceUntilIdle()

            assertEquals(listOf(Triple("deck_restore", false, 10_000L)), deckRepository.setArchivedCalls)
            assertEquals("卡组已恢复", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 删除卡片必须先进入确认态，再在确认后触发物理删除，
     * 否则回收站会退化成误触即删的危险入口。
     */
    @Test
    fun onDeleteCardClick_thenConfirmDelete_deletesCardAndClearsPendingState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val cardRepository = FakeCardRepository()
            val viewModel = createViewModel(cardRepository = cardRepository)
            val item = archivedCardSummary(cardId = "card_delete")
            advanceUntilIdle()

            viewModel.onDeleteCardClick(item)
            assertTrue(viewModel.uiState.value.pendingDelete is RecycleBinDeleteTarget.CardTarget)

            viewModel.onConfirmDelete()
            advanceUntilIdle()

            assertEquals(listOf("card_delete"), cardRepository.deletedCardIds)
            assertEquals(SuccessMessages.DELETED, viewModel.uiState.value.message)
            assertEquals(null, viewModel.uiState.value.pendingDelete)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 构造入口固定时间和默认仓储，是为了让每个用例只覆盖自己关心的交互分支。
     */
    private fun createViewModel(
        deckRepository: FakeDeckRepository = FakeDeckRepository(),
        cardRepository: FakeCardRepository = FakeCardRepository()
    ): RecycleBinViewModel = RecycleBinViewModel(
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        timeProvider = FixedTimeProvider(nowEpochMillis = 10_000L)
    )

    /**
     * 归档卡组摘要保留最小统计字段，便于回收站测试复用。
     */
    private fun deckSummary(deckId: String): DeckSummary = DeckSummary(
        deck = Deck(
            id = deckId,
            name = "数学",
            description = "",
            tags = listOf("高频"),
            intervalStepCount = 8,
            archived = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        cardCount = 1,
        questionCount = 2,
        dueQuestionCount = 1
    )

    /**
     * 归档卡片摘要同时携带 deckName，便于断言回收站完整上下文。
     */
    private fun archivedCardSummary(cardId: String): ArchivedCardSummary = ArchivedCardSummary(
        card = Card(
            id = cardId,
            deckId = "deck_1",
            title = "极限",
            description = "",
            archived = true,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        deckName = "数学",
        questionCount = 2,
        dueQuestionCount = 1
    )
}
