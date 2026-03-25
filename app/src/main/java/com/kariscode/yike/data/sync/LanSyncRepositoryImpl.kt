package com.kariscode.yike.data.sync

import android.content.Context
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.dao.SyncPeerCursorDao
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncSessionState
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.LanSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * LAN Sync 仓储对外继续只暴露单一同步能力入口，
 * 但内部把会话、发现和协议执行拆成协作者，是为了在不改变页面契约的前提下压缩实现复杂度。
 */
class LanSyncRepositoryImpl(
    context: Context,
    database: YikeDatabase,
    appSettingsRepository: AppSettingsRepository,
    reminderScheduler: ReminderScheduler,
    timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    localProfileStore: LanSyncLocalProfileStore,
    crypto: LanSyncCrypto,
    sharedSecretProtector: LanSyncSharedSecretProtector = KeystoreLanSyncSharedSecretProtector(crypto),
    portAllocator: LanSyncPortAllocator,
    syncChangeDao: SyncChangeDao,
    syncPeerDao: SyncPeerDao,
    syncPeerCursorDao: SyncPeerCursorDao,
    deckDao: DeckDao,
    cardDao: CardDao,
    questionDao: QuestionDao,
    reviewRecordDao: ReviewRecordDao,
    discoveryService: LanSyncDiscoveryService? = null,
    transportClient: LanSyncTransportClient? = null,
    transportServer: LanSyncTransportServer? = null,
    conflictResolver: LanSyncConflictResolver = LanSyncConflictResolver(),
    changeApplier: LanSyncChangeApplier = LanSyncChangeApplier(
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
    private val runtime = LanSyncSessionRuntime(scope = scope, crypto = crypto)
    private val nsdService: LanSyncDiscoveryService = discoveryService ?: LanSyncNsdService(context = context)
    private val httpClient: LanSyncTransportClient = transportClient ?: LanSyncHttpClient(crypto = crypto)
    private val peerDiscoveryCoordinator = LanSyncPeerDiscoveryCoordinator(
        runtime = runtime,
        timeProvider = timeProvider,
        nsdService = nsdService,
        httpClient = httpClient,
        syncPeerDao = syncPeerDao,
        sharedSecretProtector = sharedSecretProtector
    )
    private val syncExecutor = LanSyncSyncExecutor(
        runtime = runtime,
        scope = scope,
        timeProvider = timeProvider,
        dispatchers = dispatchers,
        crypto = crypto,
        sharedSecretProtector = sharedSecretProtector,
        httpClient = httpClient,
        nsdService = nsdService,
        syncChangeDao = syncChangeDao,
        syncPeerDao = syncPeerDao,
        syncPeerCursorDao = syncPeerCursorDao,
        conflictResolver = conflictResolver,
        changeApplier = changeApplier,
        refreshPeers = peerDiscoveryCoordinator::refreshPeers
    )
    private val httpServer: LanSyncTransportServer = transportServer ?: LanSyncHttpServer(
        portAllocator = portAllocator,
        onHello = syncExecutor::handleHello,
        onPairInit = syncExecutor::handlePairInit,
        onPing = syncExecutor::handlePing,
        onPullChanges = syncExecutor::handlePullChanges,
        onPushChanges = syncExecutor::handlePushChanges,
        onAck = syncExecutor::handleAck
    )
    private val journalSeeder = LanSyncJournalSeeder(
        database = database,
        appSettingsRepository = appSettingsRepository,
        deckDao = deckDao,
        cardDao = cardDao,
        questionDao = questionDao,
        reviewRecordDao = reviewRecordDao,
        syncChangeDao = syncChangeDao,
        syncChangeRecorder = LanSyncChangeRecorder(
            syncChangeDao = syncChangeDao,
            crypto = crypto
        ),
        crypto = crypto
    )
    private val sessionCoordinator = LanSyncSessionCoordinator(
        runtime = runtime,
        localProfileStore = localProfileStore,
        journalSeeder = journalSeeder,
        nsdService = nsdService,
        httpServer = httpServer,
        refreshPeers = peerDiscoveryCoordinator::refreshPeers,
        heartbeatTrustedPeers = peerDiscoveryCoordinator::heartbeatTrustedPeers
    )

    /**
     * 同步页只需订阅单一状态流，就能拿到发现、预览和执行全过程，是为了让页面侧保持稳定的状态机边界。
     */
    override fun observeSessionState(): Flow<LanSyncSessionState> = runtime.sessionState

    /**
     * 启动会话时委托会话协调器统一准备广播、发现与后台任务，是为了让仓储本身继续保持轻量外壳职责。
     */
    override suspend fun startSession() = withContext(dispatchers.io) {
        sessionCoordinator.startSession()
    }

    /**
     * 停止会话时统一释放发现与监听资源，是为了让同步功能在页面退出后完全回到离线状态。
     */
    override suspend fun stopSession() = withContext(dispatchers.io) {
        sessionCoordinator.stopSession()
    }

    /**
     * 本机显示名更新委托给会话协调器，是为了继续保证本地档案、对外响应与页面展示共享同一入口。
     */
    override suspend fun updateLocalDisplayName(displayName: String) = withContext(dispatchers.io) {
        sessionCoordinator.updateLocalDisplayName(displayName)
    }

    /**
     * 预览编排交给执行器，是为了把配对、冲突分析和远端摘要读取从仓储外壳中剥离出去。
     */
    override suspend fun prepareSync(peer: LanSyncPeer, pairingCode: String?): LanSyncPreview = withContext(dispatchers.io) {
        syncExecutor.prepareSync(peer = peer, pairingCode = pairingCode)
    }

    /**
     * 真正同步继续维持仓储级入口，但内部交由执行器完成，是为了保持页面契约稳定同时压缩主类职责。
     */
    override suspend fun runSync(
        preview: LanSyncPreview,
        resolutions: List<LanSyncConflictResolution>
    ) = withContext(dispatchers.io) {
        syncExecutor.runSync(preview = preview, resolutions = resolutions)
    }

    /**
     * 同步取消继续由仓储对外暴露，但内部只负责转发到执行器，是为了把取消语义与具体执行细节解耦。
     */
    override suspend fun cancelActiveSync() = withContext(dispatchers.io) {
        syncExecutor.cancelActiveSync()
    }
}

