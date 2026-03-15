package com.kariscode.yike.data.backup

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.RoomMappers
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.AppSettingsRepository
import java.io.FileNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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
    private val appSettingsRepository: AppSettingsRepository,
    private val backupValidator: BackupValidator,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers
) {
    /**
     * 导出时即便数据为空也要写出合法备份文件，
     * 这样用户才能在“先备份配置、后逐步录入内容”的场景下获得稳定结果。
     */
    suspend fun exportToUri(uri: Uri) = withContext(dispatchers.io) {
        val exportedAt = timeProvider.nowEpochMillis()
        val document = buildBackupDocument(exportedAtEpochMillis = exportedAt)
        val jsonString = BackupJson.json.encodeToString(document)
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
    suspend fun restoreFromUri(uri: Uri) = withContext(dispatchers.io) {
        val jsonString = application.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: throw FileNotFoundException("无法读取备份文件")
        val document = BackupJson.json.decodeFromString<BackupDocument>(jsonString)
        backupValidator.validate(document).getOrThrow()
        restoreDocument(document)
    }

    /**
     * 导出文件名单独提供生成方法，是为了把命名规范固定下来并便于页面直接复用。
     */
    fun createSuggestedFileName(nowEpochMillis: Long = timeProvider.nowEpochMillis()): String {
        val stamp = BackupJson.formatEpochMillis(nowEpochMillis)
            .replace("-", "")
            .replace(":", "")
            .replace("T", "-")
            .substringBefore("+")
        return "yike-backup-$stamp.json"
    }

    /**
     * 构建备份文档时读取完整数据快照，可确保序列化结果与当下本地状态严格一致。
     */
    private suspend fun buildBackupDocument(exportedAtEpochMillis: Long): BackupDocument {
        val settings = appSettingsRepository.observeSettings().first()
        val decks = deckDao.listAll()
        val cards = cardDao.listAll()
        val questions = questionDao.listAll()
        val reviewRecords = reviewRecordDao.listAll()

        return BackupDocument(
            app = BackupAppInfo(
                name = "忆刻",
                backupVersion = BackupConstants.BACKUP_VERSION,
                exportedAt = BackupJson.formatEpochMillis(exportedAtEpochMillis)
            ),
            settings = BackupSettings(
                dailyReminderEnabled = settings.dailyReminderEnabled,
                dailyReminderTime = settings.toBackupReminderTime(),
                schemaVersion = settings.schemaVersion,
                backupLastAt = settings.backupLastAt?.let(BackupJson::formatEpochMillis)
            ),
            decks = decks.map { deck ->
                BackupDeck(
                    id = deck.id,
                    name = deck.name,
                    description = deck.description,
                    archived = deck.archived,
                    sortOrder = deck.sortOrder,
                    createdAt = BackupJson.formatEpochMillis(deck.createdAt),
                    updatedAt = BackupJson.formatEpochMillis(deck.updatedAt)
                )
            },
            cards = cards.map { card ->
                BackupCard(
                    id = card.id,
                    deckId = card.deckId,
                    title = card.title,
                    description = card.description,
                    archived = card.archived,
                    sortOrder = card.sortOrder,
                    createdAt = BackupJson.formatEpochMillis(card.createdAt),
                    updatedAt = BackupJson.formatEpochMillis(card.updatedAt)
                )
            },
            questions = questions.map { question ->
                val domainQuestion = RoomMappers.run { question.toDomain() }
                BackupQuestion(
                    id = domainQuestion.id,
                    cardId = domainQuestion.cardId,
                    prompt = domainQuestion.prompt,
                    answer = domainQuestion.answer,
                    tags = domainQuestion.tags,
                    status = when (domainQuestion.status) {
                        QuestionStatus.ACTIVE -> QuestionEntity.STATUS_ACTIVE
                        QuestionStatus.ARCHIVED -> QuestionEntity.STATUS_ARCHIVED
                    },
                    stageIndex = domainQuestion.stageIndex,
                    dueAt = BackupJson.formatEpochMillis(domainQuestion.dueAt),
                    lastReviewedAt = domainQuestion.lastReviewedAt?.let(BackupJson::formatEpochMillis),
                    reviewCount = domainQuestion.reviewCount,
                    lapseCount = domainQuestion.lapseCount,
                    createdAt = BackupJson.formatEpochMillis(domainQuestion.createdAt),
                    updatedAt = BackupJson.formatEpochMillis(domainQuestion.updatedAt)
                )
            },
            reviewRecords = reviewRecords.map { reviewRecord ->
                val domainRecord = RoomMappers.run { reviewRecord.toDomain() }
                BackupReviewRecord(
                    id = domainRecord.id,
                    questionId = domainRecord.questionId,
                    rating = domainRecord.rating.name,
                    oldStageIndex = domainRecord.oldStageIndex,
                    newStageIndex = domainRecord.newStageIndex,
                    oldDueAt = BackupJson.formatEpochMillis(domainRecord.oldDueAt),
                    newDueAt = BackupJson.formatEpochMillis(domainRecord.newDueAt),
                    reviewedAt = BackupJson.formatEpochMillis(domainRecord.reviewedAt),
                    responseTimeMs = domainRecord.responseTimeMs,
                    note = domainRecord.note
                )
            }
        )
    }

    /**
     * 为了做到“恢复失败时当前数据不被修改”，这里先保留旧快照并在设置写入失败时执行补偿恢复。
     */
    private suspend fun restoreDocument(document: BackupDocument) {
        val previousSettings = appSettingsRepository.observeSettings().first()
        val previousDecks = deckDao.listAll()
        val previousCards = cardDao.listAll()
        val previousQuestions = questionDao.listAll()
        val previousReviewRecords = reviewRecordDao.listAll()

        try {
            database.withTransaction {
                reviewRecordDao.clearAll()
                questionDao.clearAll()
                cardDao.clearAll()
                deckDao.clearAll()

                deckDao.upsertAll(document.decks.map { deck ->
                    com.kariscode.yike.data.local.db.entity.DeckEntity(
                        id = deck.id,
                        name = deck.name,
                        description = deck.description,
                        archived = deck.archived,
                        sortOrder = deck.sortOrder,
                        createdAt = BackupJson.parseEpochMillis(deck.createdAt),
                        updatedAt = BackupJson.parseEpochMillis(deck.updatedAt)
                    )
                })
                cardDao.upsertAll(document.cards.map { card ->
                    com.kariscode.yike.data.local.db.entity.CardEntity(
                        id = card.id,
                        deckId = card.deckId,
                        title = card.title,
                        description = card.description,
                        archived = card.archived,
                        sortOrder = card.sortOrder,
                        createdAt = BackupJson.parseEpochMillis(card.createdAt),
                        updatedAt = BackupJson.parseEpochMillis(card.updatedAt)
                    )
                })
                questionDao.upsertAll(document.questions.map { question ->
                    RoomMappers.run {
                        com.kariscode.yike.domain.model.Question(
                            id = question.id,
                            cardId = question.cardId,
                            prompt = question.prompt,
                            answer = question.answer,
                            tags = question.tags,
                            status = if (question.status == QuestionEntity.STATUS_ARCHIVED) {
                                QuestionStatus.ARCHIVED
                            } else {
                                QuestionStatus.ACTIVE
                            },
                            stageIndex = question.stageIndex,
                            dueAt = BackupJson.parseEpochMillis(question.dueAt),
                            lastReviewedAt = question.lastReviewedAt?.let(BackupJson::parseEpochMillis),
                            reviewCount = question.reviewCount,
                            lapseCount = question.lapseCount,
                            createdAt = BackupJson.parseEpochMillis(question.createdAt),
                            updatedAt = BackupJson.parseEpochMillis(question.updatedAt)
                        ).toEntity()
                    }
                })
                reviewRecordDao.insertAll(document.reviewRecords.map { record ->
                    RoomMappers.run {
                        com.kariscode.yike.domain.model.ReviewRecord(
                            id = record.id,
                            questionId = record.questionId,
                            rating = enumValueOf(record.rating),
                            oldStageIndex = record.oldStageIndex,
                            newStageIndex = record.newStageIndex,
                            oldDueAt = BackupJson.parseEpochMillis(record.oldDueAt),
                            newDueAt = BackupJson.parseEpochMillis(record.newDueAt),
                            reviewedAt = BackupJson.parseEpochMillis(record.reviewedAt),
                            responseTimeMs = record.responseTimeMs,
                            note = record.note
                        ).toEntity()
                    }
                })
            }

            writeSettingsFromBackup(document.settings)
        } catch (throwable: Throwable) {
            database.withTransaction {
                reviewRecordDao.clearAll()
                questionDao.clearAll()
                cardDao.clearAll()
                deckDao.clearAll()

                deckDao.upsertAll(previousDecks)
                cardDao.upsertAll(previousCards)
                questionDao.upsertAll(previousQuestions)
                reviewRecordDao.insertAll(previousReviewRecords)
            }
            restorePreviousSettings(previousSettings)
            throw IllegalStateException("恢复失败，当前数据未被修改", throwable)
        }
    }

    /**
     * 设置写入单独封装，是为了让恢复成功后的提醒配置和版本信息与备份内容保持一致。
     */
    private suspend fun writeSettingsFromBackup(settings: BackupSettings) {
        val parts = settings.dailyReminderTime.split(":")
        appSettingsRepository.setDailyReminderEnabled(settings.dailyReminderEnabled)
        appSettingsRepository.setDailyReminderTime(parts[0].toInt(), parts[1].toInt())
        appSettingsRepository.setSchemaVersion(settings.schemaVersion)
        appSettingsRepository.setBackupLastAt(settings.backupLastAt?.let(BackupJson::parseEpochMillis))
    }

    /**
     * 发生补偿回滚时要把旧设置一并恢复，
     * 否则即使数据库回滚成功，提醒配置仍可能与当前数据脱节。
     */
    private suspend fun restorePreviousSettings(settings: AppSettings) {
        appSettingsRepository.setDailyReminderEnabled(settings.dailyReminderEnabled)
        appSettingsRepository.setDailyReminderTime(settings.dailyReminderHour, settings.dailyReminderMinute)
        appSettingsRepository.setSchemaVersion(settings.schemaVersion)
        appSettingsRepository.setBackupLastAt(settings.backupLastAt)
    }

    /**
     * 统一把设置映射为固定 `HH:mm` 文本，是为了让备份文件结构稳定且便于人工阅读。
     */
    private fun AppSettings.toBackupReminderTime(): String =
        "%02d:%02d".format(dailyReminderHour, dailyReminderMinute)
}
