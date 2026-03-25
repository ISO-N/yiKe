package com.kariscode.yike.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel

/**
 * 统计页独立存在，是为了遵守首页不承担复杂统计的边界，并让用户在需要时再展开详细观察。
 */
@Composable
fun AnalyticsScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<AnalyticsViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "复习统计",
        subtitle = "用连续学习、评分分布和遗忘率判断当前学习节奏是否健康。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        AnalyticsContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onRangeSelected = viewModel::onRangeSelected,
            onOpenPreview = navigator::openTodayPreview,
            onOpenSearch = { navigator.openQuestionSearch() },
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 统计页主体统一处理加载、错误和成功态，是为了让时间范围切换后依然保持稳定反馈。
 */
@Composable
private fun AnalyticsContent(
    uiState: AnalyticsUiState,
    onRetry: () -> Unit,
    onRangeSelected: (AnalyticsRange) -> Unit,
    onOpenPreview: () -> Unit,
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
                    title = "正在整理统计数据",
                    description = "稍等一下，我们会把评分分布、遗忘率和平均响应时间一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = ErrorMessages.ANALYTICS_LOAD_FAILED,
                    description = uiState.errorMessage
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "看今日预览",
                            onClick = onOpenPreview,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            else -> {
                AnalyticsHeroSection(uiState = uiState, onRangeSelected = onRangeSelected)
                AnalyticsMetricSection(uiState = uiState)
                AnalyticsDistributionSection(uiState = uiState)
                AnalyticsDeckSection(deckBreakdowns = uiState.deckBreakdowns)
                AnalyticsConclusionSection(
                    conclusion = uiState.conclusion,
                    onOpenPreview = onOpenPreview,
                    onOpenSearch = onOpenSearch
                )
            }
        }
    }
}

