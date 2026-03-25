package com.kariscode.yike.feature.practice

import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PracticeSetupSelectionSupportTest 锁定练习范围纯函数的聚合与归一化语义，
 * 避免后续继续压缩集合处理时把“deck 题量”“唯一卡片数”和“全选回退”这些基础规则改偏。
 */
class PracticeSetupSelectionSupportTest {
    /**
     * deck 选项必须基于题目上下文同时得出题量和唯一卡片数，
     * 这样练习设置页才能让用户在不展开卡片层时就建立范围感知。
     */
    @Test
    fun buildDeckOptions_aggregatesUniqueCardCountAndQuestionCount() {
        val options = buildDeckOptions(
            allQuestionContexts = listOf(
                questionContext(questionId = "q1", deckId = "deck_1", deckName = "数学", cardId = "card_1"),
                questionContext(questionId = "q2", deckId = "deck_1", deckName = "数学", cardId = "card_1"),
                questionContext(questionId = "q3", deckId = "deck_1", deckName = "数学", cardId = "card_2"),
                questionContext(questionId = "q4", deckId = "deck_2", deckName = "英语", cardId = "card_3")
            ),
            selectedDeckIds = setOf("deck_1")
        )

        assertEquals(2, options.size)
        assertEquals("deck_1", options.first().deckId)
        assertEquals(2, options.first().cardCount)
        assertEquals(3, options.first().questionCount)
        assertEquals(true, options.first().isSelected)
        assertEquals(1, options.last().cardCount)
    }

    /**
     * 范围投影必须同时清理失效 deck/card/question 选择，
     * 这样 ViewModel 在内容变化后重建状态时不会继续保留界面上已经不存在的旧范围。
     */
    @Test
    fun buildPracticeSelectionProjection_normalizesInvalidSelectionsAndQuestionCount() {
        val projection = buildPracticeSelectionProjection(
            allQuestionContexts = listOf(
                questionContext(questionId = "q1", deckId = "deck_1", deckName = "数学", cardId = "card_1"),
                questionContext(questionId = "q2", deckId = "deck_1", deckName = "数学", cardId = "card_2")
            ),
            selectedDeckIds = setOf("deck_1", "deck_missing"),
            selectedCardIds = setOf("card_2", "card_missing"),
            selectedQuestionIds = setOf("q2", "q_missing")
        )

        assertEquals(setOf("deck_1"), projection.selectedDeckIds)
        assertEquals(setOf("card_2"), projection.selectedCardIds)
        assertNull(projection.selectedQuestionIds)
        assertEquals(1, projection.effectiveQuestionCount)
        assertEquals(listOf("q2"), projection.questionOptions.map { it.questionId })
    }

    /**
     * 题目层手选若覆盖当前全集，就应回退成 `null`，
     * 这样状态可以明确区分“用户真的缩小了范围”和“当前范围等价于全选”。
     */
    @Test
    fun normalizeQuestionSelection_returnsNullWhenSelectionMatchesAvailableQuestions() {
        val normalized = setOf("q1", "q2").normalizeQuestionSelection(
            availableQuestionIds = setOf("q1", "q2")
        )

        assertNull(normalized)
    }

    /**
     * toggle helper 必须保持可逆，是为了让 deck/card/question 三层多选都能共享同一种点击语义。
     */
    @Test
    fun applyToggle_addsAndRemovesIdsSymmetrically() {
        val selection = mutableSetOf("q1")

        val added = selection.applyToggle("q2")
        val removed = selection.applyToggle("q1")

        assertEquals(setOf("q1", "q2"), added)
        assertEquals(setOf("q2"), removed)
    }

    /**
     * 测试数据只保留练习设置页会读取的字段，
     * 这样断言能聚焦在分组与归一化，而不是对象构造噪音。
     */
    private fun questionContext(
        questionId: String,
        deckId: String,
        deckName: String,
        cardId: String
    ): QuestionContext = QuestionContext(
        question = Question(
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
        ),
        deckId = deckId,
        deckName = deckName,
        cardTitle = "卡片 $cardId"
    )
}
