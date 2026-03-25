package com.kariscode.yike.feature.card

import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.feature.common.TextMetadataDraft
import com.kariscode.yike.domain.model.CardSummary

/**
 * 卡片列表页的状态变更集中在 reducer 中，是为了把“状态如何变化”从协程编排中拆出来，
 * 让 ViewModel 聚焦于何时读取数据、何时执行写操作。
 */
internal object CardListStateReducer {
    /**
     * 卡组元信息加载成功后只更新标题相关字段与首屏 loading，
     * 这样列表数据与标题可以按各自节奏到达，但仍共享同一首屏判定。
     */
    fun deckLoaded(
        state: CardListUiState,
        deckName: String?,
        loadingTracker: CardListLoadingTracker
    ): CardListUiState = state.copy(
        deckName = deckName,
        isLoading = loadingTracker.isLoading
    )

    /**
     * 初始化失败时统一保留现有内容并回写错误，是为了避免失败分支各自散落 copy 模板。
     */
    fun loadFailed(
        state: CardListUiState,
        loadingTracker: CardListLoadingTracker,
        errorMessage: String
    ): CardListUiState = state.copy(
        isLoading = loadingTracker.isLoading,
        message = null,
        errorMessage = errorMessage
    )

    /**
     * 卡片列表成功返回后同步更新列表与 loading 状态，是为了让首屏和实时刷新沿用同一回写路径。
     */
    fun itemsLoaded(
        state: CardListUiState,
        items: List<CardSummary>,
        loadingTracker: CardListLoadingTracker
    ): CardListUiState = state.copy(
        isLoading = loadingTracker.isLoading,
        items = items,
        errorMessage = null
    )

    /**
     * 打开编辑器时统一清空旧反馈，是为了让创建和编辑都从同一干净状态进入弹窗。
     */
    fun openEditor(
        state: CardListUiState,
        editor: TextMetadataDraft
    ): CardListUiState = state.copy(
        editor = editor,
        message = null,
        errorMessage = null
    )

    /**
     * 关闭编辑器时只收口编辑态和错误提示，是为了保留成功提示但移除已过期的表单上下文。
     */
    fun dismissEditor(state: CardListUiState): CardListUiState = state.copy(
        editor = null,
        errorMessage = null
    )

    /**
     * 草稿更新通过 reducer 集中后，标题和说明输入就不需要重复 editor 判空模板。
     */
    fun updateEditor(
        state: CardListUiState,
        transform: (TextMetadataDraft) -> TextMetadataDraft
    ): CardListUiState {
        val editor = state.editor ?: return state
        return state.copy(editor = transform(editor))
    }

    /**
     * 删除确认单独收口，是为了让高风险操作的进入与退出都只维护一处状态模板。
     */
    fun showDeleteConfirmation(
        state: CardListUiState,
        item: CardSummary
    ): CardListUiState = state.copy(
        pendingDelete = item,
        errorMessage = null
    )

    /**
     * 退出删除确认态时不触碰列表数据，是为了避免误把确认态关闭当成一次业务操作。
     */
    fun dismissDelete(state: CardListUiState): CardListUiState = state.copy(
        pendingDelete = null,
        errorMessage = null
    )

    /**
     * 保存成功后统一关闭编辑器并展示成功提示，是为了让不同写操作共享同一反馈语义。
     */
    fun saveSucceeded(
        state: CardListUiState,
        successMessage: String
    ): CardListUiState = state.copy(
        editor = null,
        message = successMessage,
        errorMessage = null
    )

    /**
     * 删除成功要同时清理确认态和提示，是为了让页面回到稳定浏览状态而不是停留在高风险上下文。
     */
    fun deleteSucceeded(state: CardListUiState): CardListUiState = state.copy(
        pendingDelete = null,
        message = SuccessMessages.DELETED,
        errorMessage = null
    )

    /**
     * 写操作失败统一经由这一出口，是为了避免归档、删除和保存各自遗漏旧提示清理。
     */
    fun mutationFailed(
        state: CardListUiState,
        errorMessage: String
    ): CardListUiState = state.copy(
        message = null,
        errorMessage = errorMessage
    )

    /**
     * 熟练度摘要成功回写单独收口，是为了让列表刷新和统计刷新保持解耦。
     */
    fun masteryLoaded(
        state: CardListUiState,
        summary: DeckMasterySummary
    ): CardListUiState = state.copy(masterySummary = summary)

    /**
     * 统计失败只清空摘要，不污染列表主流程，是为了让页面仍可继续内容管理。
     */
    fun masteryLoadFailed(state: CardListUiState): CardListUiState = state.copy(masterySummary = null)
}

