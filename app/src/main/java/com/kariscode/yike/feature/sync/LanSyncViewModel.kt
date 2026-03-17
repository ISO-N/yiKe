package com.kariscode.yike.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.viewmodel.launchMutation
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncSessionState
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.domain.repository.LanSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 同步页状态同时承载仓储会话状态和页面临时输入，
 * 是为了把“协议事实”和“用户正在编辑什么”分开维护，避免一边刷新设备列表一边覆盖掉输入中的配对码。
 */
data class LanSyncUiState(
    val session: LanSyncSessionState,
    val pendingPairingPeer: LanSyncPeer?,
    val pairingCodeInput: String,
    val pendingPreview: LanSyncPreview?,
    val showConflictDialog: Boolean,
    val conflictChoices: Map<String, LanSyncConflictChoice>,
    val isEditingLocalName: Boolean,
    val localNameInput: String
)

/**
 * LAN Sync V2 的 ViewModel 把设备发现、配对、预览和执行串成一条状态机，
 * 避免页面层同时操作仓储和本地 remember 状态而让流程失控。
 */
class LanSyncViewModel(
    private val lanSyncRepository: LanSyncRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LanSyncUiState(
            session = LanSyncSessionState(
                localProfile = com.kariscode.yike.domain.model.LanSyncLocalProfile(
                    deviceId = "loading",
                    displayName = "当前设备",
                    shortDeviceId = "------",
                    pairingCode = "------"
                ),
                peers = emptyList(),
                isSessionActive = false,
                preview = null,
                progress = com.kariscode.yike.domain.model.LanSyncProgress(
                    stage = LanSyncStage.IDLE,
                    message = "等待开始发现",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                ),
                activeFailure = null,
                message = null
            ),
            pendingPairingPeer = null,
            pairingCodeInput = "",
            pendingPreview = null,
            showConflictDialog = false,
            conflictChoices = emptyMap(),
            isEditingLocalName = false,
            localNameInput = ""
        )
    )
    val uiState: StateFlow<LanSyncUiState> = _uiState.asStateFlow()

    init {
        /**
         * 页面只订阅仓储单一状态源，是为了让发现、心跳和同步进度这些后台变化都自动反映到 UI。
         */
        viewModelScope.launch {
            lanSyncRepository.observeSessionState().collect { session ->
                _uiState.update { state ->
                    state.copy(
                        session = session,
                        localNameInput = if (state.isEditingLocalName) state.localNameInput else session.localProfile.displayName
                    )
                }
            }
        }
    }

    /**
     * 权限具备后再启动会话，可以保持“只在用户主动进入同步页时发现设备”的产品边界。
     */
    fun onPermissionReady() {
        if (_uiState.value.session.isSessionActive) {
            return
        }
        launchMutation(action = { lanSyncRepository.startSession() })
    }

    /**
     * 主动停止会话可以让用户在同步页内就把应用恢复到完全离线状态，而不必依赖返回导航触发 onCleared。
     */
    fun onStopSession() {
        launchMutation(
            action = { lanSyncRepository.stopSession() },
            onSuccess = {
                _uiState.update { state -> state.clearTransientSyncState() }
            }
        )
    }

    /**
     * 设备点击先决定是否需要配对，是为了让未信任设备先走授权，再进入真正的同步预览。
     */
    fun onPeerClick(peer: LanSyncPeer) {
        if (peer.trustState == com.kariscode.yike.domain.model.LanSyncTrustState.UNTRUSTED) {
            _uiState.update {
                it.copy(
                    pendingPairingPeer = peer,
                    pairingCodeInput = ""
                )
            }
            return
        }
        buildPreview(peer = peer, pairingCode = null)
    }

    /**
     * 配对码输入留在 ViewModel 中，是为了让配置变更或旋转后不丢失用户正在输入的授权信息。
     */
    fun onPairingCodeChange(value: String) {
        _uiState.update { it.copy(pairingCodeInput = value.filter(Char::isDigit).take(6)) }
    }

    /**
     * 首次配对确认后继续走预览，而不是直接开始同步，是为了保持“先看影响、再执行”的风险边界。
     */
    fun onConfirmPairing() {
        val peer = _uiState.value.pendingPairingPeer ?: return
        buildPreview(peer = peer, pairingCode = _uiState.value.pairingCodeInput)
    }

    /**
     * 关闭配对弹窗时只清理授权输入，是为了让设备列表和后台发现状态保持不受影响。
     */
    fun onDismissPairing() {
        _uiState.update { state -> state.clearPairingState() }
    }

    /**
     * 同步预览确认后，若存在冲突则进入显式决议阶段，否则直接执行同步。
     */
    fun onConfirmPreview() {
        val preview = _uiState.value.pendingPreview ?: return
        if (preview.conflicts.isEmpty()) {
            runSync(preview = preview, resolutions = emptyList())
            return
        }
        val defaultChoices = preview.conflicts.associate { conflict ->
            "${conflict.entityType.name}:${conflict.entityId}" to LanSyncConflictChoice.KEEP_REMOTE
        }
        _uiState.update {
            it.copy(
                showConflictDialog = true,
                conflictChoices = defaultChoices
            )
        }
    }

    /**
     * 预览关闭时只清空当前待确认内容，是为了让会话本身继续存活并可立即重新选择设备。
     */
    fun onDismissPreview() {
        _uiState.update { state -> state.clearPreviewDecisionState() }
    }

    /**
     * 冲突选择显式按实体 key 存储，是为了让同一弹窗中的多个决议在用户滚动和修改时保持稳定对应。
     */
    fun onConflictChoiceChange(entityKey: String, choice: LanSyncConflictChoice) {
        _uiState.update { state ->
            state.copy(conflictChoices = state.conflictChoices + (entityKey to choice))
        }
    }

    /**
     * 用户确认冲突决议后再真正执行同步，是为了让传输和落库只围绕明确选择推进。
     */
    fun onConfirmConflicts() {
        val preview = _uiState.value.pendingPreview ?: return
        val resolutions = preview.conflicts.map { conflict ->
            LanSyncConflictResolution(
                entityType = conflict.entityType,
                entityId = conflict.entityId,
                choice = _uiState.value.conflictChoices["${conflict.entityType.name}:${conflict.entityId}"]
                    ?: LanSyncConflictChoice.KEEP_REMOTE
            )
        }
        _uiState.update { it.copy(showConflictDialog = false) }
        runSync(preview = preview, resolutions = resolutions)
    }

    /**
     * 冲突弹窗关闭只退回预览态，是为了让用户能重新阅读本次同步规模后再决定是否执行。
     */
    fun onDismissConflicts() {
        _uiState.update { state -> state.clearPreviewDecisionState(keepPreview = true) }
    }

    /**
     * 本机设备名编辑通过独立弹窗承载，是为了避免主页面列表刷新时直接打断文本输入。
     */
    fun onEditLocalName() {
        _uiState.update {
            it.copy(
                isEditingLocalName = true,
                localNameInput = it.session.localProfile.displayName
            )
        }
    }

    /**
     * 文本输入留在本地状态，是为了让用户在确认保存前可以多次修改而不触发频繁持久化。
     */
    fun onLocalNameInputChange(value: String) {
        _uiState.update { it.copy(localNameInput = value) }
    }

    /**
     * 保存设备名时走仓储统一入口，是为了让本地存储、hello 响应和页面展示围绕同一份身份信息收敛。
     */
    fun onSaveLocalName() {
        val name = _uiState.value.localNameInput.trim()
        if (name.isBlank()) {
            return
        }
        launchMutation(
            action = { lanSyncRepository.updateLocalDisplayName(name) },
            onSuccess = {
                _uiState.update { it.copy(isEditingLocalName = false) }
            }
        )
    }

    /**
     * 取消设备名编辑时保留仓储中的真实名字，是为了避免未保存草稿误覆盖会话状态。
     */
    fun onDismissLocalNameEditor() {
        _uiState.update {
            it.copy(
                isEditingLocalName = false,
                localNameInput = it.session.localProfile.displayName
            )
        }
    }

    /**
     * 传输中取消统一委托给仓储，是为了由同一处处理网络请求中断和状态回滚。
     */
    fun onCancelActiveSync() {
        launchMutation(action = { lanSyncRepository.cancelActiveSync() })
    }

    /**
     * ViewModel 销毁时主动结束会话，可以避免同步页退出后仍在局域网里广播自身。
     */
    override fun onCleared() {
        viewModelScope.launch {
            lanSyncRepository.stopSession()
        }
        super.onCleared()
    }

    /**
     * 预览生成统一收口在单点，是为了让配对后的首轮预览和已信任设备的直接预览共享同一套处理逻辑。
     */
    private fun buildPreview(peer: LanSyncPeer, pairingCode: String?) {
        launchResult(
            action = { lanSyncRepository.prepareSync(peer = peer, pairingCode = pairingCode) },
            onSuccess = { preview ->
                _uiState.update {
                    it.clearTransientSyncState().copy(pendingPreview = preview)
                }
            },
            onFailure = {}
        )
    }

    /**
     * 真正执行同步前先把本地弹窗态清掉，是为了让页面把注意力切回统一的会话进度而不是残留在旧预览上。
     */
    private fun runSync(
        preview: LanSyncPreview,
        resolutions: List<LanSyncConflictResolution>
    ) {
        _uiState.update { state -> state.clearPreviewDecisionState() }
        launchMutation(
            action = { lanSyncRepository.runSync(preview = preview, resolutions = resolutions) }
        )
    }

    /**
     * 配对输入与待配对设备总是成对消失，收口成状态 helper 后可以避免多个入口反复复制同一组字段模板。
     */
    private fun LanSyncUiState.clearPairingState(): LanSyncUiState = copy(
        pendingPairingPeer = null,
        pairingCodeInput = ""
    )

    /**
     * 预览和冲突决议属于同一阶段的临时 UI 状态，
     * 集中清理可以避免结束同步流后残留上一次的冲突选择。
     */
    private fun LanSyncUiState.clearPreviewDecisionState(
        keepPreview: Boolean = false
    ): LanSyncUiState = copy(
        pendingPreview = if (keepPreview) pendingPreview else null,
        showConflictDialog = false,
        conflictChoices = emptyMap()
    )

    /**
     * 停止会话或拿到新预览时都需要回到干净的同步临时态，
     * 抽成单点后可以明确表达“把上一次的配对和预览痕迹一起清掉”这一意图。
     */
    private fun LanSyncUiState.clearTransientSyncState(): LanSyncUiState =
        clearPairingState().clearPreviewDecisionState()

    companion object {
        /**
         * 工厂显式注入仓储，是为了让同步页在测试中可以替换为假实现而不依赖全局容器。
         */
        fun factory(
            lanSyncRepository: LanSyncRepository
        ): ViewModelProvider.Factory = typedViewModelFactory {
            LanSyncViewModel(lanSyncRepository = lanSyncRepository)
        }
    }
}
