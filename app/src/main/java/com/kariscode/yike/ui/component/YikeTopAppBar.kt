package com.kariscode.yike.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import com.kariscode.yike.ui.theme.YikeThemeTokens

/**
 * Material3 的 TopAppBar 目前仍处于实验 API，集中封装能把 opt-in 控制在单点，
 * 避免在每个页面到处散落 @OptIn 注解导致后续调整 AppBar 样式时遗漏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YikeTopAppBar(
    title: String,
    subtitle: String? = null,
    navigationAction: NavigationAction? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val spacing = YikeThemeTokens.spacing
    TopAppBar(
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(text = title)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        navigationIcon = {
            if (navigationAction != null) {
                if (navigationAction.label.isNullOrBlank()) {
                    IconButton(
                        onClick = navigationAction.onClick
                    ) {
                        Icon(
                            imageVector = navigationAction.icon,
                            contentDescription = navigationAction.contentDescription
                        )
                    }
                } else {
                    TextButton(onClick = navigationAction.onClick) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Icon(
                                imageVector = navigationAction.icon,
                                contentDescription = null
                            )
                            Text(text = navigationAction.label)
                        }
                    }
                }
            }
        },
        actions = {
            if (!actionText.isNullOrBlank() && onActionClick != null) {
                TextButton(onClick = onActionClick) {
                    Text(actionText)
                }
            }
        }
    )
}

/**
 * 通过显式的导航动作模型，能让“返回/退出”等语义在 UI 层保持一致，
 * 避免以后出现同一页面用图标、另一页面用文字导致交互不统一。
 */
data class NavigationAction(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    val label: String? = null,
    val onClick: () -> Unit
)

/**
 * 统一返回动作的图标与语义，是为了让各页面共享一致的顶部返回体验，
 * 同时默认收敛成更轻的纯图标入口，避免“返回上级”这类文案在紧凑顶栏里显得过重。
 */
fun backNavigationAction(
    onClick: () -> Unit,
    contentDescription: String = "返回",
    label: String? = null
): NavigationAction = NavigationAction(
    contentDescription = contentDescription,
    label = label,
    onClick = onClick
)

