package com.kariscode.yike.feature.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchStateResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 回收站需要统一管理两类归档内容和高风险删除确认，
 * 因此把页面状态收口到单一 UiState 可以避免界面层重复维护分支。
 */
data class RecycleBinUiState(
    val isLoading: Boolean,
    val archivedDecks: List<DeckSummary>,
    val archivedCards: List<ArchivedCardSummary>,
    val pendingDelete: RecycleBinDeleteTarget?,
    val message: String?,
    val errorMessage: String?
)

/**
 * 彻底删除确认要明确区分卡组和卡片，
 * 这样同一弹窗逻辑才能复用而不丢失实际删除目标。
 */
sealed interface RecycleBinDeleteTarget {
    data class DeckTarget(val item: DeckSummary) : RecycleBinDeleteTarget
    data class CardTarget(val item: ArchivedCardSummary) : RecycleBinDeleteTarget
}

/**
 * 回收站 ViewModel 统一编排归档列表订阅、恢复和彻底删除，
 * 从而确保恢复后页面与原列表页都能通过同一数据源自然刷新。
 */
class RecycleBinViewModel(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        RecycleBinUiState(
            isLoading = true,
            archivedDecks = emptyList(),
            archivedCards = emptyList(),
            pendingDelete = null,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<RecycleBinUiState> = _uiState.asStateFlow()

    init {
        /**
         * 回收站改为同时订阅卡组和卡片，是为了让恢复或删除任意一类内容后都能即时回写同一页面。
         */
        viewModelScope.launch {
            val now = timeProvider.nowEpochMillis()
            combine(
                deckRepository.observeArchivedDeckSummaries(now),
                cardRepository.observeArchivedCardSummaries(now)
            ) { decks, cards -> decks to cards }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            errorMessage = throwable.userMessageOr(ErrorMessages.LOAD_FAILED)
                        )
                    }
                }
                .collect { (decks, cards) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            archivedDecks = decks,
                            archivedCards = cards,
                            errorMessage = null
                        )
                    }
                }
        }
    }

    /**
     * 恢复卡组本质上是撤销归档，这样原有卡组页无需新增一套专门的导入路径。
     */
    fun onRestoreDeckClick(item: DeckSummary) {
        executeMutation(
            errorMessage = ErrorMessages.UPDATE_FAILED,
            successMessage = SuccessMessages.RESTORED_DECK
        ) {
            deckRepository.setArchived(
                deckId = item.deck.id,
                archived = false,
                updatedAt = timeProvider.nowEpochMillis()
            )
        }
    }

    /**
     * 恢复卡片同样复用 archived 字段切换，可保证卡片页和检索页继续沿用既有过滤规则。
     */
    fun onRestoreCardClick(item: ArchivedCardSummary) {
        executeMutation(
            errorMessage = ErrorMessages.UPDATE_FAILED,
            successMessage = SuccessMessages.RESTORED_CARD
        ) {
            cardRepository.setArchived(
                cardId = item.card.id,
                archived = false,
                updatedAt = timeProvider.nowEpochMillis()
            )
        }
    }

    /**
     * 卡组的彻底删除必须先进入确认态，避免把“回收站浏览”误触成不可逆操作。
     */
    fun onDeleteDeckClick(item: DeckSummary) {
        _uiState.update { it.copy(pendingDelete = RecycleBinDeleteTarget.DeckTarget(item), errorMessage = null) }
    }

    /**
     * 卡片删除与卡组共用确认流，但仍需保留独立入口以便界面语义清晰。
     */
    fun onDeleteCardClick(item: ArchivedCardSummary) {
        _uiState.update { it.copy(pendingDelete = RecycleBinDeleteTarget.CardTarget(item), errorMessage = null) }
    }

    /**
     * 关闭确认态时不修改列表数据，是为了让用户能安全地继续浏览当前回收站上下文。
     */
    fun onDismissDelete() {
        _uiState.update { it.copy(pendingDelete = null, errorMessage = null) }
    }

    /**
     * 确认删除后直接触发底层级联删除，让回收站承担真正的数据清理职责。
     */
    fun onConfirmDelete() {
        when (val target = _uiState.value.pendingDelete) {
            is RecycleBinDeleteTarget.DeckTarget -> executeMutation(
                errorMessage = ErrorMessages.DELETE_FAILED,
                successMessage = SuccessMessages.DELETED
            ) {
                deckRepository.delete(target.item.deck.id)
            }

            is RecycleBinDeleteTarget.CardTarget -> executeMutation(
                errorMessage = ErrorMessages.DELETE_FAILED,
                successMessage = SuccessMessages.DELETED
            ) {
                cardRepository.delete(target.item.card.id)
            }

            null -> Unit
        }
    }

    /**
     * 回收站里的写操作都共享同一套反馈出口，
     * 可以避免恢复和删除各自遗漏提示状态清理。
     */
    private fun executeMutation(
        errorMessage: String,
        successMessage: String,
        action: suspend () -> Unit
    ) {
        launchStateResult(state = _uiState) {
            action(action)
            onSuccess { state, _ ->
                state.copy(
                    pendingDelete = null,
                    message = successMessage,
                    errorMessage = null
                )
            }
            onFailure { state, _ ->
                state.copy(
                    message = null,
                    errorMessage = errorMessage
                )
            }
        }
    }

    companion object {
        /**
         * 工厂显式注入仓储与时间依赖，是为了让回收站状态逻辑继续保持可测试和可替换。
         */
        fun factory(
            deckRepository: DeckRepository,
            cardRepository: CardRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            RecycleBinViewModel(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                timeProvider = timeProvider
            )
        }
    }
}

