package com.kariscode.yike.feature.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikeLoadingBanner
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeProgressBar
import com.kariscode.yike.ui.component.YikeRatingButton
import com.kariscode.yike.ui.component.YikeRatingPalette
import com.kariscode.yike.ui.component.YikeScrollableColumn
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
    navigator: YikeNavigator,
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
            ReviewCardEffect.NavigateHome -> navigator.backToHome()
            ReviewCardEffect.NavigateToQueue -> navigator.openReviewQueue()
        }
    }

    YikeFlowScaffold(
        title = uiState.cardTitle.ifBlank { "复习" },
        subtitle = buildReviewSubtitle(uiState),
        navigationAction = backNavigationAction(
            onClick = viewModel::onExitAttempt,
            contentDescription = "退出复习",
            label = "退出复习"
        )
    ) { padding ->
        ReviewCardContent(
            uiState = uiState,
            onRevealAnswer = viewModel::onRevealAnswerClick,
            onHideAnswer = viewModel::onHideAnswerClick,
            onRate = viewModel::onRateClick,
            onRetryLoad = viewModel::loadSession,
            onContinueNextCard = viewModel::onContinueNextCardClick,
            onOpenAnalytics = navigator::openAnalytics,
            onBackHome = viewModel::onBackHomeClick,
            modifier = modifier,
            contentPadding = padding
        )
    }

    if (uiState.exitConfirmationVisible) {
        YikeDangerConfirmationDialog(
            title = "要退出本次复习吗？",
            description = "当前未评分的问题不会计入完成，稍后需要重新处理。",
            confirmText = "退出复习",
            dismissText = "继续复习",
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
    onHideAnswer: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    onRetryLoad: () -> Unit,
    onContinueNextCard: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onBackHome: () -> Unit,
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
                YikeLoadingBanner(
                    title = "正在加载复习内容",
                    description = "稍等一下，我们会按当前卡片的到期问题顺序为你展开。"
                )
            }

            uiState.errorMessage != null && uiState.currentQuestion == null && !uiState.isCompleted -> {
                YikeStateBanner(
                    title = "暂时没能打开这张卡片",
                    description = uiState.errorMessage.orEmpty()
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
                    onOpenAnalytics = onOpenAnalytics,
                    onBackHome = onBackHome
                )
            }

            else -> {
                ReviewProgressSection(uiState = uiState)
                uiState.currentQuestion?.let { currentQuestion ->
                    ReviewQuestionInteractionSection(
                        question = currentQuestion,
                        answerVisible = uiState.answerVisible,
                        isSubmitting = uiState.isSubmitting,
                        onRevealAnswer = onRevealAnswer,
                        onHideAnswer = onHideAnswer,
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
 * 当前题目的交互集中在同一块中，是为了让滑动评分只作用于明确区域，
 * 同时把“先看题、再看答案、最后评分”的节奏维持在同一视觉流里。
 */
@Composable
private fun ReviewQuestionInteractionSection(
    question: ReviewQuestionUiModel,
    answerVisible: Boolean,
    isSubmitting: Boolean,
    onRevealAnswer: () -> Unit,
    onHideAnswer: () -> Unit,
    onRate: (ReviewRating) -> Unit
) {
    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    Column(
        verticalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.lg),
        modifier = Modifier.pointerInput(question.questionId, answerVisible, isSubmitting) {
            if (!answerVisible || isSubmitting) {
                return@pointerInput
            }
            var totalDrag = Offset.Zero
            detectDragGestures(
                onDragStart = { totalDrag = Offset.Zero },
                onDrag = { change, dragAmount ->
                    change.consume()
                    totalDrag += Offset(dragAmount.x, dragAmount.y)
                },
                onDragEnd = {
                    ReviewSwipeGesture.fromOffset(
                        totalDrag = totalDrag,
                        thresholdPx = swipeThresholdPx
                    )?.let { gesture ->
                        onRate(gesture.rating)
                    }
                }
            )
        }
    ) {
        QuestionPromptSection(question = question)
        AnswerSection(
            answerVisible = answerVisible,
            answerText = question.answerText,
            isSubmitting = isSubmitting,
            onRevealAnswer = onRevealAnswer,
            onHideAnswer = onHideAnswer
        )
        RatingSection(
            question = question,
            answerVisible = answerVisible,
            isSubmitting = isSubmitting,
            onRate = onRate
        )
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
    val progress = (uiState.completedCount + if (uiState.answerVisible) 1 else 0).coerceAtMost(totalCount).toFloat() / totalCount.toFloat()
    YikeStateBanner(
        title = "本卡完成度",
        description = if (uiState.answerVisible) {
            "第 ${uiState.completedCount + 1} 题已展开答案，下一步请选择最贴近的评分。"
        } else {
            "第 ${uiState.completedCount + 1} 题，共 ${uiState.totalCount} 题，先尝试完整回忆再展开答案。"
        },
        trailing = {
            YikeBadge(text = "${(progress * 100).toInt()}%")
        }
    ) {
        YikeProgressBar(
            progress = progress,
            description = "本卡完成度 ${(progress * 100).toInt()}%，已完成 ${uiState.completedCount} / ${uiState.totalCount} 题"
        )
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            YikeHeaderBlock(
                eyebrow = "问题",
                title = "先在脑中组织答案",
                subtitle = "${buildQuestionSubtitle(question)} · 计划间隔 ${question.plannedIntervalDays} 天"
            )
            question.overdueBadgeText?.let { badgeText ->
                YikeBadge(text = if (question.needsReinforcement) "重新巩固" else badgeText)
            }
        }
        question.overdueHintText?.let { hintText ->
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    isSubmitting: Boolean,
    onRevealAnswer: () -> Unit,
    onHideAnswer: () -> Unit
) {
    AnimatedContent(
        targetState = answerVisible,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 320)) + expandVertically()) togetherWith
                (fadeOut(animationSpec = tween(durationMillis = 180)) + shrinkVertically())
        },
        label = "answer_section_transition"
    ) { isAnswerVisible ->
        if (!isAnswerVisible) {
            YikePrimaryButton(
                text = "显示答案",
                onClick = onRevealAnswer,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            YikeSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    YikeHeaderBlock(
                        eyebrow = "答案",
                        title = "已展开，准备评分",
                        subtitle = "先核对答案，再根据下次复习间隔暗示选择最贴近的一档评分。"
                    )
                    YikeBadge(text = "等待评分")
                }
                Text(text = answerText)
                YikeSecondaryButton(
                    text = "重新记忆",
                    onClick = onHideAnswer,
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "如果想先再完整回忆一遍，可以先收起答案，再重新决定评分。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 评分区统一承载四档掌握度按钮，并在提交中禁用重复点击，保持复习推进节奏稳定。
 */
@Composable
private fun RatingSection(
    question: ReviewQuestionUiModel,
    answerVisible: Boolean,
    isSubmitting: Boolean,
    onRate: (ReviewRating) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    AnimatedVisibility(
        visible = answerVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 320)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(durationMillis = 180)) + shrinkVertically()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeHeaderBlock(
                eyebrow = "评分",
                title = "这题掌握得怎么样？",
                subtitle = if (isSubmitting) {
                    "正在记录本次评分，请稍等。"
                } else {
                    "支持滑动评分：左滑完全不会，右滑很轻松，上滑基本会，下滑有印象。"
                }
            )
            ReviewGestureHintSection()
            BoxWithConstraints {
                val useSingleColumn = maxWidth < 420.dp || LocalDensity.current.fontScale >= 1.15f
                if (useSingleColumn) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        question.ratingHints.forEach { hint ->
                            ReviewRatingHintCard(
                                hint = hint,
                                isSubmitting = isSubmitting,
                                onRate = onRate,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    question.ratingHints.chunked(2).forEach { rowHints ->
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            rowHints.forEach { hint ->
                                ReviewRatingHintCard(
                                    hint = hint,
                                    isSubmitting = isSubmitting,
                                    onRate = onRate,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 手势提示显式展示方向映射，是为了让首次进入的新交互具备可发现性，
 * 不要求用户靠试错才知道上下左右各代表什么评分。
 */
@Composable
private fun ReviewGestureHintSection() {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "滑动也能评分",
        description = "按钮点击仍然可用，手势只是给连续复习补一条更顺手的快捷路径。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeBadge(text = "左滑 完全不会")
            YikeBadge(text = "右滑 很轻松")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeBadge(text = "上滑 基本会")
            YikeBadge(text = "下滑 有印象")
        }
    }
}

/**
 * 本卡完成态需要把“继续复习 / 查看统计 / 返回首页”三种收尾动作放到一起，
 * 避免用户完成一张卡后还要回忆统计页或首页入口分别在哪里。
 */
@Composable
private fun ReviewCompletedSection(
    uiState: ReviewCardUiState,
    onContinueNextCard: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onBackHome: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    val elapsedMinutes = uiState.sessionStartedAtEpochMillis?.let { startedAt ->
        ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 60000L).toInt().coerceAtLeast(1)
    } ?: 1
    YikeStateBanner(
        title = "本卡完成",
        description = "已完成 ${uiState.completedCount} / ${uiState.totalCount} 题，耗时约 $elapsedMinutes 分钟。现在可以继续复习、查看统计，或先回首页收尾。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ReviewRating.entries.forEach { rating ->
                val count = uiState.ratingCounts[rating] ?: 0
                if (count > 0) {
                    YikeBadge(text = "${rating.displayLabel()} $count")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = "继续复习",
                onClick = onContinueNextCard,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "查看统计",
                onClick = onOpenAnalytics,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikeSecondaryButton(
                text = "返回首页",
                onClick = onBackHome,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 副标题跟随当前复习状态切换，是为了让顶部栏也能反映“正在答题 / 已完成”的节奏变化。
 */
private fun buildReviewSubtitle(uiState: ReviewCardUiState): String = when {
    uiState.isCompleted -> "这张卡片已经完成，可以继续下一张。"
    uiState.currentQuestion != null -> "第 ${uiState.completedCount + 1} 题，共 ${uiState.totalCount} 题"
    else -> "正在准备当前卡片的复习内容。"
}

/**
 * 题面副标题吸收过期标签，是为了让用户在答题前就理解这题当前处于怎样的记忆状态。
 */
private fun buildQuestionSubtitle(question: ReviewQuestionUiModel): String = buildString {
    append("阶段 ${question.stageIndex}")
    question.overdueBadgeText?.let { badgeText ->
        append(" · ")
        append(badgeText)
    }
}

/**
 * 评分提示卡把按钮和下次间隔说明放在一起，是为了让用户直接比较四档评分的节奏差异。
 */
@Composable
private fun ReviewRatingHintCard(
    hint: ReviewRatingHintUiModel,
    isSubmitting: Boolean,
    onRate: (ReviewRating) -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = YikeRatingPalette.toneFor(hint.rating)
    YikeSurfaceCard(modifier = modifier) {
        YikeRatingButton(
            text = hint.title,
            containerColor = tone.containerColor,
            contentColor = tone.contentColor,
            onClick = { onRate(hint.rating) },
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = hint.intervalHint,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = hint.stageHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 手势方向和评分的映射集中在一处，是为了把阈值判断与评分语义绑定起来，
 * 后续如果要调灵敏度或补测试，不必再到 UI 结构里翻找分散条件。
 */
private enum class ReviewSwipeGesture(val rating: ReviewRating) {
    LEFT(ReviewRating.AGAIN),
    RIGHT(ReviewRating.EASY),
    UP(ReviewRating.GOOD),
    DOWN(ReviewRating.HARD);

    companion object {
        /**
         * 只有拖拽距离足够明确时才判定一次滑动评分，是为了避免普通滚动或轻微抖动被误识别成提交。
         */
        fun fromOffset(
            totalDrag: Offset,
            thresholdPx: Float
        ): ReviewSwipeGesture? {
            val horizontalDistance = kotlin.math.abs(totalDrag.x)
            val verticalDistance = kotlin.math.abs(totalDrag.y)
            val dominantDistance = maxOf(horizontalDistance, verticalDistance)
            if (dominantDistance < thresholdPx) {
                return null
            }
            return if (horizontalDistance >= verticalDistance) {
                if (totalDrag.x < 0f) LEFT else RIGHT
            } else {
                if (totalDrag.y < 0f) UP else DOWN
            }
        }
    }
}

/**
 * 完成态和评分区共用同一套文案，是为了避免按钮标题与统计摘要出现两种不同叫法。
 */
private fun ReviewRating.displayLabel(): String = when (this) {
    ReviewRating.AGAIN -> "完全不会"
    ReviewRating.HARD -> "有印象"
    ReviewRating.GOOD -> "基本会"
    ReviewRating.EASY -> "很轻松"
}
