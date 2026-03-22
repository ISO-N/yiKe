package com.kariscode.yike.feature.editor

import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionEditorDraftItemSnapshot
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import com.kariscode.yike.testsupport.FakeCardRepository
import com.kariscode.yike.testsupport.FakeQuestionEditorDraftRepository
import com.kariscode.yike.testsupport.FakeQuestionRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * QuestionEditorViewModel 测试聚焦“恢复草稿、自动保存、正式保存”三条高风险路径，
 * 避免编辑体验退化后只能依赖人工杀进程回归才能发现问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class QuestionEditorViewModelTest {

    /**
     * 首次进入若检测到草稿，应先停留在正式内容并要求用户显式选择，
     * 否则旧草稿会悄悄覆盖数据库中的真实内容。
     */
    @Test
    fun init_existingDraftShowsRestoreDialogBeforeApplyingSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val cardRepository = FakeCardRepository().apply {
                cardById["card_1"] = card(
                    id = "card_1",
                    title = "正式标题",
                    description = "正式说明"
                )
            }
            val questionRepository = FakeQuestionRepository().apply {
                questionsByCard["card_1"] = mutableListOf(
                    question(id = "question_1", cardId = "card_1", prompt = "正式问题")
                )
            }
            val draftRepository = FakeQuestionEditorDraftRepository().apply {
                draftsByCardId["card_1"] = draftSnapshot(
                    title = "草稿标题",
                    questionPrompt = "草稿问题",
                    savedAt = 1_700_000_000_123L
                )
            }

            val viewModel = createViewModel(
                cardRepository = cardRepository,
                questionRepository = questionRepository,
                draftRepository = draftRepository
            )
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("正式标题", viewModel.uiState.value.title)
            assertEquals("正式说明", viewModel.uiState.value.description)
            assertEquals(listOf("正式问题"), viewModel.uiState.value.questions.map { it.prompt })
            assertTrue(viewModel.uiState.value.restoreDraftDialogVisible)
            assertEquals(1_700_000_000_123L, viewModel.uiState.value.restoreDraftInfo?.savedAt)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 用户确认恢复后应完整回填草稿和删除列表，
     * 否则“删除旧题 + 新增新题”的混合编辑在恢复后会出现语义缺失。
     */
    @Test
    fun onRestoreDraftConfirm_appliesSnapshotAndMarksDraftAsUnsavedChanges() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val draftRepository = FakeQuestionEditorDraftRepository().apply {
                draftsByCardId["card_1"] = QuestionEditorDraftSnapshot(
                    cardId = "card_1",
                    title = "草稿标题",
                    description = "草稿说明",
                    questions = listOf(
                        QuestionEditorDraftItemSnapshot(
                            id = "temp_question_1",
                            prompt = "草稿问题",
                            answer = "草稿答案",
                            isNew = true
                        )
                    ),
                    deletedQuestionIds = listOf("question_old"),
                    savedAt = 1_700_000_000_222L
                )
            }
            val viewModel = createViewModel(draftRepository = draftRepository)
            advanceUntilIdle()

            viewModel.onRestoreDraftConfirm()

            assertEquals("草稿标题", viewModel.uiState.value.title)
            assertEquals("草稿说明", viewModel.uiState.value.description)
            assertEquals(listOf("草稿问题"), viewModel.uiState.value.questions.map { it.prompt })
            assertTrue(viewModel.uiState.value.hasUnsavedChanges)
            assertFalse(viewModel.uiState.value.hasPendingDraftChanges)
            assertFalse(viewModel.uiState.value.restoreDraftDialogVisible)
            assertEquals(SuccessMessages.DRAFT_RESTORED, viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 自动保存必须防抖，只保存最后一次输入，
     * 否则连续打字会产生大量无意义磁盘写入并增加退出时的竞态风险。
     */
    @Test
    fun onQuestionPromptChange_debouncesAutoSaveAndPersistsLatestSnapshot() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val draftRepository = FakeQuestionEditorDraftRepository()
            val viewModel = createViewModel(draftRepository = draftRepository)
            advanceUntilIdle()

            viewModel.onAddQuestionClick()
            val draftId = viewModel.uiState.value.questions.single().id
            viewModel.onQuestionPromptChange(draftId, "第一版")
            advanceTimeBy(1_000L)
            viewModel.onQuestionPromptChange(draftId, "最终版")
            advanceTimeBy(1_499L)

            assertTrue(draftRepository.savedDrafts.isEmpty())

            advanceTimeBy(1L)
            advanceUntilIdle()

            assertEquals(1, draftRepository.savedDrafts.size)
            assertEquals("最终版", draftRepository.savedDrafts.single().questions.single().prompt)
            assertFalse(viewModel.uiState.value.hasPendingDraftChanges)
            assertTrue(viewModel.uiState.value.hasUnsavedChanges)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 手动保存草稿应该只写本地草稿而不写正式仓储，
     * 这样用户才能明确区分“暂存进度”和“正式生效”两个动作。
     */
    @Test
    fun onSaveDraftClick_persistsDraftWithoutWritingOfficialRepositories() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val questionRepository = FakeQuestionRepository()
            val draftRepository = FakeQuestionEditorDraftRepository()
            val viewModel = createViewModel(
                questionRepository = questionRepository,
                draftRepository = draftRepository
            )
            advanceUntilIdle()

            viewModel.onTitleChange("新的卡片标题")
            viewModel.onSaveDraftClick()
            advanceUntilIdle()

            assertEquals(1, draftRepository.savedDrafts.size)
            assertEquals("新的卡片标题", draftRepository.savedDrafts.single().title)
            assertTrue(questionRepository.upsertCalls.isEmpty())
            assertEquals(SuccessMessages.DRAFT_SAVED, viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 返回前补存草稿后再导航，是为了保证用户即使忘记点“保存草稿”也不会丢掉最后一次编辑。
     */
    @Test
    fun onExitAttempt_persistsPendingDraftThenEmitsNavigateBack() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val effects = mutableListOf<QuestionEditorEffect>()
            val draftRepository = FakeQuestionEditorDraftRepository()
            val viewModel = createViewModel(draftRepository = draftRepository)
            val effectJob = launch { viewModel.effects.collect { effect -> effects += effect } }
            advanceUntilIdle()

            viewModel.onDescriptionChange("准备离开前的说明")
            viewModel.onExitAttempt()
            advanceUntilIdle()

            assertEquals(1, draftRepository.savedDrafts.size)
            assertEquals("准备离开前的说明", draftRepository.savedDrafts.single().description)
            assertEquals(listOf(QuestionEditorEffect.NavigateBack), effects)

            effectJob.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 正式保存成功后必须清掉本地草稿，
     * 否则下次重新进入页面会被已经提交过的旧草稿再次打断。
     */
    @Test
    fun onSaveClick_successClearsDraftAndPersistsOfficialContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
            val cardRepository = FakeCardRepository().apply {
                cardById["card_1"] = card(id = "card_1", title = "原始标题")
            }
            val existingQuestion = question(id = "question_old", cardId = "card_1", prompt = "旧题")
            val questionRepository = FakeQuestionRepository().apply {
                questionsByCard["card_1"] = mutableListOf(existingQuestion)
            }
            val draftRepository = FakeQuestionEditorDraftRepository()

            val viewModel = createViewModel(
                cardRepository = cardRepository,
                questionRepository = questionRepository,
                draftRepository = draftRepository,
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
                nowEpochMillis = timeProvider.nowEpochMillis()
            )

            assertEquals("新题", persistedQuestion.prompt)
            assertEquals("新答案", persistedQuestion.answer)
            assertEquals(expectedDueAt, persistedQuestion.dueAt)
            assertEquals(listOf(listOf("question_old")), questionRepository.deleteAllCalls)
            assertEquals(listOf("card_1"), draftRepository.deletedCardIds)
            assertEquals(SuccessMessages.SAVED, viewModel.uiState.value.message)
            assertFalse(viewModel.uiState.value.hasUnsavedChanges)
            assertFalse(viewModel.uiState.value.restoreDraftDialogVisible)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 损坏草稿应当被安全忽略并回退到正式内容，
     * 否则单个坏文件就会把整张卡片的编辑入口卡死。
     */
    @Test
    fun init_corruptedDraftFallsBackToOfficialContent() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val draftRepository = FakeQuestionEditorDraftRepository().apply {
                corruptedCardIds += "card_1"
            }
            val viewModel = createViewModel(draftRepository = draftRepository)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.restoreDraftDialogVisible)
            assertEquals(SuccessMessages.DRAFT_CORRUPTED_RESET, viewModel.uiState.value.message)
            assertEquals("默认卡片", viewModel.uiState.value.title)
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
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
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
     * 构造入口统一注入固定依赖，是为了让每个测试只改自己关心的部分状态。
     */
    private fun createViewModel(
        cardRepository: FakeCardRepository = FakeCardRepository().apply {
            cardById["card_1"] = card(id = "card_1", title = "默认卡片")
        },
        questionRepository: FakeQuestionRepository = FakeQuestionRepository(),
        draftRepository: FakeQuestionEditorDraftRepository = FakeQuestionEditorDraftRepository(),
        timeProvider: FixedTimeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
    ): QuestionEditorViewModel = QuestionEditorViewModel(
        cardId = "card_1",
        deckId = "deck_1",
        cardRepository = cardRepository,
        questionRepository = questionRepository,
        questionEditorDraftRepository = draftRepository,
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

    /**
     * 草稿快照构造集中在测试底部，是为了让恢复相关断言只关注差异字段而不是重复样板。
     */
    private fun draftSnapshot(
        title: String,
        questionPrompt: String,
        savedAt: Long
    ): QuestionEditorDraftSnapshot = QuestionEditorDraftSnapshot(
        cardId = "card_1",
        title = title,
        description = "草稿说明",
        questions = listOf(
            QuestionEditorDraftItemSnapshot(
                id = "temp_question_1",
                prompt = questionPrompt,
                answer = "草稿答案",
                isNew = true
            )
        ),
        deletedQuestionIds = emptyList(),
        savedAt = savedAt
    )
}
