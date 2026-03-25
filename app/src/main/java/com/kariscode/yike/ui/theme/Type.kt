package com.kariscode.yike.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 系统字体链显式补上中文友好的设备字体名，是为了让中英文混排时优先走各厂商已经针对本地语言优化过的字形。
 */
private val YikeSystemSansFontFamily = FontFamily(
    Font(familyName = DeviceFontFamilyName("sans-serif")),
    Font(familyName = DeviceFontFamilyName("sans-serif-medium"), weight = FontWeight.Medium),
    Font(familyName = DeviceFontFamilyName("sans-serif"), weight = FontWeight.SemiBold),
    Font(familyName = DeviceFontFamilyName("sans-serif"), weight = FontWeight.Bold)
)

/**
 * 等宽字体单独导出，是为了让访问码、种子和未来代码块等内容拥有稳定对齐效果，
 * 避免在比例字体里出现字符宽度飘动导致的可读性下降。
 */
val YikeSystemMonoFontFamily = FontFamily(
    Font(familyName = DeviceFontFamilyName("monospace")),
    Font(familyName = DeviceFontFamilyName("monospace"), weight = FontWeight.Medium),
    Font(familyName = DeviceFontFamilyName("monospace"), weight = FontWeight.Bold)
)

/**
 * 字重和字号令牌优先贴合原型中的“标题突出、说明克制”节奏，
 * 这样能让首页、复习页和设置页共享同一套信息层级，而不是各写各的字号。
 */
val Typography = Typography(
    headlineLarge = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelSmall = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.8.sp
    ),
    bodySmall = TextStyle(
        fontFamily = YikeSystemSansFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    )
)
