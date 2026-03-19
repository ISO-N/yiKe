package com.kariscode.yike.feature.deck

import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DeckTagNormalizerTest 锁定标签归一化与候选合并规则，
 * 避免字符串清洗逻辑重新散回 ViewModel 分支后出现口径漂移。
 */
class DeckTagNormalizerTest {
    /**
     * 清洗应移除空白、折叠多空格并按忽略大小写去重，
     * 这样编辑弹窗和候选标签都不会出现肉眼重复的噪声。
     */
    @Test
    fun normalize_trimsCollapsesWhitespaceAndDeduplicatesCaseInsensitive() {
        val normalized = DeckTagNormalizer.normalize(
            listOf(" 高频 ", "高频", "Linear   Algebra", "linear algebra", "")
        )

        assertEquals(listOf("高频", "Linear Algebra"), normalized)
    }

    /**
     * 候选合并应同时覆盖洞察标签和已有卡组标签，
     * 这样用户在编辑卡组时才能看到完整且去重后的共识词汇。
     */
    @Test
    fun mergeAvailableTags_combinesInsightTagsAndDeckTagsIntoSortedUniqueList() {
        val availableTags = DeckTagNormalizer.mergeAvailableTags(
            items = listOf(
                createDeckSummary(tags = listOf("函数", " 高频 ")),
                createDeckSummary(tags = listOf("线性代数"))
            ),
            insightTags = listOf("高频", "定义")
        )

        assertEquals(listOf("函数", "定义", "线性代数", "高频"), availableTags)
    }

    /**
     * 用最小聚合模型构造卡组摘要，是为了让测试只聚焦标签来源而不引入无关字段噪声。
     */
    private fun createDeckSummary(tags: List<String>): DeckSummary = DeckSummary(
        deck = Deck(
            id = "deck_1",
            name = "数学",
            description = "",
            tags = tags,
            intervalStepCount = 8,
            archived = false,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        cardCount = 0,
        questionCount = 0,
        dueQuestionCount = 0
    )
}
