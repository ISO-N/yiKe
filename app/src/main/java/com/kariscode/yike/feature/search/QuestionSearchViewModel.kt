package com.kariscode.yike.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.userMessageOr
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.launchStateResult
import com.kariscode.yike.core.viewmodel.restartStateResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.usecase.GetQuestionSearchMetadataUseCase
import com.kariscode.yike.domain.usecase.QuestionSearchMetadataSnapshot
import com.kariscode.yike.domain.usecase.SearchQuestionsUseCase
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
    private val initialTag: String?,
    private val getQuestionSearchMetadataUseCase: GetQuestionSearchMetadataUseCase,
    private val searchQuestionsUseCase: SearchQuestionsUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        QuestionSearchUiState(
            isLoading = true,
            keyword = "",
            selectedTag = initialTag,
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
        launchStateResult(state = _uiState) {
            action {
                val snapshot = _uiState.value
                parallel(
                    first = { loadSearchMetadata(snapshot.selectedDeckId) },
                    second = { searchQuestions(snapshot) }
                )
            }
            onStart { it.copy(isLoading = true, errorMessage = null) }
            onSuccess { state, result ->
                val metadata = result.first
                val results = result.second
                QuestionSearchStateFactory.withMetadata(
                    state = state,
                    metadata = metadata,
                    results = results
                )
            }
            onFailure { state, throwable ->
                state.copy(
                    isLoading = false,
                    errorMessage = throwable.userMessageOr(ErrorMessages.SEARCH_LOAD_FAILED)
                )
            }
        }
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
            action = {
                getQuestionSearchMetadataUseCase(
                    selectedDeckId = deckId,
                    tagLimit = 8
                ).cards.map { card -> SearchCardOption(id = card.id, title = card.title) }
            },
            onSuccess = { cards ->
                updateSearchFilters {
                    QuestionSearchStateFactory.withDeckSelection(
                        state = it,
                        deckId = deckId,
                        cards = cards,
                        errorMessage = null
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    QuestionSearchStateFactory.withDeckSelection(
                        state = it,
                        deckId = deckId,
                        cards = emptyList(),
                        errorMessage = throwable.userMessageOr(ErrorMessages.SEARCH_LOAD_FAILED)
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
        val snapshot = _uiState.value
        searchJob = restartStateResult(
            state = _uiState,
            previousJob = searchJob,
            action = { searchQuestions(snapshot) },
            onStart = { it.copy(isLoading = true, errorMessage = null) },
            onSuccess = { state, results ->
                state.copy(
                    isLoading = false,
                    results = results,
                    errorMessage = null
                )
            },
            onFailure = { state, throwable ->
                state.copy(
                    isLoading = false,
                    results = emptyList(),
                    errorMessage = throwable.userMessageOr(ErrorMessages.SEARCH_FAILED)
                )
            }
        )
    }

    /**
     * 刷新把标签、卡组和当前卡片候选一次性并发取回，是为了让搜索页只维护一轮一致的筛选快照。
     */
    private suspend fun loadSearchMetadata(selectedDeckId: String?): SearchMetadata {
        val metadata = getQuestionSearchMetadataUseCase(
            selectedDeckId = selectedDeckId,
            tagLimit = 8
        )
        return metadata.toSearchMetadata()
    }

    /**
     * 搜索元数据到 UI 选项的映射保留在 ViewModel 内，是为了让领域层继续只关心模型，界面层自行决定展示文案。
     */
    private fun QuestionSearchMetadataSnapshot.toSearchMetadata(): SearchMetadata {
        return SearchMetadata(
            tags = tags,
            decks = decks.map { deck -> SearchDeckOption(id = deck.id, name = deck.name) },
            cards = cards.map { card -> SearchCardOption(id = card.id, title = card.title) }
        )
    }

    /**
     * 搜索条件先快照后执行，能避免协程运行中 UI 再次改筛选时把半旧半新的条件拼到同一轮查询里。
     */
    private suspend fun searchQuestions(snapshot: QuestionSearchUiState): List<QuestionSearchResultUiModel> {
        val now = timeProvider.nowEpochMillis()
        val questionContexts = searchQuestionsUseCase(
            filters = QuestionSearchStateFactory.toQueryFilters(snapshot)
        )
        return QuestionSearchStateFactory.buildResults(
            questionContexts = questionContexts,
            nowEpochMillis = now
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

    companion object {
        /**
         * 工厂显式接收初始筛选参数，是为了让首页和卡片页都能通过路由预置不同搜索上下文。
         */
        fun factory(
            initialDeckId: String?,
            initialCardId: String?,
            initialTag: String?,
            studyInsightsRepository: StudyInsightsRepository,
            deckRepository: DeckRepository,
            cardRepository: CardRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            QuestionSearchViewModel(
                initialDeckId = initialDeckId,
                initialCardId = initialCardId,
                initialTag = initialTag,
                getQuestionSearchMetadataUseCase = GetQuestionSearchMetadataUseCase(
                    studyInsightsRepository = studyInsightsRepository,
                    deckRepository = deckRepository,
                    cardRepository = cardRepository
                ),
                searchQuestionsUseCase = SearchQuestionsUseCase(
                    studyInsightsRepository = studyInsightsRepository
                ),
                timeProvider = timeProvider
            )
        }
    }
}
