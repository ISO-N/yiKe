package com.kariscode.yike.feature.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 卡片编辑草稿状态集中在 ViewModel，是为了让“标题必填”等校验规则有稳定落点，
 * 避免在 UI 中散落多个临时状态变量导致保存行为不可预测。
 */
data class CardEditorDraft(
    val cardId: String?,
    val title: String,
    val description: String,
    val validationMessage: String?
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
    val editor: CardEditorDraft?,
    val pendingDelete: CardSummary?,
    val message: String?
)

/**
 * 卡片列表 ViewModel 负责协调 deck 信息加载与列表聚合流订阅，
 * 以避免页面同时处理多个异步来源导致状态分叉。
 */
class CardListViewModel(
    private val deckId: String,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CardListUiState(
            deckId = deckId,
            deckName = null,
            isLoading = true,
            items = emptyList(),
            editor = null,
            pendingDelete = null,
            message = null
        )
    )
    val uiState: StateFlow<CardListUiState> = _uiState.asStateFlow()

    init {
        /**
         * deckName 单独加载是为了让页面标题更可读，同时保持数据加载完全基于 deckId 参数。
         */
        viewModelScope.launch {
            val deck = deckRepository.findById(deckId)
            _uiState.update { it.copy(deckName = deck?.name) }
        }

        /**
         * 订阅聚合流以保持列表统计实时更新，避免在新增问题后仍显示旧的 questionCount。
         */
        viewModelScope.launch {
            cardRepository.observeActiveCardSummaries(deckId)
                .catch { throwable ->
                    _uiState.update { it.copy(isLoading = false, message = throwable.message ?: "加载失败") }
                }
                .collect { items ->
                    _uiState.update { it.copy(isLoading = false, items = items, message = null) }
                }
        }
    }

    /**
     * 新建卡片先打开空草稿，便于复用同一套保存校验逻辑。
     */
    fun onCreateCardClick() {
        _uiState.update {
            it.copy(editor = CardEditorDraft(cardId = null, title = "", description = "", validationMessage = null))
        }
    }

    /**
     * 编辑卡片时把现有字段写入草稿，避免 UI 自己维护副本导致更新错位。
     */
    fun onEditCardClick(item: CardSummary) {
        _uiState.update {
            it.copy(
                editor = CardEditorDraft(
                    cardId = item.card.id,
                    title = item.card.title,
                    description = item.card.description,
                    validationMessage = null
                )
            )
        }
    }

    /**
     * 标题属于必填字段，因此每次输入变更都需要清理上次校验提示以避免误导。
     */
    fun onDraftTitleChange(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(title = value, validationMessage = null))
        }
    }

    /**
     * 描述不参与必填校验，但仍需进入草稿以确保保存读取到一致状态。
     */
    fun onDraftDescriptionChange(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(description = value, validationMessage = null))
        }
    }

    /**
     * 关闭编辑器直接丢弃草稿，明确“未保存不落库”的交互语义。
     */
    fun onDismissEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    /**
     * 保存时统一注入 ID 与时间戳，避免上层在多个入口创建卡片时产生不同的时间语义。
     */
    fun onConfirmSave() {
        val editor = _uiState.value.editor ?: return
        val trimmedTitle = editor.title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(editor = editor.copy(validationMessage = "标题不能为空")) }
            return
        }

        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            val existing = editor.cardId?.let { cardRepository.findById(it) }
            val card = if (existing == null) {
                Card(
                    id = "card_${UUID.randomUUID()}",
                    deckId = deckId,
                    title = trimmedTitle,
                    description = editor.description,
                    archived = false,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                existing.copy(
                    title = trimmedTitle,
                    description = editor.description,
                    updatedAt = now
                )
            }
            cardRepository.upsert(card)
            _uiState.update { it.copy(editor = null, message = "已保存") }
        }
    }

    /**
     * 归档用于默认列表过滤，以降低误删风险并保持内容可恢复。
     */
    fun onArchiveCardClick(item: CardSummary) {
        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            cardRepository.setArchived(cardId = item.card.id, archived = !item.card.archived, updatedAt = now)
        }
    }

    /**
     * 删除需要先进入确认态，避免在列表交互中误触造成不可逆的数据丢失。
     */
    fun onDeleteCardClick(item: CardSummary) {
        _uiState.update { it.copy(pendingDelete = item) }
    }

    /**
     * 退出确认态可以避免误触删除，符合高风险操作需要二次确认的原则。
     */
    fun onDismissDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * 删除是高风险操作，确认后通过级联约束清理问题与复习记录，避免数据残留。
     */
    fun onConfirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            cardRepository.delete(pending.card.id)
            _uiState.update { it.copy(pendingDelete = null, message = "已删除") }
        }
    }

    companion object {
        /**
         * 工厂将 deckId 与容器依赖注入 ViewModel，避免 ViewModel 直接依赖全局单例。
         */
        fun factory(
            deckId: String,
            deckRepository: DeckRepository,
            cardRepository: CardRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return CardListViewModel(deckId, deckRepository, cardRepository, timeProvider) as T
            }
        }
    }
}
