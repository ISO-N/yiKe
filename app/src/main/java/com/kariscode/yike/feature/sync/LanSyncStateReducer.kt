package com.kariscode.yike.feature.sync

import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncSessionState

/**
 * 同步页状态更新集中在 reducer 中，是为了把 ViewModel 里的“事件 -> 状态”映射保持为纯函数，
 * 从而让配对/预览/冲突决议等临时 UI 状态更容易复用、测试，也更不容易在分支里漏清理字段。
 */
internal object LanSyncStateReducer {
    /**
     * 会话状态更新时保留用户正在输入的本机名字，是为了避免后台发现刷新把编辑中的草稿覆盖掉。
     */
    fun sessionUpdated(state: LanSyncUiState, session: LanSyncSessionState): LanSyncUiState =
        state.copy(
            session = session,
            localNameInput = if (state.isEditingLocalName) state.localNameInput else session.localProfile.displayName
        )

    /**
     * Session message 属于一次性提示，消费后清理副本可以避免重组或返回时重复弹出。
     */
    fun consumeSessionMessage(state: LanSyncUiState): LanSyncUiState =
        state.copy(session = state.session.copy(message = null))

    /**
     * Session failure 也是一次性反馈，消费后清理可以避免后续流程推进时反复提示旧失败。
     */
    fun consumeSessionFailure(state: LanSyncUiState): LanSyncUiState =
        state.copy(session = state.session.copy(activeFailure = null))

    /**
     * 未信任设备点击后进入配对态，是为了把“授权输入”与“设备发现刷新”隔离开，避免输入被打断。
     */
    fun beginPairing(state: LanSyncUiState, peer: LanSyncPeer): LanSyncUiState =
        state.copy(
            pendingPairingPeer = peer,
            pairingCodeInput = ""
        )

    /**
     * 配对码仅允许数字并限制长度，是为了在 ViewModel 之外也能保持输入约束一致。
     */
    fun pairingCodeChanged(state: LanSyncUiState, value: String): LanSyncUiState =
        state.copy(pairingCodeInput = value.filter(Char::isDigit).take(6))

    /**
     * 关闭配对弹窗时只清理授权输入，是为了让设备列表和后台发现状态保持不受影响。
     */
    fun dismissPairing(state: LanSyncUiState): LanSyncUiState = clearPairingState(state)

    /**
     * 停止会话后清空临时同步状态，是为了让下一次进入同步流程时不携带旧输入与旧决议痕迹。
     */
    fun clearTransientSyncState(state: LanSyncUiState): LanSyncUiState = clearTransientSyncStateInternal(state)

    /**
     * 预览生成后回到干净的临时同步态，是为了避免上一次配对/预览遗留字段污染本次决策。
     */
    fun previewPrepared(state: LanSyncUiState, preview: LanSyncPreview): LanSyncUiState =
        clearTransientSyncStateInternal(state).copy(pendingPreview = preview)

    /**
     * 预览关闭只退回“无决议”的预览态，是为了让用户重新选择设备时不带走旧的冲突选择。
     */
    fun dismissPreview(state: LanSyncUiState): LanSyncUiState = clearPreviewDecisionState(state)

    /**
     * 冲突弹窗关闭时保留预览信息，是为了让用户能回到“先看影响、再决定是否执行”的风险边界。
     */
    fun dismissConflicts(state: LanSyncUiState): LanSyncUiState =
        clearPreviewDecisionState(state, keepPreview = true)

    /**
     * 冲突决议弹窗展示时默认选择 KEEP_REMOTE，是为了把风险默认值固定为“以对端为准”并可被显式覆盖。
     */
    fun showConflictDialog(state: LanSyncUiState, preview: LanSyncPreview): LanSyncUiState =
        state.copy(
            showConflictDialog = true,
            conflictChoices = preview.conflicts.associate { conflict ->
                "${conflict.entityType.name}:${conflict.entityId}" to LanSyncConflictChoice.KEEP_REMOTE
            }
        )

    /**
     * 冲突选择显式按实体 key 存储，是为了让同一弹窗中的多个决议在滚动和修改时保持稳定对应。
     */
    fun conflictChoiceChanged(
        state: LanSyncUiState,
        entityKey: String,
        choice: LanSyncConflictChoice
    ): LanSyncUiState = state.copy(conflictChoices = state.conflictChoices + (entityKey to choice))

    /**
     * 冲突确认后先退出弹窗态，是为了让页面把注意力切回会话进度而不是残留在决议 UI 上。
     */
    fun conflictsConfirmed(state: LanSyncUiState): LanSyncUiState =
        state.copy(showConflictDialog = false)

    /**
     * 本机名字编辑开始时拉取当前会话值，是为了让用户能在旧值基础上修改而不是从空输入起步。
     */
    fun editLocalName(state: LanSyncUiState): LanSyncUiState =
        state.copy(
            isEditingLocalName = true,
            localNameInput = state.session.localProfile.displayName
        )

    /**
     * 文本输入留在本地状态，是为了让用户在确认保存前可多次修改而不触发频繁持久化。
     */
    fun localNameInputChanged(state: LanSyncUiState, value: String): LanSyncUiState =
        state.copy(localNameInput = value)

    /**
     * 保存完成后退出编辑态即可，是为了让展示值完全以仓储会话刷新为准而不依赖本地草稿。
     */
    fun localNameSaved(state: LanSyncUiState): LanSyncUiState =
        state.copy(isEditingLocalName = false)

    /**
     * 取消编辑时回到仓储中的真实名字，是为了避免未保存草稿误覆盖会话状态。
     */
    fun dismissLocalNameEditor(state: LanSyncUiState): LanSyncUiState =
        state.copy(
            isEditingLocalName = false,
            localNameInput = state.session.localProfile.displayName
        )

    /**
     * 配对输入与待配对设备总是成对消失，是为了让弹窗关闭后状态完整回到“未配对”的初始形态。
     */
    private fun clearPairingState(state: LanSyncUiState): LanSyncUiState =
        state.copy(
            pendingPairingPeer = null,
            pairingCodeInput = ""
        )

    /**
     * 预览和冲突决议属于同一阶段临时 UI 状态，集中清理能避免同步结束后残留冲突选择。
     */
    private fun clearPreviewDecisionState(
        state: LanSyncUiState,
        keepPreview: Boolean = false
    ): LanSyncUiState = state.copy(
        pendingPreview = if (keepPreview) state.pendingPreview else null,
        showConflictDialog = false,
        conflictChoices = emptyMap()
    )

    /**
     * 停止会话或拿到新预览时回到干净的同步临时态，是为了把“上一次的配对和预览痕迹一起清掉”写成显式规则。
     */
    private fun clearTransientSyncStateInternal(state: LanSyncUiState): LanSyncUiState =
        clearPreviewDecisionState(clearPairingState(state))
}
