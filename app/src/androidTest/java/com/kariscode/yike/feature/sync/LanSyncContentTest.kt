package com.kariscode.yike.feature.sync

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncConflictItem
import com.kariscode.yike.domain.model.LanSyncLocalProfile
import com.kariscode.yike.domain.model.LanSyncPeer
import com.kariscode.yike.domain.model.LanSyncPeerHealth
import com.kariscode.yike.domain.model.LanSyncPreview
import com.kariscode.yike.domain.model.LanSyncProgress
import com.kariscode.yike.domain.model.LanSyncSessionState
import com.kariscode.yike.domain.model.LanSyncStage
import com.kariscode.yike.domain.model.LanSyncTrustState
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.ui.theme.YikeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 局域网同步页内容测试聚焦权限提示、启动入口和冲突确认弹窗，
 * 是为了在 UI 演进时守住“高风险流程必须明确告知用户下一步”的关键反馈。
 */
class LanSyncContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Android 13+ 缺少附近 Wi‑Fi 权限时必须明确提示并给出授权入口，
     * 否则用户会误以为同步功能不可用或设备发现出了故障。
     */
    @Test
    fun lanSyncContent_missingPermissionShowsRequestBanner() {
        composeRule.setContent {
            YikeTheme {
                LanSyncContent(
                    uiState = baseUiState(isSessionActive = false),
                    hasNearbyWifiPermission = false,
                    onRequestPermission = {},
                    onStartSession = {},
                    onStopSession = {},
                    onPeerClick = {},
                    onEditLocalName = {},
                    onCancelActiveSync = {}
                )
            }
        }

        composeRule.onNodeWithText("需要附近 Wi-Fi 设备权限").assertIsDisplayed()
        composeRule.onNodeWithText("授权并开始发现").assertIsDisplayed()
    }

    /**
     * 权限就绪但会话未启动时必须给出“开始发现设备”的明确入口，
     * 否则用户只会看到空列表而不知道需要先启动会话。
     */
    @Test
    fun lanSyncContent_sessionInactiveShowsStartSessionEntry() {
        composeRule.setContent {
            YikeTheme {
                LanSyncContent(
                    uiState = baseUiState(isSessionActive = false),
                    hasNearbyWifiPermission = true,
                    onRequestPermission = {},
                    onStartSession = {},
                    onStopSession = {},
                    onPeerClick = {},
                    onEditLocalName = {},
                    onCancelActiveSync = {}
                )
            }
        }

        composeRule.onNodeWithText("同步尚未启动").assertIsDisplayed()
        composeRule.onNodeWithText("开始发现设备").assertIsDisplayed()
    }

    /**
     * 冲突弹窗必须显式展示决议标题、三种选择以及确认按钮，
     * 否则高风险的“保留哪一边数据”会退化成用户看不见的隐式规则。
     */
    @Test
    fun conflictDialog_showsChoicesAndConfirmAction() {
        val peer = createPeer(deviceId = "peer_1", trustState = LanSyncTrustState.TRUSTED)
        val preview = LanSyncPreview(
            peer = peer,
            localChangeCount = 1,
            remoteChangeCount = 1,
            settingsChangeCount = 0,
            conflicts = listOf(
                LanSyncConflictItem(
                    entityType = SyncEntityType.QUESTION,
                    entityId = "q_1",
                    summary = "极限定义",
                    localSummary = "本机版本",
                    remoteSummary = "远端版本",
                    reason = "两端都修改了同一对象"
                )
            ),
            isFirstPairing = false
        )

        composeRule.setContent {
            YikeTheme {
                ConflictDialog(
                    uiState = baseUiState(isSessionActive = true).copy(
                        pendingPreview = preview,
                        showConflictDialog = true,
                        conflictChoices = mapOf("QUESTION:q_1" to LanSyncConflictChoice.KEEP_REMOTE)
                    ),
                    onChoiceChange = { _, _ -> },
                    onDismiss = {},
                    onConfirm = {}
                )
            }
        }

        composeRule.onNodeWithText("确认冲突决议").assertIsDisplayed()
        composeRule.onNodeWithText("保留本机").assertIsDisplayed()
        composeRule.onNodeWithText("保留远端").assertIsDisplayed()
        composeRule.onNodeWithText("本次跳过").assertIsDisplayed()
        composeRule.onNodeWithText("按以上决议同步").assertIsDisplayed()
    }

    /**
     * 基础状态构造器只提供内容测试所需最小字段，
     * 是为了让断言聚焦 UI 反馈，而不把测试绑死在仓储实现细节。
     */
    private fun baseUiState(isSessionActive: Boolean): LanSyncUiState {
        val peer = createPeer(deviceId = "peer_1", trustState = LanSyncTrustState.TRUSTED)
        return LanSyncUiState(
            session = LanSyncSessionState(
                localProfile = LanSyncLocalProfile(
                    deviceId = "local_1",
                    displayName = "当前设备",
                    shortDeviceId = "L-001",
                    pairingCode = "123456"
                ),
                peers = listOf(peer),
                isSessionActive = isSessionActive,
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
            ),
            pendingPairingPeer = null,
            pairingCodeInput = "",
            pendingPreview = null,
            showConflictDialog = false,
            conflictChoices = emptyMap(),
            isEditingLocalName = false,
            localNameInput = ""
        )
    }

    /**
     * 已发现设备模型在内容测试里只需稳定字段，
     * 这样冲突弹窗和会话 banner 的断言不会被网络层字段变更拖累。
     */
    private fun createPeer(deviceId: String, trustState: LanSyncTrustState): LanSyncPeer =
        LanSyncPeer(
            deviceId = deviceId,
            displayName = "Pixel",
            shortDeviceId = "P-001",
            hostAddress = "192.168.1.2",
            port = 8321,
            protocolVersion = 2,
            trustState = trustState,
            health = LanSyncPeerHealth.AVAILABLE,
            lastSeenAt = 1_700_000_000_000L
        )
}

