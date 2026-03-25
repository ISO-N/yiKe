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
    val snackbarState = LocalYikeSnackbarState.current

    LaunchedEffect(successMessage) {
        if (successMessage.isNullOrBlank()) return@LaunchedEffect
        onSuccessConsumed?.invoke()
        snackbarState.show(
            message = successMessage,
            tone = YikeSnackbarTone.SUCCESS
        )
    }

    LaunchedEffect(infoMessage) {
        if (infoMessage.isNullOrBlank()) return@LaunchedEffect
        onInfoConsumed?.invoke()
        snackbarState.show(
            message = infoMessage,
            tone = YikeSnackbarTone.INFO
        )
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage.isNullOrBlank()) return@LaunchedEffect
        onErrorConsumed?.invoke()
        snackbarState.show(
            message = errorMessage,
            tone = YikeSnackbarTone.ERROR
        )
    }
}

