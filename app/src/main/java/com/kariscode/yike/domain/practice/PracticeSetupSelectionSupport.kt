package com.kariscode.yike.domain.practice

import com.kariscode.yike.domain.model.QuestionContext

/**
 * 练习范围选择的纯函数收口到 domain，是为了让“deck -> card -> question 的层级裁剪、归一化与题量推导”
 * 不再依赖 UI 模型，从而在后续复用到其它入口（例如快捷练习、跨页面推荐）时保持口径一致。
 */

/**
 * deck 选项输出只保留练习范围需要的统计字段，是为了让上层在不展开卡片层时也能建立范围感知。
 */
data class PracticeDeckOption(
    val deckId: String,
    val deckName: String,
    val cardCount: Int,
    val questionCount: Int,
    val isSelected: Boolean
)

/**
 * card 选项显式带出所属 deck 信息，是为了支持跨 deck 联合练习时仍能保留清晰的范围来源。
 */
data class PracticeCardOption(
    val cardId: String,
    val deckId: String,
    val deckName: String,
    val cardTitle: String,
    val questionCount: Int,
    val isSelected: Boolean
)

/**
 * question 选项只承载领域数据，不做 UI 预览截断，是为了避免展示策略反向污染领域层。
 */
data class PracticeQuestionOption(
    val questionId: String,
    val cardId: String,
    val deckName: String,
    val cardTitle: String,
    val prompt: String,
    val answer: String,
    val isSelected: Boolean
)

/**
 * 练习范围投影把 deck/card/question 三层候选与归一化后的选择结果打包，是为了让上层只消费“一次计算的快照”，
 * 而不是在 ViewModel 里拼接多份中间集合并自行维护一致性。
 */
data class PracticeSelectionProjection(
    val deckOptions: List<PracticeDeckOption>,
    val cardOptions: List<PracticeCardOption>,
    val questionOptions: List<PracticeQuestionOption>,
    val selectedDeckIds: Set<String>,
    val selectedCardIds: Set<String>,
    val selectedQuestionIds: Set<String>?,
    val effectiveQuestionCount: Int
)

/**
 * deck 过滤优先于 card/question 生效，是为了让“选择若干 deck”自然成为下层候选的父范围。
 */
fun List<QuestionContext>.filterBySelectedDecks(selectedDeckIds: Set<String>): List<QuestionContext> {
    if (selectedDeckIds.isEmpty()) return this
    return filter { context -> context.deckId in selectedDeckIds }
}

/**
 * card 过滤在未显式选择卡片时保留当前 deck 范围全部题目，是为了让第一版仍支持“整组过一遍”的操作语义。
 */
fun List<QuestionContext>.filterBySelectedCards(selectedCardIds: Set<String>): List<QuestionContext> {
    if (selectedCardIds.isEmpty()) return this
    return filter { context -> context.question.cardId in selectedCardIds }
}

/**
 * deck 选项统一按题目上下文聚合，是为了复用同一份只读查询结果，而不额外请求其它列表接口。
 */
fun buildDeckOptions(
    allQuestionContexts: List<QuestionContext>,
    selectedDeckIds: Set<String>
): List<PracticeDeckOption> = allQuestionContexts
    .groupBy(QuestionContext::deckId)
    .values
    .map(::buildDeckOptionSnapshot)
    .map { snapshot ->
        PracticeDeckOption(
            deckId = snapshot.deckId,
            deckName = snapshot.deckName,
            cardCount = snapshot.cardCount,
            questionCount = snapshot.questionCount,
            isSelected = snapshot.deckId in selectedDeckIds
        )
    }

/**
 * card 选项基于 deck 过滤后的题目集合重建，是为了让卡片多选始终只暴露当前父范围内可用内容。
 */
fun buildCardOptions(
    questionContexts: List<QuestionContext>,
    selectedCardIds: Set<String>
): List<PracticeCardOption> = questionContexts
    .groupBy { context -> context.question.cardId }
    .values
    .map { cardContexts ->
        val first = cardContexts.first()
        PracticeCardOption(
            cardId = first.question.cardId,
            deckId = first.deckId,
            deckName = first.deckName,
            cardTitle = first.cardTitle,
            questionCount = cardContexts.size,
            isSelected = first.question.cardId in selectedCardIds
        )
    }

/**
 * 题目手选若恰好等于全集则回退成 null，是为了让状态明确区分“全选当前范围”和“显式裁剪过”。
 */
fun Set<String>.normalizeQuestionSelection(
    availableQuestionIds: Set<String>
): Set<String>? = when {
    isEmpty() -> emptySet()
    size == availableQuestionIds.size -> null
    else -> this
}

/**
 * 多选集合统一用可逆 toggle，是为了让 deck/card/question 三层都共享同一套点击语义。
 */
fun MutableSet<String>.applyToggle(id: String): Set<String> {
    if (!add(id)) {
        remove(id)
    }
    return toSet()
}

/**
 * deck/card/question 三层范围统一在纯函数里投影，是为了把合法性校验、全选回退与候选重建固定成单一路径。
 */
fun buildPracticeSelectionProjection(
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
        PracticeQuestionOption(
            questionId = context.question.id,
            cardId = context.question.cardId,
            deckName = context.deckName,
            cardTitle = context.cardTitle,
            prompt = context.question.prompt,
            answer = context.question.answer,
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
 * deck 聚合的中间快照只服务于一次性扫描与唯一卡片计数，是为了避免对同一组选项反复执行 map + distinct。
 */
private data class DeckOptionSnapshot(
    val deckId: String,
    val deckName: String,
    val cardCount: Int,
    val questionCount: Int
)

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

