package com.kariscode.yike.data.sync

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.dao.SyncPeerCursorDao
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.data.local.db.entity.SyncChangeEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerCursorEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerEntity
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.LanSyncFailureReason
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.domain.model.LanSyncTrustState
import com.kariscode.yike.domain.model.SyncEntityType
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer

/**
 * 配对、预览、真正同步与受保护端点处理集中在执行器里，是为了把协议语义从仓储外壳中剥离出来，
 * 让仓储只保留接口适配与依赖装配职责。
 */
internal class LanSyncSyncExecutor(
    private val runtime: LanSyncSessionRuntime,
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    private val crypto: LanSyncCrypto,
    private val sharedSecretProtector: LanSyncSharedSecretProtector,
    private val httpClient: LanSyncTransportClient,
    private val nsdService: LanSyncDiscoveryService,
    private val syncChangeDao: SyncChangeDao,
    private val syncPeerDao: SyncPeerDao,
    private val syncPeerCursorDao: SyncPeerCursorDao,
    private val conflictResolver: LanSyncConflictResolver,
    private val changeApplier: LanSyncChangeApplier,
    private val refreshPeers: suspend (List<LanSyncDiscoveredService>) -> Unit
) {
    /**
     * 预览阶段先完成配对和增量摘要读取，是为了在真正传输任何双向数据前把冲突和影响规模暴露给用户。
     */
    suspend fun prepareSync(peer: LanSyncPeer, pairingCode: String?): LanSyncPreview {
        ensureProtocolSupported(peer)
        val isFirstPairing = peer.trustState == LanSyncTrustState.UNTRUSTED
        if (isFirstPairing) {
            require(!pairingCode.isNullOrBlank()) { "首次同步需要输入对方配对码" }
            performPairing(peer = peer, pairingCode = pairingCode)
            refreshPeers(nsdService.services.value)
        }
        runtime.setProgress(
            stage = LanSyncStage.PREVIEWING,
            message = "正在生成同步预览",
            clearPreview = true
        )
        val trustedPeer = runtime.sessionState.value.peers.first { candidate -> candidate.deviceId == peer.deviceId }
        val cursor = readCursor(peer.deviceId)
        val sharedSecret = readSharedSecret(peer.deviceId)
        val localChanges = loadCompressedLocalChanges(cursor.lastLocalSeqAckedByPeer)
        val remoteChanges = pullCompressedRemoteChanges(
            hostAddress = trustedPeer.hostAddress,
            port = trustedPeer.port,
            sharedSecret = sharedSecret,
            afterSeq = cursor.lastRemoteSeqAppliedLocally,
            headersOnly = true
        )
        val conflicts = conflictResolver.buildConflicts(localChanges = localChanges, remoteChanges = remoteChanges)
        val preview = LanSyncPreview(
            peer = trustedPeer,
            localChangeCount = localChanges.size,
            remoteChangeCount = remoteChanges.size,
            settingsChangeCount = (localChanges + remoteChanges).count { it.entityType == SyncEntityType.SETTINGS.name },
            conflicts = conflicts,
            isFirstPairing = isFirstPairing
        )
        runtime.sessionState.update {
            it.copy(
                preview = preview,
                progress = it.progress.copy(message = "同步预览已生成")
            )
        }
        return preview
    }

    /**
     * 真正执行同步前先把冲突决议固定下来，是为了让协议应用结果与用户确认保持一致且可重放。
     */
    suspend fun runSync(
        preview: LanSyncPreview,
        resolutions: List<LanSyncConflictResolution>
    ) {
        require(preview.conflicts.size == resolutions.size || preview.conflicts.isEmpty()) {
            "冲突决议数量与预览不一致"
        }
        ensureProtocolSupported(preview.peer)
        runtime.activeSyncJob?.cancelAndJoin()
        runtime.activeSyncJob = scope.launch(dispatchers.io) {
            runSyncInternal(preview = preview, resolutions = resolutions)
        }
        runtime.activeSyncJob?.join()
    }

    /**
     * 取消只允许在提交事务前发生，是为了避免在 Room 已进入关键写入阶段时把本地数据留在未知中间态。
     */
    suspend fun cancelActiveSync() {
        if (runtime.isApplyingChanges) {
            return
        }
        runtime.activeSyncJob?.cancelAndJoin()
        runtime.activeSyncJob = null
        runtime.setProgress(
            stage = LanSyncStage.CANCELLED,
            message = "同步已取消",
            failure = LanSyncFailureReason.CANCELLED
        )
    }

    /**
     * hello 统一从当前本机档案读取，是为了让发现页看到的设备名始终和本机页面上的设备名一致。
     */
    suspend fun handleHello(): LanSyncHelloResponse = LanSyncHelloResponse(
        deviceId = runtime.currentLocalProfile.deviceId,
        displayName = runtime.currentLocalProfile.displayName,
        shortDeviceId = runtime.currentLocalProfile.shortDeviceId,
        protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
        pairingNonce = runtime.currentPairingNonce
    )

    /**
     * 首次配对通过临时密钥解开共享密钥并持久化可信设备，是为了把首次信任建立控制在用户输入配对码的窗口内。
     */
    suspend fun handlePairInit(request: LanSyncPairInitRequest): LanSyncPairInitResponse {
        val key = crypto.derivePairingKey(
            pairingCode = runtime.currentLocalProfile.pairingCode,
            deviceId = runtime.currentLocalProfile.deviceId,
            nonce = runtime.currentPairingNonce
        )
        val payloadJson = crypto.decrypt(request.payload.toEncryptedPayload(), key)
        val payload = LanSyncJson.json.decodeFromString(
            LanSyncPairInitPayload.serializer(),
            payloadJson
        )
        syncPeerDao.upsert(
            SyncPeerEntity(
                deviceId = request.initiatorDeviceId,
                displayName = request.initiatorDisplayName,
                shortDeviceId = request.initiatorDeviceId.takeLast(6),
                encryptedSharedSecret = sharedSecretProtector.encrypt(payload.sharedSecret),
                protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
                lastSeenAt = timeProvider.nowEpochMillis(),
                missCount = 0
            )
        )
        ensureCursorExists(request.initiatorDeviceId)
        val encryptedResponse = crypto.encrypt(
            plainText = LanSyncJson.json.encodeToString(
                LanSyncPairInitResponsePayload.serializer(),
                LanSyncPairInitResponsePayload(accepted = true)
            ),
            keyBytes = key
        )
        return LanSyncPairInitResponse(payload = encryptedResponse.toEnvelope())
    }

    /**
     * 受保护 ping 解密成功本身就代表对端已通过共享密钥鉴权，因此只需回传最新健康信息即可。
     */
    suspend fun handlePing(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        val sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret)
        decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecret,
            serializer = LanSyncPingPayload.serializer()
        )
        syncPeerDao.updateHeartbeat(
            deviceId = peer.deviceId,
            displayName = peer.displayName,
            protocolVersion = peer.protocolVersion,
            lastSeenAt = timeProvider.nowEpochMillis(),
            missCount = 0
        )
        return encodeProtectedResponse(
            sharedSecret = sharedSecret,
            payload = LanSyncPingResponsePayload(
                deviceId = runtime.currentLocalProfile.deviceId,
                displayName = runtime.currentLocalProfile.displayName,
                shortDeviceId = runtime.currentLocalProfile.shortDeviceId,
                protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
                respondedAt = timeProvider.nowEpochMillis()
            ),
            serializer = LanSyncPingResponsePayload.serializer()
        )
    }

    /**
     * pull 只允许可信设备读取本机 journal 窗口，是为了把增量同步建立在明确授权的对端之上。
     */
    suspend fun handlePullChanges(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        val sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret)
        val payload: LanSyncPullChangesPayload = decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecret,
            serializer = LanSyncPullChangesPayload.serializer()
        )
        val changes: List<SyncChangePayload> = if (payload.headersOnly) {
            syncChangeDao.listAfterLimited(payload.afterSeq, LanSyncConfig.DEFAULT_PREVIEW_LIMIT).map { entity: SyncChangeEntity ->
                entity.toPayload().copy(payloadJson = null)
            }
        } else {
            syncChangeDao.listAfter(payload.afterSeq).map { entity: SyncChangeEntity -> entity.toPayload() }
        }
        return encodeProtectedResponse(
            sharedSecret = sharedSecret,
            payload = LanSyncPullChangesResponsePayload(
                changes = changes,
                latestSeq = syncChangeDao.findLatestSeq()
            ),
            serializer = LanSyncPullChangesResponsePayload.serializer()
        )
    }

    /**
     * push 会把对端增量变更直接应用到本机，因此必须在成功后同步推进“已收到远端 seq”游标。
     */
    suspend fun handlePushChanges(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        val sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret)
        val payload = decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecret,
            serializer = LanSyncPushChangesPayload.serializer()
        )
        val cursor = syncPeerCursorDao.findById(peer.deviceId) ?: emptyCursor(peer.deviceId)
        if (cursor.lastSessionId == payload.sessionId) {
            return encodeProtectedResponse(
                sharedSecret = sharedSecret,
                payload = LanSyncPushChangesResponsePayload(
                    appliedLocalSeqMax = cursor.lastRemoteSeqAppliedLocally
                ),
                serializer = LanSyncPushChangesResponsePayload.serializer()
            )
        }
        runtime.isApplyingChanges = true
        val appliedSeq = try {
            changeApplier.applyIncomingChanges(payload.changes)
        } finally {
            runtime.isApplyingChanges = false
        }
        syncPeerCursorDao.upsert(
            cursor.copy(
                lastRemoteSeqAppliedLocally = maxOf(cursor.lastRemoteSeqAppliedLocally, appliedSeq),
                lastSessionId = payload.sessionId
            )
        )
        return encodeProtectedResponse(
            sharedSecret = sharedSecret,
            payload = LanSyncPushChangesResponsePayload(appliedLocalSeqMax = appliedSeq),
            serializer = LanSyncPushChangesResponsePayload.serializer()
        )
    }

    /**
     * ack 只推进“对端已确认接收的本地 seq”，是为了把推送成功和对端真正消费成功这两个事实区分开。
     */
    suspend fun handleAck(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        val sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret)
        val payload = decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecret,
            serializer = LanSyncAckPayload.serializer()
        )
        val cursor = syncPeerCursorDao.findById(peer.deviceId) ?: emptyCursor(peer.deviceId)
        syncPeerCursorDao.upsert(
            cursor.copy(
                lastLocalSeqAckedByPeer = maxOf(cursor.lastLocalSeqAckedByPeer, payload.remoteSeqApplied),
                lastSessionId = payload.sessionId
            )
        )
        return encodeProtectedResponse(
            sharedSecret = sharedSecret,
            payload = LanSyncAckResponsePayload(accepted = true),
            serializer = LanSyncAckResponsePayload.serializer()
        )
    }

    /**
     * 真正的执行流程单独收口，是为了让外层 runSync 保持“启动任务并等待”的清晰职责。
     */
    private suspend fun runSyncInternal(
        preview: LanSyncPreview,
        resolutions: List<LanSyncConflictResolution>
    ) {
        val sessionId = UUID.randomUUID().toString()
        try {
            runtime.setProgress(
                stage = LanSyncStage.TRANSFERRING,
                message = "正在拉取远端变更"
            )
            val cursor = readCursor(preview.peer.deviceId)
            val sharedSecret = readSharedSecret(preview.peer.deviceId)
            val localChanges = loadCompressedLocalChanges(cursor.lastLocalSeqAckedByPeer)
            val remoteChanges = pullCompressedRemoteChanges(
                hostAddress = preview.peer.hostAddress,
                port = preview.peer.port,
                sharedSecret = sharedSecret,
                afterSeq = cursor.lastRemoteSeqAppliedLocally,
                headersOnly = false
            )
            val (localChangesToPush, remoteChangesToApply) = conflictResolver.applyConflictResolution(
                localChanges = localChanges,
                remoteChanges = remoteChanges,
                resolutions = resolutions
            )
            runtime.setProgress(
                stage = LanSyncStage.TRANSFERRING,
                message = "正在推送本地变更",
                itemsProcessed = localChangesToPush.size,
                totalItems = localChangesToPush.size + remoteChangesToApply.size
            )
            val pushResponse = if (localChangesToPush.isNotEmpty()) {
                httpClient.pushChanges(
                    hostAddress = preview.peer.hostAddress,
                    port = preview.peer.port,
                    requesterDeviceId = runtime.currentLocalProfile.deviceId,
                    sharedSecret = sharedSecret,
                    sessionId = sessionId,
                    changes = localChangesToPush
                )
            } else {
                LanSyncPushChangesResponsePayload(appliedLocalSeqMax = cursor.lastLocalSeqAckedByPeer)
            }
            runtime.setProgress(
                stage = LanSyncStage.APPLYING,
                message = "正在应用远端变更",
                totalItems = remoteChangesToApply.size
            )
            runtime.isApplyingChanges = true
            val appliedRemoteSeqMax = try {
                changeApplier.applyIncomingChanges(remoteChangesToApply)
            } finally {
                runtime.isApplyingChanges = false
            }
            if (appliedRemoteSeqMax > cursor.lastRemoteSeqAppliedLocally) {
                httpClient.ack(
                    hostAddress = preview.peer.hostAddress,
                    port = preview.peer.port,
                    requesterDeviceId = runtime.currentLocalProfile.deviceId,
                    sharedSecret = sharedSecret,
                    sessionId = sessionId,
                    remoteSeqApplied = appliedRemoteSeqMax
                )
            }
            syncPeerCursorDao.upsert(
                SyncPeerCursorEntity(
                    deviceId = preview.peer.deviceId,
                    lastLocalSeqAckedByPeer = maxOf(cursor.lastLocalSeqAckedByPeer, pushResponse.appliedLocalSeqMax),
                    lastRemoteSeqAppliedLocally = maxOf(cursor.lastRemoteSeqAppliedLocally, appliedRemoteSeqMax),
                    lastSessionId = sessionId
                )
            )
            refreshPeers(nsdService.services.value)
            runtime.sessionState.update {
                it.copy(
                    preview = null,
                    progress = it.progress.copy(
                        stage = LanSyncStage.COMPLETED,
                        message = "同步完成",
                        bytesTransferred = 0L,
                        totalBytes = null,
                        itemsProcessed = localChangesToPush.size + remoteChangesToApply.size,
                        totalItems = localChangesToPush.size + remoteChangesToApply.size
                    ),
                    activeFailure = null,
                    message = "已与 ${preview.peer.displayName} 完成双向同步"
                )
            }
        } catch (throwable: Throwable) {
            LanSyncLogger.e("Run sync failed", throwable)
            val cancelled = throwable is kotlinx.coroutines.CancellationException
            runtime.setProgress(
                stage = if (cancelled) LanSyncStage.CANCELLED else LanSyncStage.FAILED,
                message = if (cancelled) "同步已取消" else "同步失败",
                failure = if (cancelled) LanSyncFailureReason.CANCELLED else mapFailure(throwable)
            )
        } finally {
            runtime.activeSyncJob = null
        }
    }

    /**
     * cursor 读取封装成单点，是为了让预览与真正同步共享同一套“没有就从 0 起步”的规则。
     */
    private suspend fun readCursor(deviceId: String): SyncPeerCursorEntity =
        syncPeerCursorDao.findById(deviceId) ?: emptyCursor(deviceId)

    /**
     * 本地变更窗口读取与压缩收敛，是为了避免预览与执行阶段重复拼装 journal 查询与压缩模板。
     */
    private suspend fun loadCompressedLocalChanges(afterSeq: Long): List<SyncChangePayload> =
        conflictResolver.compressChanges(
            syncChangeDao.listAfter(afterSeq).map { entity: SyncChangeEntity -> entity.toPayload() }
        )

    /**
     * 远端变更窗口读取与压缩收敛，是为了让 headersOnly 预览与完整拉取共享同一调用路径并减少参数拼装噪音。
     */
    private suspend fun pullCompressedRemoteChanges(
        hostAddress: String,
        port: Int,
        sharedSecret: String,
        afterSeq: Long,
        headersOnly: Boolean
    ): List<SyncChangePayload> = conflictResolver.compressChanges(
        httpClient.pullChanges(
            hostAddress = hostAddress,
            port = port,
            requesterDeviceId = runtime.currentLocalProfile.deviceId,
            sharedSecret = sharedSecret,
            afterSeq = afterSeq,
            headersOnly = headersOnly
        ).changes
    )

    /**
     * 配对成功后立刻把可信设备写库，是为了让后续 pull、push 和 ping 都能围绕持久化共享密钥直接工作。
     */
    private suspend fun performPairing(peer: LanSyncPeer, pairingCode: String) {
        val hello = httpClient.hello(peer.hostAddress, peer.port)
        val sharedSecret = crypto.createSharedSecret()
        val accepted = httpClient.pair(
            hostAddress = peer.hostAddress,
            port = peer.port,
            hello = hello,
            initiatorDeviceId = runtime.currentLocalProfile.deviceId,
            initiatorDisplayName = runtime.currentLocalProfile.displayName,
            pairingCode = pairingCode,
            sharedSecret = sharedSecret
        )
        require(accepted) { "配对失败，请确认对方配对码是否正确" }
        syncPeerDao.upsert(
            SyncPeerEntity(
                deviceId = hello.deviceId,
                displayName = hello.displayName,
                shortDeviceId = hello.shortDeviceId,
                encryptedSharedSecret = sharedSecretProtector.encrypt(sharedSecret),
                protocolVersion = hello.protocolVersion,
                lastSeenAt = timeProvider.nowEpochMillis(),
                missCount = 0
            )
        )
        ensureCursorExists(hello.deviceId)
    }

    /**
     * 新 peer 首次出现时必须补上默认 cursor，才能让后续 preview 和 ack 逻辑不必反复判空分支。
     */
    private suspend fun ensureCursorExists(deviceId: String) {
        if (syncPeerCursorDao.findById(deviceId) != null) {
            return
        }
        syncPeerCursorDao.upsert(emptyCursor(deviceId))
    }

    /**
     * 空 cursor 以 0 为统一起点，是为了让首次同步自然表达成“从第一条本地/远端变更开始”。
     */
    private fun emptyCursor(deviceId: String): SyncPeerCursorEntity = SyncPeerCursorEntity(
        deviceId = deviceId,
        lastLocalSeqAckedByPeer = 0L,
        lastRemoteSeqAppliedLocally = 0L,
        lastSessionId = null
    )

    /**
     * 已信任设备的共享密钥读取集中在单点，是为了把解密数据库字段的细节从业务流程中剥离出去。
     */
    private suspend fun readSharedSecret(deviceId: String): String = syncPeerDao.findById(deviceId)
        ?.let { peer -> sharedSecretProtector.decrypt(peer.encryptedSharedSecret) }
        ?: error("设备未完成配对，无法继续同步")

    /**
     * 版本检查尽早失败，是为了把“需要升级”留在预览前，而不是等到真正传输时报协议不兼容。
     */
    private fun ensureProtocolSupported(peer: LanSyncPeer) {
        require(peer.protocolVersion == LanSyncConfig.PROTOCOL_VERSION) { "设备版本不兼容，请先升级两端应用" }
    }

    /**
     * 错误映射保持保守，是为了让页面能给出稳定可理解的用户提示，同时把详细原因留给日志。
     */
    private fun mapFailure(throwable: Throwable): LanSyncFailureReason = when {
        throwable.message?.contains("不兼容") == true -> LanSyncFailureReason.PROTOCOL_MISMATCH
        throwable.message?.contains("配对") == true -> LanSyncFailureReason.PAIRING_FAILED
        throwable.message?.contains("hash", ignoreCase = true) == true -> LanSyncFailureReason.HASH_MISMATCH
        else -> LanSyncFailureReason.UNKNOWN
    }

    /**
     * 受保护请求统一先解密业务载荷，是为了把每个端点共享的协议解码步骤收口到单点。
     */
    private fun <T> decodeProtectedPayload(
        request: LanSyncProtectedRequest,
        sharedSecret: String,
        serializer: KSerializer<T>
    ): T {
        val json = crypto.decrypt(
            payload = request.payload.toEncryptedPayload(),
            keyBytes = crypto.decodeSecret(sharedSecret)
        )
        return LanSyncJson.json.decodeFromString(serializer, json)
    }

    /**
     * 受保护响应统一用共享密钥加密，是为了让客户端处理每个端点时都能走同一套解密逻辑。
     */
    private fun <T> encodeProtectedResponse(
        sharedSecret: String,
        payload: T,
        serializer: KSerializer<T>
    ): LanSyncProtectedResponse {
        val encryptedPayload = crypto.encrypt(
            plainText = LanSyncJson.json.encodeToString(serializer, payload),
            keyBytes = crypto.decodeSecret(sharedSecret)
        )
        return LanSyncProtectedResponse(payload = encryptedPayload.toEnvelope())
    }

    /**
     * 网络 DTO 与加密工具对象彼此隔离，是为了让 Ktor 模型继续保持纯可序列化结构。
     */
    private fun LanSyncEncryptedEnvelope.toEncryptedPayload(): LanSyncCrypto.EncryptedPayload =
        LanSyncCrypto.EncryptedPayload(
            iv = iv,
            cipherText = cipherText
        )

    /**
     * 加密响应回写到网络 DTO 时统一走单点转换，是为了避免每个端点各自重复拼装 iv 和 cipherText。
     */
    private fun LanSyncCrypto.EncryptedPayload.toEnvelope(): LanSyncEncryptedEnvelope =
        LanSyncEncryptedEnvelope(
            iv = iv,
            cipherText = cipherText
        )
}

