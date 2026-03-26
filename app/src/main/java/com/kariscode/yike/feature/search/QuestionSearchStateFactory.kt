package com.kariscode.yike.feature.search

import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.PracticeSessionArgs

/**
 * 搜索页的元数据快照单独建模，是为了让 ViewModel 刷新时只围绕一份结构化筛选快照工作。
 */
internal data class SearchMetadata(
    val tags: List<String>,
    val decks: List<SearchDeckOption>,
    val cards: List<SearchCardOption>
)

/**
 * 搜索状态工厂把筛选转换与结果映射保持纯输入输出，是为了让检索页的状态拼装脱离协程编排代码独立演进。
 */
internal object QuestionSearchStateFactory {
    /**
     * 搜索或刷新开始时统一清理旧错误，是为了让页面在新一轮查询过程中只表达当前状态而不夹带旧失败信息。
     */
    fun withLoading(state: QuestionSearchUiState): QuestionSearchUiState = state.copy(
        isLoading = true,
        errorMessage = null
    )

    /**
     * 搜索条件由状态快照直接导出，是为了让新增筛选字段时只改一个映射入口，避免搜索与刷新口径漂移。
     */
    fun toQueryFilters(state: QuestionSearchUiState): QuestionQueryFilters = QuestionQueryFilters(
        keyword = state.keyword,
        tag = state.selectedTag,
        status = state.selectedStatus,
        deckId = state.selectedDeckId,
        cardId = state.selectedCardId,
        masteryLevel = state.selectedMasteryLevel
    )

    /**
     * 元数据与结果通常在同一轮刷新里一起回写，
     * 因此状态回写统一收敛后可以保持“保留合法 cardId”与“清空旧错误”始终一步完成。
     */
    fun withMetadata(
        state: QuestionSearchUiState,
        metadata: SearchMetadata,
        results: List<QuestionSearchResultUiModel> = state.results
    ): QuestionSearchUiState = state.copy(
            isLoading = false,
            availableTags = metadata.tags,
            deckOptions = metadata.decks,
            cardOptions = metadata.cards,
            selectedCardId = preserveSelectedCardId(
                selectedCardId = state.selectedCardId,
                cards = metadata.cards
            ),
            results = results,
            errorMessage = null
        )

    /**
     * 卡组切换后的卡片候选与错误清理总是成组变化，收口成纯转换后能避免成功与失败分支继续复制同一份字段模板。
     */
    fun withDeckSelection(
        state: QuestionSearchUiState,
        deckId: String?,
        cards: List<SearchCardOption>,
        errorMessage: String?
    ): QuestionSearchUiState = state.copy(
        selectedDeckId = deckId,
        selectedCardId = null,
        cardOptions = cards,
        errorMessage = errorMessage
    )

    /**
     * 一键清空统一回到默认筛选，是为了把“活动题 + 无其他条件”这条高频工作流固定成单一入口。
     */
    fun clearedFilters(state: QuestionSearchUiState): QuestionSearchUiState = state.copy(
        keyword = "",
        selectedTag = null,
        selectedStatus = QuestionStatus.ACTIVE,
        selectedDeckId = null,
        selectedCardId = null,
        selectedMasteryLevel = null,
        cardOptions = emptyList(),
        errorMessage = null
    )

    /**
     * 搜索结果在工厂里统一映射熟练度与 due 状态，是为了让 ViewModel 只负责拿快照而不是逐条拼 UI 模型。
     */
    fun buildResults(
        questionContexts: List<QuestionContext>,
        nowEpochMillis: Long
    ): List<QuestionSearchResultUiModel> = questionContexts.map { context ->
        QuestionSearchResultUiModel(
            context = context,
            mastery = QuestionMasteryCalculator.snapshot(context.question),
            isDue = context.question.status == QuestionStatus.ACTIVE && context.question.dueAt <= nowEpochMillis
        )
    }

    /**
     * 刷新成功后的搜索结果统一回写，是为了让实时搜索与手动刷新共享同一套“完成查询”状态模板。
     */
    fun withSearchSucceeded(
        state: QuestionSearchUiState,
        results: List<QuestionSearchResultUiModel>
    ): QuestionSearchUiState = state.copy(
        isLoading = false,
        results = results,
        errorMessage = null
    )

    /**
     * 搜索失败时统一清空旧结果，是为了避免页面在新查询失败后继续展示已经失效的旧命中项。
     */
    fun withSearchFailed(
        state: QuestionSearchUiState,
        errorMessage: String
    ): QuestionSearchUiState = state.copy(
        isLoading = false,
        results = emptyList(),
        errorMessage = errorMessage
    )

    /**
     * 元数据刷新失败时保留现有结果但结束加载，是为了让用户仍能基于当前可见结果继续调整查询条件。
     */
    fun withRefreshFailed(
        state: QuestionSearchUiState,
        errorMessage: String
    ): QuestionSearchUiState = state.copy(
        isLoading = false,
        errorMessage = errorMessage
    )

    /**
     * 搜索页把当前结果带去练习时统一导出参数，是为了让“整批结果练习”的 deck/card/question 口径只维护一处。
     */
    fun buildPracticeArgsForResults(state: QuestionSearchUiState): PracticeSessionArgs {
        val questionIds = state.results.mapTo(ArrayList(state.results.size)) { item ->
            item.context.question.id
        }
        val cardIds = LinkedHashSet<String>(state.results.size)
        state.results.forEach { item ->
            cardIds += item.context.question.cardId
        }
        return PracticeSessionArgs(
            deckIds = state.selectedDeckId?.let(::listOf).orEmpty(),
            cardIds = cardIds.toList(),
            questionIds = questionIds
        )
    }

    /**
     * 单条结果的练习入口直接按题目上下文导出参数，是为了让列表卡片不再重复拼装同一份导航协议。
     */
    fun buildPracticeArgsForResult(item: QuestionSearchResultUiModel): PracticeSessionArgs = PracticeSessionArgs(
        deckIds = listOf(item.context.deckId),
        cardIds = listOf(item.context.question.cardId),
        questionIds = listOf(item.context.question.id)
    )

    /**
     * 当前卡片筛选只在候选列表里仍然存在时才保留，是为了避免刷新元数据后继续带着失效 cardId 做查询。
     */
    private fun preserveSelectedCardId(
        selectedCardId: String?,
        cards: List<SearchCardOption>
    ): String? {
        if (selectedCardId == null) {
            return null
        }
        val availableCardIds = cards.asSequence().map(SearchCardOption::id).toHashSet()
        return selectedCardId.takeIf(availableCardIds::contains)
    }
}
