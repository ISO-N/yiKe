package com.kariscode.yike.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.data.reminder.ReminderScheduler
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
 * 设置页状态需要同时承载提醒配置、版本信息和权限提示，
 * 因此集中在单一 UiState 能避免页面层自行拼装多个来源导致显示不同步。
 */
data class SettingsUiState(
    val isLoading: Boolean,
    val dailyReminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val lastBackupAt: Long?,
    val appVersionName: String,
    val message: String?,
    val errorMessage: String?
)

/**
 * 设置页一次性副作用只保留权限请求与跳转备份页，
 * 能让页面渲染状态与系统交互动作保持解耦。
 */
sealed interface SettingsEffect {
    data object RequestNotificationPermission : SettingsEffect
    data object OpenBackupRestore : SettingsEffect
}

/**
 * SettingsViewModel 统一编排提醒设置持久化与任务重建，
 * 从而确保设置页不会只更新 DataStore 却漏掉后台任务同步。
 */
class SettingsViewModel(
    private val appSettingsRepository: AppSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appVersionName: String
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            dailyReminderEnabled = false,
            reminderHour = 20,
            reminderMinute = 0,
            lastBackupAt = null,
            appVersionName = appVersionName,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    init {
        /**
         * 设置页直接订阅仓储流，是为了让备份恢复后的设置变化也能在页面重新进入时自然体现。
         */
        viewModelScope.launch {
            appSettingsRepository.observeSettings().collect { settings ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dailyReminderEnabled = settings.dailyReminderEnabled,
                        reminderHour = settings.dailyReminderHour,
                        reminderMinute = settings.dailyReminderMinute,
                        lastBackupAt = settings.backupLastAt,
                        errorMessage = null
                    )
                }
            }
        }
    }

    /**
     * 开启提醒时若系统需要通知权限，则先请求权限；
     * 这样既符合 Android 13 的交互要求，也能保持“用户主动操作时再询问权限”的体验。
     */
    fun onReminderEnabledChange(enabled: Boolean, hasNotificationPermission: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            _effects.tryEmit(SettingsEffect.RequestNotificationPermission)
            return
        }
        persistReminderEnabled(enabled = enabled, showPermissionWarning = false)
    }

    /**
     * 权限结果回来后仍允许把提醒开关保持开启，
     * 这样即使通知暂时发不出来，用户的提醒意图和时间配置也不会丢失。
     */
    fun onNotificationPermissionResult(granted: Boolean) {
        persistReminderEnabled(enabled = true, showPermissionWarning = !granted)
    }

    /**
     * 时间修改后立即同步调度，能避免“设置已保存但旧任务仍按旧时间触发”的体验错位。
     */
    fun onReminderTimeConfirmed(hour: Int, minute: Int) {
        viewModelScope.launch {
            runCatching {
                appSettingsRepository.setDailyReminderTime(hour, minute)
                reminderScheduler.syncReminderFromRepository()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = "提醒设置已保存",
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        errorMessage = "设置保存失败，请稍后重试",
                        message = null
                    )
                }
            }
        }
    }

    /**
     * 跳转备份页通过 effect 发出，是为了避免在 ViewModel 中直接耦合导航控制器。
     */
    fun onBackupRestoreClick() {
        _effects.tryEmit(SettingsEffect.OpenBackupRestore)
    }

    /**
     * 临时消息在展示后可主动清除，避免用户返回页面时重复看到旧反馈。
     */
    fun onMessageConsumed() {
        _uiState.update { it.copy(message = null, errorMessage = null) }
    }

    /**
     * 开关写入与提醒重建封装成一个入口，是为了保证成功与失败反馈都围绕同一条业务路径。
     */
    private fun persistReminderEnabled(enabled: Boolean, showPermissionWarning: Boolean) {
        viewModelScope.launch {
            runCatching {
                appSettingsRepository.setDailyReminderEnabled(enabled)
                reminderScheduler.syncReminderFromRepository()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        message = if (showPermissionWarning) {
                            "通知权限未开启，提醒可能无法显示"
                        } else {
                            "提醒设置已保存"
                        },
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        message = null,
                        errorMessage = "设置保存失败，请稍后重试"
                    )
                }
            }
        }
    }

    companion object {
        /**
         * 工厂显式注入系统依赖，是为了保持 ViewModel 可测试且不依赖全局静态单例。
         */
        fun factory(
            appSettingsRepository: AppSettingsRepository,
            reminderScheduler: ReminderScheduler,
            appVersionName: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(
                    appSettingsRepository = appSettingsRepository,
                    reminderScheduler = reminderScheduler,
                    appVersionName = appVersionName
                ) as T
            }
        }
    }
}
