package com.kariscode.yike.feature.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 将编辑对话框的草稿状态放入 UiState，目的是让 UI 不依赖临时 remember 状态，
 * 从而在后续需要保存/恢复页面状态时有明确的状态承载点。
 */
data class DeckEditorDraft(
    val deckId: String?,
    val name: String,
    val description: String,
    val validationMessage: String?
)

/**
 * 列表页状态显式包含“编辑草稿/删除确认”是为了避免在 Composable 里堆叠多个 remember 变量，
 * 这样 ViewModel 可以集中管理交互分支并更容易加测试。
 */
data class DeckListUiState(
    val isLoading: Boolean,
    val items: List<DeckSummary>,
    val editor: DeckEditorDraft?,
    val pendingDelete: DeckSummary?,
    val message: String?
)

/**
 * ViewModel 持有列表页的交互状态，避免把校验、ID 生成与时间戳策略散落在 UI 层，
 * 从而保证后续复习统计、备份恢复对数据语义的理解一致。
 */
class DeckListViewModel(
    private val deckRepository: DeckRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DeckListUiState(
            isLoading = true,
            items = emptyList(),
            editor = null,
            pendingDelete = null,
            message = null
        )
    )
    val uiState: StateFlow<DeckListUiState> = _uiState.asStateFlow()

    init {
        /**
         * 订阅列表聚合流是为了让“新增/编辑/归档/删除”后 UI 自动刷新，
         * 而不是在每个操作完成后手动触发 reload。
         */
        viewModelScope.launch {
            deckRepository.observeActiveDeckSummaries()
                .catch { throwable ->
                    _uiState.update { it.copy(isLoading = false, message = throwable.message ?: "加载失败") }
                }
                .collect { items ->
                    _uiState.update { it.copy(isLoading = false, items = items, message = null) }
                }
        }
    }

    /**
     * 新建入口打开空草稿，可让保存逻辑统一复用同一套校验与时间戳策略。
     */
    fun onCreateDeckClick() {
        _uiState.update {
            it.copy(
                editor = DeckEditorDraft(deckId = null, name = "", description = "", validationMessage = null),
                message = null
            )
        }
    }

    /**
     * 编辑入口把现有值写入草稿，避免 UI 自己维护一份表单副本导致与数据源不一致。
     */
    fun onEditDeckClick(item: DeckSummary) {
        _uiState.update {
            it.copy(
                editor = DeckEditorDraft(
                    deckId = item.deck.id,
                    name = item.deck.name,
                    description = item.deck.description,
                    validationMessage = null
                ),
                message = null
            )
        }
    }

    /**
     * 输入变更统一写入草稿状态，便于后续做就地校验与按钮可用性控制。
     */
    fun onDraftNameChange(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(name = value, validationMessage = null))
        }
    }

    /**
     * 描述变更和名称变更同样进入草稿，以确保保存时读取到的是同一份表单状态。
     */
    fun onDraftDescriptionChange(value: String) {
        _uiState.update { state ->
            val editor = state.editor ?: return@update state
            state.copy(editor = editor.copy(description = value, validationMessage = null))
        }
    }

    /**
     * 关闭编辑器会丢弃草稿，这样能明确“未保存不生效”的交互语义。
     */
    fun onDismissEditor() {
        _uiState.update { it.copy(editor = null) }
    }

    /**
     * 保存逻辑集中在 ViewModel，是为了让校验与 ID/时间戳策略统一落在一处，
     * 避免不同页面对“合法名称/创建时间”产生不同理解。
     */
    fun onConfirmSave() {
        val editor = _uiState.value.editor ?: return
        val trimmedName = editor.name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(editor = editor.copy(validationMessage = "名称不能为空")) }
            return
        }

        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            val existing = editor.deckId?.let { deckRepository.findById(it) }
            val deck = if (existing == null) {
                Deck(
                    id = "deck_${UUID.randomUUID()}",
                    name = trimmedName,
                    description = editor.description,
                    archived = false,
                    sortOrder = 0,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                existing.copy(
                    name = trimmedName,
                    description = editor.description,
                    updatedAt = now
                )
            }

            deckRepository.upsert(deck)
            _uiState.update { it.copy(editor = null, message = "已保存") }
        }
    }

    /**
     * 归档与反归档通过同一入口切换，便于未来把“归档后不进入待复习”作为统一规则拓展。
     */
    fun onToggleArchiveClick(item: DeckSummary) {
        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            deckRepository.setArchived(deckId = item.deck.id, archived = !item.deck.archived, updatedAt = now)
        }
    }

    /**
     * 删除属于高风险操作，因此必须先进入确认态，而不是直接执行。
     */
    fun onDeleteDeckClick(item: DeckSummary) {
        _uiState.update { it.copy(pendingDelete = item) }
    }

    /**
     * 退出确认态可以避免误触造成的数据丢失。
     */
    fun onDismissDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /**
     * 确认删除后执行级联删除，以保证下层数据不会残留失效外键。
     */
    fun onConfirmDelete() {
        val pending = _uiState.value.pendingDelete ?: return
        viewModelScope.launch {
            deckRepository.delete(pending.deck.id)
            _uiState.update { it.copy(pendingDelete = null, message = "已删除") }
        }
    }

    companion object {
        /**
         * 在不引入 DI 框架时，通过工厂注入依赖能保持 ViewModel 的可测试性，
         * 同时避免在 ViewModel 内部直接访问全局单例。
         */
        fun factory(
            deckRepository: DeckRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DeckListViewModel(deckRepository, timeProvider) as T
            }
        }
    }
}

