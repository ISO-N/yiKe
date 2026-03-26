package com.kariscode.yike.data.repository

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 仓储层集中复用 IO 线程切换，是为了让每个实现把注意力留给查询口径与映射规则，
 * 而不是在同样的协程模板里反复埋入细节差异。
 */
internal suspend inline fun <T> AppDispatchers.onIo(
    crossinline block: suspend () -> T
): T = withContext(io) { block() }

/**
 * 观察式查询普遍都要把列表元素逐项映射成领域模型，
 * 因此抽成共享扩展可以减少样板并降低不同仓储在映射细节上逐渐漂移的风险。
 */
internal inline fun <Source, Target> Flow<List<Source>>.mapEach(
    crossinline transform: (Source) -> Target
): Flow<List<Target>> = map { list ->
    ArrayList<Target>(list.size).apply {
        list.forEach { item ->
            add(transform(item))
        }
    }
}

/**
 * DAO 同步查询的返回值往往是 List<Entity>，而仓储层必须立即映射成领域模型；
 * 用与 Flow 版本一致的 `mapEach` 命名收口，是为了让“映射是一种口径约束”这一事实更显性，
 * 并在批量映射时避免 Kotlin `map` 额外的迭代器与扩容开销。
 */
internal inline fun <Source, Target> List<Source>.mapEach(
    transform: (Source) -> Target
): List<Target> = ArrayList<Target>(size).apply {
    for (item in this@mapEach) {
        add(transform(item))
    }
}

/**
 * 单对象查询经常需要在仓储层把可空 DAO 结果转换为可空领域模型，
 * 统一收口后可以避免每个实现都各自重写一遍 `?.let(...)` 模板。
 */
internal inline fun <Source, Target> Source?.mapNullable(
    crossinline transform: (Source) -> Target
): Target? = this?.let(transform)

