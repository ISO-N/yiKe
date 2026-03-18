package com.kariscode.yike.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 今日预览页独立存在，是为了让用户在开始复习前先建立任务预期，而不是进入队列后再被动接受负荷。
 */
@Composable
fun TodayPreviewScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<TodayPreviewViewModel>(
        factory = TodayPreviewViewModel.factory(
            studyInsightsRepository = container.studyInsightsRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "今日复习预览",
        subtitle = "开始前先看清今天要复习的规模和重点，能减少进入任务后的挫败感。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        TodayPreviewContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onStartReview = navigator::openReviewQueue,
            onOpenAnalytics = navigator::openAnalytics,
            onOpenSearch = { navigator.openQuestionSearch() },
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 预览内容集中处理加载、错误和成功态，是为了让首页跳转进来时始终能收到明确反馈。
 */
@Composable
private fun TodayPreviewContent(
    uiState: TodayPreviewUiState,
    onRetry: () -> Unit,
    onStartReview: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSearch: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在整理今日任务",
                    description = "稍等一下，我们会把到期问题、预计时长和重点卡组一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = ErrorMessages.PREVIEW_LOAD_FAILED,
                    description = uiState.errorMessage
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "先去检索",
                            onClick = onOpenSearch,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            uiState.totalDueQuestions == 0 -> {
                YikeStateBanner(
                    title = "今天暂无待复习内容",
                    description = "当前没有已到期的问题，可以先去整理题库或看看统计页回顾近期节奏。"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "查看统计",
                            onClick = onOpenAnalytics,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "检索题库",
                            onClick = onOpenSearch,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            else -> {
                TodayPreviewHeroSection(
                    uiState = uiState,
                    onStartReview = onStartReview,
                    onOpenAnalytics = onOpenAnalytics
                )
                TodayPreviewSummarySection(uiState = uiState, onOpenSearch = onOpenSearch)
                TodayPreviewDeckSection(deckGroups = uiState.deckGroups)
            }
        }
    }
}
