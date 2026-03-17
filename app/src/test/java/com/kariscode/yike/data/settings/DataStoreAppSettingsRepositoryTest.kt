package com.kariscode.yike.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.kariscode.yike.data.sync.FixedTimeProvider
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 设置仓储测试用于守住 DataStore 与领域模型之间的映射边界，
 * 避免新增主题字段后出现“界面可选但重启丢失”的静默回归。
 */
class DataStoreAppSettingsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * 主题模式必须被稳定持久化，
     * 否则用户下次启动应用时会看到和上次不同的界面风格。
     */
    @Test
    fun setThemeMode_persistsSelectedMode() = runTest {
        val repository = createRepository("theme.preferences_pb", backgroundScope)

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.getSettings().themeMode)
    }

    /**
     * 空 DataStore 必须回退到稳定默认值，
     * 否则首次启动或文件损坏恢复后会得到不可预期的设置状态。
     */
    @Test
    fun getSettings_emptyStore_returnsDefaultValues() = runTest {
        val repository = createRepository("defaults.preferences_pb", backgroundScope)

        val settings = repository.getSettings()

        assertFalse(settings.dailyReminderEnabled)
        assertEquals(20, settings.dailyReminderHour)
        assertEquals(0, settings.dailyReminderMinute)
        assertEquals(ThemeMode.LIGHT, settings.themeMode)
    }

    /**
     * 修改提醒时间后，单次读取与观察流都必须看到同一份最新值，
     * 否则设置页和后台调度会出现口径漂移。
     */
    @Test
    fun setDailyReminderTime_updatesSnapshotAndFlow() = runTest {
        val repository = createRepository("time.preferences_pb", backgroundScope)

        repository.setDailyReminderTime(hour = 7, minute = 45)

        val settings = repository.getSettings()
        val observed = repository.observeSettings().first()
        assertEquals(7, settings.dailyReminderHour)
        assertEquals(45, settings.dailyReminderMinute)
        assertEquals(settings, observed)
    }

    /**
     * 整份设置快照写入时必须把提醒、备份时间和主题一起持久化，
     * 否则备份恢复后的设置会处于半更新状态。
     */
    @Test
    fun setSettings_persistsWholeSnapshot() = runTest {
        val repository = createRepository("snapshot.preferences_pb", backgroundScope)
        val expected = AppSettings(
            dailyReminderEnabled = true,
            dailyReminderHour = 6,
            dailyReminderMinute = 30,
            schemaVersion = 4,
            backupLastAt = 8_888L,
            themeMode = ThemeMode.SYSTEM
        )

        repository.setSettings(expected)

        assertEquals(expected, repository.getSettings())
    }

    /**
     * 单独修改提醒开关时必须只改变开关字段，
     * 否则设置页切换开关会意外污染已经保存的提醒时间。
     */
    @Test
    fun setDailyReminderEnabled_persistsFlagWithoutChangingTime() = runTest {
        val repository = createRepository("enabled.preferences_pb", backgroundScope)

        repository.setDailyReminderTime(hour = 6, minute = 15)
        repository.setDailyReminderEnabled(enabled = true)

        val settings = repository.getSettings()
        assertTrue(settings.dailyReminderEnabled)
        assertEquals(6, settings.dailyReminderHour)
        assertEquals(15, settings.dailyReminderMinute)
    }

    /**
     * 最近备份时间允许写入和清空两种状态，
     * 否则备份页无法稳定表达“刚备份过”和“当前未知”两类语义。
     */
    @Test
    fun setBackupLastAt_persistsValueAndSupportsClearing() = runTest {
        val repository = createRepository("backup_last_at.preferences_pb", backgroundScope)

        repository.setBackupLastAt(9_999L)
        assertEquals(9_999L, repository.getSettings().backupLastAt)

        repository.setBackupLastAt(null)
        assertNull(repository.getSettings().backupLastAt)
    }

    /**
     * 并发写入后仓储必须仍返回最后一次完成的稳定快照，
     * 否则设置页和后台任务会读到彼此错位的配置组合。
     */
    @Test
    fun concurrentWrites_leaveRepositoryInConsistentState() = runTest {
        val repository = createRepository("concurrent.preferences_pb", backgroundScope)

        awaitAll(
            async { repository.setDailyReminderTime(hour = 8, minute = 30) },
            async { repository.setThemeMode(ThemeMode.DARK) },
            async { repository.setDailyReminderEnabled(enabled = true) }
        )

        val settings = repository.getSettings()
        assertTrue(settings.dailyReminderEnabled)
        assertEquals(8, settings.dailyReminderHour)
        assertEquals(30, settings.dailyReminderMinute)
        assertEquals(ThemeMode.DARK, settings.themeMode)
    }

    private fun createRepository(fileName: String, scope: CoroutineScope): DataStoreAppSettingsRepository =
        DataStoreAppSettingsRepository(
            dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { temporaryFolder.newFile(fileName) }
            ),
            timeProvider = FixedTimeProvider(now = 123L)
        )
}
