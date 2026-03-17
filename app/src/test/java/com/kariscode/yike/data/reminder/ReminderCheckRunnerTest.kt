package com.kariscode.yike.data.reminder

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ReminderCheckRunner 测试聚焦在“是否通知”和“是否续约下一次提醒”这条后台链路，
 * 避免 Worker 继续承载过多业务判断后变得难以验证。
 */
class ReminderCheckRunnerTest {

    /**
     * 开启提醒且存在待复习内容时必须发送通知并继续调度下一次提醒，
     * 否则用户既收不到当天提醒，也会让后续提醒链路中断。
     */
    @Test
    fun run_enabledAndDueQuestions_showsNotificationAndReschedules() = runTest {
        val settings = createSettings(enabled = true)
        val appSettingsRepository = FakeAppSettingsRepository(settings)
        val questionRepository = FakeQuestionRepository(
            summary = TodayReviewSummary(dueCardCount = 2, dueQuestionCount = 5)
        )
        val notifier = FakeReminderNotifier()
        val scheduler = FakeReminderScheduler()
        val runner = ReminderCheckRunner(
            appSettingsRepository = appSettingsRepository,
            questionRepository = questionRepository,
            reminderNotifier = notifier,
            reminderScheduler = scheduler,
            timeProvider = FixedTimeProvider(now = 1_000L)
        )

        runner.run()

        assertEquals(listOf(NotificationCall(2, 5)), notifier.calls)
        assertEquals(listOf(settings), scheduler.calls)
        assertEquals(listOf(1_000L), questionRepository.requestedNowEpochMillis)
    }

    /**
     * 开启提醒但没有到期题时不应发出空通知，
     * 但仍必须续约下一次提醒以保持每日检查持续存在。
     */
    @Test
    fun run_enabledButNoDueQuestions_skipsNotificationAndReschedules() = runTest {
        val settings = createSettings(enabled = true)
        val questionRepository = FakeQuestionRepository(
            summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0)
        )
        val notifier = FakeReminderNotifier()
        val scheduler = FakeReminderScheduler()
        val runner = ReminderCheckRunner(
            appSettingsRepository = FakeAppSettingsRepository(settings),
            questionRepository = questionRepository,
            reminderNotifier = notifier,
            reminderScheduler = scheduler,
            timeProvider = FixedTimeProvider(now = 2_000L)
        )

        runner.run()

        assertEquals(emptyList<NotificationCall>(), notifier.calls)
        assertEquals(listOf(settings), scheduler.calls)
    }

    /**
     * 关闭提醒时不应查询到期数量或发送通知，
     * 只需要把当前设置交回调度器做统一收口即可。
     */
    @Test
    fun run_disabledReminder_doesNotQuerySummaryOrNotify() = runTest {
        val settings = createSettings(enabled = false)
        val questionRepository = FakeQuestionRepository(
            summary = TodayReviewSummary(dueCardCount = 9, dueQuestionCount = 9)
        )
        val notifier = FakeReminderNotifier()
        val scheduler = FakeReminderScheduler()
        val runner = ReminderCheckRunner(
            appSettingsRepository = FakeAppSettingsRepository(settings),
            questionRepository = questionRepository,
            reminderNotifier = notifier,
            reminderScheduler = scheduler,
            timeProvider = FixedTimeProvider(now = 3_000L)
        )

        runner.run()

        assertEquals(emptyList<Long>(), questionRepository.requestedNowEpochMillis)
        assertEquals(emptyList<NotificationCall>(), notifier.calls)
        assertEquals(listOf(settings), scheduler.calls)
    }

    /**
     * 固定时间实现能让后台查询时点稳定可断言，避免系统时钟让测试变得脆弱。
     */
    private class FixedTimeProvider(
        private val now: Long
    ) : TimeProvider {
        override fun nowEpochMillis(): Long = now
    }

    /**
     * 设置仓储在本组测试里只需返回固定快照，因此最小实现即可支撑业务断言。
     */
    private class FakeAppSettingsRepository(
        private val settings: AppSettings
    ) : AppSettingsRepository {
        override fun observeSettings(): Flow<AppSettings> = MutableStateFlow(settings)
        override suspend fun getSettings(): AppSettings = settings
        override suspend fun setDailyReminderEnabled(enabled: Boolean) = Unit
        override suspend fun setDailyReminderTime(hour: Int, minute: Int) = Unit
        override suspend fun setSettings(settings: AppSettings) = Unit
        override suspend fun setSchemaVersion(schemaVersion: Int) = Unit
        override suspend fun setBackupLastAt(epochMillis: Long?) = Unit
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
    }

    /**
     * 到期概览查询调用次数本身就是业务语义的一部分，因此假仓储会显式记录请求时点。
     */
    private class FakeQuestionRepository(
        private val summary: TodayReviewSummary
    ) : QuestionRepository {
        val requestedNowEpochMillis = mutableListOf<Long>()

        override fun observeQuestionsByCard(cardId: String): Flow<List<com.kariscode.yike.domain.model.Question>> =
            MutableStateFlow(emptyList())

        override suspend fun findById(questionId: String) = null

        override suspend fun listByCard(cardId: String) = emptyList<com.kariscode.yike.domain.model.Question>()

        override suspend fun upsertAll(questions: List<com.kariscode.yike.domain.model.Question>) = Unit

        override suspend fun listDueQuestions(nowEpochMillis: Long) = emptyList<com.kariscode.yike.domain.model.Question>()

        override suspend fun findNextDueCardId(nowEpochMillis: Long): String? = null

        override suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary {
            requestedNowEpochMillis += nowEpochMillis
            return summary
        }

        override suspend fun delete(questionId: String) = Unit

        override suspend fun deleteAll(questionIds: Collection<String>) = Unit
    }

    /**
     * 通知调用记录成结构体后，断言可以直接聚焦数量是否正确传递。
     */
    private class FakeReminderNotifier : ReminderNotifier {
        val calls = mutableListOf<NotificationCall>()

        override fun showDailyReminder(dueCardCount: Int, dueQuestionCount: Int) {
            calls += NotificationCall(dueCardCount = dueCardCount, dueQuestionCount = dueQuestionCount)
        }
    }

    /**
     * 调度器假实现只关心收到的设置快照，足以验证续约动作有没有发生。
     */
    private class FakeReminderScheduler : ReminderSyncScheduler {
        val calls = mutableListOf<AppSettings>()

        override suspend fun syncReminderFromRepository() = Unit

        override fun syncReminder(settings: AppSettings) {
            calls += settings
        }
    }

    private data class NotificationCall(
        val dueCardCount: Int,
        val dueQuestionCount: Int
    )

    private fun createSettings(enabled: Boolean): AppSettings = AppSettings(
        dailyReminderEnabled = enabled,
        dailyReminderHour = 20,
        dailyReminderMinute = 30,
        schemaVersion = 4,
        backupLastAt = null,
        themeMode = ThemeMode.SYSTEM
    )
}
