package com.kariscode.yike.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kariscode.yike.navigation.YikeDestination
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import com.kariscode.yike.ui.theme.YikeBestContainer
import com.kariscode.yike.ui.theme.YikeCriticalContainer
import com.kariscode.yike.ui.theme.YikeSuccessContainer
import com.kariscode.yike.ui.theme.YikeSurfaceContainerHigh
import com.kariscode.yike.ui.theme.YikeSurfaceContainerHighest
import com.kariscode.yike.ui.theme.YikeSurfaceTint
import com.kariscode.yike.ui.theme.YikeTextMuted
import com.kariscode.yike.ui.theme.YikeWarningContainer

/**
 * 一级入口目标集中定义，是为了让首页、卡组和设置在切换时共享同一套底部导航语义。
 */
enum class YikePrimaryDestination(
    val route: String,
    val label: String
) {
    HOME(route = YikeDestination.HOME, label = "首页"),
    DECKS(route = YikeDestination.DECK_LIST, label = "卡组"),
    SETTINGS(route = YikeDestination.SETTINGS, label = "设置")
}

/**
 * 页面背景统一提供柔和渐变，是为了把原型里的手机端氛围层稳定沉淀到所有页面里。
 */
@Composable
fun YikeScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    ),
                    radius = 950f
                )
            ),
        content = content
    )
}

/**
 * 一级导航壳把顶部信息层级、底部导航和可选 FAB 收敛到一起，
 * 这样首页、卡组和设置可以共享一致的手机端壳层体验。
 */
@Composable
fun YikePrimaryScaffold(
    currentDestination: YikePrimaryDestination,
    title: String,
    subtitle: String,
    onNavigate: (YikePrimaryDestination) -> Unit,
    floatingActionButton: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = navigationBarPadding + 88.dp
    val fabBottomPadding = navigationBarPadding + 76.dp

    YikeScreenBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                    .padding(horizontal = 16.dp)
                    .padding(top = 0.dp)
            ) {
                YikePrimaryHeaderBlock(
                    eyebrow = currentDestination.label,
                    title = title,
                    subtitle = subtitle
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    content(PaddingValues(bottom = contentBottomPadding))
                }
            }

            YikeBottomNavigation(
                currentDestination = currentDestination,
                onNavigate = onNavigate,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navigationBarPadding + 2.dp)
            )

            if (floatingActionButton != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = fabBottomPadding)
                ) {
                    floatingActionButton()
                }
            }
        }
    }
}

/**
 * 流内页面不复用底部导航，是为了让复习、编辑、备份这类任务保持聚焦，不被一级入口打断。
 */
@Composable
fun YikeFlowScaffold(
    title: String,
    subtitle: String,
    navigationAction: NavigationAction? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            YikeTopAppBar(
                title = title,
                subtitle = subtitle,
                navigationAction = navigationAction,
                actionText = actionText,
                onActionClick = onActionClick
            )
        }
    ) { padding ->
        YikeScreenBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
            ) {
                content(padding)
            }
        }
    }
}

/**
 * 共享标题块使用原型里的 eyebrow + 主标题层级，
 * 能让一级入口在不同业务状态下依旧保持熟悉的信息节奏。
 */
@Composable
fun YikeHeaderBlock(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 一级页头改成更轻的两行结构，是为了把“当前所在页面”和“本页意图”交代清楚后，
 * 立刻把视觉主导权还给下面的内容卡片，而不是继续占掉首屏高度。
 */
@Composable
private fun YikePrimaryHeaderBlock(
    eyebrow: String,
    title: String,
    subtitle: String
) {
    val supportingText = when {
        title.isNotBlank() -> title
        subtitle.isNotBlank() -> subtitle
        else -> null
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

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
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
                            MaterialTheme.colorScheme.surface
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
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
        shape = RoundedCornerShape(24.dp),
        color = YikeSurfaceContainerHigh,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
        shape = RoundedCornerShape(24.dp),
        color = YikeWarningContainer
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
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
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
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
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
 * 标签胶囊把“当前状态/数量提醒”压缩到统一样式里，避免不同页面各自造不同 badge。
 */
@Composable
fun YikeBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = YikeSurfaceContainerHighest,
    contentColor: Color = YikeTextMuted
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
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
    actions: @Composable RowScope.() -> Unit = {}
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            content = actions
        )
    }
}

/**
 * 主按钮承担页面最重要动作，统一封装后能让异步状态和层级关系在各页面保持一致。
 */
@Composable
fun YikePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text = text)
    }
}

/**
 * 次按钮统一用于辅助操作，避免“返回/浏览/取消”在不同页面使用不一致的样式语义。
 */
@Composable
fun YikeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 危险按钮把高风险操作明确染成错误语义，是为了降低删除和恢复类操作的误触概率。
 */
@Composable
fun YikeDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(text = text)
    }
}

/**
 * 评分按钮根据掌握度切换色阶，是为了让“完全不会/很轻松”在视觉上立刻形成强弱区分。
 */
@Composable
fun YikeRatingButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 统一 FAB 样式能让新增卡组和新增卡片保持同一种“主入口但不压过页面主任务”的语气。
 */
@Composable
fun YikeFab(
    text: String,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(text)
    }
}

/**
 * 进度条被单独封装，是为了让首页节奏、复习完成度和后续异步状态都能复用同一视觉表达。
 */
@Composable
fun YikeProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(YikeSurfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                        )
                    )
                )
        )
    }
}

/**
 * 底部导航使用文本 pill 风格，是为了贴合原型里强调当前入口高亮的手机端语气。
 */
@Composable
private fun YikeBottomNavigation(
    currentDestination: YikePrimaryDestination,
    onNavigate: (YikePrimaryDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            YikePrimaryDestination.entries.forEach { destination ->
                val selected = destination == currentDestination
                TextButton(
                    onClick = { onNavigate(destination) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (selected) YikeSurfaceTint else Color.Transparent,
                        contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(text = destination.label, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * 评分色板集中定义，是为了让复习页调用处只表达语义，而不重复散落具体颜色常量。
 */
object YikeRatingPalette {
    val criticalContainer: Color = YikeCriticalContainer
    val warningContainer: Color = YikeWarningContainer
    val successContainer: Color = YikeSuccessContainer
    val bestContainer: Color = YikeBestContainer
}
