package com.kariscode.yike.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kariscode.yike.feature.backup.BackupRestoreContent
import com.kariscode.yike.feature.backup.BackupRestoreUiState
import com.kariscode.yike.feature.home.HomeContent
import com.kariscode.yike.feature.home.HomeUiState
import com.kariscode.yike.feature.review.ReviewCardContent
import com.kariscode.yike.feature.review.ReviewCardUiState
import com.kariscode.yike.feature.review.ReviewQuestionUiModel
import com.kariscode.yike.domain.model.TodayReviewSummary
import org.junit.Rule
import org.junit.Test

/**
 * 关键页面内容测试优先验证空状态、流程状态与高风险文案，
 * 这样在后续 UI 调整时能尽早发现主路径被破坏。
 */
class FeatureContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 首页在没有待复习内容时必须给出明确下一步，
     * 否则用户会停留在“没有数据但不知道该做什么”的死路口。
     */
    @Test
    fun homeContent_emptyStateShowsCreateGuidance() {
        composeRule.setContent {
            HomeContent(
                uiState = HomeUiState(
                    isLoading = false,
                    summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                    errorMessage = null
                ),
                onRetry = {},
                onStartReview = {},
                onOpenDeckList = {},
                onOpenSettings = {},
                onOpenDebug = {}
            )
        }

        composeRule.onNodeWithText("今日暂无待复习").assertIsDisplayed()
        composeRule.onNodeWithText("进入卡组").assertIsDisplayed()
    }

    /**
     * debug 构建首页必须显式暴露调试入口，
     * 否则这次新增的造数能力虽然存在，但开发者仍然无法从主路径触达。
     */
    @Test
    fun homeContent_debugBuildShowsDebugEntry() {
        composeRule.setContent {
            HomeContent(
                uiState = HomeUiState(
                    isLoading = false,
                    summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                    errorMessage = null
                ),
                onRetry = {},
                onStartReview = {},
                onOpenDeckList = {},
                onOpenSettings = {},
                onOpenDebug = {}
            )
        }

        composeRule.onNodeWithText("调试工具").assertIsDisplayed()
    }

    /**
     * 复习页在显示答案后必须开放评分动作，
     * 这是整个逐题流程能否闭环的核心交互点。
     */
    @Test
    fun reviewCardContent_answerVisibleShowsRatingButtons() {
        composeRule.setContent {
            ReviewCardContent(
                uiState = ReviewCardUiState(
                    cardId = "card_1",
                    cardTitle = "测试卡片",
                    isLoading = false,
                    totalCount = 2,
                    completedCount = 0,
                    currentQuestion = ReviewQuestionUiModel(
                        questionId = "question_1",
                        prompt = "什么是忆刻？",
                        answerText = "一个离线复习应用",
                        stageIndex = 0
                    ),
                    answerVisible = true,
                    isSubmitting = false,
                    isCompleted = false,
                    errorMessage = null,
                    exitConfirmationVisible = false
                ),
                onRevealAnswer = {},
                onRate = {},
                onRetryLoad = {},
                onContinueNextCard = {},
                onBackHome = {}
            )
        }

        composeRule.onNodeWithText("答案：一个离线复习应用").assertIsDisplayed()
        composeRule.onNodeWithText("重来").assertIsDisplayed()
        composeRule.onNodeWithText("简单").assertIsDisplayed()
    }

    /**
     * 备份页必须常驻展示恢复风险提示，
     * 这样用户在进入页面时就能先建立对高风险操作的预期。
     */
    @Test
    fun backupRestoreContent_showsRiskWarning() {
        composeRule.setContent {
            BackupRestoreContent(
                uiState = BackupRestoreUiState(
                    isExporting = false,
                    isImporting = false,
                    lastBackupAt = null,
                    warningMessage = "从备份恢复会覆盖当前本地全部数据，请先确认是否已完成备份。",
                    message = null,
                    errorMessage = null,
                    pendingRestoreUri = null
                ),
                onExport = {},
                onImport = {}
            )
        }

        composeRule.onNodeWithText("从备份恢复会覆盖当前本地全部数据，请先确认是否已完成备份。").assertIsDisplayed()
        composeRule.onNodeWithText("从备份恢复").assertIsDisplayed()
    }
}
