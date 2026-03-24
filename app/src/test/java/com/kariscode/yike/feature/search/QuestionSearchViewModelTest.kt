package com.kariscode.yike.feature.search

import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionStatus
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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QuestionSearchViewModel 测试锁定搜索页的筛选编排和即时检索语义，
 * 避免后续加筛选条件时出现状态已变但查询参数没同步的回归。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionSearchViewModelTest {

    /**
     * 首次进入必须同时拉到筛选元数据和结果列表，
     * 否则来自首页或卡片页的搜索入口会落在不完整状态。
     */
    @Test
    fun init_refreshesMetadataAndResults() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val deckRepository = FakeDeckRepository().apply {
                activeDecks = listOf(deck(id = "deck_1", name = "数学"))
            }
            val cardRepository = FakeCardRepository().apply {
                activeCardsByDeck["deck_1"] = listOf(card(id = "card_1", deckId = "deck_1", title = "极限"))
            }
            val studyInsightsRepository = FakeStudyInsightsRepository().apply {
                availableTags = listOf("定义", "高频")
                searchResults = listOf(questionContext(questionId = "question_1"))
            }

            val viewModel = createViewModel(
                initialDeckId = "deck_1",
                initialCardId = "card_1",
                studyInsightsRepository = studyInsightsRepository,
                deckRepository = deckRepository,
                cardRepository = cardRepository
            )
            advanceUntilIdle()

            assertEquals(listOf("定义", "高频"), viewModel.uiState.value.availableTags)
            assertEquals(listOf("数学"), viewModel.uiState.value.deckOptions.map { it.name })
            assertEquals(listOf("极限"), viewModel.uiState.value.cardOptions.map { it.title })
            assertEquals(listOf("question_1"), viewModel.uiState.value.results.map { it.context.question.id })
            assertEquals("deck_1", studyInsightsRepository.searchFilters.first().deckId)
            assertEquals("card_1", studyInsightsRepository.searchFilters.first().cardId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 路由预置标签应在首轮查询里直接生效，
     * 这样用户从卡组标签跳到搜索页时，不需要再手动补一次相同筛选。
     */
    @Test
    fun init_withInitialTag_appliesTagFilterImmediately() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val studyInsightsRepository = FakeStudyInsightsRepository().apply {
                searchResults = listOf(questionContext(questionId = "question_1"))
            }

            val viewModel = createViewModel(
                initialTag = "高频",
                studyInsightsRepository = studyInsightsRepository
            )
            advanceUntilIdle()

            assertEquals("高频", viewModel.uiState.value.selectedTag)
            assertEquals("高频", studyInsightsRepository.searchFilters.first().tag)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 切换卡组后必须清空旧卡片筛选并加载新候选，
     * 否则搜索会在用户看不见的旧条件下悄悄继续生效。
     */
    @Test
    fun onDeckSelected_clearsSelectedCardAndLoadsCardsForNewDeck() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val cardRepository = FakeCardRepository().apply {
                activeCardsByDeck["deck_1"] = listOf(card(id = "card_1", deckId = "deck_1", title = "极限"))
                activeCardsByDeck["deck_2"] = listOf(card(id = "card_2", deckId = "deck_2", title = "线代"))
            }
            val viewModel = createViewModel(
                initialDeckId = "deck_1",
                initialCardId = "card_1",
                cardRepository = cardRepository
            )
            advanceUntilIdle()

            viewModel.onDeckSelected("deck_2")
            advanceUntilIdle()

            assertEquals("deck_2", viewModel.uiState.value.selectedDeckId)
            assertNull(viewModel.uiState.value.selectedCardId)
            assertEquals(listOf("线代"), viewModel.uiState.value.cardOptions.map { it.title })
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 一键清空要回到“进行中”默认筛选，
     * 这样搜索页才能快速回归最常用的题库工作流。
     */
    @Test
    fun onClearFilters_restoresDefaultSearchState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val studyInsightsRepository = FakeStudyInsightsRepository()
            val viewModel = createViewModel(studyInsightsRepository = studyInsightsRepository)
            advanceUntilIdle()

            viewModel.onKeywordChange("极限")
            viewModel.onTagSelected("定义")
            viewModel.onDeckSelected("deck_1")
            advanceUntilIdle()
            viewModel.onCardSelected("card_1")
            viewModel.onMasterySelected(QuestionMasteryLevel.FAMILIAR)
            advanceUntilIdle()

            viewModel.onClearFilters()
            advanceUntilIdle()

            val lastFilters = studyInsightsRepository.searchFilters.last()
            assertEquals("", viewModel.uiState.value.keyword)
            assertEquals(QuestionStatus.ACTIVE, viewModel.uiState.value.selectedStatus)
            assertNull(viewModel.uiState.value.selectedTag)
            assertNull(viewModel.uiState.value.selectedDeckId)
            assertNull(viewModel.uiState.value.selectedCardId)
            assertNull(viewModel.uiState.value.selectedMasteryLevel)
            assertEquals(QuestionStatus.ACTIVE, lastFilters.status)
            assertTrue(lastFilters.keyword.isBlank())
            assertNull(lastFilters.tag)
            assertNull(lastFilters.deckId)
            assertNull(lastFilters.cardId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 错误时必须清空结果并回写失败信息，避免页面显示旧结果造成误导。
     */
    @Test
    fun refresh_failureShowsErrorMessageAndClearsResults() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val studyInsightsRepository = FakeStudyInsightsRepository().apply {
                searchError = IllegalStateException("搜索异常")
            }

            val viewModel = createViewModel(studyInsightsRepository = studyInsightsRepository)
            advanceUntilIdle()

            assertEquals("搜索异常", viewModel.uiState.value.errorMessage)
            assertTrue(viewModel.uiState.value.results.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 构造入口允许按测试覆盖不同初始路由参数，避免每条用例都重复装配默认依赖。
     */
    private fun createViewModel(
        initialDeckId: String? = null,
        initialCardId: String? = null,
        initialTag: String? = null,
        studyInsightsRepository: FakeStudyInsightsRepository = FakeStudyInsightsRepository(),
        deckRepository: FakeDeckRepository = FakeDeckRepository(),
        cardRepository: FakeCardRepository = FakeCardRepository()
    ): QuestionSearchViewModel = QuestionSearchViewModel(
        initialDeckId = initialDeckId,
        initialCardId = initialCardId,
        initialTag = initialTag,
        studyInsightsRepository = studyInsightsRepository,
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        timeProvider = FixedTimeProvider(nowEpochMillis = 10_000L)
    )

    /**
     * 卡组测试数据保持最小合法结构，便于把断言聚焦在筛选状态而不是对象构造。
     */
    private fun deck(id: String, name: String): Deck = Deck(
        id = id,
        name = name,
        description = "",
        tags = emptyList(),
        intervalStepCount = 8,
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 卡片测试数据只保留搜索页真正关心的层级和标题字段。
     */
    private fun card(id: String, deckId: String, title: String): Card = Card(
        id = id,
        deckId = deckId,
        title = title,
        description = "",
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 搜索结果构造函数固定为 active question，便于同时断言 mastery 和 due 标签。
     */
    private fun questionContext(questionId: String): QuestionContext = QuestionContext(
        question = Question(
            id = questionId,
            cardId = "card_1",
            prompt = "什么是极限",
            answer = "趋近过程的结果",
            tags = listOf("定义"),
            status = QuestionStatus.ACTIVE,
            stageIndex = 3,
            dueAt = 5_000L,
            lastReviewedAt = null,
            reviewCount = 2,
            lapseCount = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        deckId = "deck_1",
        deckName = "数学",
        cardTitle = "极限"
    )
}
