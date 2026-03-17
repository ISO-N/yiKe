package com.kariscode.yike.feature.sync

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeWarningCard
import com.kariscode.yike.ui.component.backNavigationAction

/**
 * 局域网同步页独立成高风险流程页，是为了把配对、预览和冲突确认这些全局操作与普通设置项隔离开。
 */
@Composable
fun LanSyncScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<LanSyncViewModel>(
        factory = LanSyncViewModel.factory(lanSyncRepository = container.lanSyncRepository)
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

    /**
     * 权限就绪后自动启动发现，可以让用户进入同步页时立即看到局域网设备，而不是先做一次无意义点击。
     */
    LaunchedEffect(hasNearbyWifiPermission) {
        if (hasNearbyWifiPermission) {
            viewModel.onPermissionReady()
        }
    }

    YikeFlowScaffold(
        title = "局域网同步",
        subtitle = "在同一 Wi-Fi 下发现设备，配对后同步学习数据。",
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
            onPeerClick = viewModel::onPeerClick,
            onEditLocalName = viewModel::onEditLocalName,
            onCancelActiveSync = viewModel::onCancelActiveSync,
            modifier = modifier,
            contentPadding = padding
        )
    }

    uiState.pendingPairingPeer?.let { peer ->
        PairingDialog(
            peer = peer,
            pairingCode = uiState.pairingCodeInput,
            onPairingCodeChange = viewModel::onPairingCodeChange,
            onDismiss = viewModel::onDismissPairing,
            onConfirm = viewModel::onConfirmPairing
        )
    }

    uiState.pendingPreview?.let { preview ->
        PreviewDialog(
            preview = preview,
            onDismiss = viewModel::onDismissPreview,
            onConfirm = viewModel::onConfirmPreview
        )
    }

    if (uiState.showConflictDialog) {
        ConflictDialog(
            uiState = uiState,
            onChoiceChange = viewModel::onConflictChoiceChange,
            onDismiss = viewModel::onDismissConflicts,
            onConfirm = viewModel::onConfirmConflicts
        )
    }

    if (uiState.isEditingLocalName) {
        LocalNameDialog(
            value = uiState.localNameInput,
            onValueChange = viewModel::onLocalNameInputChange,
            onDismiss = viewModel::onDismissLocalNameEditor,
            onConfirm = viewModel::onSaveLocalName
        )
    }
}

/**
 * 页面主体只围绕“本机身份 -> 会话状态 -> 设备列表 -> 结果反馈”排序，
 * 是为了让用户先理解自己是谁、当前是否在线，再决定和谁同步。
 */
@Composable
private fun LanSyncContent(
    uiState: LanSyncUiState,
    hasNearbyWifiPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onPeerClick: (LanSyncPeer) -> Unit,
    onEditLocalName: () -> Unit,
    onCancelActiveSync: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    YikeScrollableColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        YikeWarningCard(
            title = "同步前先确认",
            description = "首次连接需要输入对方配对码；开始同步前会先显示本次变化，如果同一内容两台设备都改过，会要求你确认保留哪一边。"
        )

        LocalProfileSection(
            uiState = uiState,
            onEditLocalName = onEditLocalName
        )

        SessionSection(
            uiState = uiState,
            hasNearbyWifiPermission = hasNearbyWifiPermission,
            onRequestPermission = onRequestPermission,
            onStartSession = onStartSession,
            onStopSession = onStopSession,
            onCancelActiveSync = onCancelActiveSync
        )

        DeviceListSection(
            peers = uiState.session.peers,
            isSessionActive = uiState.session.isSessionActive,
            onPeerClick = onPeerClick
        )

        YikeOperationFeedback(
            successMessage = uiState.session.message,
            errorMessage = uiState.session.activeFailure?.let(::failureMessage),
            successTitle = "同步成功",
            errorTitle = "同步失败"
        )
    }
}

/**
 * 本机身份卡片把设备名、短 ID 和配对码放在一起，是为了让用户在同一处完成“确认我是谁”和“告诉对方我的配对码”。
 */
@Composable
private fun LocalProfileSection(
    uiState: LanSyncUiState,
    onEditLocalName: () -> Unit
) {
    YikeStateBanner(
        title = uiState.session.localProfile.displayName,
        description = "设备 ID：${uiState.session.localProfile.shortDeviceId} · 配对码：${uiState.session.localProfile.pairingCode}"
    ) {
        YikeSecondaryButton(
            text = "修改设备名",
            onClick = onEditLocalName,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 会话区块把权限、发现状态和运行进度集中展示，是为了让用户始终知道当前缺的是权限、发现还是同步执行。
 */
@Composable
private fun SessionSection(
    uiState: LanSyncUiState,
    hasNearbyWifiPermission: Boolean,
    onRequestPermission: () -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onCancelActiveSync: () -> Unit
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

        !uiState.session.isSessionActive -> {
            YikeStateBanner(
                title = "同步尚未启动",
                description = "开始会话后，本机会广播自身并持续发现同一 Wi-Fi 下可同步的设备。"
            ) {
                YikePrimaryButton(
                    text = "开始发现设备",
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        else -> {
            YikeStateBanner(
                title = stageTitle(uiState.session.progress.stage),
                description = uiState.session.progress.message
            ) {
                if (uiState.session.progress.stage != LanSyncStage.IDLE) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(com.kariscode.yike.ui.theme.LocalYikeSpacing.current.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(com.kariscode.yike.ui.theme.LocalYikeSpacing.current.sm)
                ) {
                    YikeSecondaryButton(
                        text = "停止发现",
                        onClick = onStopSession,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.session.progress.stage != LanSyncStage.APPLYING
                    )
                    YikeSecondaryButton(
                        text = "取消同步",
                        onClick = onCancelActiveSync,
                        modifier = Modifier.weight(1f),
                        enabled = uiState.session.progress.stage == LanSyncStage.TRANSFERRING ||
                            uiState.session.progress.stage == LanSyncStage.PREVIEWING
                    )
                }
            }
        }
    }
}

/**
 * 设备列表直接展示信任和健康状态，是为了让用户在点击前就知道这次是“先配对”还是“可直接预览同步”。
 */
@Composable
private fun DeviceListSection(
    peers: List<LanSyncPeer>,
    isSessionActive: Boolean,
    onPeerClick: (LanSyncPeer) -> Unit
) {
    when {
        !isSessionActive -> {
            YikeStateBanner(
                title = "等待开始发现",
                description = "会话启动后，这里会显示同一局域网内可同步的设备。"
            )
        }

        peers.isEmpty() -> {
            YikeStateBanner(
                title = "暂未发现其他设备",
                description = "请确认另一台设备也已打开局域网同步页，并连接到同一 Wi-Fi。"
            )
        }

        else -> {
            peers.forEach { peer ->
                YikeListItemCard(
                    title = peer.displayName,
                    summary = "${peer.shortDeviceId} · ${peer.hostAddress}:${peer.port}",
                    supporting = "信任：${peer.trustState.name} · 状态：${peer.health.name}"
                ) {
                    YikePrimaryButton(
                        text = if (peer.trustState == com.kariscode.yike.domain.model.LanSyncTrustState.UNTRUSTED) {
                            "输入配对码"
                        } else {
                            "查看同步内容"
                        },
                        onClick = { onPeerClick(peer) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * 首次配对弹窗单独承载输入，是为了把“建立信任”从真正同步步骤中拆开，降低一次弹窗里塞太多决策的负担。
 */
@Composable
private fun PairingDialog(
    peer: LanSyncPeer,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "输入 ${peer.displayName} 的配对码") },
        text = {
            OutlinedTextField(
                value = pairingCode,
                onValueChange = onPairingCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "6 位配对码") }
            )
        },
        confirmButton = {
            YikePrimaryButton(text = "继续预览", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}

/**
 * 预览弹窗只负责让用户确认影响规模，是为了在真正开始传输前先明确这次同步会处理多少双向变更。
 */
@Composable
private fun PreviewDialog(
    preview: LanSyncPreview,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "同步预览") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(com.kariscode.yike.ui.theme.LocalYikeSpacing.current.sm)) {
                Text(text = "设备：${preview.peer.displayName}")
                Text(text = "本机待上传：${preview.localChangeCount}")
                Text(text = "远端待下载：${preview.remoteChangeCount}")
                Text(text = "设置变更：${preview.settingsChangeCount}")
                Text(text = "冲突数量：${preview.conflicts.size}")
                if (preview.isFirstPairing) {
                    Text(text = "这是首次配对后的第一次同步。")
                }
            }
        },
        confirmButton = {
            YikePrimaryButton(
                text = if (preview.conflicts.isEmpty()) "开始同步" else "处理冲突并继续",
                onClick = onConfirm
            )
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}

/**
 * 冲突弹窗把每个对象的决议显式列出来，是为了避免“默认保留哪一边”变成用户看不见的隐式规则。
 */
@Composable
private fun ConflictDialog(
    uiState: LanSyncUiState,
    onChoiceChange: (String, LanSyncConflictChoice) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val preview = uiState.pendingPreview ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认冲突决议") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(com.kariscode.yike.ui.theme.LocalYikeSpacing.current.sm)) {
                preview.conflicts.forEach { conflict ->
                    val key = "${conflict.entityType.name}:${conflict.entityId}"
                    Text(text = "${conflict.summary} · ${conflict.reason}")
                    ConflictChoiceRow(
                        label = "保留本机",
                        selected = uiState.conflictChoices[key] == LanSyncConflictChoice.KEEP_LOCAL,
                        onSelect = { onChoiceChange(key, LanSyncConflictChoice.KEEP_LOCAL) }
                    )
                    ConflictChoiceRow(
                        label = "保留远端",
                        selected = uiState.conflictChoices[key] == LanSyncConflictChoice.KEEP_REMOTE,
                        onSelect = { onChoiceChange(key, LanSyncConflictChoice.KEEP_REMOTE) }
                    )
                    ConflictChoiceRow(
                        label = "本次跳过",
                        selected = uiState.conflictChoices[key] == LanSyncConflictChoice.SKIP,
                        onSelect = { onChoiceChange(key, LanSyncConflictChoice.SKIP) }
                    )
                }
            }
        },
        confirmButton = {
            YikePrimaryButton(text = "按以上决议同步", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "返回预览", onClick = onDismiss)
        }
    )
}

/**
 * 单条冲突决议行提取成小组件，是为了避免三个选项的可点击区域在弹窗里变得难以对齐和复用。
 */
@Composable
private fun ConflictChoiceRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(text = label)
    }
}

/**
 * 设备名编辑对话框只暴露一个字段，是为了让本机身份修改成为低风险、低心智负担的单步操作。
 */
@Composable
private fun LocalNameDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "修改本机设备名") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "设备名") }
            )
        },
        confirmButton = {
            YikePrimaryButton(text = "保存", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}

/**
 * 阶段标题单独映射后，页面文案可以围绕稳定状态机演进，而不必在多个 composable 里散落判断分支。
 */
private fun stageTitle(stage: LanSyncStage): String = when (stage) {
    LanSyncStage.IDLE -> "等待开始发现"
    LanSyncStage.DISCOVERING -> "正在发现设备"
    LanSyncStage.PAIRING -> "正在建立信任"
    LanSyncStage.PREVIEWING -> "正在生成预览"
    LanSyncStage.TRANSFERRING -> "正在传输同步数据"
    LanSyncStage.APPLYING -> "正在应用远端变更"
    LanSyncStage.COMPLETED -> "同步完成"
    LanSyncStage.FAILED -> "同步失败"
    LanSyncStage.CANCELLED -> "同步已取消"
}

/**
 * 失败原因映射保持克制，是为了让用户先知道下一步该做什么，而不是看到过多底层网络术语。
 */
private fun failureMessage(reason: com.kariscode.yike.domain.model.LanSyncFailureReason): String = when (reason) {
    com.kariscode.yike.domain.model.LanSyncFailureReason.DISCOVERY_FAILED -> "设备发现失败，请确认处于同一 Wi-Fi。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.PAIRING_FAILED -> "配对失败，请确认配对码是否正确。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.PROTOCOL_MISMATCH -> "对方设备版本不兼容，请先升级。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.AUTH_FAILED -> "设备认证失败，请重新配对。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.NETWORK_TIMEOUT -> "网络超时，请稍后重试。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.HASH_MISMATCH -> "数据校验失败，请重新同步。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.APPLY_FAILED -> "应用远端变更失败，请稍后重试。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.CANCELLED -> "同步已取消。"
    com.kariscode.yike.domain.model.LanSyncFailureReason.UNKNOWN -> "局域网同步失败，请稍后重试。"
}
