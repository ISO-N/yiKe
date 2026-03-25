package com.kariscode.yike.core.domain.dispatchers

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * 统一协程 dispatcher 的抽象，是为了把“线程选择”从业务逻辑里剥离出来，
 * 使 domain 层的用例在测试中可以替换为可控的 dispatcher，避免并发导致用例不稳定。
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

/**
 * 首版默认实现直接桥接到 Kotlin 提供的 Dispatchers，
 * 选择单独封装是为了后续引入测试调度器或自定义线程池时不影响上层代码。
 */
class DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}


