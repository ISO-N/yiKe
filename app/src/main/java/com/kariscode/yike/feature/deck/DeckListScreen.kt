package com.kariscode.yike.feature.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeActionDialog
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeDialogAction
import com.kariscode.yike.ui.component.YikeDialogActionStyle
import com.kariscode.yike.ui.component.YikeFab
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeLoadingBanner
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikeOperationSnackbarEffect
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikePullToRefresh
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeScrollableRow
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeTextMetadataDialog
import com.kariscode.yike.ui.component.YikeShimmerBlock
import com.kariscode.yike.ui.component.YikeEmptyStateIcon
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel

/**
 * 卡组列表属于一级入口，因此必须复用统一导航壳，
 * 这样用户可以在首页、卡组和设置之间保持一致的切换体验。
 */
@Composable
fun DeckListScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<DeckListViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeOperationSnackbarEffect(
        successMessage = uiState.message,
        errorMessage = null,
        onSuccessConsumed = viewModel::consumeMessage
    )

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
            onRetry = viewModel::refresh,
            navigator = navigator,
            onKeywordChange = viewModel::onKeywordChange,
            onNameChange = viewModel::onDraftNameChange,
            onDescriptionChange = viewModel::onDraftDescriptionChange,
            onTagsChange = viewModel::onDraftTagsChange,
            onIntervalStepCountChange = viewModel::onDraftIntervalStepCountChange,
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
    onRetry: () -> Unit,
    navigator: YikeNavigator,
    onKeywordChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onIntervalStepCountChange: (String) -> Unit,
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
    val isInitialLoading = uiState.isLoading && uiState.items.isEmpty()
    val isRefreshing = uiState.isLoading && uiState.items.isNotEmpty()

    YikePullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRetry,
        modifier = modifier
    ) {
        YikeScrollableColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            DeckOverviewSection(items = uiState.visibleItems)
            DeckSearchSection(
                keyword = uiState.keyword,
                onKeywordChange = onKeywordChange
            )

            when {
                isInitialLoading -> {
                    DeckListLoadingSection()
                }

                uiState.errorMessage != null -> {
                    YikeStateBanner(
                        title = ErrorMessages.DECK_LIST_LOAD_FAILED,
                        description = uiState.errorMessage.orEmpty()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)) {
                            YikePrimaryButton(
                                text = "重试",
                                onClick = onRetry,
                                modifier = Modifier.weight(1f)
                            )
                            YikeSecondaryButton(
                                text = "创建卡组",
                                onClick = onCreateDeck,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                uiState.items.isEmpty() -> {
                    YikeStateBanner(
                        title = "还没有卡组",
                        description = "先创建一个卡组开始整理内容。",
                        leading = { YikeEmptyStateIcon() }
                    ) {
                        YikePrimaryButton(
                            text = "创建第一个卡组",
                            onClick = onCreateDeck,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                uiState.visibleItems.isEmpty() -> {
                    YikeStateBanner(
                        title = "没有找到匹配的卡组",
                        description = "换个关键词试试，卡组名称、说明和标签都会参与查找。",
                        leading = { YikeEmptyStateIcon() }
                    ) {
                        YikeSecondaryButton(
                            text = "清空关键词",
                            onClick = { onKeywordChange("") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                else -> {
                    uiState.visibleItems.forEach { item ->
                        DeckSummaryCard(
                            item = item,
                            onOpen = { navigator.openCardList(item.deck.id) },
                            onPractice = {
                                navigator.openPracticeSetup(
                                    PracticeSessionArgs(deckIds = listOf(item.deck.id))
                                )
                            },
                            onOpenTagSearch = { tag ->
                                navigator.openQuestionSearch(tag = tag)
                            },
                            onEdit = { onEditDeck(item) },
                            onArchive = { onToggleArchive(item) },
                            onDelete = { onDeleteDeck(item) }
                        )
                    }
                }
            }
        }
    }

    uiState.editor?.let { editor ->
        YikeTextMetadataDialog(
            title = if (editor.entityId == null) "新建卡组" else "编辑卡组",
            primaryLabel = "名称",
            primaryValue = editor.name,
            onPrimaryValueChange = onNameChange,
            secondaryLabel = "说明",
            secondaryValue = editor.description,
            onSecondaryValueChange = onDescriptionChange,
            validationMessage = editor.validationMessage,
            extraContent = {
                OutlinedTextField(
                    value = editor.intervalStepCountText,
                    onValueChange = onIntervalStepCountChange,
                    label = { Text("间隔次数") },
                    placeholder = { Text("1-8，默认 8") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                DeckTagEditor(
                    tags = editor.tags,
                    availableTags = uiState.availableTags,
                    onTagsChange = onTagsChange
                )
            },
            onDismiss = onDismissEditor,
            onConfirm = onConfirmSave
        )
    }

    uiState.pendingDelete?.let {
        YikeDangerConfirmationDialog(
            title = "确认删除卡组？",
            description = "删除会级联清理该卡组下的卡片、问题和复习记录，且无法恢复。",
            confirmText = "删除卡组",
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
}

/**
 * 卡组页加载骨架提前占位统计卡和列表行，是为了让内容到达前页面结构已经稳定，
 * 避免真实数据回填时整体布局突然跳动。
 */
@Composable
private fun DeckListLoadingSection() {
    YikeLoadingBanner(
        title = "正在加载卡组",
        description = "正在同步卡组和到期统计。"
    )
    repeat(3) {
        YikeShimmerBlock(
            modifier = Modifier.fillMaxWidth(),
            height = if (it == 0) 104.dp else 88.dp
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
        eyebrow = "内容总览",
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
    onPractice: () -> Unit,
    onOpenTagSearch: (String) -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var actionDialogVisible by remember(item.deck.id) { mutableStateOf(false) }
    YikeListItemCard(
        title = item.deck.name,
        summary = buildString {
            append("${item.cardCount} 张卡片 · ${item.questionCount} 个问题 · ${item.deck.intervalStepCount} 段间隔")
            if (item.deck.tags.isNotEmpty()) {
                append(" · ${item.deck.tags.size} 个标签")
            }
        },
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
        if (item.deck.tags.isNotEmpty()) {
            DeckTagRow(
                tags = item.deck.tags,
                onTagClick = onOpenTagSearch
            )
        }
        YikePrimaryButton(
            text = "进入卡组",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)
        ) {
            YikeSecondaryButton(
                text = "开始练习",
                onClick = onPractice,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "更多操作",
                onClick = { actionDialogVisible = true },
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (actionDialogVisible) {
        YikeActionDialog(
            title = "卡组操作",
            onDismiss = { actionDialogVisible = false },
            actions = listOf(
                YikeDialogAction(
                    text = "编辑信息",
                    style = YikeDialogActionStyle.PRIMARY,
                    onClick = {
                        actionDialogVisible = false
                        onEdit()
                    }
                ),
                YikeDialogAction(
                    text = "归档卡组",
                    style = YikeDialogActionStyle.SECONDARY,
                    onClick = {
                        actionDialogVisible = false
                        onArchive()
                    }
                ),
                YikeDialogAction(
                    text = "删除卡组",
                    style = YikeDialogActionStyle.DANGER,
                    onClick = {
                        actionDialogVisible = false
                        onDelete()
                    }
                )
            ),
            dismissText = "取消"
        )
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
        placeholder = { Text("输入名称、说明或标签") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

/**
 * 列表卡片直接展示可点击标签，是为了把“浏览卡组”和“按同类标签继续筛题”两条路径接在一起，
 * 用户不必先记住标签再手动跳到搜索页重输一次。
 */
@Composable
private fun DeckTagRow(
    tags: List<String>,
    onTagClick: (String) -> Unit
) {
    YikeScrollableRow {
        tags.forEach { tag ->
            YikeBadge(
                text = tag,
                modifier = Modifier.clickable(
                    role = Role.Button,
                    onClickLabel = "按标签查看题目"
                ) {
                    onTagClick(tag)
                }
            )
        }
    }
}


