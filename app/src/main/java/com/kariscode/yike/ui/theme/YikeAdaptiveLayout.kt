package com.kariscode.yike.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 自适应布局令牌把断点结果翻译成页面真正会消费的尺寸语义，
 * 是为了让各页面围绕“内容宽度、边距、二维码尺寸”协同变化，而不是继续散落硬编码常量。
 */
@Immutable
data class YikeAdaptiveLayout(
    val windowWidthSizeClass: WindowWidthSizeClass,
    val horizontalPadding: Dp,
    val maxContentWidth: Dp,
    val primaryContentBottomInset: Dp,
    val primaryFabBottomInset: Dp,
    val bottomNavigationIconSize: Dp,
    val qrCodeSize: Dp
)

/**
 * 预览和测试在没有显式注入断点信息时仍要保持可运行，因此提供紧凑屏默认值作为回退。
 */
val LocalYikeAdaptiveLayout = staticCompositionLocalOf {
    yikeAdaptiveLayoutFor(WindowWidthSizeClass.Compact)
}

/**
 * 断点到布局令牌的映射集中在单点后，后续页面只需要消费语义尺寸，而不必知道具体断点策略。
 */
fun yikeAdaptiveLayoutFor(
    windowWidthSizeClass: WindowWidthSizeClass
): YikeAdaptiveLayout = when (windowWidthSizeClass) {
    WindowWidthSizeClass.Compact -> YikeAdaptiveLayout(
        windowWidthSizeClass = windowWidthSizeClass,
        horizontalPadding = 20.dp,
        maxContentWidth = 680.dp,
        primaryContentBottomInset = 110.dp,
        primaryFabBottomInset = 68.dp,
        bottomNavigationIconSize = 20.dp,
        qrCodeSize = 168.dp
    )
    WindowWidthSizeClass.Medium -> YikeAdaptiveLayout(
        windowWidthSizeClass = windowWidthSizeClass,
        horizontalPadding = 28.dp,
        maxContentWidth = 840.dp,
        primaryContentBottomInset = 118.dp,
        primaryFabBottomInset = 76.dp,
        bottomNavigationIconSize = 22.dp,
        qrCodeSize = 188.dp
    )
    else -> YikeAdaptiveLayout(
        windowWidthSizeClass = windowWidthSizeClass,
        horizontalPadding = 36.dp,
        maxContentWidth = 960.dp,
        primaryContentBottomInset = 126.dp,
        primaryFabBottomInset = 84.dp,
        bottomNavigationIconSize = 24.dp,
        qrCodeSize = 208.dp
    )
}

/**
 * 统一入口暴露自适应令牌，是为了让调用方沿用主题 token 的读取方式，不额外记忆新的 Local 名称。
 */
object YikeAdaptiveTokens {
    val layout: YikeAdaptiveLayout
        @Composable get() = LocalYikeAdaptiveLayout.current
}
