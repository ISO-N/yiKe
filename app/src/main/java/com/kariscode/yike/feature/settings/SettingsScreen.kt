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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.format.formatLocalDateTime
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 设置页属于一级入口，因此必须复用统一导航壳，并在同一页面里承接提醒和备份入口。
 */
@Composable
fun SettingsScreen(
    onOpenBackupRestore: () -> Unit,
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
    val uiState by viewModel.uiState.collectAsState()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult
    )
    val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.OpenBackupRestore -> onOpenBackupRestore()
                SettingsEffect.RequestNotificationPermission -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    YikePrimaryScaffold(
        currentDestination = YikePrimaryDestination.SETTINGS,
        title = "设置",
        subtitle = "在这里管理提醒、备份以及应用的全局状态。"
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
            onOpenBackupRestore = viewModel::onBackupRestoreClick,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 设置页主体独立后，可以直接验证提醒状态卡、权限提示和备份入口是否跟真实状态保持一致。
 */
@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onChangeTime: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalYikeSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        if (uiState.isLoading) {
            YikeStateBanner(
                title = "正在读取设置",
                description = "稍等一下，我们会把提醒和最近备份状态一起载入。"
            )
            return@Column
        }

        ReminderStatusSection(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission
        )

        uiState.message?.let { message ->
            YikeStateBanner(
                title = "设置已更新",
                description = message
            )
        }
        uiState.errorMessage?.let { message ->
            YikeStateBanner(
                title = "设置更新失败",
                description = message
            )
        }

        ReminderSettingsSection(
            uiState = uiState,
            hasNotificationPermission = hasNotificationPermission,
            onReminderEnabledChange = onReminderEnabledChange,
            onChangeTime = onChangeTime,
            onOpenBackupRestore = onOpenBackupRestore
        )
        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
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
        !uiState.dailyReminderEnabled -> "当前不会自动提醒，你仍然可以随时手动打开应用开始复习。"
        hasNotificationPermission -> "每天 ${formatReminderTime(uiState.reminderHour, uiState.reminderMinute)} 检查是否有待复习内容。"
        else -> "提醒开关已开，但通知权限未开启，提醒可能无法显示。"
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
 * 提醒设置区把开关、时间、备份入口和版本信息排成统一列表，是为了保持设置页的低频任务可扫读。
 */
@Composable
private fun ReminderSettingsSection(
    uiState: SettingsUiState,
    hasNotificationPermission: Boolean,
    onReminderEnabledChange: (Boolean) -> Unit,
    onChangeTime: () -> Unit,
    onOpenBackupRestore: () -> Unit
) {
    YikeListItemCard(
        title = "每日提醒",
        summary = if (uiState.dailyReminderEnabled) "已开启" else "已关闭",
        supporting = if (hasNotificationPermission || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            "保持固定节奏，比临时补课更容易形成复习习惯。"
        } else {
            "通知权限未开启，提醒可能无法显示，但不会影响复习和内容管理。"
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
        supporting = "修改后会立即重新注册下一次提醒任务。"
    ) {
        YikeSecondaryButton(
            text = "修改时间",
            onClick = onChangeTime,
            modifier = Modifier.fillMaxWidth()
        )
    }

    YikeListItemCard(
        title = "备份与恢复",
        summary = uiState.lastBackupAt?.let(::formatBackupAt) ?: "暂无备份记录",
        supporting = "导出 JSON 或从本地文件恢复全部数据。"
    ) {
        YikeSecondaryButton(
            text = "进入备份页",
            onClick = onOpenBackupRestore,
            modifier = Modifier.fillMaxWidth()
        )
    }

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
 * 提醒时间在页面层做轻量格式化即可，是为了让用户一眼读懂当前持久化结果。
 */
private fun formatReminderTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/**
 * 最近备份时间只用于设置页展示，因此采用本地时间字符串即可满足理解成本最低的目标。
 */
private fun formatBackupAt(epochMillis: Long): String =
    formatLocalDateTime(epochMillis)
