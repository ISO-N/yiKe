package com.kariscode.yike.feature.deck

import com.kariscode.yike.domain.model.DeckSummary

/**
 * 标签清洗与候选合并抽成纯 helper，是为了让卡组编辑、补全和未来检索入口共享同一套口径，
 * 而不是把字符串规则散落在 ViewModel 的不同分支里。
 */
internal object DeckTagNormalizer {
    /**
     * 标签在保存前统一清洗空白和大小写重复，是为了避免后续搜索与补全出现肉眼相同却存成两份的噪声。
     */
    fun normalize(tags: List<String>): List<String> {
        val normalizedTags = mutableListOf<String>()
        val deduplicatedKeys = linkedSetOf<String>()
        tags.forEach { rawTag ->
            val normalizedTag = rawTag
                .trim()
                .replace(Regex("\\s+"), " ")
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
     * 卡组列表与题库洞察都可能贡献候选标签，因此合并逻辑统一收口后才能保证弹窗与列表筛选看到一致集合。
     */
    fun mergeAvailableTags(
        items: List<DeckSummary>,
        insightTags: List<String>
    ): List<String> = normalize(
        insightTags + items.flatMap { summary -> summary.deck.tags }
    ).sortedBy { tag -> tag.lowercase() }
}
