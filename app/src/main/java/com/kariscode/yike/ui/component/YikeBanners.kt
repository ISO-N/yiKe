package com.kariscode.yike.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    leading: @Composable (RowScope.() -> Unit)? = null,
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    leading?.invoke(this)
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
 * 加载态补上旋转指示器，是为了让“正在请求中”和“只是页面上有一段说明文案”能被用户一眼区分，
 * 减少长耗时场景里对是否卡住的误判。
 */
@Composable
fun YikeLoadingBanner(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    YikeStateBanner(
        title = title,
        description = description,
        modifier = modifier,
        trailing = {
            CircularProgressIndicator(
                strokeWidth = 2.dp
            )
        }
    )
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
        modifier = modifier.semantics {
            contentDescription = text
        },
        shape = RoundedCornerShape(percent = 50),
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

/**
 * 内联错误提示统一带上错误语义色和图标，是为了让弹窗与表单里的校验反馈不再像普通说明文字一样被忽略。
 */
@Composable
fun YikeInlineErrorMessage(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
                shape = MaterialTheme.shapes.medium
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * 空状态插画先用统一图标位承接，是为了让各列表页在没有内容时依然保留明确视觉锚点，
 * 不再只剩下一段孤立文字。
 */
@Composable
fun YikeEmptyStateIcon(
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Outlined.Inbox,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.primary
    )
}

/**
 * 骨架块抽成共享组件后，列表页和首页可以在加载时先稳定占位，
 * 避免真实内容到达前出现跳闪或“像白屏”的空窗期。
 */
@Composable
fun YikeSkeletonBlock(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 20.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.medium
            )
    )
}

