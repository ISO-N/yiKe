package com.kariscode.yike.feature.deck

import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.feature.common.FeedbackReducerTemplate

/**
 * 卡组列表页的状态回写集中到 reducer，是为了把异步编排和纯状态转换拆开，
 * 让 ViewModel 更容易保持在“发起操作 + 选择回写路径”的职责边界内。
 */
internal object DeckListStateReducer {
    /**
     * 列表成功返回后同步更新候选标签，是为了保证首页渲染与编辑弹窗看到的是同一轮快照。
     */
    fun itemsLoaded(
        state: DeckListUiState,
        items: List<DeckSummary>,
        visibleItems: List<DeckSummary>,
        availableTags: List<String>
    ): DeckListUiState = state.copy(
        isLoading = false,
        items = items,
        visibleItems = visibleItems,
        availableTags = availableTags,
        errorMessage = null
    )

    /**
     * 加载失败时统一保留已有内容并清理成功提示，是为了避免错误分支重复 copy 模板。
     */
    fun loadFailed(
        state: DeckListUiState,
        errorMessage: String
    ): DeckListUiState = state.copy(
        isLoading = false,
        message = null,
        errorMessage = errorMessage
    )

    /**
     * 打开编辑器时统一清空旧反馈，是为了让创建和编辑沿用同一干净起点。
     */
    fun openEditor(
        state: DeckListUiState,
        editor: DeckMetadataDraft
    ): DeckListUiState = state.copy(
        editor = editor,
        pendingDelete = null,
        message = null,
        errorMessage = null
    )

    /**
     * 关闭编辑器时只清理当前表单和错误提示，是为了避免误删上一次成功反馈。
     */
    fun dismissEditor(state: DeckListUiState): DeckListUiState = state.copy(
        editor = null,
        errorMessage = null
    )

    /**
     * 草稿更新集中到 reducer 后，名称、描述、标签和间隔次数都可以共享同一保护逻辑。
     */
    fun updateEditor(
        state: DeckListUiState,
        transform: (DeckMetadataDraft) -> DeckMetadataDraft
    ): DeckListUiState {
        val editor = state.editor ?: return state
        return state.copy(editor = transform(editor))
    }

    /**
     * 保存成功后统一关闭编辑器并提示成功，是为了让卡组编辑反馈与其他列表页保持一致。
     */
    fun saveSucceeded(
        state: DeckListUiState,
        successMessage: String
    ): DeckListUiState = state.copy(
        editor = null,
        pendingDelete = null,
        message = successMessage,
        errorMessage = null
    )

    /**
     * 标签候选刷新成功后只覆盖相关字段，是为了避免次要元数据请求污染列表主状态。
     */
    fun availableTagsLoaded(
        state: DeckListUiState,
        availableTags: List<String>
    ): DeckListUiState = state.copy(availableTags = availableTags)

    /**
     * 写操作失败统一经由这一入口，是为了避免保存、归档等分支逐渐漂移。
     */
    fun mutationFailed(
        state: DeckListUiState,
        errorMessage: String
    ): DeckListUiState = FeedbackReducerTemplate.withError(state, errorMessage)

    /**
     * 归档提示由 reducer 统一回写，是为了把文案选择和其他状态清理绑定在一处。
     */
    fun archiveToggled(
        state: DeckListUiState,
        archived: Boolean
    ): DeckListUiState = state.copy(
        pendingDelete = null,
        message = if (archived) SuccessMessages.UNARCHIVED else SuccessMessages.ARCHIVED,
        errorMessage = null
    )

    /**
     * 删除确认态单独入栈到状态里，是为了让弹窗展示与真正删除动作之间保留可测试的明确中间层。
     */
    fun showDeleteConfirmation(
        state: DeckListUiState,
        item: DeckSummary
    ): DeckListUiState = state.copy(
        pendingDelete = item,
        message = null,
        errorMessage = null
    )

    /**
     * 关闭删除确认时只清理高风险分支，是为了避免误伤列表页其他正在展示的成功反馈。
     */
    fun dismissDelete(state: DeckListUiState): DeckListUiState = state.copy(
        pendingDelete = null,
        errorMessage = null
    )

    /**
     * 删除成功后统一清理确认态并提示成功，是为了让用户明确知道本次高风险操作已经生效。
     */
    fun deleteSucceeded(state: DeckListUiState): DeckListUiState = state.copy(
        pendingDelete = null,
        message = SuccessMessages.DELETED,
        errorMessage = null
    )
}

