package com.kariscode.yike.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 对话框外观收敛到单一入口，是为了让风险确认、输入编辑和同步预览共享同一层级语气，
 * 避免页面直接调用默认 Material3 弹窗后逐渐偏离应用自己的视觉语言。
 */
@Composable
fun YikeAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.extraLarge
    )
}

/**
 * 文本元信息编辑在卡组和卡片页里属于同一类“轻量就地维护”交互，
 * 抽成共享弹窗后可以确保校验提示、按钮层级和字段节奏始终保持一致。
 */
@Composable
fun YikeTextMetadataDialog(
    title: String,
    primaryLabel: String,
    primaryValue: String,
    onPrimaryValueChange: (String) -> Unit,
    secondaryLabel: String,
    secondaryValue: String,
    onSecondaryValueChange: (String) -> Unit,
    validationMessage: String?,
    extraContent: @Composable ColumnScope.() -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    YikeAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.md)) {
                OutlinedTextField(
                    value = primaryValue,
                    onValueChange = onPrimaryValueChange,
                    label = { Text(primaryLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secondaryValue,
                    onValueChange = onSecondaryValueChange,
                    label = { Text(secondaryLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                extraContent()
                validationMessage?.let { message ->
                    Text(text = message)
                }
            }
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
 * 单字段输入弹窗作为共享组件收敛后，页面只需要表达文案与校验语义，
 * 不必在每个流程页里重复维护同一套 AlertDialog 和输入框骨架。
 */
@Composable
fun YikeSingleInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    validationMessage: String? = null,
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    YikeAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.md)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                validationMessage?.let { message ->
                    Text(text = message)
                }
            }
        },
        confirmButton = {
            YikePrimaryButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = dismissText, onClick = onDismiss)
        }
    )
}

/**
 * 不可逆删除在多个页面都需要同样明确的风险提示，
 * 共享确认弹窗可以减少文案和按钮语义在不同页面里逐渐漂移的维护成本。
 */
@Composable
fun YikeDangerConfirmationDialog(
    title: String,
    description: String,
    confirmText: String,
    dismissText: String = "取消",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    YikeAlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            YikeDangerButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = dismissText, onClick = onDismiss)
        }
    )
}
