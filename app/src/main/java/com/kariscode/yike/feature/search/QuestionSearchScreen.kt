package com.kariscode.yike.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeLoadingBanner
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikePullToRefresh
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 搜索页把全文搜索和筛选收拢到同一处，是为了让“找到问题”和“决定怎么处理”成为同一步动作。
 */
@Composable
fun QuestionSearchScreen(
    initialDeckId: String?,
    initialCardId: String?,
    initialTag: String?,
    navigator: YikeNavigator,
    deckIdForEditor: String?,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<QuestionSearchViewModel>(
        parameters = { parametersOf(initialDeckId, initialCardId, initialTag) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "问题搜索与筛选",
        subtitle = "先定位题目，再决定是立刻复习还是继续编辑补充。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        QuestionSearchContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onKeywordChange = viewModel::onKeywordChange,
            onSearchTriggered = viewModel::refresh,
            onTagSelected = viewModel::onTagSelected,
            onStatusSelected = viewModel::onStatusSelected,
            onDeckSelected = viewModel::onDeckSelected,
            onCardSelected = viewModel::onCardSelected,
            onMasterySelected = viewModel::onMasterySelected,
            onClearFilters = viewModel::onClearFilters,
            onCreateContent = {
                uiState.selectedCardId?.let { selectedCardId ->
                    navigator.openQuestionEditor(
                        cardId = selectedCardId,
                        deckId = deckIdForEditor
                    )
                } ?: uiState.selectedDeckId?.let { selectedDeckId ->
                    navigator.openCardList(selectedDeckId)
                } ?: navigator.openDeckList()
            },
            onOpenEditor = { cardId -> navigator.openQuestionEditor(cardId = cardId, deckId = deckIdForEditor) },
            onOpenReview = navigator::openReviewCard,
            onOpenPractice = navigator::openPracticeSetup,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 搜索页主体把错误态和筛选区放在同一滚动列里，是为了让用户修正条件时不必离开当前上下文。
 */
@Composable
private fun QuestionSearchContent(
    uiState: QuestionSearchUiState,
    onRetry: () -> Unit,
    onKeywordChange: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onTagSelected: (String?) -> Unit,
    onStatusSelected: (QuestionStatus?) -> Unit,
    onDeckSelected: (String?) -> Unit,
    onCardSelected: (String?) -> Unit,
    onMasterySelected: (QuestionMasteryLevel?) -> Unit,
    onClearFilters: () -> Unit,
    onCreateContent: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    onOpenPractice: (PracticeSessionArgs) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    val isRefreshing = uiState.isLoading && uiState.results.isNotEmpty()

    YikePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRetry,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            if (uiState.isLoading && uiState.results.isEmpty() && uiState.errorMessage == null) {
                item {
                    YikeLoadingBanner(
                        title = "正在整理题库结果",
                        description = "稍等一下，我们会把关键字、标签和层级筛选对应的结果一起准备好。"
                    )
                }
            }
            if (uiState.errorMessage != null) {
                item {
                    YikeStateBanner(
                        title = ErrorMessages.SEARCH_LOAD_FAILED,
                        description = uiState.errorMessage
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            YikePrimaryButton(
                                text = "重试",
                                onClick = onRetry,
                                modifier = Modifier.weight(1f)
                            )
                            YikeSecondaryButton(
                                text = "清空筛选",
                                onClick = onClearFilters,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item {
                QuestionSearchHeroSection(
                    uiState = uiState,
                    onKeywordChange = onKeywordChange,
                    onSearchTriggered = onSearchTriggered
                )
            }
            item {
                QuestionSearchFilterSection(
                    uiState = uiState,
                    onTagSelected = onTagSelected,
                    onStatusSelected = onStatusSelected,
                    onDeckSelected = onDeckSelected,
                    onCardSelected = onCardSelected,
                    onMasterySelected = onMasterySelected,
                    onClearFilters = onClearFilters
                )
            }
            if (uiState.results.isNotEmpty()) {
                item {
                    QuestionSearchPracticeEntry(
                        uiState = uiState,
                        onOpenPractice = onOpenPractice
                    )
                }
            }
            questionSearchResultItems(
                uiState = uiState,
                onClearFilters = onClearFilters,
                onCreateContent = onCreateContent,
                onOpenEditor = onOpenEditor,
                onOpenReview = onOpenReview,
                onOpenPractice = onOpenPractice
            )
        }
    }
}

/**
 * 搜索页支持把当前结果直接带入练习设置，是为了覆盖“先搜出一小撮题，再立刻刷掉”的局部练习场景。
 */
@Composable
private fun QuestionSearchPracticeEntry(
    uiState: QuestionSearchUiState,
    onOpenPractice: (PracticeSessionArgs) -> Unit
) {
    YikeStateBanner(
        title = "把当前结果带去练习",
        description = "当前筛选已经命中 ${uiState.results.size} 题，可以继续调整范围，也可以直接进入只读练习。"
    ) {
        YikeSecondaryButton(
            text = "练习当前结果",
            onClick = { onOpenPractice(QuestionSearchStateFactory.buildPracticeArgsForResults(uiState)) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

