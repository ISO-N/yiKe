package com.kariscode.yike.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.data.settings.SettingsConstants
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
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
    val themeMode: ThemeMode,
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
    data object OpenLanSync : SettingsEffect
    data object OpenWebConsole : SettingsEffect
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
    private var latestSettings = AppSettings(
        dailyReminderEnabled = false,
        dailyReminderHour = 20,
        dailyReminderMinute = 0,
        schemaVersion = SettingsConstants.SCHEMA_VERSION,
        backupLastAt = null,
        themeMode = ThemeMode.LIGHT,
        streakAchievementUnlocks = emptyList()
    )

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            isLoading = true,
            dailyReminderEnabled = false,
            reminderHour = 20,
            reminderMinute = 0,
            lastBackupAt = null,
            themeMode = ThemeMode.LIGHT,
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
                latestSettings = settings
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dailyReminderEnabled = settings.dailyReminderEnabled,
                        reminderHour = settings.dailyReminderHour,
                        reminderMinute = settings.dailyReminderMinute,
                        lastBackupAt = settings.backupLastAt,
                        themeMode = settings.themeMode,
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
        persistSettingsChange(successMessage = "提醒设置已保存") {
            appSettingsRepository.setDailyReminderTime(hour, minute)
            reminderScheduler.syncReminder(
                currentReminderSettings(
                    hour = hour,
                    minute = minute
                )
            )
        }
    }

    /**
     * 跳转备份页通过 effect 发出，是为了避免在 ViewModel 中直接耦合导航控制器。
     */
    fun onBackupRestoreClick() {
        _effects.tryEmit(SettingsEffect.OpenBackupRestore)
    }

    /**
     * 局域网同步属于高风险全局能力，通过 effect 发导航可以保持 ViewModel 不依赖具体路由实现。
     */
    fun onLanSyncClick() {
        _effects.tryEmit(SettingsEffect.OpenLanSync)
    }

    /**
     * 网页后台与局域网同步都属于对外开放能力，但生命周期和登录方式不同，因此保持独立入口更利于用户理解风险。
     */
    fun onWebConsoleClick() {
        _effects.tryEmit(SettingsEffect.OpenWebConsole)
    }

    /**
     * 主题切换只依赖设置仓储即可即时生效，是为了把“显示偏好”与提醒等系统副作用隔离开，
     * 避免简单外观设置也走一遍无关的调度逻辑。
     */
    fun onThemeModeChange(themeMode: ThemeMode) {
        if (themeMode == _uiState.value.themeMode) {
            return
        }
        persistSettingsChange(successMessage = "主题设置已保存") {
            appSettingsRepository.setThemeMode(themeMode)
        }
    }

    /**
     * Snackbar 负责展示一次性成功提示，因此展示后需要清理 message，
     * 以免配置变更或重新进入页面时重复弹出同一条反馈。
     */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * 错误提示同样属于一次性反馈，展示后清理可以避免用户在恢复后仍被旧错误反复打断。
     */
    fun consumeErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 开关写入与提醒重建封装成一个入口，是为了保证成功与失败反馈都围绕同一条业务路径。
     */
    private fun persistReminderEnabled(enabled: Boolean, showPermissionWarning: Boolean) {
        val successMessage = if (showPermissionWarning) {
            "通知权限未开启，提醒可能无法显示"
        } else {
            "提醒设置已保存"
        }
        persistSettingsChange(successMessage = successMessage) {
            appSettingsRepository.setDailyReminderEnabled(enabled)
            reminderScheduler.syncReminder(currentReminderSettings(enabled = enabled))
        }
    }

    /**
     * 提醒写操作共享同一套反馈模板，是为了避免开关与时间修改的成功/失败提示逐渐漂移。
     */
    private fun persistSettingsChange(
        successMessage: String,
        action: suspend () -> Unit
    ) {
        launchResult(
            action = action,
            onSuccess = {
                _uiState.update {
                    it.copy(
                        message = successMessage,
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update {
                    it.copy(
                        message = null,
                        errorMessage = ErrorMessages.SETTINGS_SAVE_FAILED
                    )
                }
            }
        )
    }

    /**
     * 设置页本身已经持有最新提醒状态，因此直接复用当前快照可以减少写入后的二次读盘，
     * 同时保持调度口径仍由 `ReminderScheduler` 统一收敛。
     */
    private fun currentReminderSettings(
        enabled: Boolean = _uiState.value.dailyReminderEnabled,
        hour: Int = _uiState.value.reminderHour,
        minute: Int = _uiState.value.reminderMinute
    ): AppSettings = latestSettings.copy(
        dailyReminderEnabled = enabled,
        dailyReminderHour = hour,
        dailyReminderMinute = minute
    )

    companion object {
        /**
         * 工厂显式注入系统依赖，是为了保持 ViewModel 可测试且不依赖全局静态单例。
         */
        fun factory(
            appSettingsRepository: AppSettingsRepository,
            reminderScheduler: ReminderScheduler,
            appVersionName: String
        ): ViewModelProvider.Factory = typedViewModelFactory {
            SettingsViewModel(
                appSettingsRepository = appSettingsRepository,
                reminderScheduler = reminderScheduler,
                appVersionName = appVersionName
            )
        }
    }
}

