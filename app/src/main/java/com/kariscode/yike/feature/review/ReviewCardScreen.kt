package com.kariscode.yike.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 复习页将来会承载“问题 -> 显示答案 -> 评分 -> 下一题”的流程状态机；
 * 当前实现直接把状态机落进 ViewModel，是为了保证逐题流程、退出确认和失败重试在同一状态来源下运行。
 */
@Composable
fun ReviewCardScreen(
    cardId: String,
    onExit: () -> Unit,
    onNextCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<ReviewCardViewModel>(
        factory = ReviewCardViewModel.factory(
            cardId = cardId,
            cardRepository = container.cardRepository,
            reviewRepository = container.reviewRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(onBack = viewModel::onExitAttempt)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReviewCardEffect.NavigateHome -> onExit()
                ReviewCardEffect.NavigateToQueue -> onNextCard()
            }
        }
    }

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "复习",
                navigationAction = NavigationAction(label = "退出", onClick = viewModel::onExitAttempt)
            )
        },
        modifier = modifier
    ) { padding ->
        ReviewCardContent(
            uiState = uiState,
            onRevealAnswer = viewModel::onRevealAnswerClick,
            onRate = viewModel::onRateClick,
            onRetryLoad = viewModel::loadSession,
            onContinueNextCard = viewModel::onContinueNextCardClick,
            onBackHome = viewModel::onBackHomeClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    if (uiState.exitConfirmationVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissExitConfirmation,
            title = { Text("要退出本次复习吗？") },
            text = { Text("未评分的问题不会计入完成。") },
            confirmButton = {
                Button(onClick = viewModel::onConfirmExit) {
                    Text("退出")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissExitConfirmation) {
                    Text("继续复习")
                }
            }
        )
    }
}

/**
 * 将复习内容拆成独立展示层，是为了让 UI 测试能直接验证关键状态，
 * 同时避免顶层 Screen 被导航与状态渲染两种职责混在一起。
 */
@Composable
fun ReviewCardContent(
    uiState: ReviewCardUiState,
    onRevealAnswer: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    onRetryLoad: () -> Unit,
    onContinueNextCard: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator()
                Text("正在加载复习内容…")
            }

            uiState.errorMessage != null && uiState.currentQuestion == null && !uiState.isCompleted -> {
                Text(uiState.errorMessage)
                Button(onClick = onRetryLoad) {
                    Text("重试")
                }
            }

            uiState.isCompleted -> {
                Text("本卡完成")
                Text("已完成 ${uiState.completedCount} / ${uiState.totalCount} 题")
                Button(onClick = onContinueNextCard) {
                    Text("继续下一张")
                }
                TextButton(onClick = onBackHome) {
                    Text("返回首页")
                }
            }

            else -> {
                val currentQuestion = uiState.currentQuestion
                if (currentQuestion != null) {
                    Text(uiState.cardTitle.ifBlank { "当前卡片" })
                    Text("第 ${uiState.completedCount + 1} / ${uiState.totalCount} 题")
                    Text("阶段：${currentQuestion.stageIndex}")
                    Text(currentQuestion.prompt)

                    if (uiState.answerVisible) {
                        Text("答案：${currentQuestion.answerText}")
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewActionButton(
                                    label = "重来",
                                    enabled = !uiState.isSubmitting,
                                    onClick = { onRate(ReviewRating.AGAIN) }
                                )
                                ReviewActionButton(
                                    label = "困难",
                                    enabled = !uiState.isSubmitting,
                                    onClick = { onRate(ReviewRating.HARD) }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewActionButton(
                                    label = "良好",
                                    enabled = !uiState.isSubmitting,
                                    onClick = { onRate(ReviewRating.GOOD) }
                                )
                                ReviewActionButton(
                                    label = "简单",
                                    enabled = !uiState.isSubmitting,
                                    onClick = { onRate(ReviewRating.EASY) }
                                )
                            }
                        }
                    } else {
                        Button(onClick = onRevealAnswer) {
                            Text("显示答案")
                        }
                    }

                    if (uiState.errorMessage != null) {
                        Text(uiState.errorMessage)
                    }
                }
            }
        }
    }
}

/**
 * 四档评分按钮统一封装，是为了保持禁用态和文案风格一致，
 * 避免某个分支漏掉重复点击保护。
 */
@Composable
private fun ReviewActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled
    ) {
        Text(label)
    }
}
