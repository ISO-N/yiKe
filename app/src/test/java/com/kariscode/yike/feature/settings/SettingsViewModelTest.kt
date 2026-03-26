package com.kariscode.yike.feature.settings

import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.testsupport.FakeAppSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SettingsViewModelTest 锁定设置页的保存反馈与提醒同步边界，
 * 避免后续继续简化依赖关系时把“落盘成功”和“调度同步成功”之间的职责切分弄乱。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    /**
     * 修改提醒时间后应同时写入仓储并把最新快照交给调度接口，
     * 这样设置页既不会依赖具体调度实现，也能保证后台任务立即跟上用户的新时间。
     */
    @Test
    fun onReminderTimeConfirmed_updatesRepositoryAndSyncsReminderThroughInterface() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val appSettingsRepository = FakeAppSettingsRepository()
            val reminderScheduler = FakeReminderSyncScheduler()
            val viewModel = SettingsViewModel(
                appSettingsRepository = appSettingsRepository,
                reminderScheduler = reminderScheduler,
                appVersionName = "1.0.0"
            )
            advanceUntilIdle()

            viewModel.onReminderTimeConfirmed(hour = 7, minute = 45)
            advanceUntilIdle()

            assertEquals(7, appSettingsRepository.getSettings().dailyReminderHour)
            assertEquals(45, appSettingsRepository.getSettings().dailyReminderMinute)
            assertEquals(1, reminderScheduler.syncedSettings.size)
            assertEquals(7, reminderScheduler.syncedSettings.single().dailyReminderHour)
            assertEquals(45, reminderScheduler.syncedSettings.single().dailyReminderMinute)
            assertEquals("提醒设置已保存", viewModel.uiState.value.message)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 主题修改成功后应只回写成功提示而不触发提醒同步，
     * 这样显示偏好仍然保持为单纯的设置写入，而不会意外耦合到无关的系统副作用。
     */
    @Test
    fun onThemeModeChange_updatesThemeWithoutSchedulingReminder() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val appSettingsRepository = FakeAppSettingsRepository()
            val reminderScheduler = FakeReminderSyncScheduler()
            val viewModel = SettingsViewModel(
                appSettingsRepository = appSettingsRepository,
                reminderScheduler = reminderScheduler,
                appVersionName = "1.0.0"
            )
            advanceUntilIdle()

            viewModel.onThemeModeChange(ThemeMode.DARK)
            advanceUntilIdle()

            assertEquals(ThemeMode.DARK, appSettingsRepository.getSettings().themeMode)
            assertEquals("主题设置已保存", viewModel.uiState.value.message)
            assertEquals(emptyList<AppSettings>(), reminderScheduler.syncedSettings)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 提示消费后应从状态中移除，是为了避免配置变更或返回重进时重复展示同一条一次性反馈。
     */
    @Test
    fun consumeMessageAndErrorMessage_clearTransientFeedback() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = SettingsViewModel(
                appSettingsRepository = FakeAppSettingsRepository(),
                reminderScheduler = FakeReminderSyncScheduler(),
                appVersionName = "1.0.0"
            )
            advanceUntilIdle()

            viewModel.onThemeModeChange(ThemeMode.DARK)
            advanceUntilIdle()
            viewModel.consumeMessage()

            assertNull(viewModel.uiState.value.message)
            assertNull(viewModel.uiState.value.errorMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 记录型调度假实现只保留设置快照，是为了让测试聚焦 ViewModel 是否只依赖接口语义而不是具体类实现。
     */
    private class FakeReminderSyncScheduler : ReminderSyncScheduler {
        val syncedSettings = mutableListOf<AppSettings>()

        override suspend fun syncReminderFromRepository() = Unit

        override fun syncReminder(settings: AppSettings) {
            syncedSettings += settings
        }
    }
}
