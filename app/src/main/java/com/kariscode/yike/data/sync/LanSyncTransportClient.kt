package com.kariscode.yike.data.sync

/**
 * 仓储通过传输客户端接口发起协议请求，避免在测试中绑定真实网络堆栈。
 */
interface LanSyncTransportClient {
    /**
     * hello 负责返回最小设备资料，是同步预览前的共同入口。
     */
    suspend fun hello(hostAddress: String, port: Int): LanSyncHelloResponse

    /**
     * pair 负责首次交换共享密钥，接口化后测试可以稳定模拟接受或拒绝。
     */
    suspend fun pair(
        hostAddress: String,
        port: Int,
        hello: LanSyncHelloResponse,
        initiatorDeviceId: String,
        initiatorDisplayName: String,
        pairingCode: String,
        sharedSecret: String
    ): Boolean

    /**
     * ping 用于可信设备健康检查，测试里需要精确控制成功与失败分支。
     */
    suspend fun ping(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        requestedAt: Long
    ): LanSyncPingResponsePayload

    /**
     * pull 同时服务预览与执行，因此保留 headersOnly 开关以覆盖两条路径。
     */
    suspend fun pullChanges(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        afterSeq: Long,
        headersOnly: Boolean
    ): LanSyncPullChangesResponsePayload

    /**
     * push 不自动重试，接口显式暴露 sessionId 以便测试验证幂等行为。
     */
    suspend fun pushChanges(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        sessionId: String,
        changes: List<SyncChangePayload>
    ): LanSyncPushChangesResponsePayload

    /**
     * ack 负责推进对端看到的本地游标，是双向增量闭环的一部分。
     */
    suspend fun ack(
        hostAddress: String,
        port: Int,
        requesterDeviceId: String,
        sharedSecret: String,
        sessionId: String,
        remoteSeqApplied: Long
    ): LanSyncAckResponsePayload
}
