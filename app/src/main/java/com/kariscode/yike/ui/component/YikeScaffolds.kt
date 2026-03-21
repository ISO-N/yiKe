package com.kariscode.yike.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kariscode.yike.navigation.YikeDestination
import com.kariscode.yike.ui.theme.LocalYikeSpacing

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
    floatingActionButton: @Composable (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottomPadding = navigationBottomPadding + 112.dp
    val contentBlurOverlayHeight = navigationBottomPadding + 148.dp
    val fabBottomPadding = navigationBottomPadding + 68.dp
    val contentGraphicsLayer = rememberGraphicsLayer()

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
                    modifier = Modifier
                        .fillMaxSize()
                        .yikeBottomContentBlur(
                            graphicsLayer = contentGraphicsLayer,
                            overlayHeight = contentBlurOverlayHeight,
                            tintColor = MaterialTheme.colorScheme.surface
                        )
                ) {
                    content(PaddingValues(bottom = contentBottomPadding))
                }
            }

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
 * 一级导航被提升成共享壳层，是为了让底部导航在一级页面切换时保持静止，
 * 只让内容区发生位移，而不是每个目的地各自带着一份导航一起滑来滑去。
 */
@Composable
fun YikePrimaryNavigationChrome(
    currentDestination: YikePrimaryDestination,
    onNavigate: (YikePrimaryDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBottomOffset = navigationBarPadding + 2.dp

    Box(modifier = modifier.fillMaxSize()) {
        YikeBottomNavigation(
            currentDestination = currentDestination,
            onNavigate = onNavigate,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = navigationBottomOffset)
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
    val navigationShape = RoundedCornerShape(22.dp)
    Surface(
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f),
                shape = navigationShape
            ),
        shape = navigationShape
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
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            Color.Transparent
                        },
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
 * 一级页底部内容需要在导航开始前进入朦胧层级，
 * 因此这里直接对底部一段内容重绘模糊版本，让导航上缘不再像“另起一块底板”。
 */
private fun Modifier.yikeBottomContentBlur(
    graphicsLayer: GraphicsLayer,
    overlayHeight: Dp,
    tintColor: Color
): Modifier {
    return drawWithContent {
        graphicsLayer.record {
            this@drawWithContent.drawContent()
        }
        drawLayer(graphicsLayer)
        val overlayHeightPx = overlayHeight.toPx().coerceAtMost(size.height)
        if (overlayHeightPx <= 0f) {
            return@drawWithContent
        }
        val overlayTop = size.height - overlayHeightPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val previousRenderEffect = graphicsLayer.renderEffect
            graphicsLayer.renderEffect = BlurEffect(
                renderEffect = null,
                radiusX = 20f,
                radiusY = 20f,
                edgeTreatment = TileMode.Decal
            )
            clipRect(left = 0f, top = overlayTop, right = size.width, bottom = size.height) {
                drawLayer(graphicsLayer)
            }
            graphicsLayer.renderEffect = previousRenderEffect
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    tintColor.copy(alpha = 0f),
                    tintColor.copy(alpha = 0.08f),
                    tintColor.copy(alpha = 0.18f),
                    tintColor.copy(alpha = 0.3f)
                ),
                startY = overlayTop,
                endY = size.height
            ),
            topLeft = androidx.compose.ui.geometry.Offset(x = 0f, y = overlayTop),
            size = androidx.compose.ui.geometry.Size(width = size.width, height = overlayHeightPx)
        )
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
        title.isNotBlank() && title != eyebrow -> title
        subtitle.isNotBlank() -> subtitle
        title.isNotBlank() -> title
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
 * 多个页面都依赖相同的纵向滚动骨架来承载状态卡、列表块和按钮区，
 * 因此抽成共享容器后可以减少样板代码，并保持页面间的滚动与间距节奏一致。
 */
@Composable
fun YikeScrollableColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(LocalYikeSpacing.current.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    val tailHeight = contentPadding.calculateBottomPadding()
    Column(
        modifier = modifier
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection)
            )
            .verticalScroll(rememberScrollState()),
        verticalArrangement = verticalArrangement
    ) {
        content()
        if (tailHeight > 0.dp) {
            YikeScrollableTailBlock(height = tailHeight)
        }
    }
}

/**
 * 滚动内容尾部补一段真实延展块，是为了让悬浮导航覆盖的区域下方仍然属于内容流的一部分，
 * 而不是只剩一段空白 padding，滚到底时看起来像被硬生生截断。
 */
@Composable
private fun YikeScrollableTailBlock(
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.08f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.26f)
                    )
                ),
                shape = RoundedCornerShape(28.dp)
            )
    )
}

