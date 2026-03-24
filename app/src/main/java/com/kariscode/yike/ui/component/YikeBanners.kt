package com.kariscode.yike.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 状态区块统一承载加载、空、成功和错误提示，避免各页面用不同文案层级表达相同状态。
 */
@Composable
fun YikeStateBanner(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val spacing = LocalYikeSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
                    shape = MaterialTheme.shapes.large
                )
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (trailing != null) {
                    Spacer(modifier = Modifier.width(spacing.md))
                    trailing()
                }
            }
            content()
        }
    }
}

/**
 * 操作结果反馈经常以“成功/失败二选一”的形式成对出现，抽成共享组件后可以让页面只表达文案差异，
 * 避免在设置、备份等低频操作页反复手写相同的条件渲染模板。
 */
@Composable
fun YikeOperationFeedback(
    successMessage: String?,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    successTitle: String = "操作已完成",
    errorTitle: String = "操作失败"
) {
    successMessage?.let { message ->
        YikeStateBanner(
            title = successTitle,
            description = message,
            modifier = modifier
        )
    }
    errorMessage?.let { message ->
        YikeStateBanner(
            title = errorTitle,
            description = message,
            modifier = modifier
        )
    }
}

/**
 * 标签胶囊把“当前状态/数量提醒”压缩到统一样式里，避免不同页面各自造不同 badge。
 */
@Composable
fun YikeBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor ?: MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

