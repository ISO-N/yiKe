package com.kariscode.yike.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 语义色令牌补齐前景色与容器色，是为了让共享组件只按“成功/警示/风险/高掌握度”取色，
 * 而不再各自硬编码一套明暗主题分支。
 */
@Immutable
data class YikeSemanticColors(
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
    val bestContainer: Color,
    val onBestContainer: Color
)

/**
 * 一级壳层和首页装饰统一走 chrome 令牌，是为了把背景氛围、导航选中态和渐变收敛到单一入口。
 */
@Immutable
data class YikeChromeColors(
    val screenGlow: Color,
    val heroGradientStart: Color,
    val heroGradientEnd: Color,
    val navigationContainer: Color,
    val navigationBorder: Color,
    val navigationSelectedContainer: Color,
    val navigationSelectedContent: Color
)

internal val LightSemanticColors = YikeSemanticColors(
    successContainer = YikeSuccessContainer,
    onSuccessContainer = Color(0xFF1F5C22),
    warningContainer = YikeWarningContainer,
    onWarningContainer = Color(0xFF7A4A00),
    criticalContainer = YikeCriticalContainer,
    onCriticalContainer = Color(0xFF7B1E1A),
    bestContainer = YikeBestContainer,
    onBestContainer = Color(0xFF004F47)
)

internal val DarkSemanticColors = YikeSemanticColors(
    successContainer = YikeSuccessContainerDark,
    onSuccessContainer = Color(0xFFC8F2CB),
    warningContainer = YikeWarningContainerDark,
    onWarningContainer = Color(0xFFFFE2B8),
    criticalContainer = YikeCriticalContainerDark,
    onCriticalContainer = Color(0xFFFFDAD6),
    bestContainer = YikeBestContainerDark,
    onBestContainer = Color(0xFFB7F0E7)
)

internal val LightChromeColors = YikeChromeColors(
    screenGlow = YikePrimaryContainer.copy(alpha = YikeChromeAlphaConstants.LIGHT_SCREEN_GLOW),
    heroGradientStart = YikePrimaryContainer.copy(alpha = YikeChromeAlphaConstants.LIGHT_HERO_GRADIENT_START),
    heroGradientEnd = YikeSurface,
    navigationContainer = YikeSurface.copy(alpha = YikeChromeAlphaConstants.LIGHT_NAVIGATION_CONTAINER),
    navigationBorder = YikeOutlineVariant.copy(alpha = YikeChromeAlphaConstants.LIGHT_NAVIGATION_BORDER),
    navigationSelectedContainer = YikePrimary.copy(alpha = YikeChromeAlphaConstants.LIGHT_NAVIGATION_SELECTED_CONTAINER),
    navigationSelectedContent = YikePrimary
)

internal val DarkChromeColors = YikeChromeColors(
    screenGlow = YikePrimaryDark.copy(alpha = YikeChromeAlphaConstants.DARK_SCREEN_GLOW),
    heroGradientStart = YikePrimaryContainerDark.copy(alpha = YikeChromeAlphaConstants.DARK_HERO_GRADIENT_START),
    heroGradientEnd = YikeSurfaceDark,
    navigationContainer = YikeSurfaceContainerDark.copy(alpha = YikeChromeAlphaConstants.DARK_NAVIGATION_CONTAINER),
    navigationBorder = YikeOutlineVariantDark.copy(alpha = YikeChromeAlphaConstants.DARK_NAVIGATION_BORDER),
    navigationSelectedContainer = YikePrimaryDark.copy(alpha = YikeChromeAlphaConstants.DARK_NAVIGATION_SELECTED_CONTAINER),
    navigationSelectedContent = YikePrimaryDark
)

/**
 * Chrome 令牌在动态配色启用时需要跟随当前 `ColorScheme` 一起变化，
 * 这样导航壳、背景辉光和 Hero 渐变才不会停留在旧品牌色导致界面割裂。
 */
internal fun yikeChromeColorsFor(
    colorScheme: ColorScheme,
    isDark: Boolean
): YikeChromeColors = YikeChromeColors(
    screenGlow = colorScheme.primary.copy(
        alpha = if (isDark) YikeChromeAlphaConstants.DYNAMIC_DARK_SCREEN_GLOW else YikeChromeAlphaConstants.DYNAMIC_LIGHT_SCREEN_GLOW
    ),
    heroGradientStart = colorScheme.primaryContainer.copy(
        alpha = if (isDark) YikeChromeAlphaConstants.DYNAMIC_DARK_HERO_GRADIENT_START else YikeChromeAlphaConstants.DYNAMIC_LIGHT_HERO_GRADIENT_START
    ),
    heroGradientEnd = colorScheme.surface,
    navigationContainer = colorScheme.surface.copy(
        alpha = if (isDark) YikeChromeAlphaConstants.DYNAMIC_DARK_NAVIGATION_CONTAINER else YikeChromeAlphaConstants.DYNAMIC_LIGHT_NAVIGATION_CONTAINER
    ),
    navigationBorder = colorScheme.outlineVariant.copy(
        alpha = if (isDark) YikeChromeAlphaConstants.DYNAMIC_DARK_NAVIGATION_BORDER else YikeChromeAlphaConstants.DYNAMIC_LIGHT_NAVIGATION_BORDER
    ),
    navigationSelectedContainer = colorScheme.primary.copy(
        alpha = if (isDark) YikeChromeAlphaConstants.DYNAMIC_DARK_NAVIGATION_SELECTED_CONTAINER else YikeChromeAlphaConstants.DYNAMIC_LIGHT_NAVIGATION_SELECTED_CONTAINER
    ),
    navigationSelectedContent = colorScheme.primary
)

/**
 * 默认值只服务于预览与测试回退，正式页面必须由 `YikeTheme` 提供成对的浅深色令牌。
 */
val LocalYikeSemanticColors = staticCompositionLocalOf { LightSemanticColors }

/**
 * Chrome 令牌默认值只用于无主题上下文的兜底，防止预览时直接读取空引用。
 */
val LocalYikeChromeColors = staticCompositionLocalOf { LightChromeColors }

/**
 * 统一的令牌读取入口，是为了让页面与共享组件优先表达设计语义，而不是记住具体色值常量名。
 */
object YikeThemeTokens {
    /**
     * 间距令牌通过单一入口暴露，是为了让共享组件不再混用 `LocalYikeSpacing` 和散落的 dp 常量。
     */
    val spacing: YikeSpacing
        @Composable get() = LocalYikeSpacing.current

    /**
     * 语义色令牌通过统一入口读取，是为了让成功、警示和风险状态只按语义访问，不按实现细节访问。
     */
    val semanticColors: YikeSemanticColors
        @Composable get() = LocalYikeSemanticColors.current

    /**
     * 壳层视觉令牌集中暴露，是为了让导航、背景和 Hero 渐变共享同一套视觉来源。
     */
    val chromeColors: YikeChromeColors
        @Composable get() = LocalYikeChromeColors.current
}
