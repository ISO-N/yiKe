package com.kariscode.yike.data.sync

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPeerHealth
import com.kariscode.yike.domain.model.LanSyncTrustState
import kotlinx.coroutines.flow.update

/**
 * 设备发现与健康探测单独抽出，是为了把网络候选地址、可信设备表和在线状态的合并规则
 * 固定在单一地方，避免仓储主流程被设备列表刷新细节淹没。
 */
internal class LanSyncPeerDiscoveryCoordinator(
    private val runtime: LanSyncSessionRuntime,
    private val timeProvider: TimeProvider,
    private val nsdService: LanSyncDiscoveryService,
    private val httpClient: LanSyncTransportClient,
    private val syncPeerDao: SyncPeerDao,
    private val sharedSecretProtector: LanSyncSharedSecretProtector
) {
    /**
     * 发现层候选地址和可信设备表需要合并后才能得到真正可展示的 peer 列表，
     * 因此刷新逻辑集中在单点可以避免 UI 看到半完成的信任与健康状态。
     */
    suspend fun refreshPeers(services: List<LanSyncDiscoveredService>) {
        val trustedPeers = syncPeerDao.listAll().associateBy { peer -> peer.deviceId }
        val peers = buildList {
            services.forEach { service ->
                val hello = runCatching {
                    httpClient.hello(service.hostAddress, service.port)
                }.getOrElse { throwable ->
                    LanSyncLogger.e("Hello failed for ${service.hostAddress}:${service.port}", throwable)
                    return@forEach
                }
                val trusted = trustedPeers[hello.deviceId]
                add(
                    LanSyncPeer(
                        deviceId = hello.deviceId,
                        displayName = hello.displayName,
                        shortDeviceId = hello.shortDeviceId,
                        hostAddress = service.hostAddress,
                        port = service.port,
                        protocolVersion = hello.protocolVersion,
                        trustState = if (trusted == null) LanSyncTrustState.UNTRUSTED else LanSyncTrustState.TRUSTED,
                        health = when {
                            trusted == null -> LanSyncPeerHealth.AVAILABLE
                            trusted.missCount >= LanSyncConfig.HEARTBEAT_MAX_MISSES -> LanSyncPeerHealth.OFFLINE
                            trusted.missCount > 0 -> LanSyncPeerHealth.STALE
                            else -> LanSyncPeerHealth.AVAILABLE
                        },
                        lastSeenAt = trusted?.lastSeenAt ?: timeProvider.nowEpochMillis()
                    )
                )
            }
        }.sortedBy { peer -> peer.displayName.lowercase() }
        runtime.sessionState.update { it.copy(peers = peers) }
    }

    /**
     * 只对可信设备做心跳，是为了避免未配对设备在未授权前就能借由 ping 获得更高频的在线信息。
     */
    suspend fun heartbeatTrustedPeers() {
        val peers = runtime.sessionState.value.peers.filter { peer -> peer.trustState == LanSyncTrustState.TRUSTED }
        peers.forEach { peer ->
            val trustedPeer = syncPeerDao.findById(peer.deviceId) ?: return@forEach
            val sharedSecret = sharedSecretProtector.decrypt(trustedPeer.encryptedSharedSecret)
            runCatching {
                httpClient.ping(
                    hostAddress = peer.hostAddress,
                    port = peer.port,
                    requesterDeviceId = runtime.currentLocalProfile.deviceId,
                    sharedSecret = sharedSecret,
                    requestedAt = timeProvider.nowEpochMillis()
                )
            }.onSuccess { response ->
                syncPeerDao.updateHeartbeat(
                    deviceId = peer.deviceId,
                    displayName = response.displayName,
                    protocolVersion = response.protocolVersion,
                    lastSeenAt = response.respondedAt,
                    missCount = 0
                )
            }.onFailure { throwable ->
                LanSyncLogger.e("Ping failed for ${peer.deviceId}", throwable)
                syncPeerDao.updateHeartbeat(
                    deviceId = trustedPeer.deviceId,
                    displayName = trustedPeer.displayName,
                    protocolVersion = trustedPeer.protocolVersion,
                    lastSeenAt = trustedPeer.lastSeenAt,
                    missCount = trustedPeer.missCount + 1
                )
            }
        }
        refreshPeers(nsdService.services.value)
    }
}

