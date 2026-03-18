package com.kariscode.yike.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 复习队列页只负责决定下一张卡片，因此页面重点应放在当前状态说明，而不是展示无关操作。
 */
@Composable
fun ReviewQueueScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<ReviewQueueViewModel>(
        factory = ReviewQueueViewModel.factory(
            questionRepository = container.questionRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            is ReviewQueueEffect.NavigateToCard -> navigator.openReviewCard(effect.cardId)
            ReviewQueueEffect.BackToHomeCompleted -> navigator.backToHome()
        }
    }

    YikeFlowScaffold(
        title = "准备开始复习",
        subtitle = "我们会先为你选择今天最该处理的那张卡片。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        ReviewQueueContent(
            uiState = uiState,
            onRetry = viewModel::loadNext,
            onBackToHome = navigator::backToHome,
            modifier = modifier.padding(padding)
        )
    }
}

/**
 * 队列页主体只表达加载、失败和回退动作，是为了让用户理解“当前还在选卡”而不是卡住了。
 */
@Composable
private fun ReviewQueueContent(
    uiState: ReviewQueueUiState,
    onRetry: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在选择下一张卡片",
                    description = "系统会优先挑选最早到期的内容，尽量缩短你的决策时间。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = "暂时没能进入复习",
                    description = uiState.errorMessage
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重新选择",
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth()
                        )
                        YikeSecondaryButton(
                            text = "返回首页",
                            onClick = onBackToHome,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                YikeStateBanner(
                    title = "即将跳转到复习页",
                    description = "如果没有待复习内容，系统会自动带你回到首页。"
                ) {
                    YikeSecondaryButton(
                        text = "返回首页",
                        onClick = onBackToHome,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
