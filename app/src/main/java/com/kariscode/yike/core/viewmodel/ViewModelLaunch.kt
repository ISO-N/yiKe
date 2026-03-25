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
 * 因此把 action/start/success/failure 收口成声明式配置，可以减少调用点在一长串参数里来回对照位置。
 */
class StateResultSpec<State, T> {
    private var actionBlock: (suspend () -> T)? = null
    private var onStartBlock: (State) -> State = { it }
    private var onSuccessBlock: ((State, T) -> State)? = null
    private var onFailureBlock: ((State, Throwable) -> State)? = null

    /**
     * 真正的异步工作需要显式声明，是为了避免调用点只写状态回调却忘记绑定核心动作。
     */
    fun action(block: suspend () -> T) {
        actionBlock = block
    }

    /**
     * 启动态默认保持原样，仅在调用点确实需要 loading 或清错时才覆盖。
     */
    fun onStart(block: (State) -> State) {
        onStartBlock = block
    }

    /**
     * 成功分支必须显式提供，是为了确保结果值一定会被消费成新的状态。
     */
    fun onSuccess(block: (State, T) -> State) {
        onSuccessBlock = block
    }

    /**
     * 失败分支也显式声明，是为了让每个调用点把错误反馈语义留在本地状态机里，而不是退回全局默认。
     */
    fun onFailure(block: (State, Throwable) -> State) {
        onFailureBlock = block
    }

    /**
     * 运行前统一校验 DSL 是否完整，是为了把“漏写核心回调”的问题提前暴露成开发期错误。
     */
    internal fun build(): BuiltStateResultSpec<State, T> = BuiltStateResultSpec(
        action = requireNotNull(actionBlock) { "launchStateResult 必须声明 action" },
        onStart = onStartBlock,
        onSuccess = requireNotNull(onSuccessBlock) { "launchStateResult 必须声明 onSuccess" },
        onFailure = requireNotNull(onFailureBlock) { "launchStateResult 必须声明 onFailure" }
    )
}

/**
 * 运行时配置和 DSL 构建器分离后，真正执行逻辑只依赖完整规格，避免每次执行都重复做空值判断。
 */
internal data class BuiltStateResultSpec<State, T>(
    val action: suspend () -> T,
    val onStart: (State) -> State,
    val onSuccess: (State, T) -> State,
    val onFailure: (State, Throwable) -> State
)

/**
 * DSL 版状态结果启动器把成功/失败更新逻辑内聚在一个块里，
 * 可以让调用点按“先做什么，再怎么更新状态”的顺序阅读，而不是在多个位置来回跳。
 */
fun <State, T> ViewModel.launchStateResult(
    state: MutableStateFlow<State>,
    build: StateResultSpec<State, T>.() -> Unit
): Job {
    val spec = StateResultSpec<State, T>().apply(build).build()
    state.update(spec.onStart)
    return launchResult(
        action = spec.action,
        onSuccess = { result ->
            state.update { current -> spec.onSuccess(current, result) }
        },
        onFailure = { throwable ->
            state.update { current -> spec.onFailure(current, throwable) }
        }
    )
}

/**
 * 只关心“是否完成”的写操作也经常需要配合状态机回写，
 * 单独提供 mutation 版本可以让列表页和表单页直接复用同一套成功/失败骨架。
 */
fun <State> ViewModel.launchStateMutation(
    state: MutableStateFlow<State>,
    action: suspend () -> Unit,
    onStart: (State) -> State = { it },
    onSuccess: (State) -> State = { it },
    onFailure: (State, Throwable) -> State
): Job = launchStateResult(state = state) {
    action(action)
    onStart(onStart)
    onSuccess { current, _ -> onSuccess(current) }
    onFailure(onFailure)
}

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
