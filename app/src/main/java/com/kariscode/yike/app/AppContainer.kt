package com.kariscode.yike.app

import android.app.Application
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.core.time.SystemTimeProvider
import com.kariscode.yike.core.time.TimeProvider

/**
 * 首版采用手动装配依赖，目的是把“谁负责创建什么”集中到单一位置，
 * 以防后续功能增长时出现跨层直接 new 实现导致的分层漂移。
 */
class AppContainer(
    private val application: Application
) {
    /**
     * 抽象时间是为了让调度、提醒与备份的时间计算可预测、可测试，
     * 避免在业务逻辑中散落 System.currentTimeMillis() 导致测试脆弱。
     */
    val timeProvider: TimeProvider = SystemTimeProvider()

    /**
     * 统一 dispatcher 注入是为了让 IO 与计算的线程选择可替换，
     * 并为后续测试中替换为 TestDispatcher 预留入口。
     */
    val dispatchers: AppDispatchers = DefaultAppDispatchers()

    /**
     * 当前阶段仅保留 Application 引用，为后续 Room/DataStore/WorkManager 初始化提供上下文。
     */
    fun application(): Application = application
}

