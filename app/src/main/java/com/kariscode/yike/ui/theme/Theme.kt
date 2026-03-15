package com.kariscode.yike.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

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

/**
 * 主题默认固定为轻色令牌，是为了确保移动端实现稳定贴合已确认的前端原型，
 * 而不会因为系统动态色或深色模式把信息层级冲散。
 */
@Composable
fun YikeTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalYikeSpacing provides YikeSpacing()) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            typography = Typography,
            shapes = YikeShapes,
            content = content
        )
    }
}
