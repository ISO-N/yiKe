package com.kariscode.yike.feature.practice

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeProgressBar
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 练习会话页复用流内导航壳，是为了让用户把它理解成一次明确开始、明确结束的专注流程。
 */
@Composable
fun PracticeSessionScreen(
    args: PracticeSessionArgs,
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<PracticeSessionViewModel>(
        parameters = { parametersOf(args) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = viewModel::onFinishPracticeClick)

    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            PracticeSessionEffect.ExitPractice -> navigator.backToHome()
        }
    }

    YikeFlowScaffold(
        title = if (uiState.isCompleted) "练习完成" else "自由练习",
        subtitle = buildPracticeSessionSubtitle(uiState),
        navigationAction = backNavigationAction(
            onClick = viewModel::onFinishPracticeClick,
            contentDescription = "结束练习"
        )
    ) { padding ->
        PracticeSessionContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onRevealAnswer = viewModel::onRevealAnswerClick,
            onPreviousQuestion = viewModel::onPreviousQuestionClick,
            onNextQuestion = viewModel::onNextQuestionClick,
            onFinishPractice = viewModel::onFinishPracticeClick,
            onRestartFromSetup = { navigator.openPracticeSetup(args) },
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 会话内容只围绕当前题目推进，是为了把练习模式保持在“题面 -> 答案 -> 上一题/下一题”这条轻节奏上。
 */
@Composable
private fun PracticeSessionContent(
    uiState: PracticeSessionUiState,
    onRetry: () -> Unit,
    onRevealAnswer: () -> Unit,
    onPreviousQuestion: () -> Unit,
    onNextQuestion: () -> Unit,
    onFinishPractice: () -> Unit,
    onRestartFromSetup: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在准备练习会话",
                    description = "我们会按你刚才选择的范围恢复题目顺序和当前位置。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = "暂时没能打开练习会话",
                    description = uiState.errorMessage
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "结束练习",
                            onClick = onFinishPractice,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            uiState.isEmpty || uiState.currentQuestion == null -> {
                YikeStateBanner(
                    title = "当前范围下没有可练习题目",
                    description = "可以回到设置页调整范围，或稍后再从其他入口进入练习模式。"
                ) {
                    YikePrimaryButton(
                        text = "结束练习",
                        onClick = onFinishPractice,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            uiState.isCompleted -> {
                PracticeCompletedSection(
                    uiState = uiState,
                    onFinishPractice = onFinishPractice,
                    onRestartFromSetup = onRestartFromSetup
                )
            }

            else -> {
                PracticeSessionProgressSection(uiState = uiState)
                PracticePromptSection(question = uiState.currentQuestion)
                PracticeAnswerSection(
                    answerVisible = uiState.answerVisible,
                    answerText = uiState.currentQuestion.answerText,
                    onRevealAnswer = onRevealAnswer
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    YikeSecondaryButton(
                        text = "上一题",
                        onClick = onPreviousQuestion,
                        enabled = uiState.currentIndex > 0,
                        modifier = Modifier.weight(1f)
                    )
                    YikeSecondaryButton(
                        text = "结束练习",
                        onClick = onFinishPractice,
                        modifier = Modifier.weight(1f)
                    )
                    YikePrimaryButton(
                        text = if (uiState.currentIndex >= uiState.totalCount - 1) "完成练习" else "下一题",
                        onClick = if (uiState.currentIndex >= uiState.totalCount - 1) onFinishPractice else onNextQuestion,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/**
 * 进度区同时展示索引和随机 seed 信息，是为了让随机模式恢复时能看出当前会话顺序仍然固定。
 */
@Composable
private fun PracticeSessionProgressSection(
    uiState: PracticeSessionUiState
) {
    val progress = if (uiState.totalCount == 0) 0f else (uiState.currentIndex + 1).toFloat() / uiState.totalCount.toFloat()
    val sessionTag = when (uiState.orderMode) {
        com.kariscode.yike.domain.model.PracticeOrderMode.SEQUENTIAL -> "顺序练习"
        com.kariscode.yike.domain.model.PracticeOrderMode.RANDOM -> "随机练习"
    }
    YikeStateBanner(
        title = "本次练习不会影响正式复习计划",
        description = "第 ${uiState.currentIndex + 1} 题，共 ${uiState.totalCount} 题。随时可以结束，也可以返回查看上一题。"
    ) {
        YikeProgressBar(
            progress = progress,
            description = "练习进度 ${(progress * 100).toInt()}%，当前第 ${uiState.currentIndex + 1} 题，共 ${uiState.totalCount} 题"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)) {
            YikeBadge(text = sessionTag)
            uiState.sessionSeed?.let { seed ->
                YikeBadge(text = "seed $seed")
            }
        }
    }
}

/**
 * 题面区把来源卡片一起展示，是为了让用户在主动练习时仍能保留知识块上下文。
 */
@Composable
private fun PracticePromptSection(
    question: PracticeSessionQuestionUiModel
) {
    YikeSurfaceCard {
        YikeHeaderBlock(
            eyebrow = "${question.deckName} / ${question.cardTitle}",
            title = "先在脑中作答",
            subtitle = "练习模式只做回忆和核对，不会记录评分。"
        )
        Text(text = question.prompt)
    }
}

/**
 * 答案区延续“先隐藏、后展开”的节奏，是为了让练习体验和正式复习在记忆动作上保持一致。
 */
@Composable
private fun PracticeAnswerSection(
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
        YikeHeaderBlock(
            eyebrow = "答案",
            title = "已展开",
            subtitle = "如果答案为空，会直接显示“无答案”，避免把空内容误判成加载失败。"
        )
        Text(text = answerText)
    }
}

/**
 * 顶部副标题随当前索引变化，是为了让用户在系统返回或切应用后回到页面时立刻知道自己停在哪里。
 */
private fun buildPracticeSessionSubtitle(uiState: PracticeSessionUiState): String = when {
    uiState.isLoading -> "正在恢复本次练习题序。"
    uiState.isEmpty -> "当前范围下暂无题目。"
    uiState.isCompleted -> "本次练习已经收尾，不会写入任何正式评分。"
    uiState.currentQuestion != null -> "第 ${uiState.currentIndex + 1} 题，共 ${uiState.totalCount} 题"
    else -> "练习会话已准备完成。"
}

/**
 * 完成态显式说明“已结束但未写分”，是为了把自由练习和正式复习的收尾语义彻底分开。
 */
@Composable
private fun PracticeCompletedSection(
    uiState: PracticeSessionUiState,
    onFinishPractice: () -> Unit,
    onRestartFromSetup: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    val elapsedMinutes = uiState.startedAtEpochMillis?.let { startedAt ->
        ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 60000L).toInt()
    } ?: 0
    YikeStateBanner(
        title = "本轮自由练习已完成",
        description = "共浏览 ${uiState.totalCount} 题，耗时约 ${elapsedMinutes.coerceAtLeast(1)} 分钟。本次只做回忆和核对，没有写入正式评分。"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                YikeBadge(text = if (uiState.orderMode == com.kariscode.yike.domain.model.PracticeOrderMode.RANDOM) "随机模式" else "顺序模式")
                YikeBadge(text = "${uiState.totalCount} 题已完成")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                YikeSecondaryButton(
                    text = "重新调整范围",
                    onClick = onRestartFromSetup,
                    modifier = Modifier.weight(1f)
                )
                YikePrimaryButton(
                    text = "返回首页",
                    onClick = onFinishPractice,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
