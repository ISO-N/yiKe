package com.kariscode.yike.data.backup

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份校验测试用于守住恢复前最后一道防线，
 * 防止非法文件穿透到全量覆盖流程污染本地数据。
 */
class BackupValidatorTest {
    private val validator = BackupValidator()

    /**
     * 合法结构必须通过校验，
     * 否则用户导出的正常文件会在恢复时被误判为无效。
     */
    @Test
    fun validate_validDocument_returnsSuccess() {
        val result = validator.validate(createValidDocument())

        assertTrue(result.isSuccess)
    }

    /**
     * 版本不兼容时必须提前失败，
     * 这样恢复流程才能在写库前阻止不可预测的兼容问题。
     */
    @Test
    fun validate_invalidBackupVersion_returnsFailure() {
        val result = validator.validate(
            createValidDocument().copy(
                app = createValidDocument().app.copy(backupVersion = 99)
            )
        )

        assertTrue(result.isFailure)
    }

    /**
     * 引用关系损坏的文件必须被拒绝，
     * 否则恢复后会留下失效外键和不可解释的数据缺口。
     */
    @Test
    fun validate_missingCardReference_returnsFailure() {
        val result = validator.validate(
            createValidDocument().copy(
                questions = listOf(
                    createValidDocument().questions.first().copy(cardId = "missing_card")
                )
            )
        )

        assertTrue(result.isFailure)
    }

    /**
     * 非法提醒时间必须在写库前被拒绝，
     * 否则恢复后调度逻辑会接收到不合法的小时分钟组合。
     */
    @Test
    fun validate_invalidReminderTime_returnsFailure() {
        val result = validator.validate(
            createValidDocument().copy(
                settings = createValidDocument().settings.copy(dailyReminderTime = "25:99")
            )
        )

        assertTrue(result.isFailure)
    }

    /**
     * 非法主题模式必须在恢复前失败，
     * 否则损坏备份会把设置仓储带入未知显示状态。
     */
    @Test
    fun validate_invalidThemeMode_returnsFailure() {
        val result = validator.validate(
            createValidDocument().copy(
                settings = createValidDocument().settings.copy(themeMode = "amoled")
            )
        )

        assertTrue(result.isFailure)
    }

    /**
     * 用固定的最小合法样本构造文档，能让测试聚焦在校验规则而不是样板数据准备。
     */
    private fun createValidDocument(): BackupDocument = BackupDocument(
        app = BackupAppInfo(
            name = "忆刻",
            backupVersion = BackupConstants.BACKUP_VERSION,
            exportedAt = "2026-03-15T20:00:00+08:00"
        ),
        settings = BackupSettings(
            dailyReminderEnabled = true,
            dailyReminderTime = "20:30",
            schemaVersion = 1,
            backupLastAt = null,
            themeMode = "system"
        ),
        decks = listOf(
            BackupDeck(
                id = "deck_1",
                name = "测试卡组",
                description = "",
                tags = listOf("数学", "微积分"),
                intervalStepCount = 4,
                archived = false,
                sortOrder = 0,
                createdAt = "2026-03-15T10:00:00+08:00",
                updatedAt = "2026-03-15T10:00:00+08:00"
            )
        ),
        cards = listOf(
            BackupCard(
                id = "card_1",
                deckId = "deck_1",
                title = "测试卡片",
                description = "",
                archived = false,
                sortOrder = 0,
                createdAt = "2026-03-15T10:10:00+08:00",
                updatedAt = "2026-03-15T10:10:00+08:00"
            )
        ),
        questions = listOf(
            BackupQuestion(
                id = "question_1",
                cardId = "card_1",
                prompt = "什么是忆刻？",
                answer = "一个离线复习应用",
                tags = listOf("定义"),
                status = "active",
                stageIndex = 0,
                dueAt = "2026-03-16T20:30:00+08:00",
                lastReviewedAt = null,
                reviewCount = 0,
                lapseCount = 0,
                createdAt = "2026-03-15T10:20:00+08:00",
                updatedAt = "2026-03-15T10:20:00+08:00"
            )
        ),
        reviewRecords = emptyList()
    )
}
