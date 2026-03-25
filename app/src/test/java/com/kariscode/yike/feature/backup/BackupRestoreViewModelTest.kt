package com.kariscode.yike.feature.backup

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.backup.BackupExportMode
import com.kariscode.yike.data.backup.BackupOperations
import com.kariscode.yike.data.export.CsvExporter
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.core.ui.message.SuccessMessages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BackupRestoreViewModel 测试聚焦在“先确认、再恢复、恢复后重建提醒”的业务链路，
 * 避免高风险操作只靠界面手测兜底。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupRestoreViewModelTest {
    private lateinit var application: Application
    private lateinit var database: YikeDatabase

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(application, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 选择恢复文件后必须先进入确认态，而不是立刻覆盖本地数据，
     * 这样页面才能兑现高风险操作需要二次确认的承诺。
     */
    @Test
    fun onImportUriSelected_setsPendingRestoreUri() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel()
            val uri = Uri.parse("content://backup/sample.json")

            viewModel.onImportUriSelected(uri)

            assertEquals(uri, viewModel.uiState.value.pendingRestoreUri)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 恢复成功后必须调用备份服务和提醒重建，
     * 否则数据虽然恢复了，但后台提醒会继续停留在旧状态。
     */
    @Test
    fun onConfirmRestore_restoresDataAndReschedulesReminder() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val backupOperations = FakeBackupOperations()
            val reminderScheduler = FakeReminderScheduler()
            val viewModel = createViewModel(
                backupOperations = backupOperations,
                reminderScheduler = reminderScheduler
            )
            val uri = Uri.parse("content://backup/restore.json")

            viewModel.onImportUriSelected(uri)
            viewModel.onConfirmRestore()
            advanceUntilIdle()

            assertEquals(listOf(uri), backupOperations.restoredUris)
            assertEquals(1, reminderScheduler.calls.size)
            assertEquals("恢复成功", viewModel.uiState.value.message)
            assertEquals(false, viewModel.uiState.value.isImporting)
            assertNull(viewModel.uiState.value.pendingRestoreUri)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 恢复失败时必须清空进行中状态并回写错误信息，
     * 否则页面会把用户卡在无法继续操作的假 loading 里。
     */
    @Test
    fun onConfirmRestore_failureShowsErrorMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val backupOperations = FakeBackupOperations(restoreError = IllegalStateException("恢复失败"))
            val reminderScheduler = FakeReminderScheduler()
            val viewModel = createViewModel(
                backupOperations = backupOperations,
                reminderScheduler = reminderScheduler
            )
            val uri = Uri.parse("content://backup/bad.json")

            viewModel.onImportUriSelected(uri)
            viewModel.onConfirmRestore()
            advanceUntilIdle()

            assertEquals(false, viewModel.uiState.value.isImporting)
            assertEquals("恢复失败", viewModel.uiState.value.errorMessage)
            assertEquals(emptyList<AppSettings>(), reminderScheduler.calls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 导出成功后必须退出进行中状态并给出明确反馈，
     * 否则用户无法判断文件是否真的已经写出。
     */
    @Test
    fun onExportUriSelected_successShowsExportedMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val backupOperations = FakeBackupOperations()
            val viewModel = createViewModel(backupOperations = backupOperations)
            val uri = Uri.parse("content://backup/export.json")

            viewModel.onExportUriSelected(uri)
            advanceUntilIdle()

            assertEquals(listOf(BackupExportRecord(uri, BackupExportMode.FULL)), backupOperations.exportedUris)
            assertEquals(SuccessMessages.BACKUP_EXPORTED, viewModel.uiState.value.message)
            assertEquals(false, viewModel.uiState.value.isExporting)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 增量导出必须把模式传给底层服务，
     * 这样同一个文件选择流程才能真正区分完整备份和增量备份。
     */
    @Test
    fun onExportIncrementalClick_thenUriSelected_exportsIncrementalBackup() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val backupOperations = FakeBackupOperations()
            val viewModel = createViewModel(backupOperations = backupOperations)
            val uri = Uri.parse("content://backup/export-incremental.json")

            viewModel.onExportIncrementalClick()
            viewModel.onExportUriSelected(uri)
            advanceUntilIdle()

            assertEquals(
                listOf(BackupExportRecord(uri, BackupExportMode.INCREMENTAL)),
                backupOperations.exportedUris
            )
            assertEquals("增量备份导出成功", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        backupOperations: FakeBackupOperations = FakeBackupOperations(),
        appSettingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(),
        reminderScheduler: FakeReminderScheduler = FakeReminderScheduler()
    ): BackupRestoreViewModel = BackupRestoreViewModel(
        backupService = backupOperations,
        csvExporter = CsvExporter(
            application = application,
            questionDao = database.questionDao(),
            dispatchers = testDispatchers()
        ),
        appSettingsRepository = appSettingsRepository,
        reminderScheduler = reminderScheduler
    )

    /**
     * 测试 dispatcher 统一映射到 Main，是为了让导出这类 `withContext(io)` 的路径在单元测试里无需额外线程配置。
     */
    private fun testDispatchers(): AppDispatchers = object : AppDispatchers {
        override val main = Dispatchers.Main
        override val io = Dispatchers.Main
        override val default = Dispatchers.Main
    }

    /**
     * 假备份操作只记录导出名与恢复参数，足以验证 ViewModel 的流程编排。
     */
    private class FakeBackupOperations(
        private val restoreError: Throwable? = null
    ) : BackupOperations {
        val exportedUris = mutableListOf<BackupExportRecord>()
        val restoredUris = mutableListOf<Uri>()

        override fun createSuggestedFileName(mode: BackupExportMode): String = "yike-backup-test.json"

        override suspend fun exportToUri(uri: Uri, mode: BackupExportMode) {
            exportedUris += BackupExportRecord(uri = uri, mode = mode)
        }

        override suspend fun restoreFromUri(uri: Uri) {
            restoreError?.let { throw it }
            restoredUris += uri
        }
    }

    /**
     * 导出记录显式带出模式后，测试可以直接断言 ViewModel 是否把完整/增量语义正确传到服务层。
     */
    private data class BackupExportRecord(
        val uri: Uri,
        val mode: BackupExportMode
    )

    /**
     * 设置仓储在本组测试中只需要提供最近备份时间流，因此保留最小实现即可。
     */
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val settingsFlow = MutableStateFlow(
            AppSettings(
                dailyReminderEnabled = true,
                dailyReminderHour = 20,
                dailyReminderMinute = 30,
                schemaVersion = 4,
                backupLastAt = null,
                themeMode = ThemeMode.SYSTEM
            )
        )

        override fun observeSettings(): Flow<AppSettings> = settingsFlow
        override suspend fun getSettings(): AppSettings = settingsFlow.value
        override suspend fun setDailyReminderEnabled(enabled: Boolean) = Unit
        override suspend fun setDailyReminderTime(hour: Int, minute: Int) = Unit
        override suspend fun setSettings(settings: AppSettings) {
            settingsFlow.value = settings
        }
        override suspend fun setSchemaVersion(schemaVersion: Int) = Unit
        override suspend fun setBackupLastAt(epochMillis: Long?) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
    }

    /**
     * 调度器假实现只关心收到的设置快照，便于验证恢复后是否触发了重建提醒。
     */
    private class FakeReminderScheduler : ReminderSyncScheduler {
        val calls = mutableListOf<AppSettings>()

        override suspend fun syncReminderFromRepository() {
            calls += AppSettings(
                dailyReminderEnabled = true,
                dailyReminderHour = 20,
                dailyReminderMinute = 30,
                schemaVersion = 4,
                backupLastAt = null,
                themeMode = ThemeMode.SYSTEM
            )
        }

        override fun syncReminder(settings: AppSettings) {
            calls += settings
        }
    }
}

