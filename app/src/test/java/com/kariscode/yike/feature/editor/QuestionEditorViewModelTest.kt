package com.kariscode.yike.feature.editor

import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import com.kariscode.yike.testsupport.FakeAppSettingsRepository
import com.kariscode.yike.testsupport.FakeCardRepository
import com.kariscode.yike.testsupport.FakeQuestionRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import com.kariscode.yike.testsupport.defaultAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QuestionEditorViewModel 测试聚焦“载入、校验、保存”三条主路径，
 * 避免多问题编辑的高风险行为只能靠人工回归兜底。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuestionEditorViewModelTest {

    /**
     * 首次载入必须把卡片标题和问题草稿一起回填，
     * 否则编辑页会在已有内容场景下误显示为空表单。
     */
    @Test
    fun init_loadsCardAndQuestionsIntoUiState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val cardRepository = FakeCardRepository().apply {
                cardById["card_1"] = card(
                    id = "card_1",
                    title = "极限",
                    description = "数学分析"
                )
            }
            val questionRepository = FakeQuestionRepository().apply {
                questionsByCard["card_1"] = mutableListOf(
                    question(id = "question_1", cardId = "card_1", prompt = "什么是极限")
                )
            }

            val viewModel = createViewModel(
                cardRepository = cardRepository,
                questionRepository = questionRepository
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("极限", viewModel.uiState.value.title)
            assertEquals("数学分析", viewModel.uiState.value.description)
            assertEquals(listOf("什么是极限"), viewModel.uiState.value.questions.map { it.prompt })
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 保存前必须拦截空题面并在草稿上回写校验信息，
     * 否则多题编辑会把半成品问题一起写入数据库。
     */
    @Test
    fun onSaveClick_blankPromptShowsValidationAndSkipsPersist() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val questionRepository = FakeQuestionRepository()
            val viewModel = createViewModel(questionRepository = questionRepository)
            advanceUntilIdle()

            viewModel.onAddQuestionClick()
            viewModel.onSaveClick()

            assertEquals(ErrorMessages.VALIDATION_ERROR, viewModel.uiState.value.errorMessage)
            assertEquals(
                ErrorMessages.QUESTION_CONTENT_REQUIRED,
                viewModel.uiState.value.questions.single().validationMessage
            )
            assertTrue(questionRepository.upsertCalls.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 新增问题保存时必须按提醒设置初始化 dueAt，并把已删除旧题批量清掉，
     * 这样“新增 + 删除”混合编辑才不会留下脏数据或错误的初始调度。
     */
    @Test
    fun onSaveClick_newQuestionUsesReminderTimeAndDeletesRemovedItems() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            val settingsRepository = FakeAppSettingsRepository(
                defaultAppSettings().copy(
                    dailyReminderHour = 7,
                    dailyReminderMinute = 45
                )
            )
            val cardRepository = FakeCardRepository().apply {
                cardById["card_1"] = card(id = "card_1", title = "极限")
            }
            val existingQuestion = question(id = "question_old", cardId = "card_1", prompt = "旧题")
            val questionRepository = FakeQuestionRepository().apply {
                questionsByCard["card_1"] = mutableListOf(existingQuestion)
            }

            val viewModel = createViewModel(
                cardRepository = cardRepository,
                questionRepository = questionRepository,
                appSettingsRepository = settingsRepository,
                timeProvider = timeProvider
            )
            advanceUntilIdle()

            viewModel.onDeleteQuestionClick("question_old")
            viewModel.onAddQuestionClick()
            val newQuestionId = viewModel.uiState.value.questions.single().id
            viewModel.onQuestionPromptChange(newQuestionId, "新题")
            viewModel.onQuestionAnswerChange(newQuestionId, "新答案")
            viewModel.onSaveClick()
            advanceUntilIdle()

            val persistedQuestion = questionRepository.upsertCalls.single().single()
            val expectedDueAt = InitialDueAtCalculator.compute(
                nowEpochMillis = timeProvider.nowEpochMillis(),
                reminderHour = 7,
                reminderMinute = 45
            )

            assertEquals("新题", persistedQuestion.prompt)
            assertEquals("新答案", persistedQuestion.answer)
            assertEquals(0, persistedQuestion.stageIndex)
            assertEquals(expectedDueAt, persistedQuestion.dueAt)
            assertEquals(listOf(listOf("question_old")), questionRepository.deleteAllCalls)
            assertEquals(SuccessMessages.SAVED, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 丢失卡片时必须回写明确错误，而不是让页面一直停留在 loading。
     */
    @Test
    fun init_missingCardShowsNotFoundError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(cardRepository = FakeCardRepository())
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(ErrorMessages.CARD_NOT_FOUND, viewModel.uiState.value.errorMessage)
            assertNull(viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 构造入口统一注入固定依赖，是为了让每个测试只改自己关心的部分状态。
     */
    private fun createViewModel(
        cardRepository: FakeCardRepository = FakeCardRepository().apply {
            cardById["card_1"] = card(id = "card_1", title = "默认卡片")
        },
        questionRepository: FakeQuestionRepository = FakeQuestionRepository(),
        appSettingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        timeProvider: FixedTimeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
    ): QuestionEditorViewModel = QuestionEditorViewModel(
        cardId = "card_1",
        deckId = "deck_1",
        cardRepository = cardRepository,
        questionRepository = questionRepository,
        appSettingsRepository = appSettingsRepository,
        timeProvider = timeProvider
    )

    /**
     * 卡片测试数据固定在单一 deck 下，便于把断言聚焦在编辑页自身语义。
     */
    private fun card(
        id: String,
        title: String,
        description: String = ""
    ): Card = Card(
        id = id,
        deckId = "deck_1",
        title = title,
        description = description,
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 问题测试数据显式保留调度字段，避免保存前后断言脱离真实业务结构。
     */
    private fun question(
        id: String,
        cardId: String,
        prompt: String
    ): Question = Question(
        id = id,
        cardId = cardId,
        prompt = prompt,
        answer = "",
        tags = emptyList(),
        status = QuestionStatus.ACTIVE,
        stageIndex = 1,
        dueAt = 5_000L,
        lastReviewedAt = null,
        reviewCount = 2,
        lapseCount = 0,
        createdAt = 1L,
        updatedAt = 2L
    )
}
