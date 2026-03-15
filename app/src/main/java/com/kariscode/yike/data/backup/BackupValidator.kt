package com.kariscode.yike.data.backup

import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1

/**
 * 备份校验器把版本、字段和引用关系的检查集中起来，
 * 是为了在真正覆盖本地数据前尽早拦截无效文件，守住恢复安全边界。
 */
class BackupValidator {
    /**
     * 任何不合法输入都返回用户可理解的统一错误，
     * 这样页面层不需要理解底层校验细节也能给出稳定反馈。
     */
    fun validate(document: BackupDocument): Result<Unit> = runCatching {
        require(document.app.backupVersion == BackupConstants.BACKUP_VERSION) {
            "备份文件无效或版本不兼容"
        }
        BackupJson.parseEpochMillis(document.app.exportedAt)
        parseReminderTime(document.settings.dailyReminderTime)
        document.settings.backupLastAt?.let(BackupJson::parseEpochMillis)

        val deckIds = document.decks.map { deck ->
            require(deck.id.isNotBlank() && deck.name.isNotBlank()) { "备份文件无效或版本不兼容" }
            BackupJson.parseEpochMillis(deck.createdAt)
            BackupJson.parseEpochMillis(deck.updatedAt)
            deck.id
        }.toSet()

        val cardIds = document.cards.map { card ->
            require(card.id.isNotBlank() && card.title.isNotBlank() && card.deckId in deckIds) {
                "备份文件无效或版本不兼容"
            }
            BackupJson.parseEpochMillis(card.createdAt)
            BackupJson.parseEpochMillis(card.updatedAt)
            card.id
        }.toSet()

        val questionIds = document.questions.map { question ->
            require(question.id.isNotBlank() && question.prompt.isNotBlank() && question.cardId in cardIds) {
                "备份文件无效或版本不兼容"
            }
            require(question.status == "active" || question.status == "archived") {
                "备份文件无效或版本不兼容"
            }
            require(question.stageIndex in 0..ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex) {
                "备份文件无效或版本不兼容"
            }
            BackupJson.parseEpochMillis(question.dueAt)
            question.lastReviewedAt?.let(BackupJson::parseEpochMillis)
            BackupJson.parseEpochMillis(question.createdAt)
            BackupJson.parseEpochMillis(question.updatedAt)
            question.id
        }.toSet()

        document.reviewRecords.forEach { record ->
            require(record.questionId in questionIds) { "备份文件无效或版本不兼容" }
            require(record.rating in setOf("AGAIN", "HARD", "GOOD", "EASY")) {
                "备份文件无效或版本不兼容"
            }
            require(record.oldStageIndex in 0..ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex) {
                "备份文件无效或版本不兼容"
            }
            require(record.newStageIndex in 0..ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex) {
                "备份文件无效或版本不兼容"
            }
            BackupJson.parseEpochMillis(record.oldDueAt)
            BackupJson.parseEpochMillis(record.newDueAt)
            BackupJson.parseEpochMillis(record.reviewedAt)
        }
    }.mapErrorToUserMessage()

    /**
     * 提醒时间在恢复后会立即参与调度，因此必须在校验阶段就保证格式正确。
     */
    private fun parseReminderTime(value: String) {
        val parts = value.split(":")
        require(parts.size == 2) { "备份文件无效或版本不兼容" }
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59) { "备份文件无效或版本不兼容" }
    }

    /**
     * 对外统一收敛错误文案，是为了让页面层稳定展示“文件无效/版本不兼容”，
     * 而不把底层字段细节直接泄露给用户。
     */
    private fun Result<Unit>.mapErrorToUserMessage(): Result<Unit> = fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { Result.failure(IllegalArgumentException("备份文件无效或版本不兼容")) }
    )
}
