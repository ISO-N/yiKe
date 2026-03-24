package com.kariscode.yike.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import com.kariscode.yike.ui.theme.YikeThemeTokens

/**
 * Hero 卡承担“当前页面最重要的信息块”，这样首页待复习、卡组总览和设置状态都能共享同一层级。
 */
@Composable
fun YikeHeroCard(
    eyebrow: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val spacing = LocalYikeSpacing.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            YikeThemeTokens.chromeColors.heroGradientStart,
                            YikeThemeTokens.chromeColors.heroGradientEnd
                        )
                    )
                )
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            YikeHeaderBlock(eyebrow = eyebrow, title = title, subtitle = description)
            content()
        }
    }
}

/**
 * 普通信息卡用于承载列表项、表单块和操作区，让各页面在视觉节奏上保持统一。
 */
@Composable
fun YikeSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalYikeSpacing.current
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            content = content
        )
    }
}

/**
 * 危险提示卡始终使用暖色层级，是为了让删除、恢复等不可逆动作在视觉上立刻可分辨。
 */
@Composable
fun YikeWarningCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = YikeThemeTokens.semanticColors.warningContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = YikeThemeTokens.semanticColors.onWarningContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = YikeThemeTokens.semanticColors.onWarningContainer
            )
        }
    }
}

/**
 * 指标卡将数量信息封装成统一视觉样式，能让首页、卡组和复习进度共享同一种统计表达方式。
 */
@Composable
fun YikeMetricCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 列表项卡统一标题、摘要和操作区布局，是为了降低卡组、卡片和设置项之间的视觉漂移。
 */
@Composable
fun YikeListItemCard(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,
    supporting: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {}
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (badge != null) {
                Spacer(modifier = Modifier.width(spacing.md))
                badge()
            }
        }
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            content = actions
        )
    }
}

