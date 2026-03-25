package com.kariscode.yike.feature.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchStateResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import com.kariscode.yike.domain.usecase.DeckSaveRequest
import com.kariscode.yike.domain.usecase.DeckSaveResult
import com.kariscode.yike.domain.usecase.GetDeckAvailableTagsUseCase
import com.kariscode.yike.domain.usecase.ObserveDeckSummariesUseCase
import com.kariscode.yike.domain.usecase.SaveDeckUseCase
import com.kariscode.yike.domain.usecase.ToggleDeckArchiveUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 列表页状态显式包含“编辑草稿/删除确认”是为了避免在 Composable 里堆叠多个 remember 变量，
 * 这样 ViewModel 可以集中管理交互分支并更容易加测试。
 */
data class DeckListUiState(
    val isLoading: Boolean,
    val keyword: String,
    val items: List<DeckSummary>,
    val visibleItems: List<DeckSummary>,
    val availableTags: List<String>,
    val editor: DeckMetadataDraft?,
    val message: String?,
    val errorMessage: String?
)

/**
 * ViewModel 持有列表页的交互状态，避免把校验、ID 生成与时间戳策略散落在 UI 层，
 * 从而保证后续复习统计、备份恢复对数据语义的理解一致。
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    timeProvider: TimeProvider,
    private val observeDeckSummariesUseCase: ObserveDeckSummariesUseCase =
        ObserveDeckSummariesUseCase(deckRepository = deckRepository, timeProvider = timeProvider),
    private val getDeckAvailableTagsUseCase: GetDeckAvailableTagsUseCase =
        GetDeckAvailableTagsUseCase(studyInsightsRepository = studyInsightsRepository),
    private val saveDeckUseCase: SaveDeckUseCase =
        SaveDeckUseCase(deckRepository = deckRepository, timeProvider = timeProvider),
    private val toggleDeckArchiveUseCase: ToggleDeckArchiveUseCase =
        ToggleDeckArchiveUseCase(deckRepository = deckRepository, timeProvider = timeProvider)
) : ViewModel() {
    /**
     * 卡组聚合流在失败后需要可重启，因此单独保存订阅任务，供“重试”明确取消并重新拉起。
     */
    private var deckSummariesJob: Job? = null

    private var insightTags: List<String> = emptyList()

    private val _uiState = MutableStateFlow(
        DeckListUiState(
            isLoading = true,
            keyword = "",
            items = emptyList(),
            visibleItems = emptyList(),
            availableTags = emptyList(),
            editor = null,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Snackbar 展示完成功提示后就应清理 message，
     * 这样在配置变更或重新进入页面时不会重复弹出同一条反馈。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 主动重试时需要同时重建卡组流订阅和标签候选，是为了把失败后的恢复路径也保持成完整快照。
     */
    fun refresh() {
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                errorMessage = null
            )
        }
        observeDeckSummaries()
        refreshAvailableTags()
    }

    /**
     * 新建入口打开空草稿，可让保存逻辑统一复用同一套校验与时间戳策略。
     */
    fun onCreateDeckClick() {
        openEditor(
            DeckMetadataDraft(
                entityId = null,
                name = "",
                description = "",
                tags = emptyList(),
                intervalStepCountText = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT.toString()
            )
        )
    }

    /**
     * 编辑入口把现有值写入草稿，避免 UI 自己维护一份表单副本导致与数据源不一致。
     */
    fun onEditDeckClick(item: DeckSummary) {
        openEditor(
            DeckMetadataDraft(
                entityId = item.deck.id,
                name = item.deck.name,
                description = item.deck.description,
                tags = item.deck.tags,
                intervalStepCountText = item.deck.intervalStepCount.toString()
            )
        )
    }

    /**
     * 输入变更统一写入草稿状态，便于后续做就地校验与按钮可用性控制。
     */
    fun onDraftNameChange(value: String) {
        updateEditor { it.updateName(value) }
    }

    /**
     * 描述变更和名称变更同样进入草稿，以确保保存时读取到的是同一份表单状态。
     */
    fun onDraftDescriptionChange(value: String) {
        updateEditor { it.updateDescription(value) }
    }

    /**
     * 标签改动与名称、描述同样回写草稿，是为了让补全结果在保存前可见且可撤销。
     */
    fun onDraftTagsChange(tags: List<String>) {
        updateEditor { it.updateTags(DeckTagNormalizer.normalize(tags)) }
    }

    /**
     * 间隔次数和其他表单字段一样由草稿统一持有，是为了让保存时读取到的是同一份编辑上下文。
     */
    fun onDraftIntervalStepCountChange(value: String) {
        updateEditor { it.updateIntervalStepCountText(value) }
    }

    /**
     * 查找关键字只保留在页面状态里，是为了让筛选输入在配置变更后仍能保留当前浏览上下文。
     */
    fun onKeywordChange(value: String) {
        _uiState.update { state ->
            state.copy(
                keyword = value,
                visibleItems = filterVisibleItems(
                    items = state.items,
                    keyword = value
                )
            )
        }
    }

    /**
     * 关闭编辑器会丢弃草稿，这样能明确“未保存不生效”的交互语义。
     */
    fun onDismissEditor() {
        _uiState.update(DeckListStateReducer::dismissEditor)
    }

    /**
     * 保存逻辑集中在 ViewModel，是为了让校验与 ID/时间戳策略统一落在一处，
     * 避免不同页面对“合法名称/创建时间”产生不同理解。
     */
    fun onConfirmSave() {
        val editor = _uiState.value.editor ?: return
        val trimmedName = editor.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { state ->
                DeckListStateReducer.updateEditor(state) {
                    it.withValidationMessage(ErrorMessages.NAME_REQUIRED)
                }
            }
            return
        }
        val intervalStepCount = editor.intervalStepCountText.toIntOrNull()?.let { parsed ->
            parsed.takeIf {
                it in ReviewSchedulerV1.MIN_INTERVAL_STEP_COUNT..ReviewSchedulerV1.MAX_INTERVAL_STEP_COUNT
            }
        }
        if (intervalStepCount == null) {
            _uiState.update { state ->
                DeckListStateReducer.updateEditor(state) {
                    it.withValidationMessage(ErrorMessages.INTERVAL_STEP_COUNT_INVALID)
                }
            }
            return
        }

        launchStateResult(state = _uiState) {
            action {
                val normalizedTags = DeckTagNormalizer.normalize(editor.tags)
                saveDeckUseCase(
                    DeckSaveRequest(
                        deckId = editor.entityId,
                        name = trimmedName,
                        description = editor.description,
                        tags = normalizedTags,
                        intervalStepCount = intervalStepCount
                    )
                )
            }
            onSuccess { state, result ->
                val successMessage = if (result is DeckSaveResult.Created) {
                    SuccessMessages.DECK_CREATED
                } else {
                    SuccessMessages.DECK_UPDATED
                }
                DeckListStateReducer.saveSucceeded(
                    state = state,
                    successMessage = successMessage
                )
            }
            onFailure { state, _ ->
                DeckListStateReducer.mutationFailed(state, ErrorMessages.SAVE_FAILED)
            }
        }
    }

    /**
     * 题库标签补全单独读取，是为了让卡组标签能复用用户已经在问题层建立的分类词汇。
     */
    private fun refreshAvailableTags() {
        launchStateResult(state = _uiState) {
            action { getDeckAvailableTagsUseCase(limit = 12) }
            onSuccess { state, tags ->
                insightTags = DeckTagNormalizer.normalize(tags)
                DeckListStateReducer.availableTagsLoaded(
                    state = state,
                    availableTags = DeckTagNormalizer.mergeAvailableTags(
                        items = state.items,
                        insightTags = insightTags
                    )
                )
            }
            onFailure { state, _ -> state }
        }
    }

    /**
     * 卡组流订阅单独收口后，“首屏加载”和“用户点击重试”都能走同一条重建订阅路径。
     */
    private fun observeDeckSummaries() {
        deckSummariesJob?.cancel()
        deckSummariesJob = viewModelScope.launch {
            observeDeckSummariesUseCase()
                .catch { throwable ->
                    _uiState.update { state ->
                        DeckListStateReducer.loadFailed(
                            state = state,
                            errorMessage = throwable.userMessageOr(ErrorMessages.LOAD_FAILED)
                        )
                    }
                }
                .collect { items ->
                    _uiState.update { state ->
                        DeckListStateReducer.itemsLoaded(
                            state = state,
                            items = items,
                            visibleItems = filterVisibleItems(
                                items = items,
                                keyword = state.keyword
                            ),
                            availableTags = DeckTagNormalizer.mergeAvailableTags(
                                items = items,
                                insightTags = insightTags
                            )
                        )
                    }
                }
        }
    }

    /**
     * 卡组页只保留归档入口，是为了把“暂时移出默认列表、需要时再恢复”的语义收敛成单一路径。
     */
    fun onToggleArchiveClick(item: DeckSummary) {
        launchStateResult(state = _uiState) {
            action {
                toggleDeckArchiveUseCase(
                    deckId = item.deck.id,
                    archived = !item.deck.archived
                )
            }
            onSuccess { state, _ ->
                DeckListStateReducer.archiveToggled(state, item.deck.archived)
            }
            onFailure { state, _ ->
                DeckListStateReducer.mutationFailed(state, ErrorMessages.UPDATE_FAILED)
            }
        }
    }

    /**
     * 打开编辑器时统一清空旧反馈，是为了避免新一轮编辑仍残留上一次保存或失败提示。
     */
    private fun openEditor(editor: DeckMetadataDraft) {
        _uiState.update { state -> DeckListStateReducer.openEditor(state, editor) }
    }

    /**
     * 草稿更新集中到单点后，标题和描述输入就能共享同一套“无草稿则忽略”的保护逻辑。
     */
    private fun updateEditor(transform: (DeckMetadataDraft) -> DeckMetadataDraft) {
        _uiState.update { state -> DeckListStateReducer.updateEditor(state, transform) }
    }

    /**
     * 筛选逻辑下沉到 ViewModel，是为了避免每次重组重复过滤列表，
     * 同时让测试可以直接断言“输入关键词后可见列表”这一页面核心结果。
     */
    private fun filterVisibleItems(
        items: List<DeckSummary>,
        keyword: String
    ): List<DeckSummary> {
        val trimmedKeyword = keyword.trim()
        if (trimmedKeyword.isBlank()) {
            return items
        }
        return items.filter { item ->
            item.deck.name.contains(trimmedKeyword, ignoreCase = true) ||
                item.deck.description.contains(trimmedKeyword, ignoreCase = true) ||
                item.deck.tags.any { tag -> tag.contains(trimmedKeyword, ignoreCase = true) }
        }
    }

    companion object {
        /**
         * 在不引入 DI 框架时，通过工厂注入依赖能保持 ViewModel 的可测试性，
         * 同时避免在 ViewModel 内部直接访问全局单例。
         */
        fun factory(
            deckRepository: DeckRepository,
            studyInsightsRepository: StudyInsightsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            DeckListViewModel(deckRepository, studyInsightsRepository, timeProvider)
        }
    }
}


