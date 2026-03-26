package com.kariscode.yike.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 页面层仍然可能保留 “message / errorMessage” 这类持久状态字段，
 * 这里集中把它们桥接到 Snackbar，是为了在不强迫所有 ViewModel 立刻改造成 effect 的前提下，
 * 仍然实现统一的 Snackbar 反馈体验，并把“消费一次性消息”的时机固定下来。
 */
@Composable
fun YikeOperationSnackbarEffect(
    successMessage: String?,
    errorMessage: String?,
    infoMessage: String? = null,
    onSuccessConsumed: (() -> Unit)? = null,
    onErrorConsumed: (() -> Unit)? = null,
    onInfoConsumed: (() -> Unit)? = null
) {
    OperationSnackbarMessageEffect(
        message = successMessage,
        tone = YikeSnackbarTone.SUCCESS,
        onConsumed = onSuccessConsumed
    )
    OperationSnackbarMessageEffect(
        message = infoMessage,
        tone = YikeSnackbarTone.INFO,
        onConsumed = onInfoConsumed
    )
    OperationSnackbarMessageEffect(
        message = errorMessage,
        tone = YikeSnackbarTone.ERROR,
        onConsumed = onErrorConsumed
    )
}

/**
 * 单条消息的副作用统一收口，是为了固定“先消费状态，再展示 Snackbar”的顺序，
 * 避免 success/info/error 三条分支后续继续复制同一份模板逻辑。
 */
@Composable
private fun OperationSnackbarMessageEffect(
    message: String?,
    tone: YikeSnackbarTone,
    onConsumed: (() -> Unit)? = null
) {
    val snackbarState = LocalYikeSnackbarState.current
    LaunchedEffect(message) {
        if (message.isNullOrBlank()) return@LaunchedEffect
        onConsumed?.invoke()
        snackbarState.show(
            message = message,
            tone = tone
        )
    }
}

