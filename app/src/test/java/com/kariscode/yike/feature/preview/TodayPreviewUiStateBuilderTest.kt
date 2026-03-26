package com.kariscode.yike.feature.preview

import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TodayPreviewUiStateBuilderTest 锁定预览页的汇总与排序口径，
 * 避免后续继续简化 builder 时把“总数一致”与“优先展示更早到期卡组”这两个核心语义改坏。
 */
class TodayPreviewUiStateBuilderTest {
    /**
     * 构建预览状态时应从同一批题目里同时得出汇总和分组，
     * 这样页面头部数字与下方卡组列表才不会出现口径不一致。
     */
    @Test
    fun build_usesSingleQuestionSnapshotForSummaryAndDeckSorting() {
        val dueQuestions = listOf(
            questionContext(
                questionId = "question_1",
                deckId = "deck_1",
                deckName = "数学",
                cardId = "card_1",
                cardTitle = "极限",
                stageIndex = 0,
                dueAt = 2_000L
            ),
            questionContext(
                questionId = "question_2",
                deckId = "deck_1",
                deckName = "数学",
                cardId = "card_1",
                cardTitle = "极限",
                stageIndex = 3,
                dueAt = 5_000L
            ),
            questionContext(
                questionId = "question_3",
                deckId = "deck_2",
                deckName = "英语",
                cardId = "card_2",
                cardTitle = "阅读",
                stageIndex = 1,
                dueAt = 1_000L
            )
        )

        val state = TodayPreviewUiStateBuilder.build(
            dueQuestions = dueQuestions,
            averageResponseTimeMs = 12_000.0
        )

        assertEquals(3, state.totalDueQuestions)
        assertEquals(2, state.totalDueCards)
        assertEquals(2, state.totalDecks)
        assertEquals(2, state.lowMasteryCount)
        assertEquals(1_000L, state.earliestDueAt)
        assertEquals(1, state.estimatedMinutes)
        assertEquals(12, state.averageSecondsPerQuestion)
        assertEquals(listOf("数学", "英语"), state.deckGroups.map { it.deckName })
        assertEquals(2_000L, state.deckGroups.first().cards.first().questions.first().dueAt)
        assertNull(state.errorMessage)
    }

    /**
     * 空输入仍然要给出稳定的默认状态，
     * 这样预览页在“今天没有待复习题”时不需要依赖额外分支也能安全渲染。
     */
    @Test
    fun build_withEmptyQuestions_returnsStableEmptyState() {
        val state = TodayPreviewUiStateBuilder.build(
            dueQuestions = emptyList(),
            averageResponseTimeMs = null
        )

        assertEquals(0, state.totalDueQuestions)
        assertEquals(0, state.totalDueCards)
        assertEquals(0, state.totalDecks)
        assertEquals(0, state.lowMasteryCount)
        assertEquals(0, state.estimatedMinutes)
        assertEquals(15, state.averageSecondsPerQuestion)
        assertNull(state.earliestDueAt)
        assertEquals(emptyList<TodayPreviewDeckUiModel>(), state.deckGroups)
        assertNull(state.errorMessage)
    }

    /**
     * 测试数据只保留预览页真正会读取的字段，
     * 这样断言可以聚焦在 builder 的汇总和排序，而不是对象构造噪音。
     */
    private fun questionContext(
        questionId: String,
        deckId: String,
        deckName: String,
        cardId: String,
        cardTitle: String,
        stageIndex: Int,
        dueAt: Long
    ): QuestionContext = QuestionContext(
        question = Question(
            id = questionId,
            cardId = cardId,
            prompt = "题目 $questionId",
            answer = "答案 $questionId",
            tags = emptyList(),
            status = QuestionStatus.ACTIVE,
            stageIndex = stageIndex,
            dueAt = dueAt,
            lastReviewedAt = null,
            reviewCount = stageIndex,
            lapseCount = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        deckId = deckId,
        deckName = deckName,
        cardTitle = cardTitle
    )
}
