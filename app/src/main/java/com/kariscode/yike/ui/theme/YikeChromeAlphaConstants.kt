package com.kariscode.yike.ui.theme

/**
 * Chrome 相关 alpha 常量集中定义，是为了让背景辉光、导航壳与 Hero 渐变的“透明度语义”稳定可控，
 * 避免在多个令牌分支里散落魔法数字导致微调时难以保持浅/深色与动态配色的一致性。
 */
internal object YikeChromeAlphaConstants {
    const val LIGHT_SCREEN_GLOW: Float = 0.52f
    const val LIGHT_HERO_GRADIENT_START: Float = 0.72f
    const val LIGHT_NAVIGATION_CONTAINER: Float = 0.96f
    const val LIGHT_NAVIGATION_BORDER: Float = 0.75f
    const val LIGHT_NAVIGATION_SELECTED_CONTAINER: Float = 0.12f

    const val DARK_SCREEN_GLOW: Float = 0.18f
    const val DARK_HERO_GRADIENT_START: Float = 0.68f
    const val DARK_NAVIGATION_CONTAINER: Float = 0.96f
    const val DARK_NAVIGATION_BORDER: Float = 0.92f
    const val DARK_NAVIGATION_SELECTED_CONTAINER: Float = 0.22f

    const val DYNAMIC_LIGHT_SCREEN_GLOW: Float = 0.24f
    const val DYNAMIC_DARK_SCREEN_GLOW: Float = 0.18f
    const val DYNAMIC_LIGHT_HERO_GRADIENT_START: Float = 0.72f
    const val DYNAMIC_DARK_HERO_GRADIENT_START: Float = 0.68f
    const val DYNAMIC_LIGHT_NAVIGATION_CONTAINER: Float = 0.96f
    const val DYNAMIC_DARK_NAVIGATION_CONTAINER: Float = 0.94f
    const val DYNAMIC_LIGHT_NAVIGATION_BORDER: Float = 0.75f
    const val DYNAMIC_DARK_NAVIGATION_BORDER: Float = 0.92f
    const val DYNAMIC_LIGHT_NAVIGATION_SELECTED_CONTAINER: Float = 0.12f
    const val DYNAMIC_DARK_NAVIGATION_SELECTED_CONTAINER: Float = 0.22f
}

