package com.kariscode.yike.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.component.YikeWarningCard
import com.kariscode.yike.ui.format.formatLocalDateTime

/**
 * 备份恢复页属于高风险流内页面，因此继续使用聚焦式返回路径，并把风险提示固定在页面顶部。
 */
@Composable
fun BackupRestoreScreen(
    navigator: YikeNavigator,
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = viewModel::onExportUriSelected
    )
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::onImportUriSelected
    )

    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            is BackupRestoreEffect.LaunchExport -> exportLauncher.launch(effect.suggestedFileName)
            BackupRestoreEffect.LaunchImport -> importLauncher.launch(arrayOf("application/json"))
        }
    }

    YikeFlowScaffold(
        title = "备份与恢复",
        subtitle = "导出完整 JSON，或在确认风险后从本地备份恢复全部数据。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        BackupRestoreContent(
            uiState = uiState,
            onExport = viewModel::onExportClick,
            onImport = viewModel::onImportClick,
            modifier = modifier,
            contentPadding = padding
        )
    }

    if (uiState.pendingRestoreUri != null) {
        YikeDangerConfirmationDialog(
            title = "确认恢复备份？",
            description = "恢复后将覆盖当前本地全部数据，且无法撤销。",
            confirmText = "继续恢复",
            onDismiss = viewModel::onDismissRestoreConfirmation,
            onConfirm = viewModel::onConfirmRestore
        )
    }
}

/**
 * 页面主体拆出来后，可以直接验证最近备份状态、风险提示和导出/恢复按钮反馈。
 */
@Composable
fun BackupRestoreContent(
    uiState: BackupRestoreUiState,
    onExport: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        YikeWarningCard(
            title = "恢复会覆盖当前本地全部数据",
            description = uiState.warningMessage
        )

        YikeStateBanner(
            title = "最近备份",
            description = uiState.lastBackupAt?.let { formatLocalDateTime(it) } ?: "暂无备份记录，建议先导出一次再进行恢复操作。",
            trailing = {
                YikeBadge(text = if (uiState.lastBackupAt != null) "可用" else "暂无")
            }
        )

        YikeSurfaceCard {
            Text(text = "导出完整备份")
            Text(text = "包含卡组、卡片、问题、设置和复习记录，不会修改当前本地数据。")
            YikePrimaryButton(
                text = if (uiState.isExporting) "导出中…" else "导出 JSON",
                onClick = onExport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting && !uiState.isImporting
            )
        }

        YikeSurfaceCard {
            Text(text = "从备份恢复")
            Text(text = "恢复前建议先导出当前数据，避免误操作后无法回滚。")
            YikeSecondaryButton(
                text = if (uiState.isImporting) "恢复中…" else "选择备份文件",
                onClick = onImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isExporting && !uiState.isImporting
            )
        }

        YikeOperationFeedback(
            successMessage = uiState.message,
            errorMessage = uiState.errorMessage
        )
    }
}


