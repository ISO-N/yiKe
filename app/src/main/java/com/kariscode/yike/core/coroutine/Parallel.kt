package com.kariscode.yike.core.domain.coroutine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * 用少量可复用的并行 helper 收敛 `coroutineScope + async + await` 的样板代码，
 * 目的是让 ViewModel/Repository 的主逻辑更聚焦于“并行读取什么”，而不是“如何并行”。
 */
object Parallel {
    /**
     * 同时执行两个 suspend 任务并返回结果对，是为了把“并行读取两份数据”表达成一行声明式代码，
     * 避免在业务代码里重复写 async/await 模板从而掩盖核心意图。
     */
    suspend inline fun <A, B> pair(
        crossinline first: suspend CoroutineScope.() -> A,
        crossinline second: suspend CoroutineScope.() -> B
    ): Pair<A, B> = coroutineScope {
        val firstDeferred = async { first() }
        val secondDeferred = async { second() }
        firstDeferred.await() to secondDeferred.await()
    }

    /**
     * 同时执行三个 suspend 任务并返回 Triple，是为了覆盖“筛选元数据 + 列表 + 依赖数据”等常见场景，
     * 从而让并行读取保持一致写法而不必为每个 ViewModel 单独维护一套协程模板。
     */
    suspend inline fun <A, B, C> triple(
        crossinline first: suspend CoroutineScope.() -> A,
        crossinline second: suspend CoroutineScope.() -> B,
        crossinline third: suspend CoroutineScope.() -> C
    ): Triple<A, B, C> = coroutineScope {
        val firstDeferred = async { first() }
        val secondDeferred = async { second() }
        val thirdDeferred = async { third() }
        Triple(firstDeferred.await(), secondDeferred.await(), thirdDeferred.await())
    }
}

/**
 * 语义化别名保留调用点的可读性：业务代码里写 `parallel { ... }` 比直接依赖对象名更贴近“并行读取”的意图。
 */
suspend inline fun <A, B> parallel(
    crossinline first: suspend CoroutineScope.() -> A,
    crossinline second: suspend CoroutineScope.() -> B
): Pair<A, B> = Parallel.pair(first, second)

/**
 * 语义化别名用于三路并行读取，避免调用点出现 `Parallel.triple(...)` 这种偏工具化的表达。
 */
suspend inline fun <A, B, C> parallel3(
    crossinline first: suspend CoroutineScope.() -> A,
    crossinline second: suspend CoroutineScope.() -> B,
    crossinline third: suspend CoroutineScope.() -> C
): Triple<A, B, C> = Parallel.triple(first, second, third)


