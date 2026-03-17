package com.kariscode.yike.feature.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.LocalSyncSnapshot
import com.kariscode.yike.domain.model.SyncConflict
import com.kariscode.yike.domain.model.SyncDevice
import com.kariscode.yike.domain.repository.LanSyncRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/**
 * 同步页状态同时覆盖设备发现、本机摘要、冲突确认与结果反馈，
 * 统一建模可以避免网络流程散落在页面 remember 状态里难以收口。
 */
data class LanSyncUiState(
    val isSessionActive: Boolean,
    val isPreparing: Boolean,
    val isSyncing: Boolean,
    val localDeviceName: String,
    val localSnapshot: LocalSyncSnapshot?,
    val devices: List<SyncDevice>,
    val message: String?,
    val errorMessage: String?,
    val pendingConflict: SyncConflict?
)

/**
 * 局域网同步 ViewModel 负责把发现、摘要读取和恢复流程编排成一个受控交互序列，
 * 避免页面层直接操作网络与数据库能力。
 */
class LanSyncViewModel(
    private val lanSyncRepository: LanSyncRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LanSyncUiState(
            isSessionActive = false,
            isPreparing = false,
            isSyncing = false,
            localDeviceName = lanSyncRepository.getLocalDeviceName(),
            localSnapshot = null,
            devices = emptyList(),
            message = null,
            errorMessage = null,
            pendingConflict = null
        )
    )
    val uiState: StateFlow<LanSyncUiState> = _uiState.asStateFlow()

    init {
        /**
         * 设备列表通过持续订阅回写状态，是为了让局域网内设备上下线时页面无需人工刷新。
         */
        viewModelScope.launch {
            lanSyncRepository.observeDevices().collect { devices ->
                _uiState.update { state -> state.copy(devices = devices) }
            }
        }
    }

    /**
     * 拿到权限后再启动发现，可以把网络广播窗口压缩到用户明确进入同步页的时刻。
     */
    fun onPermissionReady() {
        if (_uiState.value.isSessionActive || _uiState.value.isPreparing) {
            return
        }
        _uiState.update { it.copy(isPreparing = true, message = null, errorMessage = null) }
        launchResult(
            action = {
                lanSyncRepository.start()
                lanSyncRepository.getLocalSnapshot()
            },
            onSuccess = { snapshot ->
                _uiState.update {
                    it.copy(
                        isSessionActive = true,
                        isPreparing = false,
                        localSnapshot = snapshot,
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        isSessionActive = false,
                        isPreparing = false,
                        message = null,
                        errorMessage = "局域网同步启动失败，请确认设备处于同一 Wi-Fi 后重试"
                    )
                }
            }
        )
    }

    /**
     * 用户离开页面前允许主动停止发现，是为了在需要时把应用恢复到完全离线的默认边界。
     */
    fun onStopSession() {
        if (!_uiState.value.isSessionActive || _uiState.value.isPreparing) {
            return
        }
        _uiState.update { it.copy(isPreparing = true, message = null, errorMessage = null) }
        launchResult(
            action = { lanSyncRepository.stop() },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isSessionActive = false,
                        isPreparing = false,
                        devices = emptyList(),
                        pendingConflict = null,
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        isPreparing = false,
                        errorMessage = "局域网同步关闭失败，请稍后重试"
                    )
                }
            }
        )
    }

    /**
     * 同步前先拉取远端摘要，是为了让页面有机会在覆盖本机前展示明确的风险提示。
     */
    fun onSyncDeviceClick(device: SyncDevice) {
        if (_uiState.value.isSyncing || _uiState.value.isPreparing) {
            return
        }
        _uiState.update { it.copy(isPreparing = true, message = null, errorMessage = null) }
        launchResult(
            action = {
                parallel(
                    first = { lanSyncRepository.getLocalSnapshot() },
                    second = { lanSyncRepository.fetchRemoteSnapshot(device) }
                )
            },
            onSuccess = { (localSnapshot, remoteSnapshot) ->
                val conflict = buildConflictOrNull(
                    device = device,
                    localSnapshot = localSnapshot,
                    remoteSnapshot = remoteSnapshot
                )
                _uiState.update {
                    it.copy(
                        isPreparing = false,
                        localSnapshot = localSnapshot,
                        pendingConflict = conflict
                    )
                }
                if (conflict == null) {
                    performImport(device)
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        isPreparing = false,
                        errorMessage = "读取远端设备摘要失败，请确认对方已打开局域网同步页"
                    )
                }
            }
        )
    }

    /**
     * 冲突确认后继续同步，意味着用户已经知晓会覆盖本机内容，因此可以直接进入恢复流程。
     */
    fun onConfirmConflictSync() {
        val conflict = _uiState.value.pendingConflict ?: return
        _uiState.update { it.copy(pendingConflict = null) }
        performImport(conflict.device)
    }

    /**
     * 取消冲突弹窗时只清理当前待确认状态，是为了让设备列表和本机摘要保持可继续操作。
     */
    fun onDismissConflict() {
        _uiState.update { it.copy(pendingConflict = null) }
    }

    /**
     * ViewModel 销毁时及时停止服务与发现，可以避免页面退出后仍继续在局域网中广播自身。
     */
    override fun onCleared() {
        viewModelScope.launch {
            lanSyncRepository.stop()
        }
        super.onCleared()
    }

    /**
     * 真正写入本地前后都刷新本机摘要，是为了让页面上的本机规模能准确反映最新状态。
     */
    private fun performImport(device: SyncDevice) {
        _uiState.update { it.copy(isSyncing = true, message = null, errorMessage = null) }
        launchResult(
            action = {
                lanSyncRepository.importFromDevice(device)
                lanSyncRepository.getLocalSnapshot()
            },
            onSuccess = { snapshot ->
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        localSnapshot = snapshot,
                        message = "已从 ${device.deviceName} 同步到本机",
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        message = null,
                        errorMessage = ErrorMessages.BACKUP_RESTORE_FAILED
                    )
                }
            }
        )
    }

    /**
     * 只要本机已有内容就先要求确认，是为了明确维持“覆盖式同步”的风险语义而不是静默替换。
     */
    private fun buildConflictOrNull(
        device: SyncDevice,
        localSnapshot: LocalSyncSnapshot,
        remoteSnapshot: com.kariscode.yike.domain.model.LanSyncSnapshot
    ): SyncConflict? {
        val localItemCount = localSnapshot.deckCount + localSnapshot.cardCount + localSnapshot.questionCount
        if (localItemCount <= 0) {
            return null
        }
        val reason = if (
            localSnapshot.lastBackupAt != null &&
            remoteSnapshot.exportedAt < localSnapshot.lastBackupAt
        ) {
            "远端快照早于本机最近一次备份，继续同步仍会覆盖当前本机内容。"
        } else {
            "同步会覆盖当前本机已有的卡组、卡片和问题数据。"
        }
        return SyncConflict(
            device = device,
            remoteSnapshot = remoteSnapshot,
            localSnapshot = localSnapshot,
            reason = reason
        )
    }

    companion object {
        /**
         * 同步页工厂显式注入仓储，是为了保持网络与恢复流程可测试且不依赖全局单例。
         */
        fun factory(
            lanSyncRepository: LanSyncRepository
        ): ViewModelProvider.Factory = typedViewModelFactory {
            LanSyncViewModel(lanSyncRepository = lanSyncRepository)
        }
    }
}
