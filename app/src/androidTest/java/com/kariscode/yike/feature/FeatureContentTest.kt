package com.kariscode.yike.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.feature.backup.BackupRestoreContent
import com.kariscode.yike.feature.backup.BackupRestoreUiState
import com.kariscode.yike.feature.home.HomeContent
import com.kariscode.yike.feature.home.HomeContentMode
import com.kariscode.yike.feature.home.HomeUiState
import com.kariscode.yike.feature.review.ReviewCardContent
import com.kariscode.yike.feature.review.ReviewCardUiState
import com.kariscode.yike.feature.review.ReviewQuestionUiModel
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.theme.YikeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 关键页面内容测试优先验证加载、空、错、完成和高风险提示，
 * 这样在样式继续演进时也能守住主路径的可理解反馈。
 */
class FeatureContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 首页内容测试只关心文案和入口是否出现，因此导航使用空实现即可避免把断言绑到真实路由。
     */
    private val navigator = object : YikeNavigator {
        override fun back() = Unit
        override fun openPrimary(route: String) = Unit
        override fun backToHome() = Unit
        override fun openDeckList() = Unit
        override fun openSettings() = Unit
        override fun openCardList(deckId: String) = Unit
        override fun openReviewQueue() = Unit
        override fun openReviewCard(cardId: String) = Unit
        override fun openPracticeSetup(args: PracticeSessionArgs) = Unit
        override fun openPracticeSession(args: PracticeSessionArgs) = Unit
        override fun openTodayPreview() = Unit
        override fun openAnalytics() = Unit
        override fun openQuestionSearch(deckId: String?, cardId: String?) = Unit
        override fun openQuestionEditor(cardId: String, deckId: String?) = Unit
        override fun openBackupRestore() = Unit
        override fun openLanSync() = Unit
        override fun openRecycleBin() = Unit
        override fun openDebug() = Unit
    }

    /**
     * 首页加载时必须告诉用户系统正在准备什么，
     * 否则新的原型壳层会退化成“看起来空白”的误导体验。
     */
    @Test
    fun homeContent_loadingStateShowsPreparationMessage() {
        composeRule.setContent {
            YikeTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = true,
                        summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                        recentDecks = emptyList(),
                        contentMode = HomeContentMode.CONTENT_EMPTY,
                        errorMessage = null
                    ),
                    onRetry = {},
                    navigator = navigator
                )
            }
        }

        composeRule.onNodeWithText("正在整理今天的复习内容").assertIsDisplayed()
    }

    /**
     * 首页空状态必须给出创建内容的下一步，
     * 否则用户会停留在“今天没题可复习，但也不知道该做什么”的死路口。
     */
    @Test
    fun homeContent_emptyStateShowsCreateGuidance() {
        composeRule.setContent {
            YikeTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = false,
                        summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                        recentDecks = emptyList(),
                        contentMode = HomeContentMode.CONTENT_EMPTY,
                        errorMessage = null
                    ),
                    onRetry = {},
                    navigator = navigator
                )
            }
        }

        composeRule.onNodeWithText("先创建第一组学习内容").assertIsDisplayed()
        composeRule.onNodeWithText("创建内容").assertIsDisplayed()
    }

    /**
     * 首页错误态必须明确说明失败，而不是把用户留在看似正常但不可操作的页面里。
     */
    @Test
    fun homeContent_errorStateShowsRetryMessage() {
        composeRule.setContent {
            YikeTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = false,
                        summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                        recentDecks = emptyList(),
                        contentMode = HomeContentMode.CONTENT_EMPTY,
                        errorMessage = "数据库读取失败"
                    ),
                    onRetry = {},
                    navigator = navigator
                )
            }
        }

        composeRule.onNodeWithText("首页加载失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    /**
     * debug 构建首页必须显式暴露调试入口，
     * 由于首页内容会在真机上滚动，断言存在即可守住“入口已接入调试构建”的要求，
     * 而不把测试和当前视口高度强绑定。
     */
    @Test
    fun homeContent_debugBuildShowsDebugEntry() {
        composeRule.setContent {
            YikeTheme {
                HomeContent(
                    uiState = HomeUiState(
                        isLoading = false,
                        summary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0),
                        recentDecks = emptyList(),
                        contentMode = HomeContentMode.CONTENT_EMPTY,
                        errorMessage = null
                    ),
                    onRetry = {},
                    navigator = navigator
                )
            }
        }

        composeRule.onNodeWithText("调试工具").fetchSemanticsNode()
    }

    /**
     * 复习页在显示答案后必须开放评分动作，
     * 这是逐题流程能否闭环的关键交互点。
     */
    @Test
    fun reviewCardContent_answerVisibleShowsRatingButtons() {
        composeRule.setContent {
            YikeTheme {
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
                            stageIndex = 0,
                            overdueBadgeText = null,
                            overdueHintText = null,
                            needsReinforcement = false
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
        }

        composeRule.onNodeWithText("一个离线复习应用").assertIsDisplayed()
        composeRule.onNodeWithText("完全不会").assertIsDisplayed()
        composeRule.onNodeWithText("很轻松").assertIsDisplayed()
    }

    /**
     * 本卡完成态必须给出继续下一张和返回首页的双出口，
     * 否则用户完成一张卡后会不知道下一步应该去哪里。
     */
    @Test
    fun reviewCardContent_completedStateShowsExitOptions() {
        composeRule.setContent {
            YikeTheme {
                ReviewCardContent(
                    uiState = ReviewCardUiState(
                        cardId = "card_1",
                        cardTitle = "测试卡片",
                        isLoading = false,
                        totalCount = 2,
                        completedCount = 2,
                        currentQuestion = null,
                        answerVisible = false,
                        isSubmitting = false,
                        isCompleted = true,
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
        }

        composeRule.onNodeWithText("本卡完成").assertIsDisplayed()
        composeRule.onNodeWithText("继续下一张").assertIsDisplayed()
    }

    /**
     * 备份页必须常驻展示恢复风险提示，
     * 这样用户在进入页面时就能先建立对高风险操作的预期。
     */
    @Test
    fun backupRestoreContent_showsRiskWarning() {
        composeRule.setContent {
            YikeTheme {
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
        }

        composeRule.onNodeWithText("恢复会覆盖当前本地全部数据").assertIsDisplayed()
        composeRule.onNodeWithText("选择备份文件").assertIsDisplayed()
    }
}
