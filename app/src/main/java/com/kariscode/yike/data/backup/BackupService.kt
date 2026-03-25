package com.kariscode.yike.data.backup

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.domain.time.TimeTextFormatter
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.search.NoOpQuestionSearchIndexWriter
import com.kariscode.yike.data.search.QuestionSearchIndexWriter
import com.kariscode.yike.data.sync.LanSyncChangeApplier
import com.kariscode.yike.data.sync.toPayload
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.StreakAchievementUnlock
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import java.io.FileNotFoundException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 备份快照把设置和各数据表绑定成同一份视图，
 * 是为了让导出与补偿回滚共享同一套读取边界并避免再次写出串行样板。
 */
private data class BackupSnapshot(
    val settings: AppSettings,
    val decks: List<DeckEntity>,
    val cards: List<CardEntity>,
    val questions: List<QuestionEntity>,
    val reviewRecords: List<ReviewRecordEntity>
)

/**
 * 备份服务集中处理导出、校验与恢复，是为了让页面只负责触发文件选择而不触碰高风险数据操作细节。
 */
class BackupService(
    private val application: Application,
    private val database: YikeDatabase,
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val questionDao: QuestionDao,
    private val reviewRecordDao: ReviewRecordDao,
    private val syncChangeDao: SyncChangeDao,
    private val appSettingsRepository: AppSettingsRepository,
    private val backupValidator: BackupValidator,
    private val changeApplier: LanSyncChangeApplier,
    private val questionSearchIndexWriter: QuestionSearchIndexWriter = NoOpQuestionSearchIndexWriter,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers
) : BackupOperations {
    /**
     * 导出时即便数据为空也要写出合法备份文件，
     * 这样用户才能在“先备份配置、后逐步录入内容”的场景下获得稳定结果。
     */
    override suspend fun exportToUri(
        uri: Uri,
        mode: BackupExportMode
    ) = withContext(dispatchers.io) {
        val exportedAt = timeProvider.nowEpochMillis()
        val jsonString = exportToJsonString(
            exportedAtEpochMillis = exportedAt,
            mode = mode
        )
        application.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(jsonString.toByteArray())
            outputStream.flush()
        } ?: throw FileNotFoundException("无法创建备份文件")
        appSettingsRepository.setBackupLastAt(exportedAt)
    }

    /**
     * 恢复前先做解析与校验，再执行全量覆盖；
     * 这样能把“文件非法”和“写库失败”两类风险明确分层，便于给用户稳定反馈。
     */
    override suspend fun restoreFromUri(uri: Uri) = withContext(dispatchers.io) {
        val jsonString = application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("无法读取备份文件")
        restoreFromJsonString(jsonString)
    }

    /**
     * 导出文件名单独提供生成方法，是为了把命名规范固定下来并便于页面直接复用。
     */
    override fun createSuggestedFileName(mode: BackupExportMode): String {
        val nowEpochMillis = timeProvider.nowEpochMillis()
        val stamp = BackupJson.formatEpochMillis(nowEpochMillis)
            .replace("-", "")
            .replace(":", "")
            .replace("T", "-")
            .substringBefore("+")
        val prefix = when (mode) {
            BackupExportMode.FULL -> "yike-backup"
            BackupExportMode.INCREMENTAL -> "yike-backup-incremental"
        }
        return "$prefix-$stamp.json"
    }

    /**
     * 局域网同步与导出文件都需要同一份 JSON 快照，因此单独暴露字符串导出入口可以避免重复序列化骨架。
     */
    suspend fun exportToJsonString(
        exportedAtEpochMillis: Long = timeProvider.nowEpochMillis(),
        mode: BackupExportMode = BackupExportMode.FULL
    ): String = withContext(dispatchers.io) {
        val document = exportDocument(
            exportedAtEpochMillis = exportedAtEpochMillis,
            mode = mode
        )
        BackupJson.json.encodeToString(document)
    }

    /**
     * 同步拉取到的远端备份本质上与本地导入文件等价，
     * 因此共享同一恢复入口可以确保校验、事务与失败补偿完全一致。
     */
    suspend fun restoreFromJsonString(jsonString: String) = withContext(dispatchers.io) {
        val document = BackupJson.json.decodeFromString<BackupDocument>(jsonString)
        backupValidator.validate(document).getOrThrow()
        restoreDocument(document)
    }

    /**
     * 同步预览只需要结构化文档而不一定马上写文件，因此公开文档导出入口能减少二次反序列化成本。
     */
    suspend fun exportDocument(
        exportedAtEpochMillis: Long = timeProvider.nowEpochMillis(),
        mode: BackupExportMode = BackupExportMode.FULL
    ): BackupDocument = withContext(dispatchers.io) {
        when (mode) {
            BackupExportMode.FULL -> buildFullBackupDocument(exportedAtEpochMillis = exportedAtEpochMillis)
            BackupExportMode.INCREMENTAL -> buildIncrementalBackupDocument(exportedAtEpochMillis = exportedAtEpochMillis)
        }
    }

    /**
     * 构建备份文档时读取完整数据快照，可确保序列化结果与当下本地状态严格一致。
     */
    private suspend fun buildFullBackupDocument(exportedAtEpochMillis: Long): BackupDocument {
        val snapshot = readCurrentSnapshot()

        return BackupDocument(
            app = BackupAppInfo(
                name = "忆刻",
                backupVersion = BackupConstants.BACKUP_VERSION,
                exportedAt = BackupJson.formatEpochMillis(exportedAtEpochMillis),
                kind = BackupDocumentKind.FULL
            ),
            full = BackupFullPayload(
                settings = BackupSettings(
                    dailyReminderEnabled = snapshot.settings.dailyReminderEnabled,
                    dailyReminderTime = snapshot.settings.toBackupReminderTime(),
                    schemaVersion = snapshot.settings.schemaVersion,
                    backupLastAt = snapshot.settings.backupLastAt?.let(BackupJson::formatEpochMillis),
                    themeMode = snapshot.settings.themeMode.storageValue,
                    streakAchievementUnlocks = snapshot.settings.streakAchievementUnlocks.map { unlock ->
                        BackupStreakAchievementUnlock(
                            id = unlock.achievementId,
                            unlockedAt = BackupJson.formatEpochMillis(unlock.unlockedAtEpochMillis)
                        )
                    }
                ),
                decks = snapshot.decks.map { deck -> deck.toBackup() },
                cards = snapshot.cards.map { card -> card.toBackup() },
                questions = snapshot.questions.map { question -> question.toBackup() },
                reviewRecords = snapshot.reviewRecords.map { reviewRecord -> reviewRecord.toBackup() }
            )
        )
    }

    /**
     * 增量备份直接复用同步 journal，是为了在不额外维护第二套差异快照的前提下提供更轻量的导出能力。
     */
    private suspend fun buildIncrementalBackupDocument(exportedAtEpochMillis: Long): BackupDocument {
        val settings = appSettingsRepository.getSettings()
        val baseBackupAt = requireNotNull(settings.backupLastAt) {
            "暂无可增量导出的基线备份，请先导出一次完整备份"
        }
        val changes = syncChangeDao.listModifiedAfter(baseBackupAt)
            .map { change -> change.toPayload() }
        return BackupDocument(
            app = BackupAppInfo(
                name = "忆刻",
                backupVersion = BackupConstants.BACKUP_VERSION,
                exportedAt = BackupJson.formatEpochMillis(exportedAtEpochMillis),
                kind = BackupDocumentKind.INCREMENTAL
            ),
            incremental = BackupIncrementalPayload(
                baseBackupAt = BackupJson.formatEpochMillis(baseBackupAt),
                changes = changes
            )
        )
    }

    /**
     * 为了做到“恢复失败时当前数据不被修改”，这里先保留旧快照并在设置写入失败时执行补偿恢复。
     */
    private suspend fun restoreDocument(document: BackupDocument) {
        when (document.app.kind) {
            BackupDocumentKind.FULL -> restoreFullDocument(requireNotNull(document.full))
            BackupDocumentKind.INCREMENTAL -> restoreIncrementalDocument(requireNotNull(document.incremental))
        }
    }

    /**
     * 完整恢复继续沿用“先替换数据库、再写设置、失败则回滚”的高安全策略，
     * 是为了保持原有整包恢复的可靠性不回退。
     */
    private suspend fun restoreFullDocument(payload: BackupFullPayload) {
        val previousSnapshot = readCurrentSnapshot()
        val restoredEntities = payload.toRestorePayload()

        try {
            replaceDatabaseContent(restoredEntities)
            writeSettingsFromBackup(payload.settings)
        } catch (throwable: Throwable) {
            replaceDatabaseContent(previousSnapshot.toRestorePayload())
            restorePreviousSettings(previousSnapshot.settings)
            throw IllegalStateException("恢复失败，当前数据未被修改", throwable)
        }
    }

    /**
     * 增量恢复同样保留失败回滚，是为了避免部分变更落库成功、部分失败时把当前数据留在中间态。
     */
    private suspend fun restoreIncrementalDocument(payload: BackupIncrementalPayload) {
        val previousSnapshot = readCurrentSnapshot()
        try {
            changeApplier.applyIncomingChanges(payload.changes)
        } catch (throwable: Throwable) {
            replaceDatabaseContent(previousSnapshot.toRestorePayload())
            restorePreviousSettings(previousSnapshot.settings)
            throw IllegalStateException("恢复失败，当前数据未被修改", throwable)
        }
    }

    /**
     * 导出、恢复前快照和失败回滚都依赖同一时刻的数据视图，
     * 因此统一并发读取可以同时收敛代码路径和等待成本，
     * 并避免多层 `parallel` 解构把“谁和谁是一组”这种实现细节带进返回值结构。
     */
    private suspend fun readCurrentSnapshot(): BackupSnapshot {
        return coroutineScope {
            val settingsDeferred = async { appSettingsRepository.getSettings() }
            val decksDeferred = async { deckDao.listAll() }
            val cardsDeferred = async { cardDao.listAll() }
            val questionsDeferred = async { questionDao.listAll() }
            val reviewRecordsDeferred = async { reviewRecordDao.listAll() }
            BackupSnapshot(
                settings = settingsDeferred.await(),
                decks = decksDeferred.await(),
                cards = cardsDeferred.await(),
                questions = questionsDeferred.await(),
                reviewRecords = reviewRecordsDeferred.await()
            )
        }
    }

    /**
     * 设置写入单独封装，是为了让恢复成功后的提醒配置和版本信息与备份内容保持一致。
     */
    private suspend fun writeSettingsFromBackup(settings: BackupSettings) {
        val (hour, minute) = TimeTextFormatter.parseHourMinute(settings.dailyReminderTime)
        persistSettings(
            settings = AppSettings(
                dailyReminderEnabled = settings.dailyReminderEnabled,
                dailyReminderHour = hour,
                dailyReminderMinute = minute,
                schemaVersion = settings.schemaVersion,
                backupLastAt = settings.backupLastAt?.let(BackupJson::parseEpochMillis),
                themeMode = ThemeMode.fromStorageValue(settings.themeMode),
                streakAchievementUnlocks = settings.streakAchievementUnlocks.map { payload ->
                    StreakAchievementUnlock(
                        achievementId = payload.id,
                        unlockedAtEpochMillis = BackupJson.parseEpochMillis(payload.unlockedAt)
                    )
                }
            )
        )
    }

    /**
     * 发生补偿回滚时要把旧设置一并恢复，
     * 否则即使数据库回滚成功，提醒配置仍可能与当前数据脱节。
     */
    private suspend fun restorePreviousSettings(settings: AppSettings) {
        persistSettings(settings = settings)
    }

    /**
     * 恢复与回滚都通过同一快照入口写入设置，是为了把多字段一致性约束收口到仓储层维护。
     */
    private suspend fun persistSettings(settings: AppSettings) {
        appSettingsRepository.setSettings(settings)
    }

    /**
     * 恢复主流程与失败补偿都需要执行“先清空再全量写回”，
     * 抽成统一入口可以确保事务骨架只维护一份，不会在两条路径上逐渐分叉。
     */
    private suspend fun replaceDatabaseContent(payload: RestorePayload) {
        database.withTransaction {
            clearDatabaseContent()
            deckDao.upsertAll(payload.decks)
            cardDao.upsertAll(payload.cards)
            questionDao.upsertAll(payload.questions)
            questionSearchIndexWriter.refreshQuestions(payload.questions)
            reviewRecordDao.insertAll(payload.reviewRecords)
        }
    }

    /**
     * 清空顺序集中在单点，是为了让恢复语义与级联关系调整时只需要校验一个入口。
     */
    private suspend fun clearDatabaseContent() {
        reviewRecordDao.clearAll()
        questionDao.clearAll()
        cardDao.clearAll()
        deckDao.clearAll()
    }

    /**
     * 备份文档先一次性转成实体载荷，是为了让校验通过后的恢复事务只专注处理数据库替换。
     */
    private fun BackupFullPayload.toRestorePayload(): RestorePayload = RestorePayload(
        decks = decks.map { deck -> deck.toEntity() },
        cards = cards.map { card -> card.toEntity() },
        questions = questions.map { question -> question.toEntity() },
        reviewRecords = reviewRecords.map { record -> record.toEntity() }
    )

    /**
     * 旧快照同样投影成统一载荷，是为了让补偿恢复与正式恢复完全复用同一套写入骨架。
     */
    private fun BackupSnapshot.toRestorePayload(): RestorePayload = RestorePayload(
        decks = decks,
        cards = cards,
        questions = questions,
        reviewRecords = reviewRecords
    )

    /**
     * 恢复时把待写入的实体集合显式建模，可以避免多组平行列表在参数传递中错位。
     */
    private data class RestorePayload(
        val decks: List<DeckEntity>,
        val cards: List<CardEntity>,
        val questions: List<QuestionEntity>,
        val reviewRecords: List<ReviewRecordEntity>
    )

}

