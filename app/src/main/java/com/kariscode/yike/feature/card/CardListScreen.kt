package com.kariscode.yike.feature.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeFab
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeTextMetadataDialog
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 卡片列表属于流内页面，因此保持聚焦式返回路径，不复用一级底部导航。
 */
@Composable
fun CardListScreen(
    deckId: String,
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<CardListViewModel>(
        factory = CardListViewModel.factory(
            deckId = deckId,
            deckRepository = container.deckRepository,
            cardRepository = container.cardRepository,
            studyInsightsRepository = container.studyInsightsRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = uiState.deckName ?: "卡片列表",
        subtitle = "按章节或知识块拆分卡片，能让复习时更容易进入上下文。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        CardListContent(
            deckId = deckId,
            uiState = uiState,
            navigator = navigator,
            onCreateCard = viewModel::onCreateCardClick,
            onTitleChange = viewModel::onDraftTitleChange,
            onDescriptionChange = viewModel::onDraftDescriptionChange,
            onDismissEditor = viewModel::onDismissEditor,
            onConfirmSave = viewModel::onConfirmSave,
            onEditCardMeta = viewModel::onEditCardClick,
            onArchive = viewModel::onArchiveCardClick,
            onDelete = viewModel::onDeleteCardClick,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 卡片列表主体独立出来后，编辑弹窗、删除确认和真实统计就可以在同一状态树下统一处理。
 */
@Composable
private fun CardListContent(
    deckId: String,
    uiState: CardListUiState,
    navigator: YikeNavigator,
    onCreateCard: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmSave: () -> Unit,
    onEditCardMeta: (CardSummary) -> Unit,
    onArchive: (CardSummary) -> Unit,
    onDelete: (CardSummary) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val openTodayPreview: () -> Unit = navigator::openTodayPreview
    val openSearch: (String?) -> Unit = { cardId ->
        navigator.openQuestionSearch(deckId = deckId, cardId = cardId)
    }
    val openEditor: (String) -> Unit = { cardId ->
        navigator.openQuestionEditor(cardId = cardId, deckId = deckId)
    }
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        CardOverviewSection(
            items = uiState.items,
            onCreateCard = onCreateCard,
            onOpenTodayPreview = openTodayPreview,
            onOpenSearch = { openSearch(null) }
        )
        uiState.masterySummary?.let { summary ->
            CardMasterySection(summary = summary)
        }

        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载卡片",
                    description = "稍等一下，我们会把卡片统计和今日到期数量一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = ErrorMessages.CARD_LIST_LOAD_FAILED,
                    description = uiState.errorMessage
                )
            }

            uiState.items.isEmpty() -> {
                YikeStateBanner(
                    title = "还没有卡片",
                    description = "先创建第一张卡片，再进入问题编辑把复习内容录进去。"
                ) {
                    YikePrimaryButton(
                        text = "新建卡片",
                        onClick = onCreateCard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            else -> {
                uiState.items.forEach { item ->
                    CardSummaryCard(
                        item = item,
                        onOpenEditor = { openEditor(item.card.id) },
                        onOpenSearch = { openSearch(item.card.id) },
                        onEditMeta = { onEditCardMeta(item) },
                        onArchive = { onArchive(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }

        YikeOperationFeedback(
            successMessage = uiState.message,
            errorMessage = null
        )
    }

    uiState.editor?.let { editor ->
        YikeTextMetadataDialog(
            title = if (editor.entityId == null) "新建卡片" else "编辑卡片",
            primaryLabel = "标题",
            primaryValue = editor.primaryValue,
            onPrimaryValueChange = onTitleChange,
            secondaryLabel = "说明",
            secondaryValue = editor.secondaryValue,
            onSecondaryValueChange = onDescriptionChange,
            validationMessage = editor.validationMessage,
            onDismiss = onDismissEditor,
            onConfirm = onConfirmSave
        )
    }

    uiState.pendingDelete?.let {
        YikeDangerConfirmationDialog(
            title = "确认删除卡片？",
            description = "删除会级联清理该卡片下的问题与复习记录，且无法恢复。",
            confirmText = "删除",
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
}

/**
 * 顶部总览把卡片规模和到期压力放在一起，是为了让用户进入某个卡组时先看清今天要维护多少内容。
 */
@Composable
private fun CardOverviewSection(
    items: List<CardSummary>,
    onCreateCard: () -> Unit,
    onOpenTodayPreview: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    val totalQuestions = items.sumOf { it.questionCount }
    val totalDue = items.sumOf { it.dueQuestionCount }
    YikeHeroCard(
        eyebrow = "Chapter Cards",
        title = "${items.size} 张卡片",
        description = "按章节或知识块拆分卡片，复习时更容易保持上下文。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = totalQuestions.toString(),
                label = "问题总数",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = totalDue.toString(),
                label = "今日到期题目",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = "查看题库",
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "看今日任务",
                onClick = onOpenTodayPreview,
                modifier = Modifier.weight(1f)
            )
        }
        YikeFab(text = "+ 新卡片", onClick = onCreateCard)
    }
}

/**
 * 熟练度摘要在卡组层先给出分布，是为了让用户在进入具体卡片前就知道薄弱点主要落在哪里。
 */
@Composable
private fun CardMasterySection(
    summary: DeckMasterySummary
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "熟练度分布",
        description = "自动标签不写回数据库，只根据复习次数、阶段和遗忘次数即时计算。",
        trailing = {
            YikeBadge(text = "${summary.totalQuestions} 题")
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                YikeBadge(text = "新问题 ${summary.newCount}")
                YikeBadge(text = "学习中 ${summary.learningCount}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                YikeBadge(text = "熟悉 ${summary.familiarCount}")
                YikeBadge(text = "已掌握 ${summary.masteredCount}")
            }
        }
    }
}

/**
 * 单卡片列表项直接暴露“进入编辑”和“维护元信息”入口，是为了缩短内容管理主路径。
 */
@Composable
private fun CardSummaryCard(
    item: CardSummary,
    onOpenEditor: () -> Unit,
    onOpenSearch: () -> Unit,
    onEditMeta: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeListItemCard(
        title = item.card.title,
        summary = "${item.questionCount} 个问题",
        supporting = item.card.description.ifBlank {
            if (item.dueQuestionCount > 0) {
                "今天有 ${item.dueQuestionCount} 题到期，适合作为当前复习入口。"
            } else {
                "今天暂无到期题目，可以继续补充问题和答案。"
            }
        },
        badge = {
            YikeBadge(
                text = if (item.dueQuestionCount > 0) "${item.dueQuestionCount} 题到期" else "今日无到期"
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            YikePrimaryButton(
                text = "编辑问题",
                onClick = onOpenEditor,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "检索本卡",
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            TextButton(onClick = onEditMeta, modifier = Modifier.weight(1f)) { Text("编辑卡片") }
            TextButton(onClick = onArchive, modifier = Modifier.weight(1f)) { Text("归档") }
            TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("删除") }
        }
    }
}

