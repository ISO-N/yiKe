package com.kariscode.yike.data.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.kariscode.yike.testsupport.FakeAppSettingsRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import com.kariscode.yike.testsupport.defaultAppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ReminderScheduler 测试用于锁定“唯一任务 + 改时间重建 + 关闭即取消”的调度语义，
 * 避免提醒开关和时间设置在后续改动里悄悄失效。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    /**
     * 初始化测试专用 WorkManager，能让提醒调度断言围绕真实唯一任务行为展开。
     */
    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().build()
        )
        workManager = WorkManager.getInstance(context)
    }

    /**
     * 每个测试后清空任务队列，避免唯一任务状态串到下一条用例。
     */
    @After
    fun tearDown() {
        workManager.cancelAllWork()
    }

    /**
     * 开启提醒时必须创建唯一任务，
     * 否则设置页保存成功后后台不会真的触发每日检查。
     */
    @Test
    fun syncReminder_enabledSettingsEnqueuesUniqueWork() = runTest {
        val scheduler = createScheduler()

        scheduler.syncReminder(
            defaultAppSettings().copy(
                dailyReminderEnabled = true,
                dailyReminderHour = 7,
                dailyReminderMinute = 45
            )
        )

        val workInfos = workManager.getWorkInfosForUniqueWork(ReminderConstants.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }

    /**
     * 关闭提醒时必须取消已存在的唯一任务，
     * 否则用户关闭开关后仍可能继续收到旧提醒。
     */
    @Test
    fun syncReminder_disabledSettingsCancelsExistingWork() = runTest {
        val scheduler = createScheduler()
        scheduler.syncReminder(defaultAppSettings())

        scheduler.syncReminder(defaultAppSettings().copy(dailyReminderEnabled = false))

        val workInfos = workManager.getWorkInfosForUniqueWork(ReminderConstants.UNIQUE_WORK_NAME).get()
        assertTrue(workInfos.all { info -> info.state != WorkInfo.State.ENQUEUED })
    }

    /**
     * 从仓储重建提醒时必须读取最新设置，
     * 这样应用启动和恢复备份都能复用同一套后台任务语义。
     */
    @Test
    fun syncReminderFromRepository_usesRepositorySnapshot() = runTest {
        val repository = FakeAppSettingsRepository(
            defaultAppSettings().copy(
                dailyReminderEnabled = true,
                dailyReminderHour = 9,
                dailyReminderMinute = 30
            )
        )
        val scheduler = createScheduler(appSettingsRepository = repository)

        scheduler.syncReminderFromRepository()

        val workInfos = workManager.getWorkInfosForUniqueWork(ReminderConstants.UNIQUE_WORK_NAME).get()
        assertEquals(1, workInfos.size)
        assertEquals(WorkInfo.State.ENQUEUED, workInfos.single().state)
    }

    /**
     * 构造入口统一复用测试 WorkManager 和固定时钟，避免每条用例重复装配调度依赖。
     */
    private fun createScheduler(
        appSettingsRepository: FakeAppSettingsRepository = FakeAppSettingsRepository(defaultAppSettings())
    ): ReminderScheduler = ReminderScheduler(
        workManager = workManager,
        appSettingsRepository = appSettingsRepository,
        timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L)
    )
}
