package com.kariscode.yike.data.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 变更应用器集成测试覆盖“远端 upsert/delete + 设置合并 + 提醒重建”链路，
 * 是为了在同步流程继续演进时守住“落库结果与副作用编排”这条高风险主路径。
 */
@RunWith(RobolectricTestRunner::class)
class LanSyncChangeApplierIntegrationTest {
    private lateinit var database: YikeDatabase
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var reminderScheduler: RecordingReminderSyncScheduler
    private lateinit var applier: LanSyncChangeApplier

    /**
     * 使用内存数据库能让测试验证真实 Room 行为，
     * 同时避免污染本机开发数据。
     */
    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        database = Room.inMemoryDatabaseBuilder(application, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appSettingsRepository = FakeAppSettingsRepository()
        reminderScheduler = RecordingReminderSyncScheduler()
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

    /**
     * 释放数据库连接可以避免资源泄漏影响后续测试。
     */
    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 远端变更应用必须同时覆盖 upsert、delete 与设置合并，
     * 否则跨设备同步会出现“数据对齐了但设置没对齐/提醒没更新”的割裂体验。
     */
    @Test
    fun applyIncomingChanges_appliesDataAndSettingsAndSchedulesReminder() = runBlocking {
        database.deckDao().upsertAll(listOf(deckEntity(id = "deck_1", name = "旧卡组")))
        database.cardDao().upsertAll(listOf(cardEntity(id = "card_1", deckId = "deck_1")))
        database.questionDao().upsertAll(listOf(questionEntity(id = "q_1", cardId = "card_1")))
        appSettingsRepository.setSettings(
            AppSettings(
                dailyReminderEnabled = false,
                dailyReminderHour = 9,
                dailyReminderMinute = 0,
                schemaVersion = 4,
                backupLastAt = 8_000L,
                themeMode = ThemeMode.LIGHT
            )
        )

        val incoming = listOf(
            change(
                seq = 10L,
                entityType = SyncEntityType.DECK,
                entityId = "deck_1",
                operation = SyncChangeOperation.UPSERT,
                summary = "新卡组",
                payloadJson = LanSyncJson.json.encodeToString(
                    SyncDeckPayload.serializer(),
                    SyncDeckPayload(
                        id = "deck_1",
                        name = "新卡组",
                        description = "",
                        tags = emptyList(),
                        intervalStepCount = 6,
                        archived = false,
                        sortOrder = 0,
                        createdAt = 1_000L,
                        updatedAt = 2_000L
                    )
                ),
                payloadHash = "hash_deck"
            ),
            change(
                seq = 11L,
                entityType = SyncEntityType.SETTINGS,
                entityId = "settings",
                operation = SyncChangeOperation.UPSERT,
                summary = "settings",
                payloadJson = LanSyncJson.json.encodeToString(
                    SyncSettingsPayload.serializer(),
                    SyncSettingsPayload(
                        dailyReminderEnabled = true,
                        dailyReminderHour = 21,
                        dailyReminderMinute = 15,
                        themeMode = ThemeMode.DARK.storageValue,
                        streakAchievementUnlocks = emptyList()
                    )
                ),
                payloadHash = "hash_settings"
            ),
            change(
                seq = 12L,
                entityType = SyncEntityType.QUESTION,
                entityId = "q_1",
                operation = SyncChangeOperation.DELETE,
                summary = "q_1",
                payloadJson = null,
                payloadHash = "hash_delete"
            )
        )

        val latestSeq = applier.applyIncomingChanges(incoming)

        assertEquals(12L, latestSeq)
        assertEquals("新卡组", database.deckDao().findById("deck_1")?.name)
        assertNull(database.questionDao().findById("q_1"))

        val mergedSettings = appSettingsRepository.getSettings()
        assertTrue(mergedSettings.dailyReminderEnabled)
        assertEquals(21, mergedSettings.dailyReminderHour)
        assertEquals(15, mergedSettings.dailyReminderMinute)
        assertEquals(ThemeMode.DARK, mergedSettings.themeMode)
        assertEquals(4, mergedSettings.schemaVersion)
        assertEquals(8_000L, mergedSettings.backupLastAt)

        assertEquals(1, reminderScheduler.syncedSettings.size)
        assertEquals(ThemeMode.DARK, reminderScheduler.syncedSettings.single().themeMode)
    }

    /**
     * 变更构造器统一填充协议字段，是为了让测试用例只关心实体类型、操作与 seq 顺序。
     */
    private fun change(
        seq: Long,
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncChangeOperation,
        summary: String,
        payloadJson: String?,
        payloadHash: String
    ): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = entityType.name,
        entityId = entityId,
        operation = operation.name,
        summary = summary,
        payloadJson = payloadJson,
        payloadHash = payloadHash,
        modifiedAt = 1_000L
    )

    /**
     * 假设置仓储只保留同步测试所需的 get/set 与 Flow，
     * 是为了把断言聚焦在“合并后的快照”而不是 DataStore 细节。
     */
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val settingsFlow = MutableStateFlow(
            AppSettings(
                dailyReminderEnabled = false,
                dailyReminderHour = 9,
                dailyReminderMinute = 0,
                schemaVersion = 1,
                backupLastAt = null,
                themeMode = ThemeMode.SYSTEM
            )
        )

        override fun observeSettings(): Flow<AppSettings> = settingsFlow

        override suspend fun getSettings(): AppSettings = settingsFlow.value

        override suspend fun setDailyReminderEnabled(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(dailyReminderEnabled = enabled)
        }

        override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
            settingsFlow.value = settingsFlow.value.copy(dailyReminderHour = hour, dailyReminderMinute = minute)
        }

        override suspend fun setSettings(settings: AppSettings) {
            settingsFlow.value = settings
        }

        override suspend fun setSchemaVersion(schemaVersion: Int) {
            settingsFlow.value = settingsFlow.value.copy(schemaVersion = schemaVersion)
        }

        override suspend fun setBackupLastAt(epochMillis: Long?) {
            settingsFlow.value = settingsFlow.value.copy(backupLastAt = epochMillis)
        }

        override suspend fun setThemeMode(mode: ThemeMode) {
            settingsFlow.value = settingsFlow.value.copy(themeMode = mode)
        }
    }

    /**
     * 记录型提醒调度器用来验证设置应用后的副作用确实被触发，
     * 这样同步测试不会仅停留在落库层面而遗漏提醒重建。
     */
    private class RecordingReminderSyncScheduler : ReminderSyncScheduler {
        val syncedSettings = mutableListOf<AppSettings>()

        override suspend fun syncReminderFromRepository() = Unit

        override fun syncReminder(settings: AppSettings) {
            syncedSettings.add(settings)
        }
    }

    /**
     * DeckEntity 构造器把与同步无关字段收口在默认值里，是为了让测试只关注字段是否被 upsert 覆盖。
     */
    private fun deckEntity(id: String, name: String): DeckEntity = DeckEntity(
        id = id,
        name = name,
        description = "",
        tagsJson = "[]",
        intervalStepCount = 6,
        archived = false,
        sortOrder = 0,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    /**
     * CardEntity 在本测试里只用于提供外键关系，最小字段即可守住“应用顺序不破坏层级”的约束。
     */
    private fun cardEntity(id: String, deckId: String): CardEntity = CardEntity(
        id = id,
        deckId = deckId,
        title = "卡片",
        description = "",
        archived = false,
        sortOrder = 0,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    /**
     * QuestionEntity 只要存在即可验证 delete 生效，
     * 因此测试里用最小字段即可避免引入调度规则的额外复杂度。
     */
    private fun questionEntity(id: String, cardId: String): QuestionEntity = QuestionEntity(
        id = id,
        cardId = cardId,
        prompt = "题面",
        answer = "答案",
        tagsJson = "[]",
        status = QuestionEntity.STATUS_ACTIVE,
        stageIndex = 0,
        dueAt = 1_000L,
        lastReviewedAt = null,
        reviewCount = 0,
        lapseCount = 0,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )
}
