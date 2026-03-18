package com.kariscode.yike.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 搜索页把全文搜索和筛选收拢到同一处，是为了让“找到问题”和“决定怎么处理”成为同一步动作。
 */
@Composable
fun QuestionSearchScreen(
    initialDeckId: String?,
    initialCardId: String?,
    navigator: YikeNavigator,
    deckIdForEditor: String?,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<QuestionSearchViewModel>(
        factory = QuestionSearchViewModel.factory(
            initialDeckId = initialDeckId,
            initialCardId = initialCardId,
            studyInsightsRepository = container.studyInsightsRepository,
            deckRepository = container.deckRepository,
            cardRepository = container.cardRepository,
            timeProvider = container.timeProvider
        )
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
            onTagSelected = viewModel::onTagSelected,
            onStatusSelected = viewModel::onStatusSelected,
            onDeckSelected = viewModel::onDeckSelected,
            onCardSelected = viewModel::onCardSelected,
            onMasterySelected = viewModel::onMasterySelected,
            onClearFilters = viewModel::onClearFilters,
            onOpenEditor = { cardId -> navigator.openQuestionEditor(cardId = cardId, deckId = deckIdForEditor) },
            onOpenReview = navigator::openReviewCard,
            modifier = modifier.padding(padding)
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
    onTagSelected: (String?) -> Unit,
    onStatusSelected: (com.kariscode.yike.domain.model.QuestionStatus?) -> Unit,
    onDeckSelected: (String?) -> Unit,
    onCardSelected: (String?) -> Unit,
    onMasterySelected: (com.kariscode.yike.domain.model.QuestionMasteryLevel?) -> Unit,
    onClearFilters: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        if (uiState.isLoading && uiState.results.isEmpty() && uiState.errorMessage == null) {
            item {
                YikeStateBanner(
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
            QuestionSearchHeroSection(uiState = uiState, onKeywordChange = onKeywordChange)
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
        questionSearchResultItems(
            uiState = uiState,
            onOpenEditor = onOpenEditor,
            onOpenReview = onOpenReview
        )
    }
}
