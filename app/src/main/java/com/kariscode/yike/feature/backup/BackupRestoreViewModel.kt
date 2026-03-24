package com.kariscode.yike.feature.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.core.message.userMessageOr
import com.kariscode.yike.data.backup.BackupExportMode
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.data.backup.BackupOperations
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 备份页状态需要同时覆盖导出、恢复确认与结果反馈，
 * 统一建模可避免高风险流程分散在页面临时变量中而难以维护。
 */
data class BackupRestoreUiState(
    val isExporting: Boolean,
    val isImporting: Boolean,
    val lastBackupAt: Long?,
    val warningMessage: String,
    val message: String?,
    val errorMessage: String?,
    val pendingRestoreUri: Uri?
)

/**
 * 文件选择器属于一次性系统交互，因此通过 effect 发出可避免把 Uri 启动逻辑塞进持续状态。
 */
sealed interface BackupRestoreEffect {
    data class LaunchExport(
        val suggestedFileName: String,
        val mode: BackupExportMode
    ) : BackupRestoreEffect

    data object LaunchImport : BackupRestoreEffect
}

/**
 * BackupRestoreViewModel 把导出、恢复确认和提醒重建串成完整业务路径，
 * 以确保页面不会越过校验或遗漏恢复后的系统协同。
 */
class BackupRestoreViewModel(
    private val backupService: BackupOperations,
    private val appSettingsRepository: AppSettingsRepository,
    private val reminderScheduler: ReminderSyncScheduler
) : ViewModel() {
    private var pendingExportMode: BackupExportMode = BackupExportMode.FULL

    private val _uiState = MutableStateFlow(
        BackupRestoreUiState(
            isExporting = false,
            isImporting = false,
            lastBackupAt = null,
            warningMessage = "从备份恢复会覆盖当前本地全部数据，请先确认是否已完成备份。",
            message = null,
            errorMessage = null,
            pendingRestoreUri = null
        )
    )
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<BackupRestoreEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BackupRestoreEffect> = _effects.asSharedFlow()

    init {
        /**
         * 最近备份时间来自设置仓储，是为了让导出成功与恢复后的设置变化都能自动反映到页面。
         */
        viewModelScope.launch {
            appSettingsRepository.observeSettings().collect { settings ->
                _uiState.update { it.copy(lastBackupAt = settings.backupLastAt) }
            }
        }
    }

    /**
     * 导出入口只负责触发系统文件创建器，
     * 真正写文件要等用户明确选择保存位置后再执行。
     */
    fun onExportClick() {
        launchExport(mode = BackupExportMode.FULL)
    }

    /**
     * 增量导出和完整导出共用文件选择流程，是为了让页面只维护一套系统文件交互入口。
     */
    fun onExportIncrementalClick() {
        launchExport(mode = BackupExportMode.INCREMENTAL)
    }

    /**
     * 导出模式进入 effect 前先记住当前选择，是为了让系统文件选择返回后仍能执行正确的导出分支。
     */
    private fun launchExport(mode: BackupExportMode) {
        pendingExportMode = mode
        _effects.tryEmit(
            BackupRestoreEffect.LaunchExport(
                suggestedFileName = backupService.createSuggestedFileName(mode = mode),
                mode = mode
            )
        )
    }

    /**
     * 导出 URI 回来后才开始写文件，可保证用户取消操作时不产生误导性的成功提示。
     */
    fun onExportUriSelected(uri: Uri?) {
        if (uri == null) return
        val exportMode = pendingExportMode
        _uiState.update { it.copy(isExporting = true, message = null, errorMessage = null) }
        launchResult(
            action = {
                backupService.exportToUri(uri = uri, mode = exportMode)
            },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        message = exportMode.successMessage(),
                        errorMessage = null
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        message = null,
                        errorMessage = throwable.userMessageOr(ErrorMessages.BACKUP_EXPORT_FAILED)
                    )
                }
            }
        )
    }

    /**
     * 恢复入口先打开系统文件选择器，是为了保持 Android 文件访问权限流程可解释且最小化。
     */
    fun onImportClick() {
        _effects.tryEmit(BackupRestoreEffect.LaunchImport)
    }

    /**
     * 选择文件后必须先进入确认态，
     * 这样用户在真正覆盖本地数据前会再看到一次不可逆风险提示。
     */
    fun onImportUriSelected(uri: Uri?) {
        if (uri == null) return
        _uiState.update {
            it.copy(
                pendingRestoreUri = uri,
                message = null,
                errorMessage = null
            )
        }
    }

    /**
     * 用户取消确认后应清空待恢复文件，
     * 避免页面后续误把旧选择当作当前操作目标。
     */
    fun onDismissRestoreConfirmation() {
        _uiState.update { it.copy(pendingRestoreUri = null) }
    }

    /**
     * 恢复成功后立即重建提醒，是为了让恢复后的设置与数据状态能马上影响后台任务。
     */
    fun onConfirmRestore() {
        val uri = _uiState.value.pendingRestoreUri ?: return
        _uiState.update {
            it.copy(
                isImporting = true,
                message = null,
                errorMessage = null,
                pendingRestoreUri = null
            )
        }
        launchResult(
            action = {
                backupService.restoreFromUri(uri)
                reminderScheduler.syncReminderFromRepository()
            },
            onSuccess = {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = SuccessMessages.BACKUP_RESTORED,
                        errorMessage = null
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        message = null,
                        errorMessage = throwable.userMessageOr(ErrorMessages.BACKUP_RESTORE_FAILED)
                    )
                }
            }
        )
    }

    /**
     * 导出反馈按模式区分后，用户能明确知道刚刚落地的是完整备份还是增量备份。
     */
    private fun BackupExportMode.successMessage(): String = when (this) {
        BackupExportMode.FULL -> SuccessMessages.BACKUP_EXPORTED
        BackupExportMode.INCREMENTAL -> "增量备份导出成功"
    }

    companion object {
        /**
         * 工厂注入高风险服务依赖，可让页面测试时替换为假实现而不触碰真实文件与数据库。
         */
        fun factory(
            backupService: BackupOperations,
            appSettingsRepository: AppSettingsRepository,
            reminderScheduler: ReminderSyncScheduler
        ): ViewModelProvider.Factory = typedViewModelFactory {
            BackupRestoreViewModel(
                backupService = backupService,
                appSettingsRepository = appSettingsRepository,
                reminderScheduler = reminderScheduler
            )
        }
    }
}
