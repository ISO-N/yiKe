package com.kariscode.yike.feature.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.core.message.userMessageOr
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.launchStateMutation
import com.kariscode.yike.core.viewmodel.launchStateResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.feature.common.TextMetadataDraft
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 熟练度摘要集中在卡片页状态里，是为了让卡组层先暴露“整体薄弱分布”，再让用户进入具体卡片。
 */
data class DeckMasterySummary(
    val totalQuestions: Int,
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int
)

/**
 * 卡片列表状态同时包含 deck 信息与列表聚合结果，原因是页面标题与返回行为都依赖 deckId，
 * 且进程重建时需要只靠参数就能重新加载对应数据。
 */
data class CardListUiState(
    val deckId: String,
    val deckName: String?,
    val isLoading: Boolean,
    val items: List<CardSummary>,
    val masterySummary: DeckMasterySummary?,
    val editor: TextMetadataDraft?,
    val pendingDelete: CardSummary?,
    val message: String?,
    val errorMessage: String?
)

/**
 * 卡片列表 ViewModel 负责协调 deck 信息加载与列表聚合流订阅，
 * 以避免页面同时处理多个异步来源导致状态分叉。
 */
class CardListViewModel(
    private val deckId: String,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    /**
     * 熟练度摘要用独立 Job 收口，是为了在列表连续发射时只保留最后一次统计，避免无意义叠加查询。
     */
    private var masterySummaryJob: Job? = null
    private var lastMasterySummarySignature: String? = null
    private var loadingTracker = CardListLoadingTracker()

    private val _uiState = MutableStateFlow(
        CardListUiState(
            deckId = deckId,
            deckName = null,
            isLoading = true,
            items = emptyList(),
            masterySummary = null,
            editor = null,
            pendingDelete = null,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<CardListUiState> = _uiState.asStateFlow()

    init {
        /**
         * deck 元数据与卡片列表改成各自独立加载，是为了去掉 `first + collect` 的双读模式，
         * 同时继续保持列表与标题都能各自实时更新。
         */
        viewModelScope.launch { loadDeckMetadata() }
        viewModelScope.launch { observeCardSummaries() }
    }

    /**
     * deck 名称单独读取后，列表订阅就不需要为了首屏并行而额外做一次一次性读取。
     */
    private suspend fun loadDeckMetadata() {
        runCatching { deckRepository.findById(deckId) }
            .onSuccess { deck ->
                loadingTracker = loadingTracker.markDeckLoaded()
                _uiState.update { state ->
                    CardListStateReducer.deckLoaded(
                        state = state,
                        deckName = deck?.name,
                        loadingTracker = loadingTracker
                    )
                }
            }
            .onFailure {
                loadingTracker = loadingTracker.markDeckLoaded()
                _uiState.update { state ->
                    CardListStateReducer.loadFailed(
                        state = state,
                        loadingTracker = loadingTracker,
                        errorMessage = ErrorMessages.LOAD_FAILED
                    )
                }
            }
    }

    /**
     * 卡片列表始终只保留一个订阅入口，是为了让首屏和后续增删改都走同一条状态更新路径。
     */
    private suspend fun observeCardSummaries() {
        val now = timeProvider.nowEpochMillis()
        cardRepository.observeActiveCardSummaries(deckId, now)
            .distinctUntilChanged()
            .catch { throwable ->
                loadingTracker = loadingTracker.markCardsLoaded()
                _uiState.update { state ->
                    CardListStateReducer.loadFailed(
                        state = state,
                        loadingTracker = loadingTracker,
                        errorMessage = throwable.userMessageOr(ErrorMessages.LOAD_FAILED)
                    )
                }
            }
            .collect { items ->
                loadingTracker = loadingTracker.markCardsLoaded()
                _uiState.update { state ->
                    CardListStateReducer.itemsLoaded(
                        state = state,
                        items = items,
                        loadingTracker = loadingTracker
                    )
                }
                maybeRefreshMasterySummary(items)
            }
    }

    /**
     * 新建卡片先打开空草稿，便于复用同一套保存校验逻辑。
     */
    fun onCreateCardClick() {
        openEditor(TextMetadataDraft(entityId = null, primaryValue = "", secondaryValue = ""))
    }

    /**
     * 编辑卡片时把现有字段写入草稿，避免 UI 自己维护副本导致更新错位。
     */
    fun onEditCardClick(item: CardSummary) {
        openEditor(
            TextMetadataDraft(
                entityId = item.card.id,
                primaryValue = item.card.title,
                secondaryValue = item.card.description
            )
        )
    }

    /**
     * 标题属于必填字段，因此每次输入变更都需要清理上次校验提示以避免误导。
     */
    fun onDraftTitleChange(value: String) {
        updateEditor { it.updatePrimaryValue(value) }
    }

    /**
     * 描述不参与必填校验，但仍需进入草稿以确保保存读取到一致状态。
     */
    fun onDraftDescriptionChange(value: String) {
        updateEditor { it.updateSecondaryValue(value) }
    }

    /**
     * 关闭编辑器直接丢弃草稿，明确“未保存不落库”的交互语义。
     */
    fun onDismissEditor() {
        _uiState.update(CardListStateReducer::dismissEditor)
    }

    /**
     * 保存时统一注入 ID 与时间戳，避免上层在多个入口创建卡片时产生不同的时间语义。
     */
    fun onConfirmSave() {
        val editor = _uiState.value.editor ?: return
        val trimmedTitle = editor.primaryValue.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { state ->
                CardListStateReducer.updateEditor(state) {
                    it.withValidationMessage(ErrorMessages.TITLE_REQUIRED)
                }
            }
            return
        }

        launchStateMutation(
            state = _uiState,
            action = {
                val now = timeProvider.nowEpochMillis()
                val card = Card(
                    id = editor.entityId ?: EntityIds.newCardId(),
                    deckId = deckId,
                    title = trimmedTitle,
                    description = editor.secondaryValue,
                    archived = false,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now
                )
                cardRepository.upsert(card)
            },
            onSuccess = CardListStateReducer::saveSucceeded,
            onFailure = { state, _ -> CardListStateReducer.mutationFailed(state, ErrorMessages.SAVE_FAILED) }
        )
    }

    /**
     * 归档用于默认列表过滤，以降低误删风险并保持内容可恢复。
     */
    fun onArchiveCardClick(item: CardSummary) {
        executeMutation(errorMessage = ErrorMessages.UPDATE_FAILED) {
            val now = timeProvider.nowEpochMillis()
            cardRepository.setArchived(cardId = item.card.id, archived = !item.card.archived, updatedAt = now)
        }
    }

    /**
     * 删除需要先进入确认态，避免在列表交互中误触造成不可逆的数据丢失。
     */
    fun onDeleteCardClick(item: CardSummary) {
        _uiState.update { state -> CardListStateReducer.showDeleteConfirmation(state, item) }
    }

    /**
     * 退出确认态可以避免误触删除，符合高风险操作需要二次确认的原则。
     */
    fun onDismissDelete() {
        _uiState.update(CardListStateReducer::dismissDelete)
    }

    /**
     * 删除是高风险操作，确认后通过级联约束清理问题与复习记录，避免数据残留。
     */
    fun onConfirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        executeMutation(errorMessage = ErrorMessages.DELETE_FAILED) {
            cardRepository.delete(pending.card.id)
            _uiState.update(CardListStateReducer::deleteSucceeded)
        }
    }

    /**
     * 打开编辑器时统一清空旧反馈，是为了让创建和编辑都从同一个干净状态开始。
     */
    private fun openEditor(editor: TextMetadataDraft) {
        _uiState.update { state -> CardListStateReducer.openEditor(state, editor) }
    }

    /**
     * 草稿更新收口后，标题与描述的输入路径就不需要各自重复 editor 判空模板。
     */
    private fun updateEditor(transform: (TextMetadataDraft) -> TextMetadataDraft) {
        _uiState.update { state -> CardListStateReducer.updateEditor(state, transform) }
    }

    /**
     * 列表页写操作统一经由同一失败反馈出口，是为了避免归档、删除等分支再次遗漏异常收口。
     */
    private fun executeMutation(
        errorMessage: String,
        action: suspend () -> Unit
    ) {
        launchStateMutation(
            state = _uiState,
            action = action,
            onFailure = { state, _ -> CardListStateReducer.mutationFailed(state, errorMessage) }
        )
    }

    /**
     * 熟练度摘要基于真实问题集合即时计算，是为了遵守”不写回数据库，只按字段推导”的 P0 约束。
     * 计算下沉到纯组装器后，ViewModel 只保留查询触发与结果回写职责。
     */
    private fun refreshMasterySummary() {
        masterySummaryJob?.cancel()
        masterySummaryJob = launchStateResult(
            state = _uiState,
            action = {
                val questions = studyInsightsRepository.searchQuestionContexts(
                    filters = QuestionQueryFilters(
                        deckId = deckId,
                        status = QuestionStatus.ACTIVE
                    )
                )
                DeckMasterySummaryCalculator.calculate(questions)
            },
            onSuccess = CardListStateReducer::masteryLoaded,
            onFailure = { state, _ -> CardListStateReducer.masteryLoadFailed(state) }
        )
    }

    /**
     * 熟练度统计依赖问题集合而不是卡片展示文案，
     * 因此只有题目规模签名变化时才重算，可以避免仅编辑卡片标题时触发一次无意义检索。
     */
    private fun maybeRefreshMasterySummary(items: List<CardSummary>) {
        val currentSignature = buildMasterySummarySignature(items)
        if (currentSignature == lastMasterySummarySignature) {
            return
        }
        lastMasterySummarySignature = currentSignature
        refreshMasterySummary()
    }

    /**
     * 签名只保留会影响熟练度统计的字段，是为了把“是否需要重算”判断保持轻量且可解释。
     */
    private fun buildMasterySummarySignature(items: List<CardSummary>): String = items
        .sortedBy { it.card.id }
        .joinToString(separator = "|") { summary ->
            "${summary.card.id}:${summary.questionCount}"
        }

    companion object {
        /**
         * 工厂将 deckId 与容器依赖注入 ViewModel，避免 ViewModel 直接依赖全局单例。
         */
        fun factory(
            deckId: String,
            deckRepository: DeckRepository,
            cardRepository: CardRepository,
            studyInsightsRepository: StudyInsightsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            CardListViewModel(
                deckId = deckId,
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                studyInsightsRepository = studyInsightsRepository,
                timeProvider = timeProvider
            )
        }
    }
}
