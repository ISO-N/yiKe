package com.kariscode.yike.feature.practice

import com.kariscode.yike.domain.model.QuestionContext

/**
 * deck 选项聚合快照把展示字段和唯一卡片数先收口，是为了让 deck 选项构建保持纯聚合逻辑，
 * 避免 `buildDeckOptions` 随着统计字段增加继续堆叠更多临时集合操作。
 */
private data class DeckOptionSnapshot(
    val deckId: String,
    val deckName: String,
    val cardCount: Int,
    val questionCount: Int
)

/**
 * 练习范围投影把 deck/card/question 三层候选与归一化后的选择集中打包，
 * 是为了让 ViewModel 只消费“一轮范围计算的结果”，而不再手动拼接多份中间集合。
 */
internal data class PracticeSelectionProjection(
    val deckOptions: List<PracticeDeckOptionUiModel>,
    val cardOptions: List<PracticeCardOptionUiModel>,
    val questionOptions: List<PracticeQuestionOptionUiModel>,
    val selectedDeckIds: Set<String>,
    val selectedCardIds: Set<String>,
    val selectedQuestionIds: Set<String>?,
    val effectiveQuestionCount: Int
)

/**
 * deck 过滤先于 card 与 question 生效，是为了让“选择若干卡组”天然成为下层候选的父范围。
 */
fun List<QuestionContext>.filterBySelectedDecks(selectedDeckIds: Set<String>): List<QuestionContext> {
    if (selectedDeckIds.isEmpty()) {
        return this
    }
    return filter { context -> context.deckId in selectedDeckIds }
}

/**
 * card 过滤在未显式选择卡片时保留当前 deck 范围全部题目，是为了让第一版仍支持“整组过一遍”。
 */
fun List<QuestionContext>.filterBySelectedCards(selectedCardIds: Set<String>): List<QuestionContext> {
    if (selectedCardIds.isEmpty()) {
        return this
    }
    return filter { context -> context.question.cardId in selectedCardIds }
}

/**
 * deck 选项统一按题目上下文聚合，是为了复用同一份只读查询结果而不再额外请求列表接口。
 */
fun buildDeckOptions(
    allQuestionContexts: List<QuestionContext>,
    selectedDeckIds: Set<String>
): List<PracticeDeckOptionUiModel> = allQuestionContexts
    .groupBy(QuestionContext::deckId)
    .values
    .map(::buildDeckOptionSnapshot)
    .map { snapshot ->
        PracticeDeckOptionUiModel(
            deckId = snapshot.deckId,
            deckName = snapshot.deckName,
            cardCount = snapshot.cardCount,
            questionCount = snapshot.questionCount,
            isSelected = snapshot.deckId in selectedDeckIds
        )
    }

/**
 * 卡片选项基于 deck 过滤后的题目集合重建，是为了让卡片多选始终只暴露当前父范围内可用的内容。
 */
fun buildCardOptions(
    questionContexts: List<QuestionContext>,
    selectedCardIds: Set<String>
): List<PracticeCardOptionUiModel> = questionContexts
    .groupBy { context -> context.question.cardId }
    .values
    .map { cardContexts ->
        val first = cardContexts.first()
        PracticeCardOptionUiModel(
            cardId = first.question.cardId,
            deckId = first.deckId,
            deckName = first.deckName,
            cardTitle = first.cardTitle,
            questionCount = cardContexts.size,
            isSelected = first.question.cardId in selectedCardIds
        )
    }

/**
 * 题目手选若恰好等于全集则回退成 `null`，是为了让状态明确区分“全选当前范围”和“显式裁剪过”。
 */
fun Set<String>.normalizeQuestionSelection(
    availableQuestionIds: Set<String>
): Set<String>? = when {
    isEmpty() -> emptySet()
    size == availableQuestionIds.size -> null
    else -> this
}

/**
 * 多选集合统一用同一个切换 helper，是为了让 deck/card/question 三层都共享一致的交互语义。
 */
fun MutableSet<String>.applyToggle(id: String): Set<String> {
    if (!add(id)) {
        remove(id)
    }
    return toSet()
}

/**
 * deck/card/question 三层范围统一在纯函数里投影，是为了把合法性校验、全选回退和候选重建固定成单一口径。
 */
internal fun buildPracticeSelectionProjection(
    allQuestionContexts: List<QuestionContext>,
    selectedDeckIds: Set<String>,
    selectedCardIds: Set<String>,
    selectedQuestionIds: Set<String>?
): PracticeSelectionProjection {
    val deckOptions = buildDeckOptions(
        allQuestionContexts = allQuestionContexts,
        selectedDeckIds = selectedDeckIds
    )
    val normalizedDeckIds = selectedDeckIds.intersect(deckOptions.mapTo(LinkedHashSet()) { it.deckId })
    val deckScopedContexts = allQuestionContexts.filterBySelectedDecks(normalizedDeckIds)
    val cardOptions = buildCardOptions(
        questionContexts = deckScopedContexts,
        selectedCardIds = selectedCardIds
    )
    val normalizedCardIds = selectedCardIds.intersect(cardOptions.mapTo(LinkedHashSet()) { it.cardId })
    val questionScopedContexts = deckScopedContexts.filterBySelectedCards(normalizedCardIds)
    val availableQuestionIds = questionScopedContexts.mapTo(LinkedHashSet()) { it.question.id }
    val normalizedQuestionIds = selectedQuestionIds
        ?.intersect(availableQuestionIds)
        ?.normalizeQuestionSelection(availableQuestionIds)
    val questionOptions = questionScopedContexts.map { context ->
        PracticeQuestionOptionUiModel(
            questionId = context.question.id,
            cardId = context.question.cardId,
            deckName = context.deckName,
            cardTitle = context.cardTitle,
            prompt = context.question.prompt,
            answerPreview = context.question.answer.ifBlank { "无答案" }.take(48),
            isSelected = normalizedQuestionIds?.contains(context.question.id) ?: true
        )
    }
    return PracticeSelectionProjection(
        deckOptions = deckOptions,
        cardOptions = cardOptions,
        questionOptions = questionOptions,
        selectedDeckIds = normalizedDeckIds,
        selectedCardIds = normalizedCardIds,
        selectedQuestionIds = normalizedQuestionIds,
        effectiveQuestionCount = normalizedQuestionIds?.size ?: questionOptions.size
    )
}

/**
 * 每个 deck 分组只扫描一次来汇总唯一卡片数，是为了避免题目规模变大后仍为同一组选项重复做 `map + distinct`。
 */
private fun buildDeckOptionSnapshot(deckContexts: List<QuestionContext>): DeckOptionSnapshot {
    val first = deckContexts.first()
    val cardIds = LinkedHashSet<String>(deckContexts.size)
    deckContexts.forEach { context ->
        cardIds += context.question.cardId
    }
    return DeckOptionSnapshot(
        deckId = first.deckId,
        deckName = first.deckName,
        cardCount = cardIds.size,
        questionCount = deckContexts.size
    )
}
