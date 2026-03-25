package com.kariscode.yike.feature.practice

import androidx.lifecycle.ViewModel
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.repository.PracticeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * PracticeSetupViewModel 把范围选择和题量推导集中在状态层，
 * 是为了避免设置页在多层级多选扩展后逐步退化成难以维护的条件分支集合。
 */
class PracticeSetupViewModel(
    private val initialArgs: PracticeSessionArgs,
    private val practiceRepository: PracticeRepository
) : ViewModel() {
    private var allQuestionContexts: List<QuestionContext> = emptyList()

    private val _uiState = MutableStateFlow(
        PracticeSetupUiState(
            isLoading = true,
            deckOptions = emptyList(),
            cardOptions = emptyList(),
            questionOptions = emptyList(),
            selectedDeckIds = initialArgs.deckIds.toSet(),
            selectedCardIds = initialArgs.cardIds.toSet(),
            selectedQuestionIds = initialArgs.questionIds.takeIf(List<String>::isNotEmpty)?.toSet(),
            orderMode = initialArgs.orderMode,
            effectiveQuestionCount = 0,
            errorMessage = null
        )
    )
    val uiState: StateFlow<PracticeSetupUiState> = _uiState.asStateFlow()

    init {
        /**
         * 设置页首帧就需要知道当前能练什么内容，
         * 因此统一先加载只读题目上下文，再基于它推导全部层级候选。
         */
        refresh()
    }

    /**
     * 刷新统一重取可练习题目全集，是为了让内容管理改动后返回设置页也能得到最新范围。
     */
    fun refresh() {
        _uiState.update { state -> state.copy(isLoading = true, errorMessage = null) }
        launchResult(
            action = {
                practiceRepository.listPracticeQuestionContexts(PracticeSessionArgs())
            },
            onSuccess = { contexts ->
                allQuestionContexts = contexts
                _uiState.update { state ->
                    buildUiState(
                        allQuestionContexts = contexts,
                        selectedDeckIds = state.selectedDeckIds,
                        selectedCardIds = state.selectedCardIds,
                        selectedQuestionIds = state.selectedQuestionIds,
                        orderMode = state.orderMode
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.userMessageOr(ErrorMessages.SEARCH_LOAD_FAILED)
                    )
                }
            }
        )
    }

    /**
     * deck 多选优先收窄下层候选，是为了让用户先从大范围缩到具体卡片时不必手动清理失效选择。
     */
    fun onDeckToggle(deckId: String) {
        val nextDeckIds = _uiState.value.selectedDeckIds.toMutableSet().applyToggle(deckId)
        updateSelection(selectedDeckIds = nextDeckIds)
    }

    /**
     * 卡片多选在 deck 已确定后进一步缩圈，是为了覆盖“刷一张或几张卡片”的高频练习场景。
     */
    fun onCardToggle(cardId: String) {
        val nextCardIds = _uiState.value.selectedCardIds.toMutableSet().applyToggle(cardId)
        updateSelection(selectedCardIds = nextCardIds)
    }

    /**
     * 题目级切换第一次操作时从“全选”显式收拢到具体集合，是为了避免 `null` 语义直接暴露到 UI 层。
     */
    fun onQuestionToggle(questionId: String) {
        val allAvailableQuestionIds = _uiState.value.questionOptions.map { option -> option.questionId }.toSet()
        val nextQuestionIds = (_uiState.value.selectedQuestionIds ?: allAvailableQuestionIds)
            .toMutableSet()
            .applyToggle(questionId)
        updateSelection(selectedQuestionIds = nextQuestionIds)
    }

    /**
     * 一键恢复当前范围内全选题目，是为了让用户做过局部排除后仍能快速回到“全刷当前范围”。
     */
    fun onSelectAllQuestions() {
        updateSelection(selectedQuestionIds = null)
    }

    /**
     * 显式清空题目选择保留在设置页内，是为了让“当前范围下没有要练的题”也成为可理解状态而不是异常。
     */
    fun onClearQuestionSelection() {
        updateSelection(selectedQuestionIds = emptySet())
    }

    /**
     * 顺序模式与随机模式切换只改会话协议，不立即触发重新查询，是为了保持设置页交互足够轻。
     */
    fun onOrderModeChange(orderMode: PracticeOrderMode) {
        updateSelection(orderMode = orderMode)
    }

    /**
     * 开始练习前统一从当前状态导出参数，是为了让所有入口都能把 deck/card/question 选择映射回同一协议。
     */
    fun buildSessionArgs(): PracticeSessionArgs = PracticeSessionArgs(
        deckIds = _uiState.value.selectedDeckIds.toList(),
        cardIds = _uiState.value.selectedCardIds.toList(),
        questionIds = _uiState.value.selectedQuestionIds?.toList().orEmpty(),
        orderMode = _uiState.value.orderMode
    ).normalized()

    /**
     * 选择变更统一走同一重算入口，是为了让 deck/card/question 层级裁剪始终共享一套规则。
     */
    private fun updateSelection(
        selectedDeckIds: Set<String> = _uiState.value.selectedDeckIds,
        selectedCardIds: Set<String> = _uiState.value.selectedCardIds,
        selectedQuestionIds: Set<String>? = _uiState.value.selectedQuestionIds,
        orderMode: PracticeOrderMode = _uiState.value.orderMode
    ) {
        _uiState.update {
            buildUiState(
                allQuestionContexts = allQuestionContexts,
                selectedDeckIds = selectedDeckIds,
                selectedCardIds = selectedCardIds,
                selectedQuestionIds = selectedQuestionIds,
                orderMode = orderMode
            )
        }
    }

    /**
     * UI 状态在单点重建后，范围有效性、题量与“全选/局部手选”语义就不会在多个方法里各自漂移。
     */
    private fun buildUiState(
        allQuestionContexts: List<QuestionContext>,
        selectedDeckIds: Set<String>,
        selectedCardIds: Set<String>,
        selectedQuestionIds: Set<String>?,
        orderMode: PracticeOrderMode
    ): PracticeSetupUiState {
        val projection = buildPracticeSelectionProjection(
            allQuestionContexts = allQuestionContexts,
            selectedDeckIds = selectedDeckIds,
            selectedCardIds = selectedCardIds,
            selectedQuestionIds = selectedQuestionIds
        )

        return PracticeSetupUiState(
            isLoading = false,
            deckOptions = projection.deckOptions,
            cardOptions = projection.cardOptions,
            questionOptions = projection.questionOptions,
            selectedDeckIds = projection.selectedDeckIds,
            selectedCardIds = projection.selectedCardIds,
            selectedQuestionIds = projection.selectedQuestionIds,
            orderMode = orderMode,
            effectiveQuestionCount = projection.effectiveQuestionCount,
            errorMessage = null
        )
    }

}

