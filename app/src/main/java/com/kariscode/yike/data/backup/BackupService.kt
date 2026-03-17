package com.kariscode.yike.data.backup

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.coroutine.parallel3
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.time.TimeTextFormatter
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.mapper.RoomMappers
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewRecord
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import java.io.FileNotFoundException
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
        val jsonString = exportToJsonString(exportedAtEpochMillis = exportedAt)
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
        restoreFromJsonString(jsonString)
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
     * 局域网同步与导出文件都需要同一份 JSON 快照，因此单独暴露字符串导出入口可以避免重复序列化骨架。
     */
    suspend fun exportToJsonString(
        exportedAtEpochMillis: Long = timeProvider.nowEpochMillis()
    ): String = withContext(dispatchers.io) {
        val document = exportDocument(exportedAtEpochMillis = exportedAtEpochMillis)
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
        exportedAtEpochMillis: Long = timeProvider.nowEpochMillis()
    ): BackupDocument = withContext(dispatchers.io) {
        buildBackupDocument(exportedAtEpochMillis = exportedAtEpochMillis)
    }

    /**
     * 构建备份文档时读取完整数据快照，可确保序列化结果与当下本地状态严格一致。
     */
    private suspend fun buildBackupDocument(exportedAtEpochMillis: Long): BackupDocument {
        val snapshot = readCurrentSnapshot()

        return BackupDocument(
            app = BackupAppInfo(
                name = "忆刻",
                backupVersion = BackupConstants.BACKUP_VERSION,
                exportedAt = BackupJson.formatEpochMillis(exportedAtEpochMillis)
            ),
            settings = BackupSettings(
                dailyReminderEnabled = snapshot.settings.dailyReminderEnabled,
                dailyReminderTime = snapshot.settings.toBackupReminderTime(),
                schemaVersion = snapshot.settings.schemaVersion,
                backupLastAt = snapshot.settings.backupLastAt?.let(BackupJson::formatEpochMillis),
                themeMode = snapshot.settings.themeMode.storageValue
            ),
            decks = snapshot.decks.map { deck -> deck.toBackup() },
            cards = snapshot.cards.map { card -> card.toBackup() },
            questions = snapshot.questions.map { question -> question.toBackup() },
            reviewRecords = snapshot.reviewRecords.map { reviewRecord -> reviewRecord.toBackup() }
        )
    }

    /**
     * 为了做到“恢复失败时当前数据不被修改”，这里先保留旧快照并在设置写入失败时执行补偿恢复。
     */
    private suspend fun restoreDocument(document: BackupDocument) {
        val previousSnapshot = readCurrentSnapshot()
        val restoredEntities = document.toRestorePayload()

        try {
            replaceDatabaseContent(restoredEntities)
            writeSettingsFromBackup(document.settings)
        } catch (throwable: Throwable) {
            replaceDatabaseContent(previousSnapshot.toRestorePayload())
            restorePreviousSettings(previousSnapshot.settings)
            throw IllegalStateException("恢复失败，当前数据未被修改", throwable)
        }
    }

    /**
     * 导出、恢复前快照和失败回滚都依赖同一时刻的数据视图，
     * 因此统一并行读取可以同时收敛代码路径和等待成本。
     */
    private suspend fun readCurrentSnapshot(): BackupSnapshot {
        val (settings, deckAndCard, questionAndReview) = parallel3(
            first = { appSettingsRepository.getSettings() },
            second = {
                parallel(
                    first = { deckDao.listAll() },
                    second = { cardDao.listAll() }
                )
            },
            third = {
                parallel(
                    first = { questionDao.listAll() },
                    second = { reviewRecordDao.listAll() }
                )
            }
        )
        val (decks, cards) = deckAndCard
        val (questions, reviewRecords) = questionAndReview
        return BackupSnapshot(
            settings = settings,
            decks = decks,
            cards = cards,
            questions = questions,
            reviewRecords = reviewRecords
        )
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
                themeMode = ThemeMode.fromStorageValue(settings.themeMode)
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
     * 统一把设置映射为固定 `HH:mm` 文本，是为了让备份文件结构稳定且便于人工阅读。
     */
    private fun AppSettings.toBackupReminderTime(): String =
        TimeTextFormatter.formatHourMinute(
            hour = dailyReminderHour,
            minute = dailyReminderMinute
        )

    /**
     * Deck 到备份模型的映射收敛到单点后，字段调整时就不必同时追踪导出主流程里的长内联表达式。
     */
    private fun DeckEntity.toBackup(): BackupDeck = BackupDeck(
        id = id,
        name = name,
        description = description,
        tags = RoomMappers.decodeQuestionTags(tagsJson),
        intervalStepCount = intervalStepCount,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = BackupJson.formatEpochMillis(createdAt),
        updatedAt = BackupJson.formatEpochMillis(updatedAt)
    )

    /**
     * Card 的备份映射独立出来，是为了让层级字段与时间字段的导出规则保持易读且可复用。
     */
    private fun CardEntity.toBackup(): BackupCard = BackupCard(
        id = id,
        deckId = deckId,
        title = title,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = BackupJson.formatEpochMillis(createdAt),
        updatedAt = BackupJson.formatEpochMillis(updatedAt)
    )

    /**
     * Question 映射集中在单点后，可以把状态与时间字段的转换规则固定住，
     * 避免导出路径未来增删字段时遗漏调度相关数据。
     */
    private fun QuestionEntity.toBackup(): BackupQuestion {
        val domainQuestion = RoomMappers.run { toDomain() }
        return domainQuestion.toBackup()
    }

    /**
     * ReviewRecord 的备份映射抽出来后，导出主流程只保留“读哪些表”的骨架，更容易检查事务语义。
     */
    private fun ReviewRecordEntity.toBackup(): BackupReviewRecord {
        val domainRecord = RoomMappers.run { toDomain() }
        return domainRecord.toBackup()
    }

    /**
     * 领域问题转换成备份模型，是为了让状态枚举与字符串字段的边界集中在一处维护。
     */
    private fun Question.toBackup(): BackupQuestion = BackupQuestion(
        id = id,
        cardId = cardId,
        prompt = prompt,
        answer = answer,
        tags = tags,
        status = status.storageValue,
        stageIndex = stageIndex,
        dueAt = BackupJson.formatEpochMillis(dueAt),
        lastReviewedAt = lastReviewedAt?.let(BackupJson::formatEpochMillis),
        reviewCount = reviewCount,
        lapseCount = lapseCount,
        createdAt = BackupJson.formatEpochMillis(createdAt),
        updatedAt = BackupJson.formatEpochMillis(updatedAt)
    )

    /**
     * 领域复习记录转换为备份模型时保留原始评分链路，
     * 这样恢复后仍能重建用户真实的复习历史。
     */
    private fun ReviewRecord.toBackup(): BackupReviewRecord = BackupReviewRecord(
        id = id,
        questionId = questionId,
        rating = rating.name,
        oldStageIndex = oldStageIndex,
        newStageIndex = newStageIndex,
        oldDueAt = BackupJson.formatEpochMillis(oldDueAt),
        newDueAt = BackupJson.formatEpochMillis(newDueAt),
        reviewedAt = BackupJson.formatEpochMillis(reviewedAt),
        responseTimeMs = responseTimeMs,
        note = note
    )

    /**
     * 备份模型恢复成 DeckEntity 的规则集中后，导入主流程就能更清楚地表达层级恢复顺序。
     */
    private fun BackupDeck.toEntity(): DeckEntity = DeckEntity(
        id = id,
        name = name,
        description = description,
        tagsJson = BackupJson.json.encodeToString(tags),
        intervalStepCount = ReviewSchedulerV1.normalizeIntervalStepCount(intervalStepCount),
        archived = archived,
        sortOrder = sortOrder,
        createdAt = BackupJson.parseEpochMillis(createdAt),
        updatedAt = BackupJson.parseEpochMillis(updatedAt)
    )

    /**
     * Card 备份恢复映射抽成扩展，是为了把时间解析与层级字段恢复放在一起维护。
     */
    private fun BackupCard.toEntity(): CardEntity = CardEntity(
        id = id,
        deckId = deckId,
        title = title,
        description = description,
        archived = archived,
        sortOrder = sortOrder,
        createdAt = BackupJson.parseEpochMillis(createdAt),
        updatedAt = BackupJson.parseEpochMillis(updatedAt)
    )

    /**
     * Question 恢复映射集中到单点后，状态字符串与领域模型之间的边界就不会散落在事务主流程里。
     */
    private fun BackupQuestion.toEntity(): QuestionEntity = RoomMappers.run {
        Question(
            id = this@toEntity.id,
            cardId = this@toEntity.cardId,
            prompt = this@toEntity.prompt,
            answer = this@toEntity.answer,
            tags = this@toEntity.tags,
            status = QuestionStatus.fromStorageValue(this@toEntity.status),
            stageIndex = this@toEntity.stageIndex,
            dueAt = BackupJson.parseEpochMillis(this@toEntity.dueAt),
            lastReviewedAt = this@toEntity.lastReviewedAt?.let(BackupJson::parseEpochMillis),
            reviewCount = this@toEntity.reviewCount,
            lapseCount = this@toEntity.lapseCount,
            createdAt = BackupJson.parseEpochMillis(this@toEntity.createdAt),
            updatedAt = BackupJson.parseEpochMillis(this@toEntity.updatedAt)
        ).toEntity()
    }

    /**
     * ReviewRecord 恢复映射独立出来，是为了让枚举解析与时间解析的风险点更容易被单独检查。
     */
    private fun BackupReviewRecord.toEntity(): ReviewRecordEntity = RoomMappers.run {
        ReviewRecord(
            id = this@toEntity.id,
            questionId = this@toEntity.questionId,
            rating = enumValueOf(this@toEntity.rating),
            oldStageIndex = this@toEntity.oldStageIndex,
            newStageIndex = this@toEntity.newStageIndex,
            oldDueAt = BackupJson.parseEpochMillis(this@toEntity.oldDueAt),
            newDueAt = BackupJson.parseEpochMillis(this@toEntity.newDueAt),
            reviewedAt = BackupJson.parseEpochMillis(this@toEntity.reviewedAt),
            responseTimeMs = this@toEntity.responseTimeMs,
            note = this@toEntity.note
        ).toEntity()
    }

    /**
     * 备份文档先一次性转成实体载荷，是为了让校验通过后的恢复事务只专注处理数据库替换。
     */
    private fun BackupDocument.toRestorePayload(): RestorePayload = RestorePayload(
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
