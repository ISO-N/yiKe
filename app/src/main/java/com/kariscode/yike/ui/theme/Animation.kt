package com.kariscode.yike.ui.theme

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable

/**
 * 动画时长集中管理，是为了让导航、答案展开和反馈提示在节奏上保持同一套语言，
 * 避免后续再出现每个页面各写一组毫秒值的碎片化情况。
 */
object YikeAnimationDurations {
    const val FAST: Int = 180
    const val MEDIUM: Int = 220
    const val STANDARD: Int = 320
    const val NAVIGATION_SLIDE: Int = 300
    const val NAVIGATION_SLIDE_PRIMARY: Int = 380
    const val THEME_TRANSITION: Int = 260
}

/**
 * 系统若已关闭动画，就应该让应用内转场和微动效一起降级，
 * 这样视觉反馈才能真正尊重用户的可访问性偏好。
 */
@Composable
fun rememberReduceMotionEnabled(): Boolean = !ValueAnimator.areAnimatorsEnabled()

/**
 * 时长收口为单点换算后，调用方只需要声明“语义时长”，
 * 不需要每次都手动处理 reduce motion 的零时长分支。
 */
@Composable
fun rememberYikeDuration(durationMillis: Int): Int =
    if (rememberReduceMotionEnabled()) 0 else durationMillis
