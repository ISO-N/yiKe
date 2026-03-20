package com.kariscode.yike.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kariscode.yike.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = YikePrimary,
    onPrimary = YikeOnPrimary,
    primaryContainer = YikePrimaryContainer,
    onPrimaryContainer = YikeOnPrimaryContainer,
    secondary = YikeSecondary,
    onSecondary = YikeOnSecondary,
    secondaryContainer = YikeSecondaryContainer,
    onSecondaryContainer = YikeOnSecondaryContainer,
    tertiary = YikeTertiary,
    onTertiary = YikeOnTertiary,
    tertiaryContainer = YikeTertiaryContainer,
    onTertiaryContainer = YikeOnTertiaryContainer,
    error = YikeError,
    onError = YikeOnError,
    errorContainer = YikeErrorContainer,
    onErrorContainer = YikeOnErrorContainer,
    background = YikeBackground,
    onBackground = YikeText,
    surface = YikeSurface,
    onSurface = YikeText,
    surfaceVariant = YikeSurfaceContainer,
    onSurfaceVariant = YikeTextMuted,
    outline = YikeOutline,
    outlineVariant = YikeOutlineVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = YikePrimaryDark,
    onPrimary = YikeOnPrimaryDark,
    primaryContainer = YikePrimaryContainerDark,
    onPrimaryContainer = YikeOnPrimaryContainerDark,
    secondary = YikeSecondaryDark,
    onSecondary = YikeOnSecondaryDark,
    secondaryContainer = YikeSecondaryContainerDark,
    onSecondaryContainer = YikeOnSecondaryContainerDark,
    tertiary = YikeTertiaryDark,
    onTertiary = YikeOnTertiaryDark,
    tertiaryContainer = YikeTertiaryContainerDark,
    onTertiaryContainer = YikeOnTertiaryContainerDark,
    error = YikeErrorDark,
    onError = YikeOnErrorDark,
    errorContainer = YikeErrorContainerDark,
    onErrorContainer = YikeOnErrorContainerDark,
    background = YikeBackgroundDark,
    onBackground = YikeTextDark,
    surface = YikeSurfaceDark,
    onSurface = YikeTextDark,
    surfaceVariant = YikeSurfaceContainerDark,
    onSurfaceVariant = YikeTextMutedDark,
    outline = YikeOutlineDark,
    outlineVariant = YikeOutlineVariantDark
)

/**
 * 主题模式通过单一入口切换，是为了让设置页、系统主题与所有 Compose 页面共享同一渲染口径，
 * 避免局部页面自行判断深浅色后出现割裂。
 */
@Composable
fun YikeTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDarkColors = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    YikeSystemBarAppearance(useDarkColors = useDarkColors)
    CompositionLocalProvider(LocalYikeSpacing provides YikeSpacing()) {
        MaterialTheme(
            colorScheme = if (useDarkColors) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = YikeShapes,
            content = content
        )
    }
}

/**
 * 边到边模式下系统状态栏直接叠在应用背景上，因此主题切换时必须同步图标明暗，
 * 否则深色背景会把深色状态栏图标“吃掉”，看起来像状态栏消失。
 */
@Composable
private fun YikeSystemBarAppearance(
    useDarkColors: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) {
        return
    }
    val activity = view.context.findActivity() ?: return

    SideEffect {
        val insetsController = WindowCompat.getInsetsController(activity.window, view)
        insetsController.isAppearanceLightStatusBars = !useDarkColors
        insetsController.isAppearanceLightNavigationBars = !useDarkColors
    }
}

/**
 * Compose 宿主上下文可能被多层包装，递归回到 Activity 才能稳定拿到真正的 Window，
 * 避免主题副作用依赖某种特定 Context 实现细节。
 */
private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
