package com.kariscode.yike.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.coroutine.parallel3
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 搜索页 ViewModel 把元数据加载和实时检索集中处理，是为了让页面只表达筛选意图而不关心查询细节。
 */
class QuestionSearchViewModel(
    private val initialDeckId: String?,
    private val initialCardId: String?,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    /**
     * 标签、卡组和当前卡组下的卡片候选本质上属于同一轮筛选元数据快照，
     * 打包后可以让刷新逻辑只维护一套成功/失败分支，而不是散落多段并发结果拼装代码。
     */
    private data class SearchMetadata(
        val tags: List<String>,
        val decks: List<SearchDeckOption>,
        val cards: List<SearchCardOption>
    )

    private val _uiState = MutableStateFlow(
        QuestionSearchUiState(
            isLoading = true,
            keyword = "",
            selectedTag = null,
            selectedStatus = QuestionStatus.ACTIVE,
            selectedDeckId = initialDeckId,
            selectedCardId = initialCardId,
            selectedMasteryLevel = null,
            availableTags = emptyList(),
            deckOptions = emptyList(),
            cardOptions = emptyList(),
            results = emptyList(),
            errorMessage = null
        )
    )
    val uiState: StateFlow<QuestionSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        /**
         * 初次进入即加载筛选元数据和首轮结果，是为了让来自首页或卡片页的入口都能立即落到真实题库。
         */
        refresh()
    }

    /**
     * 主动刷新会同步重取标签和卡组，是为了避免内容维护后返回搜索页仍看到过期筛选项。
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        launchResult(
            action = {
                val snapshot = _uiState.value
                parallel(
                    first = { loadSearchMetadata(snapshot.selectedDeckId) },
                    second = { searchQuestions(snapshot) }
                )
            },
            onSuccess = { (metadata, results) ->
                applyRefreshResult(metadata = metadata, results = results)
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: ErrorMessages.SEARCH_LOAD_FAILED
                    )
                }
            }
        )
    }

    /**
     * 关键字直接驱动搜索，是为了把“想到什么就搜什么”的主路径保持足够顺手。
     */
    fun onKeywordChange(value: String) {
        updateSearchFilters { it.copy(keyword = value, errorMessage = null) }
    }

    /**
     * 标签作为单选入口即可满足 P0，能减少同一轮搜索叠加过多条件导致的空结果。
     */
    fun onTagSelected(tag: String?) {
        updateSearchFilters { it.copy(selectedTag = tag, errorMessage = null) }
    }

    /**
     * 状态筛选显式允许“全部”，是为了在需要排查归档题时不必切到其他页面。
     */
    fun onStatusSelected(status: QuestionStatus?) {
        updateSearchFilters { it.copy(selectedStatus = status, errorMessage = null) }
    }

    /**
     * 切换卡组后需要重建卡片候选，是为了避免旧卡组的 cardId 继续残留在当前筛选里。
     */
    fun onDeckSelected(deckId: String?) {
        launchResult(
            action = { loadCardsForDeck(deckId) },
            onSuccess = { cards ->
                updateSearchFilters { it.withDeckSelection(deckId = deckId, cards = cards, errorMessage = null) }
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.withDeckSelection(
                        deckId = deckId,
                        cards = emptyList(),
                        errorMessage = throwable.message ?: ErrorMessages.SEARCH_LOAD_FAILED
                    )
                }
            }
        )
    }

    /**
     * 卡片筛选单独切换即可，因为它已经建立在当前卡组上下文之上，不需要再清空其他条件。
     */
    fun onCardSelected(cardId: String?) {
        updateSearchFilters { it.copy(selectedCardId = cardId, errorMessage = null) }
    }

    /**
     * 熟练度筛选复用统一等级定义，是为了让搜索结果与卡片摘要看到的是同一套标签语义。
     */
    fun onMasterySelected(level: QuestionMasteryLevel?) {
        updateSearchFilters { it.copy(selectedMasteryLevel = level, errorMessage = null) }
    }

    /**
     * 一键清空保留“进行中”默认状态，是为了把搜索快速拉回最常用的题库工作流。
     */
    fun onClearFilters() {
        updateSearchFilters {
            it.copy(
                keyword = "",
                selectedTag = null,
                selectedStatus = QuestionStatus.ACTIVE,
                selectedDeckId = null,
                selectedCardId = null,
                selectedMasteryLevel = null,
                cardOptions = emptyList(),
                errorMessage = null
            )
        }
    }

    /**
     * 搜索任务使用可取消作业包装，是为了避免快速连续输入时旧结果反向覆盖新状态。
     */
    private fun search() {
        searchJob?.cancel()
        val snapshot = _uiState.value
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        searchJob = launchResult(
            action = { searchQuestions(snapshot) },
            onSuccess = { results ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = results,
                        errorMessage = null
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        results = emptyList(),
                        errorMessage = throwable.message ?: ErrorMessages.SEARCH_FAILED
                    )
                }
            }
        )
    }

    /**
     * 卡片候选始终根据当前卡组即时查询，是为了保证“检索本卡”入口能准确落到最新内容。
     */
    private suspend fun loadCardsForDeck(deckId: String?): List<SearchCardOption> {
        if (deckId == null) return emptyList()
        return cardRepository.listActiveCards(deckId)
            .map { card -> SearchCardOption(id = card.id, title = card.title) }
    }

    /**
     * 刷新把标签、卡组和当前卡片候选一次性并发取回，是为了让搜索页只维护一轮一致的筛选快照。
     */
    private suspend fun loadSearchMetadata(selectedDeckId: String?): SearchMetadata {
        val (tags, decks, cards) = parallel3(
            first = { studyInsightsRepository.listAvailableTags(limit = 8) },
            second = {
                deckRepository.listActiveDecks()
                    .map { deck -> SearchDeckOption(id = deck.id, name = deck.name) }
            },
            third = { loadCardsForDeck(selectedDeckId) }
        )
        return SearchMetadata(
            tags = tags,
            decks = decks,
            cards = cards
        )
    }

    /**
     * 元数据回写收敛成状态扩展，是为了让手动刷新和后续局部元数据更新共享同一套卡片保留规则。
     */
    private fun applyRefreshResult(
        metadata: SearchMetadata,
        results: List<QuestionSearchResultUiModel>
    ) {
        _uiState.update {
            it.withMetadata(
                metadata = metadata,
                results = results
            )
        }
    }

    /**
     * 搜索条件先快照后执行，能避免协程运行中 UI 再次改筛选时把半旧半新的条件拼到同一轮查询里。
     */
    private suspend fun searchQuestions(snapshot: QuestionSearchUiState): List<QuestionSearchResultUiModel> {
        val now = timeProvider.nowEpochMillis()
        return studyInsightsRepository.searchQuestionContexts(
            filters = snapshot.toQueryFilters()
        ).map { context ->
            QuestionSearchResultUiModel(
                context = context,
                mastery = QuestionMasteryCalculator.snapshot(context.question),
                isDue = context.question.status == QuestionStatus.ACTIVE && context.question.dueAt <= now
            )
        }
    }

    /**
     * 元数据与结果通常在同一轮刷新里一起回写，
     * 状态扩展可以让“保留合法 cardId”与“清空旧错误”始终保持同一步完成。
     */
    private fun QuestionSearchUiState.withMetadata(
        metadata: SearchMetadata,
        results: List<QuestionSearchResultUiModel> = this.results
    ): QuestionSearchUiState {
        val preservedCardId = selectedCardId?.takeIf { candidateId ->
            metadata.cards.any { card -> card.id == candidateId }
        }
        return copy(
            isLoading = false,
            availableTags = metadata.tags,
            deckOptions = metadata.decks,
            cardOptions = metadata.cards,
            selectedCardId = preservedCardId,
            results = results,
            errorMessage = null
        )
    }

    /**
     * 关键字、标签和熟练度等筛选项都走同一更新入口，是为了让“改筛选后立即搜索”的约束只维护一处，
     * 避免后续新增条件时漏掉触发搜索或清空旧错误提示。
     */
    private fun updateSearchFilters(
        transform: (QuestionSearchUiState) -> QuestionSearchUiState
    ) {
        _uiState.update(transform)
        search()
    }

    /**
     * 卡组切换后的卡片候选与错误清理总是成组变化，收成扩展后能避免成功/失败分支继续复制同一份字段模板。
     */
    private fun QuestionSearchUiState.withDeckSelection(
        deckId: String?,
        cards: List<SearchCardOption>,
        errorMessage: String?
    ): QuestionSearchUiState = copy(
        selectedDeckId = deckId,
        selectedCardId = null,
        cardOptions = cards,
        errorMessage = errorMessage
    )

    /**
     * 查询条件由状态快照直接导出，是为了让新增筛选字段时只改一个映射入口，避免搜索与刷新口径漂移。
     */
    private fun QuestionSearchUiState.toQueryFilters(): QuestionQueryFilters = QuestionQueryFilters(
        keyword = keyword,
        tag = selectedTag,
        status = selectedStatus,
        deckId = selectedDeckId,
        cardId = selectedCardId,
        masteryLevel = selectedMasteryLevel
    )

    companion object {
        /**
         * 工厂显式接收初始筛选参数，是为了让首页和卡片页都能通过路由预置不同搜索上下文。
         */
        fun factory(
            initialDeckId: String?,
            initialCardId: String?,
            studyInsightsRepository: StudyInsightsRepository,
            deckRepository: DeckRepository,
            cardRepository: CardRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            QuestionSearchViewModel(
                initialDeckId = initialDeckId,
                initialCardId = initialCardId,
                studyInsightsRepository = studyInsightsRepository,
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                timeProvider = timeProvider
            )
        }
    }
}
