package com.kariscode.yike.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 练习设置页单独承接范围与顺序选择，是为了让“主动巩固”与“正式复习开始”在入口语义上彻底分开。
 */
@Composable
fun PracticeSetupScreen(
    initialArgs: PracticeSessionArgs,
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<PracticeSetupViewModel>(
        parameters = { parametersOf(initialArgs) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "自由练习",
        subtitle = "先圈定范围，再进入不影响正式调度的只读练习。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        PracticeSetupContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onDeckToggle = viewModel::onDeckToggle,
            onCardToggle = viewModel::onCardToggle,
            onQuestionToggle = viewModel::onQuestionToggle,
            onSelectAllQuestions = viewModel::onSelectAllQuestions,
            onClearQuestionSelection = viewModel::onClearQuestionSelection,
            onOrderModeChange = viewModel::onOrderModeChange,
            onStartPractice = { navigator.openPracticeSession(viewModel.buildSessionArgs()) },
            onOpenDecks = navigator::openDeckList,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 设置页内容保持单一滚动列，是为了让范围、顺序和空状态说明在同一阅读流里逐步展开。
 */
@Composable
private fun PracticeSetupContent(
    uiState: PracticeSetupUiState,
    onRetry: () -> Unit,
    onDeckToggle: (String) -> Unit,
    onCardToggle: (String) -> Unit,
    onQuestionToggle: (String) -> Unit,
    onSelectAllQuestions: () -> Unit,
    onClearQuestionSelection: () -> Unit,
    onOrderModeChange: (PracticeOrderMode) -> Unit,
    onStartPractice: () -> Unit,
    onOpenDecks: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    var deckSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var cardSectionExpanded by rememberSaveable { mutableStateOf(false) }
    var questionSectionExpanded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing.xl)
    ) {
        item {
            PracticeHeroSection(
                uiState = uiState,
                onOrderModeChange = onOrderModeChange,
                onStartPractice = onStartPractice
            )
        }

        when {
            uiState.isLoading -> {
                item {
                    YikeStateBanner(
                        title = "正在整理可练习内容",
                        description = "稍等一下，我们会把卡组、卡片和题目范围一起准备好。"
                    )
                }
            }

            uiState.errorMessage != null -> {
                item {
                    YikeStateBanner(
                        title = "暂时没能加载练习范围",
                        description = uiState.errorMessage
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            YikePrimaryButton(
                                text = "重试",
                                onClick = onRetry,
                                modifier = Modifier.weight(1f)
                            )
                            YikeSecondaryButton(
                                text = "去卡组",
                                onClick = onOpenDecks,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            uiState.deckOptions.isEmpty() -> {
                item {
                    YikeStateBanner(
                        title = "还没有可练习内容",
                        description = "先创建卡组、卡片和 active 题目，练习模式才会出现可选范围。"
                    ) {
                        YikePrimaryButton(
                            text = "进入卡组",
                            onClick = onOpenDecks,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            else -> {
                item {
                    PracticeEmptyStateSection(
                        uiState = uiState,
                        onSelectAllQuestions = onSelectAllQuestions
                    )
                }
                item {
                    PracticeDeckSection(
                        deckOptions = uiState.deckOptions,
                        onDeckToggle = onDeckToggle,
                        expanded = deckSectionExpanded,
                        onExpandedChange = { deckSectionExpanded = it }
                    )
                }
                item {
                    PracticeCardSection(
                        cardOptions = uiState.cardOptions,
                        onCardToggle = onCardToggle,
                        expanded = cardSectionExpanded,
                        onExpandedChange = { cardSectionExpanded = it }
                    )
                }
                item {
                    PracticeQuestionSectionHeader(
                        uiState = uiState,
                        onSelectAllQuestions = onSelectAllQuestions,
                        onClearQuestionSelection = onClearQuestionSelection,
                        expanded = questionSectionExpanded,
                        onExpandedChange = { questionSectionExpanded = it }
                    )
                }
                if (questionSectionExpanded) {
                    items(
                        items = uiState.questionOptions,
                        key = { option -> option.questionId }
                    ) { option ->
                        PracticeQuestionCard(
                            option = option,
                            onQuestionToggle = onQuestionToggle
                        )
                    }
                }
            }
        }
    }
}
