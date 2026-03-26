package com.kariscode.yike.feature.recyclebin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchStateResult
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
                    _uiState.update { state ->
                        state.withLoadFailed(
                            throwable.userMessageOr(ErrorMessages.LOAD_FAILED)
                        )
                    }
                }
                .collect { (decks, cards) ->
                    _uiState.update { state ->
                        state.withArchivedContent(
                            archivedDecks = decks,
                            archivedCards = cards
                        )
                    }
                }
        }
    }

    /**
     * Snackbar 展示完成功提示后应清理 message，
     * 避免配置变更或重复进入页面时再次弹出同一条恢复/删除反馈。
     */
    fun consumeMessage() {
        _uiState.update(RecycleBinUiState::consumeMessage)
    }

    /**
     * 错误提示展示后清理可以避免用户完成后续操作时仍被旧错误反复打断。
     */
    fun consumeErrorMessage() {
        _uiState.update(RecycleBinUiState::consumeErrorMessage)
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
        _uiState.update { state ->
            state.withPendingDelete(RecycleBinDeleteTarget.DeckTarget(item))
        }
    }

    /**
     * 卡片删除与卡组共用确认流，但仍需保留独立入口以便界面语义清晰。
     */
    fun onDeleteCardClick(item: ArchivedCardSummary) {
        _uiState.update { state ->
            state.withPendingDelete(RecycleBinDeleteTarget.CardTarget(item))
        }
    }

    /**
     * 关闭确认态时不修改列表数据，是为了让用户能安全地继续浏览当前回收站上下文。
     */
    fun onDismissDelete() {
        _uiState.update { state -> state.withPendingDelete(null) }
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
            onSuccess { state, _ -> state.withMutationSucceeded(successMessage) }
            onFailure { state, _ -> state.withMutationFailed(errorMessage) }
        }
    }

}

/**
 * 成功提示消费后立即清空，是为了避免回收站在配置变更后重复提示同一次恢复或删除结果。
 */
private fun RecycleBinUiState.consumeMessage(): RecycleBinUiState = copy(message = null)

/**
 * 错误提示只保留到 Snackbar 展示完，是为了让用户完成修正后不再被旧失败状态反复干扰。
 */
private fun RecycleBinUiState.consumeErrorMessage(): RecycleBinUiState = copy(errorMessage = null)

/**
 * 归档列表更新统一清理加载态与旧错误，是为了让双流合并后的正常快照保持同一套回写模板。
 */
private fun RecycleBinUiState.withArchivedContent(
    archivedDecks: List<DeckSummary>,
    archivedCards: List<ArchivedCardSummary>
): RecycleBinUiState = copy(
    isLoading = false,
    archivedDecks = archivedDecks,
    archivedCards = archivedCards,
    errorMessage = null
)

/**
 * 初次加载失败时统一保留现有内容并清空成功提示，是为了避免错误态和旧操作反馈同时出现。
 */
private fun RecycleBinUiState.withLoadFailed(errorMessage: String): RecycleBinUiState = copy(
    isLoading = false,
    message = null,
    errorMessage = errorMessage
)

/**
 * 删除确认态统一通过单一 helper 进入和退出，是为了让卡组与卡片两条高风险入口保持完全一致的回写语义。
 */
private fun RecycleBinUiState.withPendingDelete(
    pendingDelete: RecycleBinDeleteTarget?
): RecycleBinUiState = copy(
    pendingDelete = pendingDelete,
    errorMessage = null
)

/**
 * 恢复或彻底删除成功后统一退出确认态，是为了把回收站高风险操作完成后的稳定状态固定下来。
 */
private fun RecycleBinUiState.withMutationSucceeded(successMessage: String): RecycleBinUiState = copy(
    pendingDelete = null,
    message = successMessage,
    errorMessage = null
)

/**
 * 写操作失败后统一清空旧成功提示，是为了避免用户看到与当前实际结果冲突的反馈。
 */
private fun RecycleBinUiState.withMutationFailed(errorMessage: String): RecycleBinUiState = copy(
    message = null,
    errorMessage = errorMessage
)

