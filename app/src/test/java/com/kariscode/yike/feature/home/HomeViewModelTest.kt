package com.kariscode.yike.feature.home

import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.usecase.GetHomeOverviewUseCase
import com.kariscode.yike.testsupport.FakeDeckRepository
import com.kariscode.yike.testsupport.FakeQuestionRepository
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
 * HomeViewModel 测试用于守住首页模式切换语义，
 * 避免“今日已完成”和“尚未建立内容”再次退化成同一种空状态。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    /**
     * 当今日仍有到期题目时，首页必须进入待复习模式，
     * 这样一级入口才能明确把用户带回应优先完成的主路径。
     */
    @Test
    fun init_withDueQuestions_setsReviewReadyMode() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val questionRepository = FakeQuestionRepository().apply {
                summary = TodayReviewSummary(
                    dueCardCount = 2,
                    dueQuestionCount = 5
                )
            }
            val deckRepository = FakeDeckRepository().apply {
                activeSummariesFlow.value = listOf(createDeckSummary(deckId = "deck_math"))
            }

            val viewModel = HomeViewModel(
                getHomeOverviewUseCase = GetHomeOverviewUseCase(
                    questionRepository = questionRepository,
                    deckRepository = deckRepository
                ),
                timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            )
            advanceUntilIdle()

            assertEquals(HomeContentMode.REVIEW_READY, viewModel.uiState.value.contentMode)
            assertEquals(5, viewModel.uiState.value.summary.dueQuestionCount)
            assertEquals(1, viewModel.uiState.value.recentDecks.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 当今日题目已清空但仍有最近卡组时，首页应进入“已完成”模式，
     * 否则用户会误以为自己还没开始建立内容。
     */
    @Test
    fun init_withNoDueButRecentDecks_setsReviewClearedMode() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val questionRepository = FakeQuestionRepository().apply {
                summary = TodayReviewSummary(
                    dueCardCount = 0,
                    dueQuestionCount = 0
                )
            }
            val deckRepository = FakeDeckRepository().apply {
                activeSummariesFlow.value = listOf(createDeckSummary(deckId = "deck_history"))
            }

            val viewModel = HomeViewModel(
                getHomeOverviewUseCase = GetHomeOverviewUseCase(
                    questionRepository = questionRepository,
                    deckRepository = deckRepository
                ),
                timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            )
            advanceUntilIdle()

            assertEquals(HomeContentMode.REVIEW_CLEARED, viewModel.uiState.value.contentMode)
            assertEquals(1, viewModel.uiState.value.recentDecks.size)
            assertEquals(null, viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 当既没有到期题目也没有任何最近卡组时，首页应明确落到“先创建内容”的空态，
     * 这样页面才能给出真正匹配当前阶段的引导动作。
     */
    @Test
    fun init_withNoDueAndNoDecks_setsContentEmptyMode() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = HomeViewModel(
                getHomeOverviewUseCase = GetHomeOverviewUseCase(
                    questionRepository = FakeQuestionRepository(),
                    deckRepository = FakeDeckRepository()
                ),
                timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            )
            advanceUntilIdle()

            assertEquals(HomeContentMode.CONTENT_EMPTY, viewModel.uiState.value.contentMode)
            assertEquals(0, viewModel.uiState.value.summary.dueQuestionCount)
            assertEquals(0, viewModel.uiState.value.recentDecks.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 刷新失败时必须退出 loading 并清掉旧统计快照，
     * 否则错误页会带着上一轮成功数据，造成“看似成功但实际上失败”的混合状态。
     */
    @Test
    fun init_whenSummaryLoadFails_showsErrorAndClearsData() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val questionRepository = object : FakeQuestionRepository() {
                override suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary {
                    throw IllegalStateException("首页读取异常")
                }
            }

            val viewModel = HomeViewModel(
                getHomeOverviewUseCase = GetHomeOverviewUseCase(
                    questionRepository = questionRepository,
                    deckRepository = FakeDeckRepository()
                ),
                timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            )
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isLoading)
            assertEquals("首页读取异常", viewModel.uiState.value.errorMessage)
            assertEquals(HomeContentMode.CONTENT_EMPTY, viewModel.uiState.value.contentMode)
            assertEquals(0, viewModel.uiState.value.recentDecks.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 最近卡组测试数据需要保留最小聚合字段，
     * 这样首页在不同模式下仍能复用真实的列表项语义。
     */
    private fun createDeckSummary(deckId: String): DeckSummary = DeckSummary(
        deck = Deck(
            id = deckId,
            name = "默认卡组",
            description = "",
            tags = emptyList(),
            intervalStepCount = 8,
            archived = false,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        cardCount = 2,
        questionCount = 4,
        dueQuestionCount = 0
    )
}
