package com.kariscode.yike.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

/**
 * 很多页面在启动异步任务时都会走“先更新状态，再根据结果回写状态”的骨架，
 * 因此提供面向 StateFlow 的结果入口可以减少 ViewModel 内反复编排 update 模板。
 */
inline fun <State, T> ViewModel.launchStateResult(
    state: MutableStateFlow<State>,
    crossinline action: suspend () -> T,
    crossinline onStart: (State) -> State = { it },
    crossinline onSuccess: (State, T) -> State,
    crossinline onFailure: (State, Throwable) -> State
): Job {
    state.update(onStart)
    return launchResult(
        action = action,
        onSuccess = { result ->
            state.update { current -> onSuccess(current, result) }
        },
        onFailure = { throwable ->
            state.update { current -> onFailure(current, throwable) }
        }
    )
}

/**
 * 只关心“是否完成”的写操作也经常需要配合状态机回写，
 * 单独提供 mutation 版本可以让列表页和表单页直接复用同一套成功/失败骨架。
 */
inline fun <State> ViewModel.launchStateMutation(
    state: MutableStateFlow<State>,
    crossinline action: suspend () -> Unit,
    crossinline onStart: (State) -> State = { it },
    crossinline onSuccess: (State) -> State = { it },
    crossinline onFailure: (State, Throwable) -> State
): Job = launchStateResult(
    state = state,
    action = action,
    onStart = onStart,
    onSuccess = { current, _ -> onSuccess(current) },
    onFailure = onFailure
)

/**
 * 很多页面的刷新流程都遵循“取消上一轮 -> 进入 loading -> 启动新任务”的固定骨架，
 * 收口为共享 helper 后可以减少重复样板，并显式保证旧请求不会反向覆盖新状态。
 */
inline fun <State, T> ViewModel.restartStateResult(
    state: MutableStateFlow<State>,
    previousJob: Job?,
    crossinline action: suspend () -> T,
    crossinline onStart: (State) -> State = { it },
    crossinline onSuccess: (State, T) -> State,
    crossinline onFailure: (State, Throwable) -> State
): Job {
    state.update(onStart)
    previousJob?.cancel()
    return launchResult(
        action = action,
        onSuccess = { result ->
            state.update { current -> onSuccess(current, result) }
        },
        onFailure = { throwable ->
            state.update { current -> onFailure(current, throwable) }
        }
    )
}
