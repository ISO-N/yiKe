package com.kariscode.yike.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * ViewModel 里的异步读写大多遵循同一套“启动协程、捕获结果、分别处理成功与失败”的骨架，
 * 因此抽成共享入口可以把页面代码重新聚焦到状态更新本身，而不是重复维护协程模板。
 */
inline fun <T> ViewModel.launchResult(
    crossinline action: suspend () -> T,
    crossinline onSuccess: (T) -> Unit = {},
    crossinline onFailure: (Throwable) -> Unit
): Job = viewModelScope.launch {
    runCatching { action() }
        .onSuccess { result -> onSuccess(result) }
        .onFailure { throwable -> onFailure(throwable) }
}

/**
 * 很多写操作只关心“完成了”而不关心返回值，
 * 单独提供 Unit 版本是为了把各个 ViewModel 里重复的 `onSuccess = {}` 模板收口到共享入口。
 */
inline fun ViewModel.launchMutation(
    crossinline action: suspend () -> Unit,
    crossinline onSuccess: () -> Unit = {},
    crossinline onFailure: (Throwable) -> Unit = {}
): Job = launchResult(
    action = action,
    onSuccess = { onSuccess() },
    onFailure = onFailure
)
