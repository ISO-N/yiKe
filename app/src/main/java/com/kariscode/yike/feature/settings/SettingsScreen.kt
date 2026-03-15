package com.kariscode.yike.feature.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 设置页集中承载提醒与备份入口的原因是这些能力都属于“全局行为”，
 * 把入口固定在此处可以避免在首页或内容页散落高风险操作入口。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "设置",
                navigationAction = NavigationAction(label = "返回", onClick = onBack)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                Text("加载中…")
            } else {
                Text("每日固定提醒")
                Switch(
                    checked = uiState.dailyReminderEnabled,
                    onCheckedChange = { enabled ->
                        viewModel.onReminderEnabledChange(
                            enabled = enabled,
                            hasNotificationPermission = hasNotificationPermission
                        )
                    }
                )
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                viewModel.onReminderTimeConfirmed(hour, minute)
                            },
                            uiState.reminderHour,
                            uiState.reminderMinute,
                            true
                        ).show()
                    }
                ) {
                    Text("提醒时间：%02d:%02d".format(uiState.reminderHour, uiState.reminderMinute))
                }
                uiState.message?.let { Text(it) }
                uiState.errorMessage?.let { Text(it) }
                Text("最近备份：${uiState.lastBackupAt?.let(::formatBackupAt) ?: "暂无记录"}")
                Text("应用版本：${uiState.appVersionName}")
            }
            Button(onClick = viewModel::onBackupRestoreClick) {
                Text("备份与恢复")
            }
        }
    }
}

/**
 * 设置页只需要可读的本地时间字符串展示最近备份时间，
 * 因此在页面层做轻量格式化即可避免引入额外 UI 模型复杂度。
 */
private fun formatBackupAt(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
        .toString()
