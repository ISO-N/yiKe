package com.kariscode.yike.feature.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeFab
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeTextMetadataDialog
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 卡组列表属于一级入口，因此必须复用统一导航壳，
 * 这样用户可以在首页、卡组和设置之间保持一致的切换体验。
 */
@Composable
fun DeckListScreen(
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikePrimaryScaffold(
        currentDestination = YikePrimaryDestination.DECKS,
        title = "卡组",
        subtitle = "管理卡组、查找内容和归档操作。",
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
            onKeywordChange = viewModel::onKeywordChange,
            onNameChange = viewModel::onDraftNameChange,
            onDescriptionChange = viewModel::onDraftDescriptionChange,
            onDismissEditor = viewModel::onDismissEditor,
            onConfirmSave = viewModel::onConfirmSave,
            onEditDeck = viewModel::onEditDeckClick,
            onToggleArchive = viewModel::onToggleArchiveClick,
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
    onKeywordChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDismissEditor: () -> Unit,
    onConfirmSave: () -> Unit,
    onEditDeck: (DeckSummary) -> Unit,
    onToggleArchive: (DeckSummary) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val visibleItems = uiState.items.filter { item ->
        val keyword = uiState.keyword.trim()
        keyword.isBlank() ||
            item.deck.name.contains(keyword, ignoreCase = true) ||
            item.deck.description.contains(keyword, ignoreCase = true)
    }
    YikeScrollableColumn(modifier = modifier) {
        DeckOverviewSection(items = visibleItems)
        DeckSearchSection(
            keyword = uiState.keyword,
            onKeywordChange = onKeywordChange
        )

        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载卡组",
                    description = "正在同步卡组和到期统计。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = ErrorMessages.DECK_LIST_LOAD_FAILED,
                    description = uiState.errorMessage
                )
            }

            uiState.items.isEmpty() -> {
                YikeStateBanner(
                    title = "还没有卡组",
                    description = "先创建一个卡组开始整理内容。"
                ) {
                    YikePrimaryButton(
                        text = "创建第一个卡组",
                        onClick = onCreateDeck,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            visibleItems.isEmpty() -> {
                YikeStateBanner(
                    title = "没有找到匹配的卡组",
                    description = "换个关键词试试，卡组名称和说明都会参与查找。"
                )
            }

            else -> {
                visibleItems.forEach { item ->
                    DeckSummaryCard(
                        item = item,
                        onOpen = { onOpenDeck(item.deck.id) },
                        onEdit = { onEditDeck(item) },
                        onArchive = { onToggleArchive(item) }
                    )
                }
            }
        }

        YikeOperationFeedback(
            successMessage = uiState.message,
            errorMessage = null
        )
        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding()))
    }

    uiState.editor?.let { editor ->
        YikeTextMetadataDialog(
            title = if (editor.entityId == null) "新建卡组" else "编辑卡组",
            primaryLabel = "名称",
            primaryValue = editor.primaryValue,
            onPrimaryValueChange = onNameChange,
            secondaryLabel = "说明",
            secondaryValue = editor.secondaryValue,
            onSecondaryValueChange = onDescriptionChange,
            validationMessage = editor.validationMessage,
            onDismiss = onDismissEditor,
            onConfirm = onConfirmSave
        )
    }
}

/**
 * 总览区把卡组总数和今日到期数放在顶部，是为了让用户一进入内容管理就知道当前维护压力。
 */
@Composable
private fun DeckOverviewSection(items: List<DeckSummary>) {
    val totalDue = items.sumOf { it.dueQuestionCount }
    YikeHeroCard(
        eyebrow = "Overview",
        title = "${items.size} 个活跃卡组",
        description = "先看数量，再决定今天先维护哪组内容。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.md)) {
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
    onArchive: () -> Unit
) {
    YikeListItemCard(
        title = item.deck.name,
        summary = "${item.cardCount} 张卡片 · ${item.questionCount} 个问题",
        supporting = item.deck.description.ifBlank {
            if (item.dueQuestionCount > 0) {
                "今天还有 ${item.dueQuestionCount} 题到期。"
            } else {
                "今天暂无到期题目。"
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
            YikeSecondaryButton(
                text = "归档",
                onClick = onArchive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 查找框放在总览之后，是为了让用户先看到当前规模，再决定是否收窄到具体卡组。
 */
@Composable
private fun DeckSearchSection(
    keyword: String,
    onKeywordChange: (String) -> Unit
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        label = { Text("查找卡组") },
        placeholder = { Text("输入名称或说明") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}
