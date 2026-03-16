package com.kariscode.yike.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerButton
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeProgressBar
import com.kariscode.yike.ui.component.YikeRatingButton
import com.kariscode.yike.ui.component.YikeRatingPalette
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import com.kariscode.yike.ui.component.backNavigationAction

/**
 * 复习页需要保持聚焦节奏，因此仍使用流内导航壳，并把退出动作收敛到顶部栏和返回键确认。
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = viewModel::onExitAttempt)

    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            ReviewCardEffect.NavigateHome -> onExit()
            ReviewCardEffect.NavigateToQueue -> onNextCard()
        }
    }

    YikeFlowScaffold(
        title = uiState.cardTitle.ifBlank { "复习" },
        subtitle = buildReviewSubtitle(uiState),
        navigationAction = backNavigationAction(
            onClick = viewModel::onExitAttempt,
            contentDescription = "退出复习"
        )
    ) { padding ->
        ReviewCardContent(
            uiState = uiState,
            onRevealAnswer = viewModel::onRevealAnswerClick,
            onRate = viewModel::onRateClick,
            onRetryLoad = viewModel::loadSession,
            onContinueNextCard = viewModel::onContinueNextCardClick,
            onBackHome = viewModel::onBackHomeClick,
            modifier = modifier.padding(padding)
        )
    }

    if (uiState.exitConfirmationVisible) {
        ExitConfirmationDialog(
            onDismiss = viewModel::onDismissExitConfirmation,
            onConfirm = viewModel::onConfirmExit
        )
    }
}

/**
 * 复习内容拆成独立展示层后，可以直接验证题面、答案、评分和完成态的关键节奏。
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
    val spacing = LocalYikeSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载复习内容",
                    description = "稍等一下，我们会按当前卡片的到期问题顺序为你展开。"
                )
            }

            uiState.errorMessage != null && uiState.currentQuestion == null && !uiState.isCompleted -> {
                YikeStateBanner(
                    title = "暂时没能打开这张卡片",
                    description = uiState.errorMessage
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重试",
                            onClick = onRetryLoad,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "返回首页",
                            onClick = onBackHome,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            uiState.isCompleted -> {
                ReviewCompletedSection(
                    uiState = uiState,
                    onContinueNextCard = onContinueNextCard,
                    onBackHome = onBackHome
                )
            }

            else -> {
                ReviewProgressSection(uiState = uiState)
                uiState.currentQuestion?.let { currentQuestion ->
                    QuestionPromptSection(question = currentQuestion)
                    AnswerSection(
                        answerVisible = uiState.answerVisible,
                        answerText = currentQuestion.answerText,
                        onRevealAnswer = onRevealAnswer
                    )
                    RatingSection(
                        answerVisible = uiState.answerVisible,
                        isSubmitting = uiState.isSubmitting,
                        onRate = onRate
                    )
                }
                uiState.errorMessage?.let { message ->
                    YikeStateBanner(
                        title = "评分提交失败",
                        description = message
                    )
                }
            }
        }
    }
}

/**
 * 进度区将本卡完成度和当前题号放在同一块，是为了减少用户在复习过程中的位置焦虑。
 */
@Composable
private fun ReviewProgressSection(
    uiState: ReviewCardUiState
) {
    val totalCount = uiState.totalCount.coerceAtLeast(1)
    val progress = uiState.completedCount.toFloat() / totalCount.toFloat()
    YikeStateBanner(
        title = "本卡完成度",
        description = "第 ${uiState.completedCount + 1} 题，共 ${uiState.totalCount} 题",
        trailing = {
            YikeBadge(text = "${(progress * 100).toInt()}%")
        }
    ) {
        YikeProgressBar(progress = progress)
    }
}

/**
 * 题面区把问题内容单独抬高，是为了保持“先回忆、后看答案”的复习节奏。
 */
@Composable
private fun QuestionPromptSection(
    question: ReviewQuestionUiModel
) {
    YikeSurfaceCard {
        YikeHeaderBlock(
            eyebrow = "问题",
            title = "先在脑中组织答案",
            subtitle = "阶段 ${question.stageIndex}"
        )
        Text(text = question.prompt)
    }
}

/**
 * 答案区在用户主动展开前只保留主动作，是为了遵守规格中“评分前必须先显示答案”的约束。
 */
@Composable
private fun AnswerSection(
    answerVisible: Boolean,
    answerText: String,
    onRevealAnswer: () -> Unit
) {
    if (!answerVisible) {
        YikePrimaryButton(
            text = "显示答案",
            onClick = onRevealAnswer,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            YikeHeaderBlock(
                eyebrow = "答案",
                title = "已展开",
                subtitle = "对照答案后，立即给出这题的掌握度。"
            )
            YikeBadge(text = "已展开")
        }
        Text(text = answerText)
    }
}

/**
 * 评分区统一承载四档掌握度按钮，并在提交中禁用重复点击，保持复习推进节奏稳定。
 */
@Composable
private fun RatingSection(
    answerVisible: Boolean,
    isSubmitting: Boolean,
    onRate: (ReviewRating) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    if (!answerVisible) return
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        YikeHeaderBlock(
            eyebrow = "Rating",
            title = "这题掌握得怎么样？",
            subtitle = if (isSubmitting) "正在记录本次评分，请稍等。" else "评分后会自动推进到下一题或本卡完成态。"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeRatingButton(
                text = "完全不会",
                containerColor = YikeRatingPalette.criticalContainer,
                contentColor = androidx.compose.ui.graphics.Color(0xFF8C1212),
                onClick = { onRate(ReviewRating.AGAIN) },
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting
            )
            YikeRatingButton(
                text = "有印象",
                containerColor = YikeRatingPalette.warningContainer,
                contentColor = androidx.compose.ui.graphics.Color(0xFF8C5400),
                onClick = { onRate(ReviewRating.HARD) },
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeRatingButton(
                text = "基本会",
                containerColor = YikeRatingPalette.successContainer,
                contentColor = androidx.compose.ui.graphics.Color(0xFF1D6620),
                onClick = { onRate(ReviewRating.GOOD) },
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting
            )
            YikeRatingButton(
                text = "很轻松",
                containerColor = YikeRatingPalette.bestContainer,
                contentColor = androidx.compose.ui.graphics.Color(0xFF005048),
                onClick = { onRate(ReviewRating.EASY) },
                modifier = Modifier.weight(1f),
                enabled = !isSubmitting
            )
        }
    }
}

/**
 * 本卡完成态需要提供“继续下一张”和“返回首页”两个明确出口，避免用户停在流程尾部无所适从。
 */
@Composable
private fun ReviewCompletedSection(
    uiState: ReviewCardUiState,
    onContinueNextCard: () -> Unit,
    onBackHome: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "本卡完成",
        description = "已完成 ${uiState.completedCount} / ${uiState.totalCount} 题，现在可以继续下一张或先回首页。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = "继续下一张",
                onClick = onContinueNextCard,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "返回首页",
                onClick = onBackHome,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 退出确认明确提示“未评分不会计入完成”，是为了帮助用户理解中断复习的真实后果。
 */
@Composable
private fun ExitConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("要退出本次复习吗？") },
        text = { Text("当前未评分的问题不会计入完成，稍后需要重新处理。") },
        confirmButton = {
            YikeDangerButton(text = "退出复习", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "继续复习", onClick = onDismiss)
        }
    )
}

/**
 * 副标题跟随当前复习状态切换，是为了让顶部栏也能反映“正在答题 / 已完成”的节奏变化。
 */
private fun buildReviewSubtitle(uiState: ReviewCardUiState): String = when {
    uiState.isCompleted -> "这张卡片已经完成，可以继续下一张。"
    uiState.currentQuestion != null -> "第 ${uiState.completedCount + 1} 题，共 ${uiState.totalCount} 题"
    else -> "正在准备当前卡片的复习内容。"
}
