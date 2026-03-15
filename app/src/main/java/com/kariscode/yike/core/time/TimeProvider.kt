package com.kariscode.yike.core.time

/**
 * 抽象时间的目的不是“换一种拿时间的方式”，而是确保调度/提醒/备份等核心逻辑
 * 在单元测试中可以以确定的时间基准运行，避免依赖系统时钟造成不可复现的边界问题。
 */
interface TimeProvider {
    fun nowEpochMillis(): Long
}

/**
 * 生产环境使用系统时钟实现即可；
 * 通过接口隔离后，测试中可以用固定时间实现稳定覆盖 dueAt 等计算。
 */
class SystemTimeProvider : TimeProvider {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}

