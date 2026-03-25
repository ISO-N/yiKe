package com.kariscode.yike.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.ui.theme.YikeBestContainer
import com.kariscode.yike.ui.theme.YikeCriticalContainer
import com.kariscode.yike.ui.theme.YikeSuccessContainer
import com.kariscode.yike.ui.theme.YikeWarningContainer
import com.kariscode.yike.ui.theme.rememberReduceMotionEnabled
import com.kariscode.yike.ui.theme.YikeThemeTokens

/**
 * 主按钮承担页面最重要动作，统一封装后能让异步状态和层级关系在各页面保持一致。
 */
@Composable
fun YikePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * 次按钮统一用于辅助操作，避免“返回/浏览/取消”在不同页面使用不一致的样式语义。
 */
@Composable
fun YikeSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContentColor = disabledContentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * 危险按钮把高风险操作明确染成错误语义，是为了降低删除和恢复类操作的误触概率。
 */
@Composable
fun YikeDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

/**
 * 评分按钮根据掌握度切换色阶，是为了让“完全不会/很轻松”在视觉上立刻形成强弱区分。
 */
@Composable
fun YikeRatingButton(
    text: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reduceMotionEnabled = rememberReduceMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (enabled && isPressed) 0.97f else 1f,
        animationSpec = if (reduceMotionEnabled) {
            snap()
        } else {
            spring(
                dampingRatio = 0.7f,
                stiffness = 650f
            )
        },
        label = "rating_button_scale"
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(text = text, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * 统一 FAB 样式能让新增卡组和新增卡片保持同一种“主入口但不压过页面主任务”的语气。
 */
@Composable
fun YikeFab(
    text: String,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Text(text)
    }
}

/**
 * 进度条被单独封装，是为了让首页节奏、复习完成度和后续异步状态都能复用同一视觉表达。
 */
@Composable
fun YikeProgressBar(
    progress: Float,
    description: String? = null,
    modifier: Modifier = Modifier
) {
    val normalizedProgress = progress.coerceIn(0f, 1f)
    val progressDescription = description ?: "当前进度 ${(normalizedProgress * 100).toInt()}%"
    Box(
        modifier = modifier
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = normalizedProgress,
                    range = 0f..1f
                )
                stateDescription = progressDescription
            }
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(normalizedProgress)
                .height(6.dp)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                        )
                    )
                )
        )
    }
}

/**
 * 评分色板不只决定背景色，还要同时定义前景色，
 * 这样复习页与统计页都能按同一语义拿到“当前主题下可读”的整组视觉令牌。
 */
data class YikeRatingTone(
    val containerColor: Color,
    val contentColor: Color
)

/**
 * 评分色板集中定义，是为了让复习页调用处只表达语义，而不重复散落具体颜色常量。
 */
object YikeRatingPalette {
    val criticalContainer: Color = YikeCriticalContainer
    val warningContainer: Color = YikeWarningContainer
    val successContainer: Color = YikeSuccessContainer
    val bestContainer: Color = YikeBestContainer

    /**
     * 复习评分按钮需要随主题切换成对调整前景与背景，
     * 因此用单一入口读取扩展语义令牌，避免调用方再自行判断浅深色和容器色。
     */
    @Composable
    fun toneFor(rating: ReviewRating): YikeRatingTone {
        val semanticColors = YikeThemeTokens.semanticColors
        return when (rating) {
            ReviewRating.AGAIN -> YikeRatingTone(
                containerColor = semanticColors.criticalContainer,
                contentColor = semanticColors.onCriticalContainer
            )
            ReviewRating.HARD -> YikeRatingTone(
                containerColor = semanticColors.warningContainer,
                contentColor = semanticColors.onWarningContainer
            )
            ReviewRating.GOOD -> YikeRatingTone(
                containerColor = semanticColors.successContainer,
                contentColor = semanticColors.onSuccessContainer
            )
            ReviewRating.EASY -> YikeRatingTone(
                containerColor = semanticColors.bestContainer,
                contentColor = semanticColors.onBestContainer
            )
        }
    }
}

