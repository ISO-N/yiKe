package com.kariscode.yike.data.backup

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.data.sync.LanSyncChangeApplier
import com.kariscode.yike.data.sync.LanSyncConflictResolver
import com.kariscode.yike.data.sync.LanSyncJson
import com.kariscode.yike.data.sync.SyncDeckPayload
import com.kariscode.yike.data.sync.SyncChangePayload
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * BackupService 测试直接覆盖 JSON 导出、完整恢复与失败回滚，
 * 这样数据安全最后一道防线就不再只靠手工验证兜底。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BackupServiceTest {
    private lateinit var database: YikeDatabase
    private lateinit var appSettingsRepository: FakeAppSettingsRepository
    private lateinit var backupService: BackupService

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val testDispatcher = UnconfinedTestDispatcher()
        val reminderScheduler = object : ReminderSyncScheduler {
            override suspend fun syncReminderFromRepository() = Unit
            override fun syncReminder(settings: AppSettings) = Unit
        }
        database = Room.inMemoryDatabaseBuilder(application, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appSettingsRepository = FakeAppSettingsRepository()
        backupService = BackupService(
            application = application,
            database = database,
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            syncChangeDao = database.syncChangeDao(),
            appSettingsRepository = appSettingsRepository,
            backupValidator = BackupValidator(),
            changeApplier = LanSyncChangeApplier(
                database = database,
                appSettingsRepository = appSettingsRepository,
                reminderScheduler = reminderScheduler,
                deckDao = database.deckDao(),
                cardDao = database.cardDao(),
                questionDao = database.questionDao(),
                reviewRecordDao = database.reviewRecordDao(),
                conflictResolver = LanSyncConflictResolver()
            ),
            timeProvider = object : com.kariscode.yike.core.time.TimeProvider {
                override fun nowEpochMillis(): Long = 123_456L
            },
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 导出必须把设置和各层级数据都写进同一份 JSON，
     * 否则用户拿到的备份文件无法完整重建本地状态。
     */
    @Test
    fun exportToJsonString_includesSettingsAndHierarchy() = runTest {
        seedDeckHierarchy()
        appSettingsRepository.setSettings(
            AppSettings(
                dailyReminderEnabled = true,
                dailyReminderHour = 21,
                dailyReminderMinute = 15,
                schemaVersion = 4,
                backupLastAt = 8_000L,
                themeMode = ThemeMode.DARK
            )
        )

        val json = backupService.exportToJsonString(exportedAtEpochMillis = 222_000L)
        val document = BackupJson.json.decodeFromString<BackupDocument>(json)

        assertEquals(BackupDocumentKind.FULL, document.app.kind)
        assertEquals("21:15", document.full?.settings?.dailyReminderTime)
        assertEquals("dark", document.full?.settings?.themeMode)
        assertEquals(listOf("数学"), document.full?.decks?.map { it.name })
        assertEquals(listOf("极限"), document.full?.cards?.map { it.title })
        assertEquals(listOf("什么是极限"), document.full?.questions?.map { it.prompt })
        assertEquals(listOf("GOOD"), document.full?.reviewRecords?.map { it.rating })
    }

    /**
     * 空数据库也必须导出成合法文档，
     * 否则用户在“先配置提醒、后逐步录入内容”的阶段无法获得可恢复快照。
     */
    @Test
    fun exportToJsonString_emptyDatabaseProducesValidEmptyDocument() = runTest {
        val document = BackupJson.json.decodeFromString<BackupDocument>(
            backupService.exportToJsonString(exportedAtEpochMillis = 222_000L)
        )

        assertTrue(document.full?.decks?.isEmpty() == true)
        assertTrue(document.full?.cards?.isEmpty() == true)
        assertTrue(document.full?.questions?.isEmpty() == true)
        assertTrue(document.full?.reviewRecords?.isEmpty() == true)
        assertEquals(BackupConstants.BACKUP_VERSION, document.app.backupVersion)
    }

    /**
     * 增量导出必须只携带最近一次备份后的变更，
     * 否则它就会退化成另一份体积更大的完整备份。
     */
    @Test
    fun exportToJsonString_incrementalIncludesOnlyChangesAfterLastBackup() = runTest {
        appSettingsRepository.setBackupLastAt(5L)
        database.syncChangeDao().insert(
            com.kariscode.yike.data.local.db.entity.SyncChangeEntity(
                entityType = SyncEntityType.DECK.name,
                entityId = "deck_1",
                operation = SyncChangeOperation.UPSERT.name,
                summary = "数学",
                payloadJson = LanSyncJson.json.encodeToString(
                    SyncDeckPayload.serializer(),
                    SyncDeckPayload(
                        id = "deck_1",
                        name = "数学",
                        description = "",
                        tags = emptyList(),
                        intervalStepCount = 8,
                        archived = false,
                        sortOrder = 0,
                        createdAt = 1L,
                        updatedAt = 6L
                    )
                ),
                payloadHash = "hash_1",
                modifiedAt = 6L
            )
        )

        val json = backupService.exportToJsonString(
            exportedAtEpochMillis = 10L,
            mode = BackupExportMode.INCREMENTAL
        )
        val document = BackupJson.json.decodeFromString<BackupDocument>(json)

        assertEquals(BackupDocumentKind.INCREMENTAL, document.app.kind)
        assertEquals("1970-01-01T08:00:00.005+08:00", document.incremental?.baseBackupAt)
        assertEquals(listOf("deck_1"), document.incremental?.changes?.map { it.entityId })
    }

    /**
     * 从合法 JSON 恢复后，数据库与设置都必须回到备份时的完整快照，
     * 否则恢复就会变成“只有部分字段生效”的危险操作。
     */
    @Test
    fun restoreFromJsonString_restoresDatabaseAndSettings() = runTest {
        seedDeckHierarchy()
        val expectedSettings = AppSettings(
            dailyReminderEnabled = true,
            dailyReminderHour = 21,
            dailyReminderMinute = 15,
            schemaVersion = 4,
            backupLastAt = 8_000L,
            themeMode = ThemeMode.SYSTEM
        )
        appSettingsRepository.setSettings(expectedSettings)
        val backupJson = backupService.exportToJsonString(exportedAtEpochMillis = 333_000L)

        database.deckDao().clearAll()
        appSettingsRepository.setSettings(
            expectedSettings.copy(
                dailyReminderEnabled = false,
                dailyReminderHour = 8,
                dailyReminderMinute = 0,
                themeMode = ThemeMode.LIGHT
            )
        )

        backupService.restoreFromJsonString(backupJson)

        assertEquals(1, database.deckDao().listAll().size)
        assertEquals(1, database.cardDao().listAll().size)
        assertEquals(1, database.questionDao().listAll().size)
        assertEquals(1, database.reviewRecordDao().listAll().size)
        assertEquals(expectedSettings, appSettingsRepository.getSettings())
    }

    /**
     * 恢复阶段若设置写入失败，数据库和设置都必须回滚到旧快照，
     * 否则用户会得到一份“库里是新数据、设置还是旧数据”的半恢复状态。
     */
    @Test
    fun restoreFromJsonString_whenSettingsWriteFails_rollsBackDatabaseAndSettings() = runTest {
        seedDeckHierarchy()
        val originalSettings = AppSettings(
            dailyReminderEnabled = false,
            dailyReminderHour = 20,
            dailyReminderMinute = 0,
            schemaVersion = 4,
            backupLastAt = null,
            themeMode = ThemeMode.LIGHT
        )
        appSettingsRepository.setSettings(originalSettings)
        val backupJson = backupService.exportToJsonString(exportedAtEpochMillis = 444_000L)

        database.deckDao().clearAll()
        database.deckDao().upsert(
            DeckEntity(
                id = "deck_other",
                name = "英语",
                description = "",
                tagsJson = "[]",
                intervalStepCount = 8,
                archived = false,
                sortOrder = 0,
                createdAt = 2L,
                updatedAt = 2L
            )
        )
        appSettingsRepository.setSettings(
            originalSettings.copy(
                dailyReminderEnabled = true,
                dailyReminderHour = 9,
                dailyReminderMinute = 30,
                themeMode = ThemeMode.DARK
            )
        )
        appSettingsRepository.failNextSetSettings = true

        val result = runCatching { backupService.restoreFromJsonString(backupJson) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf("deck_other"), database.deckDao().listAll().map { it.id })
        assertEquals(
            originalSettings.copy(
                dailyReminderEnabled = true,
                dailyReminderHour = 9,
                dailyReminderMinute = 30,
                themeMode = ThemeMode.DARK
            ),
            appSettingsRepository.getSettings()
        )
    }

    /**
     * 增量恢复必须复用同步变更应用器，把变更直接叠加到当前数据库，
     * 这样用户才能在完整备份之后继续按小文件滚动恢复最新改动。
     */
    @Test
    fun restoreFromJsonString_incrementalAppliesSyncedChanges() = runTest {
        val incrementalJson = BackupJson.json.encodeToString(
            BackupDocument(
                app = BackupAppInfo(
                    name = "忆刻",
                    backupVersion = BackupConstants.BACKUP_VERSION,
                    exportedAt = "2026-03-15T20:00:00+08:00",
                    kind = BackupDocumentKind.INCREMENTAL
                ),
                incremental = BackupIncrementalPayload(
                    baseBackupAt = "2026-03-15T19:00:00+08:00",
                    changes = listOf(
                        SyncChangePayload(
                            seq = 1L,
                            entityType = SyncEntityType.DECK.name,
                            entityId = "deck_new",
                            operation = SyncChangeOperation.UPSERT.name,
                            summary = "新卡组",
                            payloadJson = LanSyncJson.json.encodeToString(
                                SyncDeckPayload.serializer(),
                                SyncDeckPayload(
                                    id = "deck_new",
                                    name = "新卡组",
                                    description = "",
                                    tags = emptyList(),
                                    intervalStepCount = 8,
                                    archived = false,
                                    sortOrder = 0,
                                    createdAt = 1L,
                                    updatedAt = 2L
                                )
                            ),
                            payloadHash = "hash_deck_new",
                            modifiedAt = 2L
                        )
                    )
                )
            )
        )

        backupService.restoreFromJsonString(incrementalJson)

        assertEquals(listOf("deck_new"), database.deckDao().listAll().map { it.id })
    }

    private suspend fun seedDeckHierarchy() {
        database.deckDao().upsert(
            DeckEntity(
                id = "deck_math",
                name = "数学",
                description = "",
                tagsJson = """["高频"]""",
                intervalStepCount = 4,
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.cardDao().upsert(
            CardEntity(
                id = "card_limit",
                deckId = "deck_math",
                title = "极限",
                description = "",
                archived = false,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.questionDao().upsertAll(
            listOf(
                QuestionEntity(
                    id = "question_limit",
                    cardId = "card_limit",
                    prompt = "什么是极限",
                    answer = "趋近过程的结果",
                    tagsJson = """["定义"]""",
                    status = QuestionEntity.STATUS_ACTIVE,
                    stageIndex = 2,
                    dueAt = 9_000L,
                    lastReviewedAt = 8_000L,
                    reviewCount = 3,
                    lapseCount = 1,
                    createdAt = 1L,
                    updatedAt = 2L
                )
            )
        )
        database.reviewRecordDao().insert(
            ReviewRecordEntity(
                id = "record_1",
                questionId = "question_limit",
                rating = ReviewRating.GOOD.name,
                oldStageIndex = 1,
                newStageIndex = 2,
                oldDueAt = 4_000L,
                newDueAt = 9_000L,
                reviewedAt = 8_000L,
                responseTimeMs = 600L,
                note = ""
            )
        )
    }

    /**
     * 假设置仓储通过“下一次 setSettings 是否失败”开关来模拟恢复阶段的异常，
     * 便于验证 BackupService 的补偿回滚是否真的生效。
     */
    private class FakeAppSettingsRepository : AppSettingsRepository {
        private val state = MutableStateFlow(
            AppSettings(
                dailyReminderEnabled = false,
                dailyReminderHour = 20,
                dailyReminderMinute = 0,
                schemaVersion = 4,
                backupLastAt = null,
                themeMode = ThemeMode.LIGHT
            )
        )
        var failNextSetSettings: Boolean = false

        override fun observeSettings(): Flow<AppSettings> = state
        override suspend fun getSettings(): AppSettings = state.value
        override suspend fun setDailyReminderEnabled(enabled: Boolean) {
            state.value = state.value.copy(dailyReminderEnabled = enabled)
        }
        override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
            state.value = state.value.copy(dailyReminderHour = hour, dailyReminderMinute = minute)
        }
        override suspend fun setSettings(settings: AppSettings) {
            if (failNextSetSettings) {
                failNextSetSettings = false
                throw IllegalStateException("settings write failed")
            }
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
}
