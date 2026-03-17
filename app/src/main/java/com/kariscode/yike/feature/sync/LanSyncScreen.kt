package com.kariscode.yike.feature.sync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.SyncConflict
import com.kariscode.yike.domain.model.SyncDevice
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeWarningCard
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.format.formatLocalDateTime

/**
 * 局域网同步页独立成流内页面，是为了让高风险覆盖操作与一级入口保持清晰的任务边界。
 */
@Composable
fun LanSyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LanSyncViewModel>(
        factory = LanSyncViewModel.factory(
            lanSyncRepository = container.lanSyncRepository
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val needsNearbyWifiPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val hasNearbyWifiPermission = !needsNearbyWifiPermission || (
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
        )
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.onPermissionReady()
            }
        }
    )

    /** 权限已具备时自动开启发现，可以让用户进入同步页后直接看到设备列表而不必重复点击。 */
    LaunchedEffect(hasNearbyWifiPermission) {
        if (hasNearbyWifiPermission) {
            viewModel.onPermissionReady()
        }
    }

    YikeFlowScaffold(
        title = "局域网同步",
        subtitle = "发现同一 Wi-Fi 下的设备，并把远端备份覆盖同步到本机。",
        navigationAction = backNavigationAction(onClick = onBack)
    ) { padding ->
        LanSyncContent(
            uiState = uiState,
            hasNearbyWifiPermission = hasNearbyWifiPermission,
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            },
            onStartSession = viewModel::onPermissionReady,
            onStopSession = viewModel::onStopSession,
            onSyncDevice = viewModel::onSyncDeviceClick,
            modifier = modifier,
            contentPadding = padding
        )
    }

    uiState.pendingConflict?.let { conflict ->
        SyncConflictDialog(
            conflict = conflict,
            onDismiss = viewModel::onDismissConflict,
            onConfirm = viewModel::onConfirmConflictSync
        )
    }
}

/**
 * 同步页主体把权限、说明、本机摘要和设备列表串成单一路径，是为了让用户按风险顺序逐步决策。
 */
@Composable
private fun LanSyncContent(
    uiState: LanSyncUiState,
    hasNearbyWifiPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onSyncDevice: (SyncDevice) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    YikeScrollableColumn(modifier = modifier) {
        YikeWarningCard(
            title = "覆盖式同步",
            description = "当前版本会把远端设备上的完整备份恢复到本机，请先确认本机数据已备份。"
        )

        LocalSnapshotSection(uiState = uiState)

        SessionActionSection(
            uiState = uiState,
            hasNearbyWifiPermission = hasNearbyWifiPermission,
            onRequestPermission = onRequestPermission,
            onStartSession = onStartSession,
            onStopSession = onStopSession
        )

        DeviceListSection(
            uiState = uiState,
            onSyncDevice = onSyncDevice
        )

        YikeOperationFeedback(
            successMessage = uiState.message,
            errorMessage = uiState.errorMessage,
            successTitle = "同步完成",
            errorTitle = "同步失败"
        )
        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
    }
}

/** 本机摘要单独展示，是为了让用户在发起覆盖同步前先明确知道自己当前设备上有哪些内容。 */
@Composable
private fun LocalSnapshotSection(
    uiState: LanSyncUiState
) {
    val snapshot = uiState.localSnapshot
    YikeStateBanner(
        title = uiState.localDeviceName,
        description = if (snapshot == null) {
            "尚未读取本机摘要。"
        } else {
            "${snapshot.deckCount} 个卡组 · ${snapshot.cardCount} 张卡片 · ${snapshot.questionCount} 个问题"
        }
    ) {
        if (snapshot?.lastBackupAt != null) {
            YikeSecondaryButton(
                text = "最近备份：${formatLocalDateTime(snapshot.lastBackupAt)}",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false
            )
        }
    }
}

/** 发现会话按钮把权限申请和开启发现合并到同一区块，是为了让用户始终知道当前缺的是哪一步。 */
@Composable
private fun SessionActionSection(
    uiState: LanSyncUiState,
    hasNearbyWifiPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit
) {
    when {
        !hasNearbyWifiPermission -> {
            YikeStateBanner(
                title = "需要附近 Wi-Fi 设备权限",
                description = "Android 13 及以上需要授权后才能发现同一局域网里的其他设备。"
            ) {
                YikePrimaryButton(
                    text = "授权并开始发现",
                    onClick = onRequestPermission,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        uiState.isSessionActive -> {
            YikeStateBanner(
                title = if (uiState.isPreparing) "正在启动发现" else "正在发现设备",
                description = "保持当前页面打开时，本机会广播自身并持续发现同一 Wi-Fi 下的其他设备。"
            ) {
                YikeSecondaryButton(
                    text = "停止发现",
                    onClick = onStopSession,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isPreparing && !uiState.isSyncing
                )
            }
        }

        else -> {
            YikeStateBanner(
                title = "同步尚未启动",
                description = "开始发现后，本机会广播同步服务并显示同一局域网内可用设备。"
            ) {
                YikePrimaryButton(
                    text = "开始发现设备",
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isPreparing
                )
            }
        }
    }
}

/** 设备列表只在会话激活后展示，是为了让用户明确理解“列表为空”来自局域网现状而不是页面故障。 */
@Composable
private fun DeviceListSection(
    uiState: LanSyncUiState,
    onSyncDevice: (SyncDevice) -> Unit
) {
    when {
        !uiState.isSessionActive -> {
            YikeStateBanner(
                title = "等待开始发现",
                description = "启动局域网同步后，这里会显示同一 Wi-Fi 下正在广播的忆刻设备。"
            )
        }

        uiState.devices.isEmpty() -> {
            YikeStateBanner(
                title = "暂未发现其他设备",
                description = "请确认另一台设备也已打开局域网同步页，并连接到同一 Wi-Fi。"
            )
        }

        else -> {
            uiState.devices.forEach { device ->
                YikeListItemCard(
                    title = device.deviceName,
                    summary = "${device.hostAddress}:${device.port}",
                    supporting = "最后出现：${formatLocalDateTime(device.lastSeenAt)}"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(com.kariscode.yike.ui.theme.LocalYikeSpacing.current.sm)
                    ) {
                        YikePrimaryButton(
                            text = "同步到本机",
                            onClick = { onSyncDevice(device) },
                            modifier = Modifier.weight(1f),
                            enabled = !uiState.isPreparing && !uiState.isSyncing
                        )
                    }
                }
            }
        }
    }
}

/** 冲突弹窗集中展示本机与远端规模，是为了让用户在确认覆盖前能一眼看清两侧数据体量差异。 */
@Composable
private fun SyncConflictDialog(
    conflict: SyncConflict,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    YikeDangerConfirmationDialog(
        title = "确认覆盖本机数据",
        description = buildString {
            appendLine(conflict.reason)
            appendLine()
            appendLine("远端设备：${conflict.remoteSnapshot.deviceName}")
            appendLine("远端导出时间：${formatLocalDateTime(conflict.remoteSnapshot.exportedAt)}")
            appendLine("远端内容：${conflict.remoteSnapshot.deckCount} 个卡组、${conflict.remoteSnapshot.cardCount} 张卡片、${conflict.remoteSnapshot.questionCount} 个问题")
            appendLine("本机内容：${conflict.localSnapshot.deckCount} 个卡组、${conflict.localSnapshot.cardCount} 张卡片、${conflict.localSnapshot.questionCount} 个问题")
        }.trim(),
        confirmText = "继续覆盖同步",
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}
