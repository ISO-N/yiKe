package com.kariscode.yike.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.kariscode.yike.ui.theme.YikeThemeTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Snackbar 的语义分档抽成枚举，是为了让页面只表达“这是成功/失败/提示”，
 * 颜色与时长策略交由统一实现维护，避免全局反馈逐页漂移。
 */
enum class YikeSnackbarTone {
    SUCCESS,
    ERROR,
    INFO
}

/**
 * 通过自定义 visuals 扩展 tone 字段，是为了让 `SnackbarHost` 能在渲染层读取语义并决定配色，
 * 同时保持调用方仍然使用 `SnackbarHostState` 的原生 showSnackbar 能力。
 */
@Immutable
data class YikeSnackbarVisuals(
    override val message: String,
    val tone: YikeSnackbarTone,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
) : SnackbarVisuals

/**
 * SnackbarState 对 `SnackbarHostState` 做一层轻量封装，是为了提供：
 * - 固定的 SUCCESS/INFO/ERROR 自动消失时长
 * - 统一的 visuals 结构（携带 tone）
 * 从而避免每个页面各自拼装 delay + dismiss 逻辑。
 */
@Immutable
class YikeSnackbarState(
    val hostState: SnackbarHostState
) {
    /**
     * 将自动消失时长做成语义映射，是为了确保全局反馈节奏保持一致且可调整。
     */
    private fun toneDurationMillis(tone: YikeSnackbarTone): Long = when (tone) {
        YikeSnackbarTone.SUCCESS -> 3_000L
        YikeSnackbarTone.INFO -> 4_000L
        YikeSnackbarTone.ERROR -> 5_000L
    }

    /**
     * 统一 show 入口把 tone、配色与自动消失策略封装起来，
     * 这样页面只需声明语义即可获得一致的品牌反馈体验。
     */
    suspend fun show(
        message: String,
        tone: YikeSnackbarTone,
        actionLabel: String? = null,
        withDismissAction: Boolean = tone == YikeSnackbarTone.ERROR
    ): SnackbarResult = coroutineScope {
        val dismissJob = launch {
            delay(toneDurationMillis(tone))
            hostState.currentSnackbarData?.dismiss()
        }
        try {
            hostState.showSnackbar(
                visuals = YikeSnackbarVisuals(
                    message = message,
                    tone = tone,
                    actionLabel = actionLabel,
                    withDismissAction = withDismissAction,
                    duration = SnackbarDuration.Indefinite
                )
            )
        } finally {
            dismissJob.cancel()
        }
    }
}

/**
 * CompositionLocal 提供全局 SnackbarState，是为了让页面层不必层层透传 hostState。
 */
val LocalYikeSnackbarState = staticCompositionLocalOf<YikeSnackbarState> {
    error("LocalYikeSnackbarState 未提供。请在应用根节点注入 YikeSnackbarState。")
}

/**
 * remember 统一入口创建状态，是为了保证全局 `SnackbarHostState` 在重组中稳定不丢失队列。
 */
@Composable
fun rememberYikeSnackbarState(): YikeSnackbarState = remember {
    YikeSnackbarState(hostState = SnackbarHostState())
}

/**
 * 全局 SnackbarHost 统一实现配色与外观，是为了让 SUCCESS/ERROR/INFO 三种语义在视觉上可区分，
 * 同时避免各页面在不同 Container 色值之间摇摆。
 */
@Composable
fun YikeSnackbarHost(
    modifier: Modifier = Modifier,
    state: YikeSnackbarState = LocalYikeSnackbarState.current
) {
    val semanticColors = YikeThemeTokens.semanticColors
    SnackbarHost(
        hostState = state.hostState,
        modifier = modifier
    ) { data ->
        val visuals = data.visuals
        val tone = (visuals as? YikeSnackbarVisuals)?.tone ?: YikeSnackbarTone.INFO
        val (containerColor, contentColor) = when (tone) {
            YikeSnackbarTone.SUCCESS -> semanticColors.successContainer to semanticColors.onSuccessContainer
            YikeSnackbarTone.ERROR -> semanticColors.criticalContainer to semanticColors.onCriticalContainer
            YikeSnackbarTone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        }

        Snackbar(
            snackbarData = data,
            containerColor = containerColor,
            contentColor = contentColor
        )
    }
}

