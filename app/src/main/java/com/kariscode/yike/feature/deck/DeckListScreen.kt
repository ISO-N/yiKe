package com.kariscode.yike.feature.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerButton
import com.kariscode.yike.ui.component.YikeFab
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 卡组列表属于一级入口，因此必须复用统一导航壳，
 * 这样用户可以在首页、卡组和设置之间保持一致的切换体验。
 */
@Composable
fun DeckListScreen(
    onOpenHome: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDeck: (deckId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DeckListViewModel>(
        factory = DeckListViewModel.factory(
            deckRepository = container.deckRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    YikePrimaryScaffold(
        currentDestination = YikePrimaryDestination.DECKS,
        title = "卡组",
        subtitle = "优先把主题拆成卡组，复习和录入都会更容易维护。",
        showNavigationChrome = false,
        floatingActionButton = {
            YikeFab(
                text = "+ 新建",
                onClick = viewModel::onCreateDeckClick
            )
        }
    ) { padding ->
        DeckListContent(
            uiState = uiState,
            onCreateDeck = viewModel::onCreateDeckClick,
            onOpenDeck = onOpenDeck,
            onNameChange = viewModel::onDraftNameChange,
            onDescriptionChange = viewModel::onDraftDescriptionChange,
            onDismissEditor = viewModel::onDismissEditor,
            onConfirmSave = viewModel::onConfirmSave,
            onEditDeck = viewModel::onEditDeckClick,
            onToggleArchive = viewModel::onToggleArchiveClick,
            onDeleteDeck = viewModel::onDeleteDeckClick,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 列表主体拆开后可以独立覆盖空、错、成功和弹窗状态，减少导航壳与业务列表的耦合。
 */
@Composable
private fun DeckListContent(
    uiState: DeckListUiState,
    onCreateDeck: () -> Unit,
    onOpenDeck: (deckId: String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmSave: () -> Unit,
    onEditDeck: (DeckSummary) -> Unit,
    onToggleArchive: (DeckSummary) -> Unit,
    onDeleteDeck: (DeckSummary) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalYikeSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        DeckOverviewSection(items = uiState.items)

        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载卡组",
                    description = "稍等一下，我们会把卡组统计和今天到期的内容一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = "卡组列表加载失败",
                    description = uiState.errorMessage
                )
            }

            uiState.items.isEmpty() -> {
                YikeStateBanner(
                    title = "还没有卡组",
                    description = "先创建一个卡组，把知识块拆成更容易维护的复习单元。"
                ) {
                    YikePrimaryButton(
                        text = "创建第一个卡组",
                        onClick = onCreateDeck,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                uiState.items.forEach { item ->
                    DeckSummaryCard(
                        item = item,
                        onOpen = { onOpenDeck(item.deck.id) },
                        onEdit = { onEditDeck(item) },
                        onArchive = { onToggleArchive(item) },
                        onDelete = { onDeleteDeck(item) }
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
        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
    }

    uiState.editor?.let { editor ->
        DeckEditorDialog(
            editor = editor,
            onNameChange = onNameChange,
            onDescriptionChange = onDescriptionChange,
            onDismiss = onDismissEditor,
            onConfirm = onConfirmSave
        )
    }

    uiState.pendingDelete?.let {
        DeckDeleteDialog(
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
}

/**
 * 总览区把卡组总数和今日到期数放在顶部，是为了让用户一进入内容管理就知道当前维护压力。
 */
@Composable
private fun DeckOverviewSection(items: List<DeckSummary>) {
    val spacing = LocalYikeSpacing.current
    val totalDue = items.sumOf { it.dueQuestionCount }
    YikeHeroCard(
        eyebrow = "Overview",
        title = "${items.size} 个活跃卡组",
        description = "今天到期的内容会优先显示在每个列表项上，方便你快速决定先维护哪里。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = items.size.toString(),
                label = "活跃卡组",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = totalDue.toString(),
                label = "今日到期题目",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 单个卡组卡片统一承载统计与主操作，是为了让“继续进入”“维护”和“高风险动作”层级清晰可见。
 */
@Composable
private fun DeckSummaryCard(
    item: DeckSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    YikeListItemCard(
        title = item.deck.name,
        summary = "${item.cardCount} 张卡片 · ${item.questionCount} 个问题",
        supporting = item.deck.description.ifBlank {
            if (item.dueQuestionCount > 0) {
                "今天还有 ${item.dueQuestionCount} 题到期，适合继续维护和复习。"
            } else {
                "今天暂无到期题目，可以继续录入或整理内容。"
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
            horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)
        ) {
            YikePrimaryButton(
                text = "进入卡组",
                onClick = onOpen,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "编辑",
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)
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
 * 编辑弹窗继续保留轻量对话形式，是为了在不扩展新页面的前提下完成卡组创建与基础维护。
 */
@Composable
private fun DeckEditorDialog(
    editor: DeckEditorDraft,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editor.deckId == null) "新建卡组" else "编辑卡组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.md)) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("名称") },
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
 * 删除确认单独封装，是为了确保级联删除这种高风险操作始终带着明确提示出现。
 */
@Composable
private fun DeckDeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认删除卡组？") },
        text = { Text("删除会级联清理该卡组下的卡片、问题与复习记录，且无法恢复。") },
        confirmButton = {
            YikeDangerButton(text = "删除", onClick = onConfirm)
        },
        dismissButton = {
            YikeSecondaryButton(text = "取消", onClick = onDismiss)
        }
    )
}
