package com.kariscode.yike.data.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * 同步仓储依赖发现服务接口，而不是具体 NSD 实现，
 * 这样测试才能精确控制发现结果并覆盖会话状态合并逻辑。
 */
interface LanSyncDiscoveryService {
    /**
     * 发现结果保持为只读状态流，是为了让仓储继续作为 peer 合并的唯一入口。
     */
    val services: StateFlow<List<LanSyncDiscoveredService>>

    /**
     * 广播与主动发现分离，便于测试分别替换这两类系统行为。
     */
    fun registerService(serviceName: String, port: Int)

    /**
     * 结束广播后必须完全收口系统资源，接口化后测试可直接断言这一语义。
     */
    fun unregisterService()

    /**
     * 启动发现由仓储统一编排，接口化后可用假实现模拟发现生命周期。
     */
    fun startDiscovery()

    /**
     * 停止发现时清空缓存的语义也要可测，因此保留显式停止入口。
     */
    fun stopDiscovery()
}

/**
 * 发现层只暴露连接所需最小字段，是为了把 NSD 平台对象隔离在适配器内部。
 */
data class LanSyncDiscoveredService(
    val serviceName: String,
    val hostAddress: String,
    val port: Int
)
