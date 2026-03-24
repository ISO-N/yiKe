package com.kariscode.yike.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import kotlinx.coroutines.delay

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
    TimedFeedbackBanner(
        message = successMessage,
        title = successTitle,
        modifier = modifier,
        autoDismissMillis = 3_000L
    )
    TimedFeedbackBanner(
        message = errorMessage,
        title = errorTitle,
        modifier = modifier,
        autoDismissMillis = 5_000L
    )
}

/**
 * 反馈条在低频操作页只需要短暂提醒用户结果，因此内部托管可见性可以避免消息长期占据首屏。
 */
@Composable
private fun TimedFeedbackBanner(
    message: String?,
    title: String,
    modifier: Modifier = Modifier,
    autoDismissMillis: Long
) {
    var visibleMessage by remember(message) { mutableStateOf(message) }

    LaunchedEffect(visibleMessage) {
        if (visibleMessage != null) {
            delay(autoDismissMillis)
            visibleMessage = null
        }
    }

    AnimatedVisibility(
        visible = visibleMessage != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        YikeStateBanner(
            title = title,
            description = visibleMessage.orEmpty(),
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

