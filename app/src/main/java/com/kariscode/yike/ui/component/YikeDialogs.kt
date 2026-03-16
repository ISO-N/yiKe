package com.kariscode.yike.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.ui.theme.LocalYikeSpacing

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
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
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
 * 不可逆删除在多个页面都需要同样明确的风险提示，
 * 共享确认弹窗可以减少文案和按钮语义在不同页面里逐渐漂移的维护成本。
 */
@Composable
fun YikeDangerConfirmationDialog(
    title: String,
    description: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(description) },
        confirmButton = {
            YikeDangerButton(text = confirmText, onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}
