package com.kariscode.yike.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.theme.rememberYikeDuration
import com.kariscode.yike.ui.theme.YikeThemeTokens

/**
 * 首页 Hero 需要用“火焰状态”强化连续学习语义，
 * 这里把颜色与脉冲动效收敛成单组件，是为了让后续调整阈值或视觉语言时只改一处。
 */
@Composable
fun YikeStreakFlame(
    streakDays: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val semanticColors = YikeThemeTokens.semanticColors
    val isGoldFlame = streakDays >= 7
    val shouldPulse = streakDays >= 30
    val pulseDuration = rememberYikeDuration(1_200)

    val containerColor = when {
        isGoldFlame -> semanticColors.warningContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when {
        isGoldFlame -> semanticColors.onWarningContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val transition = rememberInfiniteTransition(label = "yike_streak_flame")
    val pulseScale = if (shouldPulse && pulseDuration > 0) {
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.14f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "yike_streak_flame_scale"
        ).value
    } else {
        1f
    }
    val pulseAlpha = if (shouldPulse && pulseDuration > 0) {
        transition.animateFloat(
            initialValue = 0.38f,
            targetValue = 0.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = pulseDuration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "yike_streak_flame_alpha"
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (shouldPulse && pulseDuration > 0) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(
                        scaleX = pulseScale,
                        scaleY = pulseScale,
                        alpha = pulseAlpha
                    )
                    .background(color = containerColor, shape = CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(color = containerColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.LocalFireDepartment,
                contentDescription = null,
                tint = contentColor
            )
        }
    }
}
