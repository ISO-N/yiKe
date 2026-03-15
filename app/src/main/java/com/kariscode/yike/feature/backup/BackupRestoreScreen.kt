package com.kariscode.yike.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 备份与恢复属于不可逆高风险操作，首版在页面层先固定“风险提示 + 明确确认”的交互承载位置，
 * 当前实现则把导出、校验、恢复确认与恢复后提醒重建全部收敛进同一页面流程。
 */
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<BackupRestoreViewModel>(
        factory = BackupRestoreViewModel.factory(
            backupService = container.backupService,
            appSettingsRepository = container.appSettingsRepository,
            reminderScheduler = container.reminderScheduler
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = viewModel::onExportUriSelected
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::onImportUriSelected
    )

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BackupRestoreEffect.LaunchExport -> exportLauncher.launch(effect.suggestedFileName)
                BackupRestoreEffect.LaunchImport -> importLauncher.launch(arrayOf("application/json"))
            }
        }
    }

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "备份与恢复",
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
            BackupRestoreContent(
                uiState = uiState,
                onExport = viewModel::onExportClick,
                onImport = viewModel::onImportClick
            )
        }
    }

    if (uiState.pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissRestoreConfirmation,
            title = { Text("确认恢复备份？") },
            text = { Text("恢复后将覆盖当前本地全部数据，且无法撤销。") },
            confirmButton = {
                Button(onClick = viewModel::onConfirmRestore) {
                    Text("继续恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissRestoreConfirmation) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 页面主体拆分为纯展示层，是为了让测试能直接验证高风险文案与按钮状态，不依赖系统文件选择器。
 */
@Composable
fun BackupRestoreContent(
    uiState: BackupRestoreUiState,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Text(uiState.warningMessage)
    Text("备份文件可能包含全部学习内容，请注意保管。")
    Text("最近备份：${uiState.lastBackupAt?.let(::formatBackupTime) ?: "暂无记录"}")
    Button(
        onClick = onExport,
        enabled = !uiState.isExporting && !uiState.isImporting
    ) {
        Text(if (uiState.isExporting) "导出中…" else "导出备份")
    }
    Button(
        onClick = onImport,
        enabled = !uiState.isExporting && !uiState.isImporting
    ) {
        Text(if (uiState.isImporting) "恢复中…" else "从备份恢复")
    }
    if (uiState.message != null) {
        Text(uiState.message)
    }
    if (uiState.errorMessage != null) {
        Text(uiState.errorMessage)
    }
}

/**
 * 最近备份时间仅用于页面展示，采用本地时间格式化可让用户直接理解最近一次导出发生在何时。
 */
private fun formatBackupTime(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
        .toString()
