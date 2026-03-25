package com.kariscode.yike.feature.search

import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * QuestionSearchStateFactoryTest 锁定搜索状态工厂对元数据刷新的保留语义，
 * 避免后续继续压缩映射逻辑时把合法 cardId 误清空，或把失效 cardId 继续带进搜索条件。
 */
class QuestionSearchStateFactoryTest {
    /**
     * 当前 cardId 只应在新元数据里仍然存在时保留，
     * 这样用户刷新题库后不会继续带着一个界面上已不存在的筛选条件。
     */
    @Test
    fun withMetadata_preservesOnlyStillAvailableSelectedCardId() {
        val state = baseState(selectedCardId = "card_2")
        val metadata = SearchMetadata(
            tags = listOf("定义"),
            decks = listOf(SearchDeckOption(id = "deck_1", name = "数学")),
            cards = listOf(
                SearchCardOption(id = "card_1", title = "极限"),
                SearchCardOption(id = "card_2", title = "导数")
            )
        )

        val updated = QuestionSearchStateFactory.withMetadata(
            state = state,
            metadata = metadata
        )

        assertEquals("card_2", updated.selectedCardId)
        assertEquals(metadata.cards, updated.cardOptions)
        assertNull(updated.errorMessage)
    }

    /**
     * 元数据刷新后若原 cardId 已失效，应立即回退为空，
     * 这样后续搜索与可见筛选控件始终围绕同一份合法候选集工作。
     */
    @Test
    fun withMetadata_clearsSelectedCardIdWhenItIsNoLongerAvailable() {
        val state = baseState(selectedCardId = "card_missing")
        val metadata = SearchMetadata(
            tags = emptyList(),
            decks = emptyList(),
            cards = listOf(SearchCardOption(id = "card_1", title = "极限"))
        )

        val updated = QuestionSearchStateFactory.withMetadata(
            state = state,
            metadata = metadata
        )

        assertNull(updated.selectedCardId)
        assertEquals(metadata.cards, updated.cardOptions)
    }

    /**
     * 一键清空应回到活动题默认筛选，但保留由其他来源回写的结果集合，
     * 这样 ViewModel 在触发下一轮搜索前仍能维持一份结构完整的状态快照。
     */
    @Test
    fun clearedFilters_restoresDefaultFilterState() {
        val state = baseState(
            selectedCardId = "card_1",
            results = listOf(result(questionId = "q1", deckId = "deck_1", cardId = "card_1"))
        ).copy(
            keyword = "极限",
            selectedTag = "高频",
            selectedStatus = null,
            selectedMasteryLevel = com.kariscode.yike.domain.model.QuestionMasteryLevel.FAMILIAR,
            cardOptions = listOf(SearchCardOption(id = "card_1", title = "极限")),
            errorMessage = "旧错误"
        )

        val cleared = QuestionSearchStateFactory.clearedFilters(state)

        assertEquals("", cleared.keyword)
        assertNull(cleared.selectedTag)
        assertEquals(QuestionStatus.ACTIVE, cleared.selectedStatus)
        assertNull(cleared.selectedDeckId)
        assertNull(cleared.selectedCardId)
        assertNull(cleared.selectedMasteryLevel)
        assertEquals(emptyList<SearchCardOption>(), cleared.cardOptions)
        assertNull(cleared.errorMessage)
        assertEquals(state.results, cleared.results)
    }

    /**
     * 搜索失败后必须清空旧结果，是为了避免界面继续展示和当前筛选不一致的历史命中项。
     */
    @Test
    fun withSearchFailed_clearsResultsAndStopsLoading() {
        val state = baseState(
            selectedCardId = null,
            results = listOf(result(questionId = "q1", deckId = "deck_1", cardId = "card_1"))
        ).copy(isLoading = true)

        val failed = QuestionSearchStateFactory.withSearchFailed(
            state = state,
            errorMessage = "搜索失败"
        )

        assertEquals(false, failed.isLoading)
        assertEquals(emptyList<QuestionSearchResultUiModel>(), failed.results)
        assertEquals("搜索失败", failed.errorMessage)
    }

    /**
     * 整批结果导出到练习参数时必须去重 cardId，但保留题目顺序，
     * 这样练习设置页既能拿到最小范围，又不会丢掉搜索结果当前的题目列表顺序。
     */
    @Test
    fun buildPracticeArgsForResults_deduplicatesCardIdsAndKeepsQuestionIds() {
        val results = listOf(
            result(questionId = "q1", deckId = "deck_1", cardId = "card_1"),
            result(questionId = "q2", deckId = "deck_1", cardId = "card_1"),
            result(questionId = "q3", deckId = "deck_1", cardId = "card_2")
        )
        val state = baseState(
            selectedCardId = null,
            selectedDeckId = "deck_1",
            results = results
        )

        val args = QuestionSearchStateFactory.buildPracticeArgsForResults(state)

        assertEquals(
            PracticeSessionArgs(
                deckIds = listOf("deck_1"),
                cardIds = listOf("card_1", "card_2"),
                questionIds = listOf("q1", "q2", "q3")
            ),
            args
        )
    }

    /**
     * 单条结果导出时应只带当前题目的最小范围，
     * 这样“练习这题”不会意外把同卡片的其他结果也一起带进会话。
     */
    @Test
    fun buildPracticeArgsForResult_usesSingleResultScope() {
        val item = result(questionId = "q1", deckId = "deck_1", cardId = "card_1")

        val args = QuestionSearchStateFactory.buildPracticeArgsForResult(item)

        assertEquals(
            PracticeSessionArgs(
                deckIds = listOf("deck_1"),
                cardIds = listOf("card_1"),
                questionIds = listOf("q1")
            ),
            args
        )
    }

    /**
     * 基础状态只保留工厂真正会读取的字段，
     * 这样测试可以聚焦在元数据映射和 cardId 保留规则，而不是整页状态装配噪音。
     */
    private fun baseState(
        selectedCardId: String?,
        selectedDeckId: String? = "deck_1",
        results: List<QuestionSearchResultUiModel> = emptyList()
    ): QuestionSearchUiState = QuestionSearchUiState(
        isLoading = true,
        keyword = "",
        selectedTag = null,
        selectedStatus = QuestionStatus.ACTIVE,
        selectedDeckId = selectedDeckId,
        selectedCardId = selectedCardId,
        selectedMasteryLevel = null,
        availableTags = emptyList(),
        deckOptions = emptyList(),
        cardOptions = emptyList(),
        results = results,
        errorMessage = "旧错误"
    )

    /**
     * 搜索结果测试数据只保留导出练习参数真正需要的字段，
     * 这样测试可以聚焦在协议映射，而不是 UI 额外展示状态。
     */
    private fun result(
        questionId: String,
        deckId: String,
        cardId: String
    ): QuestionSearchResultUiModel {
        val question = Question(
            id = questionId,
            cardId = cardId,
            prompt = "题目 $questionId",
            answer = "答案 $questionId",
            tags = emptyList(),
            status = QuestionStatus.ACTIVE,
            stageIndex = 0,
            dueAt = 1_000L,
            lastReviewedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            createdAt = 1L,
            updatedAt = 1L
        )
        return QuestionSearchResultUiModel(
            context = QuestionContext(
                question = question,
                deckId = deckId,
                deckName = "卡组 $deckId",
                cardTitle = "卡片 $cardId"
            ),
            mastery = QuestionMasteryCalculator.snapshot(question),
            isDue = true
        )
    }
}
