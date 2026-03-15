package com.kariscode.yike.feature.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerButton
import com.kariscode.yike.ui.component.YikeFab
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 卡片列表属于流内页面，因此保持聚焦式返回路径，不复用一级底部导航。
 */
@Composable
fun CardListScreen(
    deckId: String,
    onBack: () -> Unit,
    onEditCard: (cardId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<CardListViewModel>(
        factory = CardListViewModel.factory(
            deckId = deckId,
            deckRepository = container.deckRepository,
            cardRepository = container.cardRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    YikeFlowScaffold(
        title = uiState.deckName ?: "卡片列表",
        subtitle = "按章节或知识块拆分卡片，能让复习时更容易进入上下文。",
        navigationAction = backNavigationAction(onBack)
    ) { padding ->
        CardListContent(
            uiState = uiState,
            onCreateCard = viewModel::onCreateCardClick,
            onOpenEditor = onEditCard,
            onTitleChange = viewModel::onDraftTitleChange,
            onDescriptionChange = viewModel::onDraftDescriptionChange,
            onDismissEditor = viewModel::onDismissEditor,
            onConfirmSave = viewModel::onConfirmSave,
            onEditCardMeta = viewModel::onEditCardClick,
            onArchive = viewModel::onArchiveCardClick,
            onDelete = viewModel::onDeleteCardClick,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            modifier = modifier.padding(padding)
        )
    }
}

/**
 * 卡片列表主体独立出来后，编辑弹窗、删除确认和真实统计就可以在同一状态树下统一处理。
 */
@Composable
private fun CardListContent(
    uiState: CardListUiState,
    onCreateCard: () -> Unit,
    onOpenEditor: (cardId: String) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmSave: () -> Unit,
    onEditCardMeta: (CardSummary) -> Unit,
    onArchive: (CardSummary) -> Unit,
    onDelete: (CardSummary) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        CardOverviewSection(items = uiState.items, onCreateCard = onCreateCard)

        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载卡片",
                    description = "稍等一下，我们会把卡片统计和今日到期数量一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = "卡片列表加载失败",
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
                        onOpenEditor = { onOpenEditor(item.card.id) },
                        onEditMeta = { onEditCardMeta(item) },
                        onArchive = { onArchive(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }

        uiState.message?.let { message ->
            YikeStateBanner(
                title = "操作已完成",
                description = message
            )
        }
    }

    uiState.editor?.let { editor ->
        CardEditorDialog(
            editor = editor,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onDismiss = onDismissEditor,
            onConfirm = onConfirmSave
        )
    }

    uiState.pendingDelete?.let {
        CardDeleteDialog(
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
    onCreateCard: () -> Unit
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
        YikeFab(text = "+ 新卡片", onClick = onCreateCard)
    }
}

/**
 * 单卡片列表项直接暴露“进入编辑”和“维护元信息”入口，是为了缩短内容管理主路径。
 */
@Composable
private fun CardSummaryCard(
    item: CardSummary,
    onOpenEditor: () -> Unit,
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
                text = "编辑卡片",
                onClick = onEditMeta,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            TextButton(onClick = onArchive, modifier = Modifier.weight(1f)) {
                Text("归档")
            }
            TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("删除")
            }
        }
    }
}

/**
 * 卡片元信息编辑继续用对话框承载，是为了让“快速改标题/说明”不打断问题编辑主路径。
 */
@Composable
private fun CardEditorDialog(
    editor: CardEditorDraft,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.cardId == null) "新建卡片" else "编辑卡片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.md)) {
                OutlinedTextField(
                    value = editor.title,
                    onValueChange = onTitleChange,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editor.description,
                    onValueChange = onDescriptionChange,
                    label = { Text("说明") },
                    modifier = Modifier.fillMaxWidth()
                )
                editor.validationMessage?.let { message ->
                    Text(text = message)
                }
            }
        },
        confirmButton = {
            YikePrimaryButton(text = "保存", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}

/**
 * 删除确认单独封装，是为了让卡片层级的不可逆操作始终带着明确风险说明出现。
 */
@Composable
private fun CardDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除卡片？") },
        text = { Text("删除会级联清理该卡片下的问题与复习记录，且无法恢复。") },
        confirmButton = {
            YikeDangerButton(text = "删除", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}
