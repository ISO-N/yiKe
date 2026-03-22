package com.kariscode.yike.feature.practice

import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.testsupport.FakePracticeRepository
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
 * PracticeSetupViewModel 测试锁定范围选择与参数映射，
 * 是为了避免第二版题目级手选上线后再次把 deck/card/question 三层语义混在一起。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PracticeSetupViewModelTest {

    /**
     * 题目级手选最终必须严格映射到 `questionIds`，
     * 否则会话页虽然看起来缩圈了，真正查询时仍可能混入范围外题目。
     */
    @Test
    fun buildSessionArgs_questionSelectionMapsToQuestionIds() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakePracticeRepository().apply {
                questionContexts = listOf(
                    questionContext(questionId = "q_1"),
                    questionContext(questionId = "q_2")
                )
            }
            val viewModel = PracticeSetupViewModel(
                initialArgs = PracticeSessionArgs(),
                practiceRepository = repository
            )
            advanceUntilIdle()

            viewModel.onQuestionToggle("q_2")

            val args = viewModel.buildSessionArgs()
            assertEquals(listOf("q_1"), args.questionIds)
            assertEquals(PracticeOrderMode.SEQUENTIAL, args.orderMode)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 当用户清空题目选择时，设置页必须明确回到 0 题空状态，
     * 这样用户才知道需要返回调整范围，而不是误以为页面没有刷新。
     */
    @Test
    fun onClearQuestionSelection_setsEmptyScope() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakePracticeRepository().apply {
                questionContexts = listOf(questionContext(questionId = "q_1"))
            }
            val viewModel = PracticeSetupViewModel(
                initialArgs = PracticeSessionArgs(),
                practiceRepository = repository
            )
            advanceUntilIdle()

            viewModel.onClearQuestionSelection()

            assertEquals(0, viewModel.uiState.value.effectiveQuestionCount)
            assertEquals(emptySet<String>(), viewModel.uiState.value.selectedQuestionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 未做题目级裁剪时 `questionIds` 应保持为空，
     * 这样导航协议才能继续表达“当前 card/deck 范围下全部题目”这一稳定语义。
     */
    @Test
    fun buildSessionArgs_withoutQuestionTrim_keepsQuestionIdsEmpty() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakePracticeRepository().apply {
                questionContexts = listOf(
                    questionContext(questionId = "q_1", cardId = "card_1"),
                    questionContext(questionId = "q_2", cardId = "card_2")
                )
            }
            val viewModel = PracticeSetupViewModel(
                initialArgs = PracticeSessionArgs(deckIds = listOf("deck_1")),
                practiceRepository = repository
            )
            advanceUntilIdle()

            val args = viewModel.buildSessionArgs()
            assertEquals(listOf("deck_1"), args.deckIds)
            assertEquals(emptyList<String>(), args.questionIds)
            assertNull(viewModel.uiState.value.selectedQuestionIds)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 题目上下文辅助构造函数固定最小字段，是为了让测试断言聚焦在练习范围而不是对象装配细节。
     */
    private fun questionContext(
        questionId: String,
        cardId: String = "card_1",
        deckId: String = "deck_1"
    ): QuestionContext = QuestionContext(
        question = Question(
            id = questionId,
            cardId = cardId,
            prompt = "问题 $questionId",
            answer = "",
            tags = emptyList(),
            status = QuestionStatus.ACTIVE,
            stageIndex = 0,
            dueAt = 10_000L,
            lastReviewedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        deckId = deckId,
        deckName = deck(deckId).name,
        cardTitle = card(cardId, deckId).title
    )

    /**
     * 卡组辅助方法保持命名与 id 对齐，是为了让测试输出更直观可读。
     */
    private fun deck(id: String): Deck = Deck(
        id = id,
        name = id,
        description = "",
        tags = emptyList(),
        intervalStepCount = 8,
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 卡片辅助方法只保留练习范围真正关心的字段，避免噪音淹没断言重点。
     */
    private fun card(id: String, deckId: String): Card = Card(
        id = id,
        deckId = deckId,
        title = id,
        description = "",
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )
}
