package com.kariscode.yike.feature.sync

import com.kariscode.yike.domain.model.LanSyncSnapshot
import com.kariscode.yike.domain.model.LocalSyncSnapshot
import com.kariscode.yike.domain.model.SyncDevice
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 局域网同步测试聚焦在覆盖前确认与导入执行顺序，
 * 避免高风险操作在后续迭代中退化成“点击即覆盖”的不可控行为。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanSyncViewModelTest {

    /**
     * 本机已有内容时必须先进入冲突确认态，
     * 这样用户不会因为一次误触就把现有题库直接覆盖掉。
     */
    @Test
    fun onSyncDeviceClick_withExistingLocalData_setsPendingConflict() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onSyncDeviceClick(repository.device)
            advanceUntilIdle()

            assertNotNull(viewModel.uiState.value.pendingConflict)
            assertEquals(0, repository.importCalls.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 用户确认冲突后才允许真正导入，
     * 这样同步页就能清晰维持“先确认、后覆盖”的风险语义。
     */
    @Test
    fun onConfirmConflictSync_importsSelectedDevice() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onSyncDeviceClick(repository.device)
            advanceUntilIdle()
            viewModel.onConfirmConflictSync()
            advanceUntilIdle()

            assertEquals(listOf(repository.device.id), repository.importCalls)
            assertNull(viewModel.uiState.value.pendingConflict)
            assertEquals("已从 Pixel Test 同步到本机", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 启动会话时要立即刷新本机摘要，
     * 这样同步页一进入就能展示覆盖前最关键的风险信息。
     */
    @Test
    fun onPermissionReady_startsSessionAndLoadsLocalSnapshot() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeLanSyncRepository()
            val viewModel = LanSyncViewModel(repository)

            viewModel.onPermissionReady()
            advanceUntilIdle()

            assertEquals(1, repository.startCalls)
            assertEquals(repository.localSnapshot, viewModel.uiState.value.localSnapshot)
            assertEquals(true, viewModel.uiState.value.isSessionActive)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Fake 仓储只保留同步页本组测试关心的行为轨迹，
     * 这样断言能聚焦在 ViewModel 的决策而不是底层网络实现。
     */
    private class FakeLanSyncRepository : LanSyncRepository {
        val device = SyncDevice(
            id = "device_1",
            deviceName = "Pixel Test",
            hostAddress = "192.168.1.8",
            port = 9420,
            lastSeenAt = 1_000L
        )
        val localSnapshot = LocalSyncSnapshot(
            deckCount = 2,
            cardCount = 4,
            questionCount = 6,
            lastBackupAt = 3_000L
        )
        private val devicesFlow = MutableStateFlow(listOf(device))
        val importCalls = mutableListOf<String>()
        var startCalls: Int = 0

        override fun observeDevices(): Flow<List<SyncDevice>> = devicesFlow

        override fun getLocalDeviceName(): String = "本机 Pixel"

        override suspend fun start() {
            startCalls += 1
        }

        override suspend fun stop() = Unit

        override suspend fun getLocalSnapshot(): LocalSyncSnapshot = localSnapshot

        override suspend fun fetchRemoteSnapshot(device: SyncDevice): LanSyncSnapshot = LanSyncSnapshot(
            deviceId = device.id,
            deviceName = device.deviceName,
            exportedAt = 2_000L,
            deckCount = 1,
            cardCount = 2,
            questionCount = 3
        )

        override suspend fun importFromDevice(device: SyncDevice) {
            importCalls += device.id
        }
    }
}
