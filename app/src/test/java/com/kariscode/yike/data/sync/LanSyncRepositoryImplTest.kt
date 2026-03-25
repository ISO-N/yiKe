package com.kariscode.yike.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.local.db.entity.SyncChangeEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerCursorEntity
import com.kariscode.yike.data.local.db.entity.SyncPeerEntity
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.LanSyncPeerHealth
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncSessionState
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.domain.model.LanSyncTrustState
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.testsupport.FakeAppSettingsRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LanSyncRepositoryImpl 测试直接覆盖会话、首次配对和双向增量编排，
 * 避免同步仓储只在 ViewModel 层间接验证而缺少真正的协议执行门禁。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LanSyncRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var database: YikeDatabase
    private lateinit var workManager: WorkManager
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var dispatchers: AppDispatchers
    private lateinit var timeProvider: FixedTimeProvider
    private lateinit var crypto: LanSyncCrypto
    private lateinit var localProfileStore: LanSyncLocalProfileStore
    private lateinit var sharedSecretProtector: FakeSharedSecretProtector
    private lateinit var discoveryService: FakeDiscoveryService
    private lateinit var transportClient: FakeTransportClient
    private lateinit var transportServer: FakeTransportServer
    private lateinit var repository: LanSyncRepositoryImpl

    /**
     * 真实 Room、真实本机档案和测试 WorkManager 一起装配后，
     * 同步仓储测试就能覆盖编排层而不依赖页面或网络真机环境。
     */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(context)
        database = Room.inMemoryDatabaseBuilder(context, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appSettingsRepository = FakeAppSettingsRepository()
        timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
        reminderScheduler = ReminderScheduler(
            workManager = workManager,
            appSettingsRepository = appSettingsRepository,
            timeProvider = timeProvider
        )
        val testDispatcher = UnconfinedTestDispatcher()
        dispatchers = object : AppDispatchers {
            override val main: CoroutineDispatcher = testDispatcher
            override val io: CoroutineDispatcher = testDispatcher
            override val default: CoroutineDispatcher = testDispatcher
        }
        crypto = LanSyncCrypto()
        localProfileStore = LanSyncLocalProfileStore(context = context, crypto = crypto)
        sharedSecretProtector = FakeSharedSecretProtector()
        discoveryService = FakeDiscoveryService()
        transportClient = FakeTransportClient()
        transportServer = FakeTransportServer()
        repository = createRepository()
    }

    /**
     * 每条用例后关闭数据库与 WorkManager 队列，避免同步状态串到下一条用例。
     */
    @After
    fun tearDown() {
        transportServer.stop()
        workManager.cancelAllWork()
        database.close()
    }

    /**
     * 启动会话时必须拉起本机监听、注册发现广播并把候选设备合并进状态，
     * 否则同步页会停留在活跃态却始终看不到任何可选设备。
     */
    @Test
    fun startSession_registersDiscoveryAndPublishesUntrustedPeer() = runTest {
        discoveryService.services.value = listOf(
            LanSyncDiscoveredService(
                serviceName = "peer-service",
                hostAddress = "192.168.0.8",
                port = 9527
            )
        )
        transportClient.helloResponses["192.168.0.8:9527"] = helloResponse(
            deviceId = "peer_1",
            displayName = "Peer One"
        )

        repository.startSession()
        advanceUntilIdle()

        val state = repository.observeSessionState().first { session -> session.peers.isNotEmpty() }
        assertTrue(state.isSessionActive)
        assertEquals(LanSyncStage.DISCOVERING, state.progress.stage)
        assertEquals(1, state.peers.size)
        assertEquals(LanSyncTrustState.UNTRUSTED, state.peers.single().trustState)
        assertEquals(LanSyncPeerHealth.AVAILABLE, state.peers.single().health)
        assertEquals(1, transportServer.startCalls)
        assertEquals(1, discoveryService.registerCalls.size)
        assertEquals(1, discoveryService.startDiscoveryCalls)
        assertTrue(discoveryService.registerCalls.single().serviceName.startsWith("yike-"))
        assertEquals(transportServer.port, discoveryService.registerCalls.single().port)

        repository.stopSession()
    }

    /**
     * 首次同步必须完成配对、写入可信设备并生成冲突预览，
     * 否则用户会在没有信任关系的前提下直接进入数据传输阶段。
     */
    @Test
    fun prepareSync_firstPairing_pairsPeerAndBuildsConflictPreview() = runTest {
        val service = LanSyncDiscoveredService(
            serviceName = "peer-service",
            hostAddress = "192.168.0.9",
            port = 9528
        )
        discoveryService.services.value = listOf(service)
        transportClient.helloResponses["192.168.0.9:9528"] = helloResponse(
            deviceId = "peer_pair",
            displayName = "Pair Peer"
        )
        seedLocalDeckChange(
            entityId = "deck_conflict",
            name = "本地数学",
            payloadHash = "local-hash",
            modifiedAt = 10L
        )
        transportClient.previewPullResponse = LanSyncPullChangesResponsePayload(
            changes = listOf(
                remoteDeckChange(
                    seq = 5L,
                    entityId = "deck_conflict",
                    name = "远端数学",
                    payloadHash = "remote-hash"
                )
            ),
            latestSeq = 5L
        )

        repository.startSession()
        advanceUntilIdle()
        val peer = repository.observeSessionState().first { session -> session.peers.isNotEmpty() }.peers.single()

        val preview = repository.prepareSync(peer = peer, pairingCode = "123456")

        val trustedPeer = database.syncPeerDao().findById("peer_pair")
        val cursor = database.syncPeerCursorDao().findById("peer_pair")
        assertTrue(preview.isFirstPairing)
        assertEquals(2, preview.localChangeCount)
        assertEquals(1, preview.remoteChangeCount)
        assertEquals(1, preview.conflicts.size)
        assertEquals(1, transportClient.pairCalls.size)
        assertNotNull(trustedPeer)
        assertNotNull(cursor)

        repository.stopSession()
    }

    /**
     * 协议版本不兼容必须在任何网络拉取前立刻失败，
     * 否则用户会在注定无法同步的设备上看到一段误导性的加载过程。
     */
    @Test
    fun prepareSync_protocolMismatchFailsBeforeNetworkCalls() = runTest {
        val incompatiblePeer = LanSyncPeer(
            deviceId = "peer_old",
            displayName = "Old Peer",
            shortDeviceId = "peerld",
            hostAddress = "192.168.0.11",
            port = 9530,
            protocolVersion = LanSyncConfig.PROTOCOL_VERSION - 1,
            trustState = LanSyncTrustState.TRUSTED,
            health = LanSyncPeerHealth.AVAILABLE,
            lastSeenAt = timeProvider.nowEpochMillis()
        )

        val failure = runCatching {
            repository.prepareSync(peer = incompatiblePeer, pairingCode = null)
        }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("设备版本不兼容，请先升级两端应用", failure?.message)
        assertTrue(transportClient.pullCalls.isEmpty())
        assertTrue(transportClient.pairCalls.isEmpty())
    }

    /**
     * 启动会话前补齐历史快照到 journal，
     * 是为了让首次启用同步能力前就已存在的真实数据也能进入预览与双向同步。
     */
    @Test
    fun startSession_seedsMissingJournalFromExistingSnapshot() = runTest {
        database.deckDao().upsert(
            DeckEntity(
                id = "deck_existing",
                name = "已有卡组",
                description = "",
                tagsJson = "[]",
                intervalStepCount = 8,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        database.cardDao().upsert(
            CardEntity(
                id = "card_existing",
                deckId = "deck_existing",
                title = "已有卡片",
                description = "",
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 2L
            )
        )
        database.questionDao().upsertAll(
            listOf(
                QuestionEntity(
                    id = "question_existing",
                    cardId = "card_existing",
                    prompt = "已有问题",
                    answer = "已有答案",
                    tagsJson = "[]",
                    status = "ACTIVE",
                    stageIndex = 0,
                    dueAt = 3L,
                    lastReviewedAt = null,
                    reviewCount = 0,
                    lapseCount = 0,
                    createdAt = 1L,
                    updatedAt = 2L
                )
            )
        )
        database.reviewRecordDao().insert(
            ReviewRecordEntity(
                id = "review_existing",
                questionId = "question_existing",
                rating = "GOOD",
                oldStageIndex = 0,
                newStageIndex = 1,
                oldDueAt = 3L,
                newDueAt = 4L,
                reviewedAt = 5L,
                responseTimeMs = 600L,
                note = ""
            )
        )

        repository.startSession()
        advanceUntilIdle()

        val changes = database.syncChangeDao().listAfter(afterSeq = 0L)

        assertTrue(changes.any { change -> change.entityType == SyncEntityType.SETTINGS.name })
        assertTrue(changes.any { change -> change.entityType == SyncEntityType.DECK.name && change.entityId == "deck_existing" })
        assertTrue(changes.any { change -> change.entityType == SyncEntityType.CARD.name && change.entityId == "card_existing" })
        assertTrue(changes.any { change -> change.entityType == SyncEntityType.QUESTION.name && change.entityId == "question_existing" })
        assertTrue(changes.any { change -> change.entityType == SyncEntityType.REVIEW_RECORD.name && change.entityId == "review_existing" })

        repository.stopSession()
    }

    /**
     * 双向同步完成后必须推送本地变更、应用远端变更、发送 ack 并推进双游标，
     * 否则下一次会话会重复上传旧变更或丢失远端已落库结果。
     */
    @Test
    fun runSync_pushesLocalChangesAppliesRemoteChangesAndAdvancesCursor() = runTest {
        val service = LanSyncDiscoveredService(
            serviceName = "peer-service",
            hostAddress = "192.168.0.10",
            port = 9529
        )
        val sharedSecret = crypto.createSharedSecret()
        database.syncPeerDao().upsert(
            SyncPeerEntity(
                deviceId = "peer_trusted",
                displayName = "Trusted Peer",
                shortDeviceId = "trusted",
                encryptedSharedSecret = sharedSecretProtector.encrypt(sharedSecret),
                protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
                lastSeenAt = timeProvider.nowEpochMillis(),
                missCount = 0
            )
        )
        database.syncPeerCursorDao().upsert(
            SyncPeerCursorEntity(
                deviceId = "peer_trusted",
                lastLocalSeqAckedByPeer = 0L,
                lastRemoteSeqAppliedLocally = 0L,
                lastSessionId = null
            )
        )
        discoveryService.services.value = listOf(service)
        transportClient.helloResponses["192.168.0.10:9529"] = helloResponse(
            deviceId = "peer_trusted",
            displayName = "Trusted Peer"
        )
        seedLocalDeckChange(
            entityId = "deck_local",
            name = "本地卡组",
            payloadHash = "local-push-hash",
            modifiedAt = 100L
        )
        transportClient.previewPullResponse = LanSyncPullChangesResponsePayload(
            changes = listOf(
                remoteDeckChange(
                    seq = 8L,
                    entityId = "deck_remote",
                    name = "远端卡组",
                    payloadHash = "remote-preview-hash"
                )
            ),
            latestSeq = 8L
        )
        transportClient.syncPullResponse = LanSyncPullChangesResponsePayload(
            changes = listOf(
                remoteDeckChange(
                    seq = 8L,
                    entityId = "deck_remote",
                    name = "远端卡组",
                    payloadHash = "remote-apply-hash"
                )
            ),
            latestSeq = 8L
        )
        transportClient.pushResponse = LanSyncPushChangesResponsePayload(appliedLocalSeqMax = 1L)

        repository.startSession()
        advanceUntilIdle()
        val peer = repository.observeSessionState().first { session -> session.peers.isNotEmpty() }.peers.single()
        val preview = repository.prepareSync(peer = peer, pairingCode = null)

        repository.runSync(preview = preview, resolutions = emptyList<LanSyncConflictResolution>())
        advanceUntilIdle()

        val state = repository.observeSessionState().first { session -> session.progress.stage == LanSyncStage.COMPLETED }
        val cursor = database.syncPeerCursorDao().findById("peer_trusted")
        val decks = database.deckDao().listAll()

        assertEquals(LanSyncStage.COMPLETED, state.progress.stage)
        assertEquals(
            listOf("app_settings", "deck_local"),
            transportClient.pushCalls.single().changes.map { it.entityId }.sorted()
        )
        assertEquals(1, transportClient.ackCalls.size)
        assertEquals(8L, transportClient.ackCalls.single().remoteSeqApplied)
        assertEquals(1L, cursor!!.lastLocalSeqAckedByPeer)
        assertEquals(8L, cursor.lastRemoteSeqAppliedLocally)
        assertTrue(decks.any { deck -> deck.id == "deck_remote" })

        repository.stopSession()
    }

    /**
     * 统一构造仓储能把测试重点放在同步语义，而不是反复拼装同一套基础依赖。
     */
    private fun createRepository(): LanSyncRepositoryImpl = LanSyncRepositoryImpl(
        context = context,
        database = database,
        appSettingsRepository = appSettingsRepository,
        reminderScheduler = reminderScheduler,
        timeProvider = timeProvider,
            dispatchers = dispatchers,
            localProfileStore = localProfileStore,
            crypto = crypto,
            sharedSecretProtector = sharedSecretProtector,
            portAllocator = LanSyncPortAllocator(),
            syncChangeDao = database.syncChangeDao(),
            syncPeerDao = database.syncPeerDao(),
        syncPeerCursorDao = database.syncPeerCursorDao(),
        deckDao = database.deckDao(),
        cardDao = database.cardDao(),
        questionDao = database.questionDao(),
        reviewRecordDao = database.reviewRecordDao(),
        discoveryService = discoveryService,
        transportClient = transportClient,
        transportServer = transportServer
    )

    /**
     * 本地 journal 变更显式写入数据库，是为了让预览和执行都走真实游标读取路径。
     */
    private suspend fun seedLocalDeckChange(
        entityId: String,
        name: String,
        payloadHash: String,
        modifiedAt: Long
    ) {
        val payloadJson = LanSyncJson.json.encodeToString(
            SyncDeckPayload.serializer(),
            SyncDeckPayload(
                id = entityId,
                name = name,
                description = "",
                tags = emptyList(),
                intervalStepCount = 8,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = modifiedAt
            )
        )
        database.syncChangeDao().insert(
            SyncChangeEntity(
                entityType = SyncEntityType.DECK.storageValue(),
                entityId = entityId,
                operation = SyncChangeOperation.UPSERT.storageValue(),
                summary = name,
                payloadJson = payloadJson,
                payloadHash = payloadHash,
                modifiedAt = modifiedAt
            )
        )
    }

    /**
     * 远端 deck 变更统一生成协议载荷，便于预览和真正执行复用同一测试数据。
     */
    private fun remoteDeckChange(
        seq: Long,
        entityId: String,
        name: String,
        payloadHash: String
    ): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.DECK.name,
        entityId = entityId,
        operation = SyncChangeOperation.UPSERT.name,
        summary = name,
        payloadJson = LanSyncJson.json.encodeToString(
            SyncDeckPayload.serializer(),
            SyncDeckPayload(
                id = entityId,
                name = name,
                description = "",
                tags = emptyList(),
                intervalStepCount = 8,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = seq
            )
        ),
        payloadHash = payloadHash,
        modifiedAt = seq
    )

    /**
     * hello 响应构造集中在单点后，测试可以只关心设备身份而不重复样板字段。
     */
    private fun helloResponse(
        deviceId: String,
        displayName: String
    ): LanSyncHelloResponse = LanSyncHelloResponse(
        deviceId = deviceId,
        displayName = displayName,
        shortDeviceId = deviceId.takeLast(6),
        protocolVersion = LanSyncConfig.PROTOCOL_VERSION,
        pairingNonce = "nonce_$deviceId"
    )

    /**
     * 假发现服务显式记录生命周期调用，是为了验证仓储确实在会话开始和结束时收放系统资源。
     */
    private class FakeDiscoveryService : LanSyncDiscoveryService {
        override val services = MutableStateFlow<List<LanSyncDiscoveredService>>(emptyList())
        val registerCalls = mutableListOf<RegisterCall>()
        var unregisterCalls: Int = 0
        var startDiscoveryCalls: Int = 0
        var stopDiscoveryCalls: Int = 0

        /**
         * 广播参数建模后，测试可以直接比较仓储传给发现层的服务名和端口。
         */
        data class RegisterCall(
            val serviceName: String,
            val port: Int
        )

        override fun registerService(serviceName: String, port: Int) {
            registerCalls += RegisterCall(serviceName = serviceName, port = port)
        }

        override fun unregisterService() {
            unregisterCalls += 1
        }

        override fun startDiscovery() {
            startDiscoveryCalls += 1
        }

        override fun stopDiscovery() {
            stopDiscoveryCalls += 1
        }
    }

    /**
     * 假传输服务只记录启动和停止次数，足以验证会话资源生命周期是否完整。
     */
    private class FakeTransportServer : LanSyncTransportServer {
        override val port: Int = 9450
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start() {
            startCalls += 1
        }

        override fun stop() {
            stopCalls += 1
        }
    }

    /**
     * 测试里的共享密钥保护只需保持可逆语义，
     * 这样仓储编排测试就不必依赖 Android Keystore 运行环境。
     */
    private class FakeSharedSecretProtector : LanSyncSharedSecretProtector {
        override fun encrypt(secret: String): String = "test::$secret"

        override fun decrypt(encryptedSecret: String): String =
            encryptedSecret.removePrefix("test::")
    }

    /**
     * 假传输客户端按测试预设回放 hello/pair/pull/push/ack 结果，
     * 这样仓储测试能聚焦编排语义而不依赖真实网络。
     */
    private class FakeTransportClient : LanSyncTransportClient {
        val helloResponses = linkedMapOf<String, LanSyncHelloResponse>()
        val pairCalls = mutableListOf<PairCall>()
        val pullCalls = mutableListOf<PullCall>()
        val pushCalls = mutableListOf<PushCall>()
        val ackCalls = mutableListOf<AckCall>()
        var previewPullResponse: LanSyncPullChangesResponsePayload = LanSyncPullChangesResponsePayload(
            changes = emptyList(),
            latestSeq = 0L
        )
        var syncPullResponse: LanSyncPullChangesResponsePayload = LanSyncPullChangesResponsePayload(
            changes = emptyList(),
            latestSeq = 0L
        )
        var pushResponse: LanSyncPushChangesResponsePayload = LanSyncPushChangesResponsePayload(
            appliedLocalSeqMax = 0L
        )

        /**
         * 配对入参记录成结构体后，可以直接断言首次同步是否真的发生过密钥交换。
         */
        data class PairCall(
            val hostAddress: String,
            val port: Int,
            val initiatorDeviceId: String,
            val pairingCode: String,
            val sharedSecret: String
        )

        /**
         * pull 同时服务 preview 和 runSync，因此显式记录 headersOnly 才能区分两条路径。
         */
        data class PullCall(
            val hostAddress: String,
            val port: Int,
            val requesterDeviceId: String,
            val afterSeq: Long,
            val headersOnly: Boolean
        )

        /**
         * push 记录整批变更后，测试可以直接比对实际上传内容是否符合预期。
         */
        data class PushCall(
            val hostAddress: String,
            val port: Int,
            val requesterDeviceId: String,
            val sessionId: String,
            val changes: List<SyncChangePayload>
        )

        /**
         * ack 只保留最终确认的远端 seq，正好对应游标推进的关键事实。
         */
        data class AckCall(
            val hostAddress: String,
            val port: Int,
            val requesterDeviceId: String,
            val sessionId: String,
            val remoteSeqApplied: Long
        )

        override suspend fun hello(hostAddress: String, port: Int): LanSyncHelloResponse =
            helloResponses["$hostAddress:$port"] ?: error("No hello response for $hostAddress:$port")

        override suspend fun pair(
            hostAddress: String,
            port: Int,
            hello: LanSyncHelloResponse,
            initiatorDeviceId: String,
            initiatorDisplayName: String,
            pairingCode: String,
            sharedSecret: String
        ): Boolean {
            pairCalls += PairCall(
                hostAddress = hostAddress,
                port = port,
                initiatorDeviceId = initiatorDeviceId,
                pairingCode = pairingCode,
                sharedSecret = sharedSecret
            )
            return true
        }

        override suspend fun ping(
            hostAddress: String,
            port: Int,
            requesterDeviceId: String,
            sharedSecret: String,
            requestedAt: Long
        ) = error("Heartbeat is not part of this test suite")

        override suspend fun pullChanges(
            hostAddress: String,
            port: Int,
            requesterDeviceId: String,
            sharedSecret: String,
            afterSeq: Long,
            headersOnly: Boolean
        ): LanSyncPullChangesResponsePayload {
            pullCalls += PullCall(
                hostAddress = hostAddress,
                port = port,
                requesterDeviceId = requesterDeviceId,
                afterSeq = afterSeq,
                headersOnly = headersOnly
            )
            return if (headersOnly) previewPullResponse else syncPullResponse
        }

        override suspend fun pushChanges(
            hostAddress: String,
            port: Int,
            requesterDeviceId: String,
            sharedSecret: String,
            sessionId: String,
            changes: List<SyncChangePayload>
        ): LanSyncPushChangesResponsePayload {
            pushCalls += PushCall(
                hostAddress = hostAddress,
                port = port,
                requesterDeviceId = requesterDeviceId,
                sessionId = sessionId,
                changes = changes
            )
            return pushResponse
        }

        override suspend fun ack(
            hostAddress: String,
            port: Int,
            requesterDeviceId: String,
            sharedSecret: String,
            sessionId: String,
            remoteSeqApplied: Long
        ): LanSyncAckResponsePayload {
            ackCalls += AckCall(
                hostAddress = hostAddress,
                port = port,
                requesterDeviceId = requesterDeviceId,
                sessionId = sessionId,
                remoteSeqApplied = remoteSeqApplied
            )
            return LanSyncAckResponsePayload(accepted = true)
        }
    }
}

