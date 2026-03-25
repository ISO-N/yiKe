package com.kariscode.yike.feature.deck

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * DeckListViewModel 测试锁定卡组页的关键管理语义，
 * 避免卡组页在收敛为单一归档入口后又退回到多套重复动作。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    /**
     * 卡组页只保留归档入口后，点击归档应直接写入归档状态，
     * 这样列表页就不会再维护一套和归档等价的重复删除语义。
     */
    @Test
    fun onToggleArchiveClick_archivesDeckAndKeepsPhysicalDeleteUnused() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeDeckRepository()
            val item = createDeckSummary(deckId = "deck_1")
            repository.archivedDecksFlow.value = listOf(item)
            val viewModel = DeckListViewModel(
                deckRepository = repository,
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 321L
                }
            )

            viewModel.onToggleArchiveClick(item)
            advanceUntilIdle()

            assertEquals(1, repository.setArchivedCalls.size)
            assertEquals("deck_1", repository.setArchivedCalls.single().deckId)
            assertEquals(true, repository.setArchivedCalls.single().archived)
            assertEquals(321L, repository.setArchivedCalls.single().updatedAt)
            assertEquals(0, repository.deletedDeckIds.size)
            assertEquals("已归档，可在已归档内容中恢复", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 新建卡组默认提供 8 段间隔，是为了让用户在不理解高级设置时也能直接沿用既有默认曲线。
     */
    @Test
    fun onCreateDeckClick_prefillsDefaultIntervalStepCount() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DeckListViewModel(
                deckRepository = FakeDeckRepository(),
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 123L
                }
            )

            viewModel.onCreateDeckClick()

            assertEquals("8", viewModel.uiState.value.editor?.intervalStepCountText)
            assertNull(viewModel.uiState.value.editor?.validationMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 保存前统一清洗标签，能避免用户在补全与手输混用时留下重复或空白标签。
     */
    @Test
    fun onConfirmSave_normalizesTagsBeforePersisting() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeDeckRepository()
            val viewModel = DeckListViewModel(
                deckRepository = repository,
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 456L
                }
            )

            viewModel.onCreateDeckClick()
            viewModel.onDraftNameChange("数学")
            viewModel.onDraftTagsChange(listOf(" 高频 ", "高频", "", "线性 代数"))
            viewModel.onConfirmSave()
            advanceUntilIdle()

            assertEquals(listOf("高频", "线性 代数"), repository.upsertedDecks.single().tags)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 名称为空时必须停留在编辑态并回写校验信息，
     * 否则用户会看到保存无效却不知道具体原因。
     */
    @Test
    fun onConfirmSave_blankNameShowsValidationMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DeckListViewModel(
                deckRepository = FakeDeckRepository(),
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 456L
                }
            )

            viewModel.onCreateDeckClick()
            viewModel.onConfirmSave()

            assertEquals(ErrorMessages.NAME_REQUIRED, viewModel.uiState.value.editor?.validationMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 间隔步数越界时必须阻止保存，
     * 否则卡组会落入调度器不支持的非法区间。
     */
    @Test
    fun onConfirmSave_invalidIntervalStepCountShowsValidationMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DeckListViewModel(
                deckRepository = FakeDeckRepository(),
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 456L
                }
            )

            viewModel.onCreateDeckClick()
            viewModel.onDraftNameChange("数学")
            viewModel.onDraftIntervalStepCountChange("99")
            viewModel.onConfirmSave()

            assertEquals(
                ErrorMessages.INTERVAL_STEP_COUNT_INVALID,
                viewModel.uiState.value.editor?.validationMessage
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 关闭编辑器应丢弃当前草稿，
     * 这样页面才能明确表达“未保存不生效”的交互语义。
     */
    @Test
    fun onDismissEditor_clearsEditorState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DeckListViewModel(
                deckRepository = FakeDeckRepository(),
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 456L
                }
            )

            viewModel.onCreateDeckClick()
            viewModel.onDismissEditor()

            assertNull(viewModel.uiState.value.editor)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 关键词筛选改为 ViewModel 维护可见列表后，页面重组不应再重复过滤，
     * 因此这里直接锁定“输入关键词后 uiState.visibleItems 立即收敛”的行为。
     */
    @Test
    fun onKeywordChange_updatesVisibleItemsInUiState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeDeckRepository()
            val baseMathSummary = createDeckSummary(deckId = "deck_math")
            val mathSummary = baseMathSummary.copy(deck = baseMathSummary.deck.copy(name = "数学"))
            val englishSummary = createDeckSummary(deckId = "deck_en")
            repository.archivedDecksFlow.value = listOf(
                mathSummary,
                englishSummary
            )
            val viewModel = DeckListViewModel(
                deckRepository = repository,
                studyInsightsRepository = FakeStudyInsightsRepository(),
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 789L
                }
            )
            advanceUntilIdle()

            viewModel.onKeywordChange("英语")

            assertEquals(1, viewModel.uiState.value.visibleItems.size)
            assertEquals("英语", viewModel.uiState.value.visibleItems.single().deck.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 测试数据显式保留聚合字段，是为了让归档动作在真实列表项上下文里执行。
     */
    private fun createDeckSummary(deckId: String): DeckSummary = DeckSummary(
        deck = Deck(
            id = deckId,
            name = "英语",
            description = "",
            tags = listOf("词汇"),
            intervalStepCount = 8,
            archived = false,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        cardCount = 2,
        questionCount = 5,
        dueQuestionCount = 1
    )

    /**
     * FakeDeckRepository 只记录 ViewModel 本次关心的写路径，
     * 这样测试可以聚焦“卡组页是否只剩归档语义”这一条行为。
     */
    private class FakeDeckRepository : DeckRepository {
        val archivedDecksFlow = MutableStateFlow<List<DeckSummary>>(emptyList())
        val setArchivedCalls = mutableListOf<SetArchivedCall>()
        val deletedDeckIds = mutableListOf<String>()
        val upsertedDecks = mutableListOf<Deck>()

        data class SetArchivedCall(val deckId: String, val archived: Boolean, val updatedAt: Long)

        override fun observeActiveDecks(): Flow<List<Deck>> = MutableStateFlow(emptyList())

        override suspend fun listActiveDecks(): List<Deck> = emptyList()

        override fun observeActiveDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = archivedDecksFlow

        override fun observeArchivedDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = MutableStateFlow(emptyList())

        override suspend fun listRecentActiveDeckSummaries(nowEpochMillis: Long, limit: Int): List<DeckSummary> = emptyList()

        override suspend fun findById(deckId: String): Deck? = null

        override suspend fun upsert(deck: Deck) {
            upsertedDecks.add(deck)
        }

        override suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long) {
            setArchivedCalls.add(SetArchivedCall(deckId, archived, updatedAt))
        }

        override suspend fun delete(deckId: String) {
            deletedDeckIds.add(deckId)
        }
    }

    /**
     * 标签候选对本组测试只需要稳定返回固定数据，便于把断言聚焦在卡组页自身的清洗逻辑。
     */
    private class FakeStudyInsightsRepository : StudyInsightsRepository {
        override suspend fun searchQuestionContexts(filters: com.kariscode.yike.domain.model.QuestionQueryFilters) =
            emptyList<com.kariscode.yike.domain.model.QuestionContext>()

        override suspend fun listDueQuestionContexts(nowEpochMillis: Long) =
            emptyList<com.kariscode.yike.domain.model.QuestionContext>()

        override suspend fun listAvailableTags(limit: Int): List<String> = listOf("高频", "定义")

        override suspend fun getDeckMasterySummary(deckId: String) =
            com.kariscode.yike.domain.model.DeckMasterySummarySnapshot(
                totalQuestions = 0,
                newCount = 0,
                learningCount = 0,
                familiarCount = 0,
                masteredCount = 0
            )

        override suspend fun getReviewAnalytics(startEpochMillis: Long?) =
            throw UnsupportedOperationException("Not required for DeckListViewModelTest")

        override suspend fun listReviewTimestamps(startEpochMillis: Long?): List<Long> = emptyList()

        override suspend fun listStageAgainRatios(startEpochMillis: Long?): List<com.kariscode.yike.domain.model.StageAgainRatioSnapshot> =
            emptyList()

        override suspend fun listUpcomingDueAts(startEpochMillis: Long, endEpochMillis: Long): List<Long> = emptyList()
    }
}

