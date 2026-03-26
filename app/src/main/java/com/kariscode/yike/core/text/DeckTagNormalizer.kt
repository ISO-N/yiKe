package com.kariscode.yike.core.text

import com.kariscode.yike.core.string.normalizeSpaces
import com.kariscode.yike.domain.model.DeckSummary

/**
 * 标签清洗与候选合并抽到 core/text，是为了让“同样的标签输入”在不同入口保持一致的归一化口径，
 * 避免后续搜索/补全出现肉眼相同却存成多份的噪声。
 */
object DeckTagNormalizer {
    /**
     * 保存前统一清洗空白与大小写重复，是为了避免“Tag / tag /  Tag ”这类视觉相同但语义重复的输入污染候选集合。
     */
    fun normalize(tags: List<String>): List<String> {
        val normalizedTags = mutableListOf<String>()
        val deduplicatedKeys = linkedSetOf<String>()
        tags.forEach { rawTag ->
            val normalizedTag = rawTag.normalizeSpaces()
            if (normalizedTag.isBlank()) {
                return@forEach
            }
            if (deduplicatedKeys.add(normalizedTag.lowercase())) {
                normalizedTags.add(normalizedTag)
            }
        }
        return normalizedTags
    }

    /**
     * 列表与洞察都可能贡献候选标签，统一合并后再排序，是为了让弹窗与筛选看到的是同一套稳定集合。
     */
    fun mergeAvailableTags(
        items: List<DeckSummary>,
        insightTags: List<String>
    ): List<String> = normalize(
        insightTags + items.flatMap { summary -> summary.deck.tags }
    ).sortedBy { tag -> tag.lowercase() }
}

