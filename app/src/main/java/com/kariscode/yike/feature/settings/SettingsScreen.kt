package com.kariscode.yike.feature.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeScrollableRow
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.format.formatLocalDateTime
import com.kariscode.yike.ui.format.formatReminderTime
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 设置页属于一级入口，因此必须复用统一导航壳，并在同一页面里承接提醒、备份和归档内容入口。
 */
@Composable
fun SettingsScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val viewModel = viewModel<SettingsViewModel>(
        factory = SettingsViewModel.factory(
            appSettingsRepository = container.appSettingsRepository,
            reminderScheduler = container.reminderScheduler,
            appVersionName = BuildConfig.VERSION_NAME
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult
    )
    val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.OpenBackupRestore -> navigator.openBackupRestore()
            SettingsEffect.OpenLanSync -> navigator.openLanSync()
            SettingsEffect.OpenWebConsole -> navigator.openWebConsole()
            SettingsEffect.RequestNotificationPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    YikePrimaryScaffold(
        currentDestination = YikePrimaryDestination.SETTINGS,
        title = "设置",
        subtitle = "提醒、已归档内容和备份都在这里。"
    ) { padding ->
        SettingsContent(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            onReminderEnabledChange = { enabled ->
                viewModel.onReminderEnabledChange(
                    enabled = enabled,
                    hasNotificationPermission = hasNotificationPermission
                )
            },
            onChangeTime = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> viewModel.onReminderTimeConfirmed(hour, minute) },
                    uiState.reminderHour,
                    uiState.reminderMinute,
                    true
                ).show()
            },
            onThemeModeChange = viewModel::onThemeModeChange,
            onOpenBackupRestore = viewModel::onBackupRestoreClick,
            onOpenLanSync = viewModel::onLanSyncClick,
            onOpenWebConsole = viewModel::onWebConsoleClick,
            onOpenRecycleBin = navigator::openRecycleBin,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 设置页主体独立后，可以直接验证提醒状态卡、权限提示和低频工具入口是否跟真实状态保持一致。
 */
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onChangeTime: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenLanSync: () -> Unit,
    onOpenWebConsole: () -> Unit,
    onOpenRecycleBin: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        if (uiState.isLoading) {
            YikeStateBanner(
                title = "正在读取设置",
                description = "正在同步提醒和备份状态。"
            ) {
                CircularProgressIndicator()
            }
            return@YikeScrollableColumn
        }

        ReminderStatusSection(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission
        )
        SettingsOverviewSection()

        YikeOperationFeedback(
            successMessage = uiState.message,
            errorMessage = uiState.errorMessage,
            successTitle = "设置已更新",
            errorTitle = "设置更新失败"
        )

        ReminderSettingsSection(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            onReminderEnabledChange = onReminderEnabledChange,
            onChangeTime = onChangeTime,
            onThemeModeChange = onThemeModeChange,
            onOpenBackupRestore = onOpenBackupRestore,
            onOpenLanSync = onOpenLanSync,
            onOpenWebConsole = onOpenWebConsole,
            onOpenRecycleBin = onOpenRecycleBin
        )
    }
}

/**
 * 顶部状态卡集中表达提醒是否开启、何时触发以及权限是否阻塞通知显示。
 */
@Composable
private fun ReminderStatusSection(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean
) {
    val statusTitle = when {
        !uiState.dailyReminderEnabled -> "提醒已关闭"
        hasNotificationPermission -> "提醒已开启"
        else -> "提醒等待权限"
    }
    val statusDescription = when {
        !uiState.dailyReminderEnabled -> "当前不会自动提醒。"
        hasNotificationPermission -> "每天 ${formatReminderTime(uiState.reminderHour, uiState.reminderMinute)} 检查待复习内容。"
        else -> "提醒已开启，但通知权限未开启。"
    }
    YikeStateBanner(
        title = statusTitle,
        description = statusDescription,
        trailing = {
            YikeBadge(
                text = if (uiState.dailyReminderEnabled) "已启用" else "未启用"
            )
        }
    )
}

/**
 * 设置页先给出分组总览，是为了让用户在首屏就建立“复习设置 / 数据管理 / 关于应用”的心智地图，
 * 不必从一长串入口里再自己重新分组。
 */
@Composable
private fun SettingsOverviewSection() {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "设置分组",
        description = "先调复习节奏，再处理数据工具，最后再查看版本信息，能让整个页面更容易扫读。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeBadge(text = "复习设置")
            YikeBadge(text = "数据管理")
            YikeBadge(text = "关于应用")
        }
    }
}

/**
 * 提醒设置区把开关、时间、低频工具入口和版本信息排成统一列表，是为了保持设置页可扫读。
 */
@Composable
private fun ReminderSettingsSection(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onChangeTime: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenLanSync: () -> Unit,
    onOpenWebConsole: () -> Unit,
    onOpenRecycleBin: () -> Unit
) {
    SettingsSectionHeader(
        title = "复习设置",
        description = "先把每天的复习提醒、时间和显示偏好整理好，再继续处理低频工具入口。"
    )

    YikeListItemCard(
        title = "每日提醒",
        summary = if (uiState.dailyReminderEnabled) "已开启" else "已关闭",
        supporting = if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "保持固定节奏更容易坚持。"
        } else {
            "通知权限未开启，提醒可能无法显示。"
        },
        badge = {
            Switch(
                checked = uiState.dailyReminderEnabled,
                onCheckedChange = onReminderEnabledChange
            )
        }
    )

    YikeListItemCard(
        title = "提醒时间",
        summary = formatReminderTime(uiState.reminderHour, uiState.reminderMinute),
        supporting = "修改后会立即重排下一次提醒。"
    ) {
        YikeSecondaryButton(
            text = "修改时间",
            onClick = onChangeTime,
            modifier = Modifier.fillMaxWidth()
        )
    }

    YikeListItemCard(
        title = "显示主题",
        summary = uiState.themeMode.displayLabel,
        supporting = "切换后会立即作用到整个应用界面。"
    ) {
        ThemeModeFilterRow(
            selectedMode = uiState.themeMode,
            onThemeModeChange = onThemeModeChange
        )
    }

    SettingsSectionHeader(
        title = "数据管理",
        description = "备份、同步、网页后台和归档恢复都会跳到独立流程页，统一归在数据工具组里更容易定位。"
    )

    YikeListItemCard(
        title = "备份与恢复",
        summary = uiState.lastBackupAt?.let { formatLocalDateTime(it) } ?: "暂无备份记录",
        supporting = "导出 JSON 或恢复本地备份。",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        badge = { NavigationEntryBadge() }
    ) {
        YikeSecondaryButton(
            text = "进入备份页",
            onClick = onOpenBackupRestore,
            modifier = Modifier.fillMaxWidth()
        )
    }

    YikeListItemCard(
        title = "局域网同步",
        summary = "发现同一 Wi-Fi 下的设备",
        supporting = "支持设备配对、同步预览与冲突确认。",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        badge = { NavigationEntryBadge() }
    ) {
        YikeSecondaryButton(
            text = "进入同步页",
            onClick = onOpenLanSync,
            modifier = Modifier.fillMaxWidth()
        )
    }

    YikeListItemCard(
        title = "网页后台",
        summary = "对电脑和手机浏览器开放后台页面",
        supporting = "启动后会显示 IP、端口和一次性访问码。",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        badge = { NavigationEntryBadge() }
    ) {
        YikeSecondaryButton(
            text = "进入网页后台页",
            onClick = onOpenWebConsole,
            modifier = Modifier.fillMaxWidth()
        )
    }

    YikeListItemCard(
        title = "已归档内容",
        summary = "集中恢复或清理归档数据",
        supporting = "先归档，再按需恢复或彻底删除。",
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
        badge = { NavigationEntryBadge() }
    ) {
        YikeSecondaryButton(
            text = "查看已归档内容",
            onClick = onOpenRecycleBin,
            modifier = Modifier.fillMaxWidth()
        )
    }

    SettingsSectionHeader(
        title = "关于应用",
        description = "静态版本信息固定放在最后，是为了让操作入口和说明信息自然分层。"
    )

    YikeListItemCard(
        title = "应用版本",
        summary = "YiKe v${uiState.appVersionName}",
        supporting = "当前版本仍为离线优先 MVP。"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)
        ) {
            YikeBadge(text = "MVP")
        }
    }
}

/**
 * 设置页用小节标题分组入口，是为了让“切换类操作”和“跳转类工具”在视觉上先被用户区分开。
 */
@Composable
private fun SettingsSectionHeader(
    title: String,
    description: String
) {
    YikeStateBanner(
        title = title,
        description = description
    )
}

/**
 * 跳转类入口统一带上“工具页”徽标，是为了让用户在扫读设置页时一眼区分“立即切换”和“进入子流程”。
 */
@Composable
private fun NavigationEntryBadge() {
    YikeBadge(
        text = "工具页",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

/**
 * 主题模式用横向芯片排列，是为了在手机宽度下同时保留三个明确选项，
 * 避免下钻到二级页面才能完成一个低成本的外观偏好调整。
 */
@Composable
private fun ThemeModeFilterRow(
    selectedMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    YikeScrollableRow(modifier = Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedMode == mode,
                onClick = { onThemeModeChange(mode) },
                label = { Text(mode.displayLabel) }
            )
        }
    }
}

