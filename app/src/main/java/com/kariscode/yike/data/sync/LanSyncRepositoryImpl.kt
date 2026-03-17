package com.kariscode.yike.data.sync

import android.content.Context
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.dao.SyncPeerCursorDao
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.data.local.db.entity.SyncPeerCursorEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerEntity
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.LanSyncFailureReason
import com.kariscode.yike.domain.model.LanSyncLocalProfile
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPeerHealth
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncProgress
import com.kariscode.yike.domain.model.LanSyncSessionState
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.domain.model.LanSyncTrustState
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.LanSyncRepository
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer

/**
 * LAN Sync V2 仓储统一编排发现、配对、双向增量和本地应用，
 * 是为了把高风险同步语义压缩到单一组件，而不是让页面层直接拼装网络与数据库操作。
 */
class LanSyncRepositoryImpl(
    context: Context,
    private val database: YikeDatabase,
    private val appSettingsRepository: AppSettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    private val localProfileStore: LanSyncLocalProfileStore,
    private val crypto: LanSyncCrypto,
    private val sharedSecretProtector: LanSyncSharedSecretProtector = KeystoreLanSyncSharedSecretProtector(crypto),
    portAllocator: LanSyncPortAllocator,
    private val syncChangeDao: SyncChangeDao,
    private val syncPeerDao: SyncPeerDao,
    private val syncPeerCursorDao: SyncPeerCursorDao,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val questionDao: QuestionDao,
    private val reviewRecordDao: ReviewRecordDao,
    discoveryService: LanSyncDiscoveryService? = null,
    transportClient: LanSyncTransportClient? = null,
    transportServer: LanSyncTransportServer? = null,
    private val conflictResolver: LanSyncConflictResolver = LanSyncConflictResolver(),
    private val changeApplier: LanSyncChangeApplier = LanSyncChangeApplier(
        database = database,
        appSettingsRepository = appSettingsRepository,
        reminderScheduler = reminderScheduler,
        deckDao = deckDao,
        cardDao = cardDao,
        questionDao = questionDao,
        reviewRecordDao = reviewRecordDao,
        conflictResolver = conflictResolver
    )
) : LanSyncRepository {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val nsdService: LanSyncDiscoveryService = discoveryService ?: LanSyncNsdService(context = context)
    private val httpClient: LanSyncTransportClient = transportClient ?: LanSyncHttpClient(crypto = crypto)
    private var discoveryJob: Job? = null
    private var heartbeatJob: Job? = null
    private var activeSyncJob: Job? = null
    private var currentPairingNonce: String = crypto.createSharedSecret()
    private var currentLocalProfile: LanSyncLocalProfile = LanSyncLocalProfile(
        deviceId = "loading",
        displayName = "当前设备",
        shortDeviceId = "------",
        pairingCode = "------"
    )
    private var isApplyingChanges: Boolean = false
    private val httpServer: LanSyncTransportServer = transportServer ?: LanSyncHttpServer(
        portAllocator = portAllocator,
        onHello = ::handleHello,
        onPairInit = ::handlePairInit,
        onPing = ::handlePing,
        onPullChanges = ::handlePullChanges,
        onPushChanges = ::handlePushChanges,
        onAck = ::handleAck
    )
    private val sessionState = MutableStateFlow(
        LanSyncSessionState(
            localProfile = currentLocalProfile,
            peers = emptyList(),
            isSessionActive = false,
            preview = null,
            progress = LanSyncProgress(
                stage = LanSyncStage.IDLE,
                message = "等待开始发现",
                bytesTransferred = 0L,
                totalBytes = null,
                itemsProcessed = 0,
                totalItems = null
            ),
            activeFailure = null,
            message = null
        )
    )

    /**
     * 同步页只需订阅单一状态流，就能拿到发现、预览和执行全过程，是为了让页面侧保持稳定的状态机边界。
     */
    override fun observeSessionState(): Flow<LanSyncSessionState> = sessionState.asStateFlow()

    /**
     * 启动会话时统一准备本机身份、广播服务、开始发现和心跳，是为了把网络暴露窗口严格限制在同步页打开期间。
     */
    override suspend fun startSession() = withContext(dispatchers.io) {
        if (sessionState.value.isSessionActive) {
            return@withContext
        }
        currentPairingNonce = crypto.createSharedSecret()
        currentLocalProfile = localProfileStore.loadProfile()
        sessionState.update {
            it.copy(
                localProfile = currentLocalProfile,
                isSessionActive = true,
                preview = null,
                progress = LanSyncProgress(
                    stage = LanSyncStage.DISCOVERING,
                    message = "正在发现设备",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                ),
                activeFailure = null,
                message = null
            )
        }
        httpServer.start()
        nsdService.registerService(
            serviceName = "yike-${currentLocalProfile.shortDeviceId.lowercase()}",
            port = httpServer.port
        )
        nsdService.startDiscovery()
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            nsdService.services.collect { services ->
                refreshPeers(services)
            }
        }
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(LanSyncConfig.HEARTBEAT_INTERVAL_MILLIS)
                runCatching { heartbeatTrustedPeers() }
                    .onFailure { throwable -> LanSyncLogger.e("Heartbeat loop failed", throwable) }
            }
        }
    }

    /**
     * 结束会话时统一停掉发现、广播和后台任务，是为了避免同步页退出后仍持续占用局域网和电量资源。
     */
    override suspend fun stopSession() = withContext(dispatchers.io) {
        activeSyncJob?.cancel()
        activeSyncJob = null
        discoveryJob?.cancelAndJoin()
        heartbeatJob?.cancelAndJoin()
        discoveryJob = null
        heartbeatJob = null
        nsdService.stopDiscovery()
        nsdService.unregisterService()
        httpServer.stop()
        sessionState.update {
            it.copy(
                peers = emptyList(),
                isSessionActive = false,
                preview = null,
                progress = LanSyncProgress(
                    stage = LanSyncStage.IDLE,
                    message = "同步会话已结束",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                ),
                activeFailure = null,
                message = null
            )
        }
    }

    /**
     * 设备名更新要立即刷新本地状态，是为了让用户改名后无需重新进入页面就能看到新身份。
     */
    override suspend fun updateLocalDisplayName(displayName: String) = withContext(dispatchers.io) {
        localProfileStore.updateDisplayName(displayName)
        currentLocalProfile = localProfileStore.loadProfile()
        sessionState.update { it.copy(localProfile = currentLocalProfile) }
    }

    /**
     * 预览阶段先完成配对和增量摘要读取，是为了在真正传输任何双向数据前把冲突和影响规模暴露给用户。
     */
    override suspend fun prepareSync(peer: LanSyncPeer, pairingCode: String?): LanSyncPreview = withContext(dispatchers.io) {
        ensureProtocolSupported(peer)
        val isFirstPairing = peer.trustState == LanSyncTrustState.UNTRUSTED
        if (isFirstPairing) {
            require(!pairingCode.isNullOrBlank()) { "首次同步需要输入对方配对码" }
            performPairing(peer = peer, pairingCode = pairingCode)
            refreshPeers(nsdService.services.value)
        }
        sessionState.update {
            it.copy(
                preview = null,
                progress = LanSyncProgress(
                    stage = LanSyncStage.PREVIEWING,
                    message = "正在生成同步预览",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                ),
                activeFailure = null,
                message = null
            )
        }
        val trustedPeer = sessionState.value.peers.first { it.deviceId == peer.deviceId }
        val cursor = syncPeerCursorDao.findById(peer.deviceId) ?: emptyCursor(peer.deviceId)
        val localChanges = conflictResolver.compressChanges(syncChangeDao.listAfter(cursor.lastLocalSeqAckedByPeer).map { it.toPayload() })
        val remoteChanges = conflictResolver.compressChanges(
            httpClient.pullChanges(
                hostAddress = trustedPeer.hostAddress,
                port = trustedPeer.port,
                requesterDeviceId = currentLocalProfile.deviceId,
                sharedSecret = readSharedSecret(peer.deviceId),
                afterSeq = cursor.lastRemoteSeqAppliedLocally,
                headersOnly = true
            ).changes
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
        sessionState.update { it.copy(preview = preview, progress = it.progress.copy(message = "同步预览已生成")) }
        preview
    }

    /**
     * 真正执行同步前先把冲突决议固定下来，是为了让协议应用结果与用户确认保持一致且可重放。
     */
    override suspend fun runSync(
        preview: LanSyncPreview,
        resolutions: List<LanSyncConflictResolution>
    ) {
        withContext(dispatchers.io) {
        require(preview.conflicts.size == resolutions.size || preview.conflicts.isEmpty()) {
            "冲突决议数量与预览不一致"
        }
        ensureProtocolSupported(preview.peer)
        activeSyncJob?.cancelAndJoin()
        activeSyncJob = scope.launch {
            runSyncInternal(preview = preview, resolutions = resolutions)
        }
        activeSyncJob?.join()
        }
    }

    /**
     * 取消只允许在提交事务前发生，是为了避免在 Room 已进入关键写入阶段时把本地数据留在未知中间态。
     */
    override suspend fun cancelActiveSync() = withContext(dispatchers.io) {
        if (isApplyingChanges) {
            return@withContext
        }
        activeSyncJob?.cancelAndJoin()
        activeSyncJob = null
        sessionState.update {
            it.copy(
                progress = LanSyncProgress(
                    stage = LanSyncStage.CANCELLED,
                    message = "同步已取消",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                ),
                activeFailure = LanSyncFailureReason.CANCELLED,
                message = null
            )
        }
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
            sessionState.update {
                it.copy(
                    progress = LanSyncProgress(
                        stage = LanSyncStage.TRANSFERRING,
                        message = "正在拉取远端变更",
                        bytesTransferred = 0L,
                        totalBytes = null,
                        itemsProcessed = 0,
                        totalItems = null
                    ),
                    activeFailure = null,
                    message = null
                )
            }
            val cursor = syncPeerCursorDao.findById(preview.peer.deviceId) ?: emptyCursor(preview.peer.deviceId)
            val sharedSecret = readSharedSecret(preview.peer.deviceId)
            val localChanges = conflictResolver.compressChanges(syncChangeDao.listAfter(cursor.lastLocalSeqAckedByPeer).map { it.toPayload() })
            val remoteChangesResponse = httpClient.pullChanges(
                hostAddress = preview.peer.hostAddress,
                port = preview.peer.port,
                requesterDeviceId = currentLocalProfile.deviceId,
                sharedSecret = sharedSecret,
                afterSeq = cursor.lastRemoteSeqAppliedLocally,
                headersOnly = false
            )
            val remoteChanges = conflictResolver.compressChanges(remoteChangesResponse.changes)
            val (localChangesToPush, remoteChangesToApply) = conflictResolver.applyConflictResolution(
                localChanges = localChanges,
                remoteChanges = remoteChanges,
                resolutions = resolutions
            )
            sessionState.update {
                it.copy(
                    progress = LanSyncProgress(
                        stage = LanSyncStage.TRANSFERRING,
                        message = "正在推送本地变更",
                        bytesTransferred = 0L,
                        totalBytes = null,
                        itemsProcessed = localChangesToPush.size,
                        totalItems = localChangesToPush.size + remoteChangesToApply.size
                    )
                )
            }
            val pushResponse = if (localChangesToPush.isNotEmpty()) {
                httpClient.pushChanges(
                    hostAddress = preview.peer.hostAddress,
                    port = preview.peer.port,
                    requesterDeviceId = currentLocalProfile.deviceId,
                    sharedSecret = sharedSecret,
                    sessionId = sessionId,
                    changes = localChangesToPush
                )
            } else {
                LanSyncPushChangesResponsePayload(appliedLocalSeqMax = cursor.lastLocalSeqAckedByPeer)
            }
            sessionState.update {
                it.copy(
                    progress = LanSyncProgress(
                        stage = LanSyncStage.APPLYING,
                        message = "正在应用远端变更",
                        bytesTransferred = 0L,
                        totalBytes = null,
                        itemsProcessed = 0,
                        totalItems = remoteChangesToApply.size
                    )
                )
            }
            isApplyingChanges = true
            val appliedRemoteSeqMax = try {
                changeApplier.applyIncomingChanges(remoteChangesToApply)
            } finally {
                isApplyingChanges = false
            }
            if (appliedRemoteSeqMax > cursor.lastRemoteSeqAppliedLocally) {
                httpClient.ack(
                    hostAddress = preview.peer.hostAddress,
                    port = preview.peer.port,
                    requesterDeviceId = currentLocalProfile.deviceId,
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
            sessionState.update {
                it.copy(
                    preview = null,
                    progress = LanSyncProgress(
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
            sessionState.update {
                it.copy(
                    progress = LanSyncProgress(
                        stage = if (throwable is kotlinx.coroutines.CancellationException) {
                            LanSyncStage.CANCELLED
                        } else {
                            LanSyncStage.FAILED
                        },
                        message = if (throwable is kotlinx.coroutines.CancellationException) "同步已取消" else "同步失败",
                        bytesTransferred = 0L,
                        totalBytes = null,
                        itemsProcessed = 0,
                        totalItems = null
                    ),
                    activeFailure = if (throwable is kotlinx.coroutines.CancellationException) {
                        LanSyncFailureReason.CANCELLED
                    } else {
                        mapFailure(throwable)
                    },
                    message = null
                )
            }
        } finally {
            activeSyncJob = null
        }
    }

    /**
     * hello 统一从当前本机档案读取，是为了让发现页看到的设备名始终和本机页面上的设备名一致。
     */
    private suspend fun handleHello(): LanSyncHelloResponse = LanSyncHelloResponse(
        deviceId = currentLocalProfile.deviceId,
        displayName = currentLocalProfile.displayName,
        shortDeviceId = currentLocalProfile.shortDeviceId,
        protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
        pairingNonce = currentPairingNonce
    )

    /**
     * 首次配对通过临时密钥解开共享密钥并持久化可信设备，是为了把首次信任建立控制在用户输入配对码的窗口内。
     */
    private suspend fun handlePairInit(request: LanSyncPairInitRequest): LanSyncPairInitResponse {
        val key = crypto.derivePairingKey(
            pairingCode = currentLocalProfile.pairingCode,
            deviceId = currentLocalProfile.deviceId,
            nonce = currentPairingNonce
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
    private suspend fun handlePing(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret),
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
            sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret),
            payload = LanSyncPingResponsePayload(
                deviceId = currentLocalProfile.deviceId,
                displayName = currentLocalProfile.displayName,
                shortDeviceId = currentLocalProfile.shortDeviceId,
                protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
                respondedAt = timeProvider.nowEpochMillis()
            ),
            serializer = LanSyncPingResponsePayload.serializer()
        )
    }

    /**
     * pull 只允许可信设备读取本机 journal 窗口，是为了把增量同步建立在明确授权的对端之上。
     */
    private suspend fun handlePullChanges(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
        val peer = syncPeerDao.findById(request.requesterDeviceId)
            ?: error("未信任的设备无法访问局域网同步接口")
        val sharedSecret = sharedSecretProtector.decrypt(peer.encryptedSharedSecret)
        val payload = decodeProtectedPayload(
            request = request,
            sharedSecret = sharedSecret,
            serializer = LanSyncPullChangesPayload.serializer()
        )
        val changes = if (payload.headersOnly) {
            syncChangeDao.listAfterLimited(payload.afterSeq, LanSyncConfig.DEFAULT_PREVIEW_LIMIT).map { entity ->
                entity.toPayload().copy(payloadJson = null)
            }
        } else {
            syncChangeDao.listAfter(payload.afterSeq).map { it.toPayload() }
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
    private suspend fun handlePushChanges(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
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
        isApplyingChanges = true
        val appliedSeq = try {
            changeApplier.applyIncomingChanges(payload.changes)
        } finally {
            isApplyingChanges = false
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
    private suspend fun handleAck(request: LanSyncProtectedRequest): LanSyncProtectedResponse {
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
     * 配对成功后立刻把可信设备写库，是为了让后续 pull/push/ping 都能围绕持久化共享密钥直接工作。
     */
    private suspend fun performPairing(peer: LanSyncPeer, pairingCode: String) {
        val hello = httpClient.hello(peer.hostAddress, peer.port)
        val sharedSecret = crypto.createSharedSecret()
        val accepted = httpClient.pair(
            hostAddress = peer.hostAddress,
            port = peer.port,
            hello = hello,
            initiatorDeviceId = currentLocalProfile.deviceId,
            initiatorDisplayName = currentLocalProfile.displayName,
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
     * 发现层候选地址和可信设备表需要合并后才能生成真正可展示的 peer 列表，
     * 因此刷新逻辑集中在单点可以避免 UI 看到时而只剩地址、时而只剩信任状态的半成品。
     */
    private suspend fun refreshPeers(services: List<LanSyncDiscoveredService>) {
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
        sessionState.update { it.copy(peers = peers) }
    }

    /**
     * 只对可信设备做心跳，是为了避免未配对设备在未授权前就能借由 ping 获得更高频的在线信息。
     */
    private suspend fun heartbeatTrustedPeers() {
        val peers = sessionState.value.peers.filter { peer -> peer.trustState == LanSyncTrustState.TRUSTED }
        peers.forEach { peer ->
            val trustedPeer = syncPeerDao.findById(peer.deviceId) ?: return@forEach
            val sharedSecret = sharedSecretProtector.decrypt(trustedPeer.encryptedSharedSecret)
            runCatching {
                httpClient.ping(
                    hostAddress = peer.hostAddress,
                    port = peer.port,
                    requesterDeviceId = currentLocalProfile.deviceId,
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

    /**
     * 冲突检测只比较同一实体最新的一条变更，是为了避免中间态编辑把预览列表刷成多条重复提示。
     */
    /**
     * 受保护请求统一先查可信设备再解密，是为了把“未配对设备直接访问同步端点”的风险拦在同一入口。
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
     * 网络 DTO 与加密工具对象彼此隔离，是为了让 Ktor 模型继续保持纯可序列化结构。
     */
    private fun LanSyncEncryptedEnvelope.toEncryptedPayload(): LanSyncCrypto.EncryptedPayload =
        LanSyncCrypto.EncryptedPayload(
            iv = iv,
            cipherText = cipherText
        )

    /**
     * 加密响应回写到网络 DTO 时统一走单点转换，是为了避免每个端点各自重复拼装 iv/cipherText。
     */
    private fun LanSyncCrypto.EncryptedPayload.toEnvelope(): LanSyncEncryptedEnvelope =
        LanSyncEncryptedEnvelope(
            iv = iv,
            cipherText = cipherText
        )
}
