package com.kariscode.yike.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * LanSyncChangeApplier 测试直接验证远端变更落库与设置合并，
 * 这样同步执行阶段最关键的数据应用规则就能独立于网络层稳定回归。
 */
@RunWith(RobolectricTestRunner::class)
class LanSyncChangeApplierTest {
    private lateinit var database: YikeDatabase
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var applier: LanSyncChangeApplier

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YikeDatabase::class.java
        ).allowMainThreadQueries().build()
        appSettingsRepository = FakeAppSettingsRepository()
        reminderScheduler = FakeReminderScheduler()
        applier = LanSyncChangeApplier(
            database = database,
            appSettingsRepository = appSettingsRepository,
            reminderScheduler = reminderScheduler,
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            conflictResolver = LanSyncConflictResolver()
        )
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) {
            database.close()
        }
    }

    /**
     * 远端 upsert 与 settings 变更应用后，数据库与本地设置都必须同步更新，
     * 否则双向同步完成后应用仍会处于“数据已变、偏好未变”的不一致状态。
     */
    @Test
    fun applyIncomingChanges_upsertsEntitiesAndMergesSettings() = runTest {
        val changes = listOf(
            syncDeckChange(seq = 1L),
            syncCardChange(seq = 2L),
            syncQuestionChange(seq = 3L),
            syncReviewRecordChange(seq = 4L),
            syncSettingsChange(seq = 5L)
        )

        val latestSeq = applier.applyIncomingChanges(changes)

        assertEquals(5L, latestSeq)
        assertEquals(listOf("deck_math"), database.deckDao().listAll().map { it.id })
        assertEquals(listOf("card_limit"), database.cardDao().listAll().map { it.id })
        assertEquals(listOf("question_limit"), database.questionDao().listAll().map { it.id })
        assertEquals(listOf("record_1"), database.reviewRecordDao().listAll().map { it.id })
        assertEquals(true, appSettingsRepository.getSettings().dailyReminderEnabled)
        assertEquals(7, appSettingsRepository.getSettings().dailyReminderHour)
        assertEquals(45, appSettingsRepository.getSettings().dailyReminderMinute)
        assertEquals(ThemeMode.DARK, appSettingsRepository.getSettings().themeMode)
        assertEquals(1, reminderScheduler.calls.size)
    }

    /**
     * 删除变更必须真正从本地库移除对象，
     * 否则远端删除在本机上只会变成“看起来同步成功，但旧内容还在”的假状态。
     */
    @Test
    fun applyIncomingChanges_deletesEntities() = runTest {
        database.deckDao().upsert(
            DeckEntity(
                id = "deck_delete",
                name = "待删除卡组",
                description = "",
                tagsJson = "[]",
                intervalStepCount = 8,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

        val latestSeq = applier.applyIncomingChanges(
            listOf(
                SyncChangePayload(
                    seq = 9L,
                    entityType = SyncEntityType.DECK.name,
                    entityId = "deck_delete",
                    operation = com.kariscode.yike.domain.model.SyncChangeOperation.DELETE.name,
                    summary = "待删除卡组",
                    payloadJson = null,
                    payloadHash = "delete-hash",
                    modifiedAt = 9_000L
                )
            )
        )

        assertEquals(9L, latestSeq)
        assertEquals(emptyList<String>(), database.deckDao().listAll().map { it.id })
    }

    private fun syncDeckChange(seq: Long): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.DECK.name,
        entityId = "deck_math",
        operation = com.kariscode.yike.domain.model.SyncChangeOperation.UPSERT.name,
        summary = "数学",
        payloadJson = LanSyncJson.json.encodeToString(
            SyncDeckPayload.serializer(),
            SyncDeckPayload(
                id = "deck_math",
                name = "数学",
                description = "",
                tags = listOf("高频"),
                intervalStepCount = 4,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 2L
            )
        ),
        payloadHash = "deck-hash",
        modifiedAt = 2L
    )

    private fun syncCardChange(seq: Long): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.CARD.name,
        entityId = "card_limit",
        operation = com.kariscode.yike.domain.model.SyncChangeOperation.UPSERT.name,
        summary = "极限",
        payloadJson = LanSyncJson.json.encodeToString(
            SyncCardPayload.serializer(),
            SyncCardPayload(
                id = "card_limit",
                deckId = "deck_math",
                title = "极限",
                description = "",
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 2L
            )
        ),
        payloadHash = "card-hash",
        modifiedAt = 2L
    )

    private fun syncQuestionChange(seq: Long): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.QUESTION.name,
        entityId = "question_limit",
        operation = com.kariscode.yike.domain.model.SyncChangeOperation.UPSERT.name,
        summary = "什么是极限",
        payloadJson = LanSyncJson.json.encodeToString(
            SyncQuestionPayload.serializer(),
            SyncQuestionPayload(
                id = "question_limit",
                cardId = "card_limit",
                prompt = "什么是极限",
                answer = "趋近过程的结果",
                tags = listOf("定义"),
                status = "active",
                stageIndex = 2,
                dueAt = 9_000L,
                lastReviewedAt = 8_000L,
                reviewCount = 3,
                lapseCount = 1,
                createdAt = 1L,
                updatedAt = 2L
            )
        ),
        payloadHash = "question-hash",
        modifiedAt = 2L
    )

    private fun syncReviewRecordChange(seq: Long): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.REVIEW_RECORD.name,
        entityId = "record_1",
        operation = com.kariscode.yike.domain.model.SyncChangeOperation.UPSERT.name,
        summary = "评分 GOOD",
        payloadJson = LanSyncJson.json.encodeToString(
            SyncReviewRecordPayload.serializer(),
            SyncReviewRecordPayload(
                id = "record_1",
                questionId = "question_limit",
                rating = "GOOD",
                oldStageIndex = 1,
                newStageIndex = 2,
                oldDueAt = 4_000L,
                newDueAt = 9_000L,
                reviewedAt = 8_000L,
                responseTimeMs = 600L,
                note = ""
            )
        ),
        payloadHash = "record-hash",
        modifiedAt = 8_000L
    )

    private fun syncSettingsChange(seq: Long): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = SyncEntityType.SETTINGS.name,
        entityId = "app_settings",
        operation = com.kariscode.yike.domain.model.SyncChangeOperation.UPSERT.name,
        summary = "应用设置",
        payloadJson = LanSyncJson.json.encodeToString(
            SyncSettingsPayload.serializer(),
            SyncSettingsPayload(
                dailyReminderEnabled = true,
                dailyReminderHour = 7,
                dailyReminderMinute = 45,
                themeMode = "dark"
            )
        ),
        payloadHash = "settings-hash",
        modifiedAt = 10_000L
    )

    /**
     * 假设置仓储只保留同步关心的可跨设备字段和本地快照，足以验证合并结果。
     */
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val state = MutableStateFlow(
            AppSettings(
                dailyReminderEnabled = false,
                dailyReminderHour = 20,
                dailyReminderMinute = 0,
                schemaVersion = 4,
                backupLastAt = 9_999L,
                themeMode = ThemeMode.LIGHT
            )
        )

        override fun observeSettings(): Flow<AppSettings> = state
        override suspend fun getSettings(): AppSettings = state.value
        override suspend fun setDailyReminderEnabled(enabled: Boolean) {
            state.value = state.value.copy(dailyReminderEnabled = enabled)
        }
        override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
            state.value = state.value.copy(dailyReminderHour = hour, dailyReminderMinute = minute)
        }
        override suspend fun setSettings(settings: AppSettings) {
            state.value = settings
        }
        override suspend fun setSchemaVersion(schemaVersion: Int) {
            state.value = state.value.copy(schemaVersion = schemaVersion)
        }
        override suspend fun setBackupLastAt(epochMillis: Long?) {
            state.value = state.value.copy(backupLastAt = epochMillis)
        }
        override suspend fun setThemeMode(mode: ThemeMode) {
            state.value = state.value.copy(themeMode = mode)
        }
    }

    /**
     * 调度器假实现只记录是否发生了提醒重建，从而验证 settings 变更的系统协同。
     */
    private class FakeReminderScheduler : com.kariscode.yike.data.reminder.ReminderSyncScheduler {
        val calls = mutableListOf<AppSettings>()

        override suspend fun syncReminderFromRepository() = Unit

        override fun syncReminder(settings: AppSettings) {
            calls += settings
        }
    }
}
