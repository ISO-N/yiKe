package com.kariscode.yike.feature.sync

import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncConflictItem
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
import com.kariscode.yike.domain.repository.LanSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LAN Sync V2 的 ViewModel 测试聚焦在配对、预览和冲突决议的状态推进，
 * 是为了守住“先确认影响，再执行同步”的高风险交互边界。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanSyncViewModelTest {

    /**
     * 权限就绪后应启动发现会话并让页面进入活跃态，
     * 这样同步页不会停留在假加载状态而错失局域网发现时机。
     */
    @Test
    fun onPermissionReady_startsSessionAndUpdatesSessionState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPermissionReady()
            advanceUntilIdle()

            assertEquals(1, repository.startSessionCalls)
            assertTrue(viewModel.uiState.value.session.isSessionActive)
            assertEquals(LanSyncStage.DISCOVERING, viewModel.uiState.value.session.progress.stage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 未信任设备点击后必须先进入配对输入，而不是直接拉取预览，
     * 这样首次连接不会绕过人工确认把数据接口暴露给陌生设备。
     */
    @Test
    fun onPeerClick_untrustedPeerOpensPairingDialog() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPeerClick(repository.untrustedPeer)

            assertEquals(repository.untrustedPeer, viewModel.uiState.value.pendingPairingPeer)
            assertEquals("", viewModel.uiState.value.pairingCodeInput)
            assertEquals(0, repository.prepareCalls.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 首次配对确认后应继续生成预览，
     * 这样首次授权仍然遵守“先看规模再执行”的统一风险语义。
     */
    @Test
    fun onConfirmPairing_buildsPreviewWithPairingCode() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPeerClick(repository.untrustedPeer)
            viewModel.onPairingCodeChange("12a34567")
            viewModel.onConfirmPairing()
            advanceUntilIdle()

            assertEquals(listOf(PrepareCall(repository.untrustedPeer, "123456")), repository.prepareCalls)
            assertNotNull(viewModel.uiState.value.pendingPreview)
            assertNull(viewModel.uiState.value.pendingPairingPeer)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 预览存在冲突时必须先展示默认决议并在确认后再执行同步，
     * 这样用户选择和最终应用的变更才能保持一一对应。
     */
    @Test
    fun onConfirmConflicts_runsSyncWithSelectedResolutions() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            repository.nextPreview = repository.createConflictPreview()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPeerClick(repository.trustedPeer)
            advanceUntilIdle()
            viewModel.onConfirmPreview()

            assertTrue(viewModel.uiState.value.showConflictDialog)
            assertEquals(
                LanSyncConflictChoice.KEEP_REMOTE,
                viewModel.uiState.value.conflictChoices["QUESTION:q_1"]
            )

            viewModel.onConflictChoiceChange("QUESTION:q_1", LanSyncConflictChoice.KEEP_LOCAL)
            viewModel.onConfirmConflicts()
            advanceUntilIdle()

            assertEquals(1, repository.runSyncCalls.size)
            assertEquals(
                listOf(
                    LanSyncConflictResolution(
                        entityType = SyncEntityType.QUESTION,
                        entityId = "q_1",
                        choice = LanSyncConflictChoice.KEEP_LOCAL
                    )
                ),
                repository.runSyncCalls.single().resolutions
            )
            assertFalse(viewModel.uiState.value.showConflictDialog)
            assertNull(viewModel.uiState.value.pendingPreview)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 本机名称保存必须通过仓储统一更新，
     * 这样 hello 响应和页面展示才能围绕同一份身份信息收敛。
     */
    @Test
    fun onSaveLocalName_updatesRepositoryAndClosesEditor() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onEditLocalName()
            viewModel.onLocalNameInputChange("  我的新设备名  ")
            viewModel.onSaveLocalName()
            advanceUntilIdle()

            assertEquals(listOf("我的新设备名"), repository.updatedDisplayNames)
            assertFalse(viewModel.uiState.value.isEditingLocalName)
            assertEquals("我的新设备名", viewModel.uiState.value.session.localProfile.displayName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 停止会话后必须一并清空配对、预览和冲突决议，
     * 这样用户重新开始发现时不会带着上一次同步流程的临时状态继续操作。
     */
    @Test
    fun onStopSession_clearsTransientSyncState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            repository.nextPreview = repository.createConflictPreview()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPeerClick(repository.untrustedPeer)
            viewModel.onPairingCodeChange("123456")
            viewModel.onConfirmPairing()
            advanceUntilIdle()
            viewModel.onConfirmPreview()

            assertNotNull(viewModel.uiState.value.pendingPreview)
            assertTrue(viewModel.uiState.value.showConflictDialog)

            viewModel.onStopSession()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.pendingPairingPeer)
            assertEquals("", viewModel.uiState.value.pairingCodeInput)
            assertNull(viewModel.uiState.value.pendingPreview)
            assertFalse(viewModel.uiState.value.showConflictDialog)
            assertTrue(viewModel.uiState.value.conflictChoices.isEmpty())
            assertFalse(viewModel.uiState.value.session.isSessionActive)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 假仓储只保留 ViewModel 本组测试关心的会话轨迹，
     * 是为了把断言集中在状态推进而不是底层网络实现。
     */
    private class FakeLanSyncRepository : LanSyncRepository {
        val trustedPeer: LanSyncPeer = createPeer(
            deviceId = "peer_trusted",
            displayName = "Pixel Trusted",
            trustState = LanSyncTrustState.TRUSTED
        )
        val untrustedPeer: LanSyncPeer = createPeer(
            deviceId = "peer_untrusted",
            displayName = "Pixel New",
            trustState = LanSyncTrustState.UNTRUSTED
        )

        private val sessionFlow = MutableStateFlow(
            createSessionState(
                peers = listOf(trustedPeer, untrustedPeer),
                isSessionActive = false,
                progress = LanSyncProgress(
                    stage = LanSyncStage.IDLE,
                    message = "等待开始发现",
                    bytesTransferred = 0L,
                    totalBytes = null,
                    itemsProcessed = 0,
                    totalItems = null
                )
            )
        )

        var nextPreview: LanSyncPreview = LanSyncPreview(
            peer = trustedPeer,
            localChangeCount = 2,
            remoteChangeCount = 3,
            settingsChangeCount = 1,
            conflicts = emptyList(),
            isFirstPairing = false
        )
        val prepareCalls = mutableListOf<PrepareCall>()
        val runSyncCalls = mutableListOf<RunSyncCall>()
        val updatedDisplayNames = mutableListOf<String>()
        var startSessionCalls: Int = 0

        /**
         * 会话流作为单一事实来源暴露给 ViewModel，
         * 是为了模拟真实仓储里后台发现与进度更新都会回流到页面这一行为。
         */
        override fun observeSessionState(): Flow<LanSyncSessionState> = sessionFlow

        /**
         * 启动会话时同步切换为发现态，
         * 是为了让测试能验证 ViewModel 已正确消费仓储回推的最新状态。
         */
        override suspend fun startSession() {
            startSessionCalls += 1
            sessionFlow.value = sessionFlow.value.copy(
                isSessionActive = true,
                progress = sessionFlow.value.progress.copy(
                    stage = LanSyncStage.DISCOVERING,
                    message = "正在发现设备"
                )
            )
        }

        /**
         * 停止会话对本组测试不是断言重点，因此只保留最小状态回退。
         */
        override suspend fun stopSession() {
            sessionFlow.value = sessionFlow.value.copy(isSessionActive = false)
        }

        /**
         * 名称更新后立即回推新的本机资料，
         * 是为了模拟真实仓储里 DataStore 持久化成功后的单一状态刷新。
         */
        override suspend fun updateLocalDisplayName(displayName: String) {
            updatedDisplayNames += displayName
            sessionFlow.value = sessionFlow.value.copy(
                localProfile = sessionFlow.value.localProfile.copy(displayName = displayName)
            )
        }

        /**
         * 预览调用记录配对入参后返回预设结果，
         * 是为了让测试聚焦在 ViewModel 如何消费预览，而不是冲突计算细节。
         */
        override suspend fun prepareSync(peer: LanSyncPeer, pairingCode: String?): LanSyncPreview {
            prepareCalls += PrepareCall(peer = peer, pairingCode = pairingCode)
            return nextPreview.copy(peer = peer, isFirstPairing = pairingCode != null)
        }

        /**
         * 同步执行只记录本次决议并把会话切到完成态，
         * 这样断言可以验证 ViewModel 是否把冲突选择正确传给仓储。
         */
        override suspend fun runSync(
            preview: LanSyncPreview,
            resolutions: List<LanSyncConflictResolution>
        ) {
            runSyncCalls += RunSyncCall(preview = preview, resolutions = resolutions)
            sessionFlow.value = sessionFlow.value.copy(
                progress = sessionFlow.value.progress.copy(
                    stage = LanSyncStage.COMPLETED,
                    message = "同步完成"
                ),
                message = "同步完成",
                activeFailure = null
            )
        }

        /**
         * 取消逻辑对本组测试不是关键路径，因此只把进度切到已取消用于保持接口完整。
         */
        override suspend fun cancelActiveSync() {
            sessionFlow.value = sessionFlow.value.copy(
                progress = sessionFlow.value.progress.copy(
                    stage = LanSyncStage.CANCELLED,
                    message = "已取消"
                ),
                activeFailure = LanSyncFailureReason.CANCELLED
            )
        }

        /**
         * 冲突预览显式带上一条问题冲突，是为了验证默认决议和确认后的 resolution 映射。
         */
        fun createConflictPreview(): LanSyncPreview = LanSyncPreview(
            peer = trustedPeer,
            localChangeCount = 1,
            remoteChangeCount = 1,
            settingsChangeCount = 0,
            conflicts = listOf(
                LanSyncConflictItem(
                    entityType = SyncEntityType.QUESTION,
                    entityId = "q_1",
                    summary = "二叉树遍历",
                    localSummary = "本地改了答案",
                    remoteSummary = "远端改了提示",
                    reason = "双方都在共同游标后修改了同一题目"
                )
            ),
            isFirstPairing = false
        )
    }

    /**
     * 准备调用显式建模后，断言可以直接比较“选中的设备”和“用户输入的配对码”是否被正确转发。
     */
    private data class PrepareCall(
        val peer: LanSyncPeer,
        val pairingCode: String?
    )

    /**
     * 同步执行调用单独建模后，测试可以直接验证冲突决议列表而不必解构多个并行字段。
     */
    private data class RunSyncCall(
        val preview: LanSyncPreview,
        val resolutions: List<LanSyncConflictResolution>
    )

    private companion object {
        /**
         * 统一构造 peer 数据，是为了让多个测试共享一致的设备字段而不在断言中夹带无关差异。
         */
        fun createPeer(
            deviceId: String,
            displayName: String,
            trustState: LanSyncTrustState
        ): LanSyncPeer = LanSyncPeer(
            deviceId = deviceId,
            displayName = displayName,
            shortDeviceId = deviceId.takeLast(6),
            hostAddress = "192.168.1.10",
            port = 9420,
            protocolVersion = 2,
            trustState = trustState,
            health = LanSyncPeerHealth.AVAILABLE,
            lastSeenAt = 1_000L
        )

        /**
         * 会话默认值集中构造后，测试在关注某个字段时可以避免反复展开整份状态初始化模板。
         */
        fun createSessionState(
            peers: List<LanSyncPeer>,
            isSessionActive: Boolean,
            progress: LanSyncProgress
        ): LanSyncSessionState = LanSyncSessionState(
            localProfile = LanSyncLocalProfile(
                deviceId = "local_device",
                displayName = "当前设备",
                shortDeviceId = "device",
                pairingCode = "123456"
            ),
            peers = peers,
            isSessionActive = isSessionActive,
            preview = null,
            progress = progress,
            activeFailure = null,
            message = null
        )
    }
}
