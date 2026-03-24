package com.kariscode.yike.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 间距令牌通过 CompositionLocal 暴露，能让页面在不硬编码数字的前提下复用统一节奏。
 */
@Immutable
data class YikeSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val section: Dp = 32.dp
)

/**
 * 默认值只作为预览与测试回退，正式页面应始终由 `YikeTheme` 提供统一实例。
 */
val LocalYikeSpacing = staticCompositionLocalOf { YikeSpacing() }
