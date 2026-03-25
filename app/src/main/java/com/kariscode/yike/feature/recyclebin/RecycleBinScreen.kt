package com.kariscode.yike.feature.recyclebin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeDangerButton
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikeOperationSnackbarEffect
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel

/**
 * 已归档内容页独立成流内路由，是为了把“恢复归档内容”和“彻底删除”从设置页低频入口里抽离出来。
 */
@Composable
fun RecycleBinScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<RecycleBinViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeOperationSnackbarEffect(
        successMessage = uiState.message,
        errorMessage = uiState.errorMessage,
        onSuccessConsumed = viewModel::consumeMessage,
        onErrorConsumed = viewModel::consumeErrorMessage
    )

    YikeFlowScaffold(
        title = "已归档内容",
        subtitle = "这里集中保留归档内容，恢复后会重新回到原来的列表里。",
        navigationAction = backNavigationAction(navigator::back)
    ) { padding ->
        RecycleBinContent(
            uiState = uiState,
            onRestoreDeck = viewModel::onRestoreDeckClick,
            onDeleteDeck = viewModel::onDeleteDeckClick,
            onRestoreCard = viewModel::onRestoreCardClick,
            onDeleteCard = viewModel::onDeleteCardClick,
            onDismissDelete = viewModel::onDismissDelete,
            onConfirmDelete = viewModel::onConfirmDelete,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 页面主体把总览、两类归档内容和风险确认统一收在一处，
 * 能让用户在恢复前先看清当前积压规模。
 */
@Composable
private fun RecycleBinContent(
    uiState: RecycleBinUiState,
    onRestoreDeck: (DeckSummary) -> Unit,
    onDeleteDeck: (DeckSummary) -> Unit,
    onRestoreCard: (ArchivedCardSummary) -> Unit,
    onDeleteCard: (ArchivedCardSummary) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        RecycleBinOverviewSection(uiState = uiState)

        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在整理已归档内容",
                    description = "稍等一下，我们会把已归档的卡组和卡片一起载入。"
                )
            }

            uiState.archivedDecks.isEmpty() && uiState.archivedCards.isEmpty() -> {
                YikeStateBanner(
                    title = "还没有已归档内容",
                    description = "当前没有已归档内容，需要恢复时会在这里集中处理。"
                )
            }

            else -> {
                RecycleDeckSection(
                    items = uiState.archivedDecks,
                    onRestore = onRestoreDeck,
                    onDelete = onDeleteDeck
                )
                RecycleCardSection(
                    items = uiState.archivedCards,
                    onRestore = onRestoreCard,
                    onDelete = onDeleteCard
                )
            }
        }
    }

    uiState.pendingDelete?.let { target ->
        YikeDangerConfirmationDialog(
            title = when (target) {
                is RecycleBinDeleteTarget.DeckTarget -> "彻底删除这个卡组？"
                is RecycleBinDeleteTarget.CardTarget -> "彻底删除这张卡片？"
            },
            description = when (target) {
                is RecycleBinDeleteTarget.DeckTarget -> "删除后会级联清理其下卡片、问题与复习记录，且无法恢复。"
                is RecycleBinDeleteTarget.CardTarget -> "删除后会级联清理其下问题与复习记录，且无法恢复。"
            },
            confirmText = "彻底删除",
            onDismiss = onDismissDelete,
            onConfirm = onConfirmDelete
        )
    }
}

/**
 * 总览卡先给出归档规模，是为了帮助用户决定当前更适合恢复还是继续清理。
 */
@Composable
private fun RecycleBinOverviewSection(
    uiState: RecycleBinUiState
) {
    val spacing = LocalYikeSpacing.current
    YikeHeroCard(
        eyebrow = "回收站",
        title = "${uiState.archivedDecks.size} 个卡组 · ${uiState.archivedCards.size} 张卡片",
        description = "归档内容默认不会出现在卡组、卡片列表和复习流程里。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = uiState.archivedDecks.size.toString(),
                label = "已归档卡组",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = uiState.archivedCards.size.toString(),
                label = "已归档卡片",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 卡组区集中展示较高层级的归档内容，是为了让用户优先处理影响范围更大的恢复动作。
 */
@Composable
private fun RecycleDeckSection(
    items: List<DeckSummary>,
    onRestore: (DeckSummary) -> Unit,
    onDelete: (DeckSummary) -> Unit
) {
    if (items.isEmpty()) return
    items.forEach { item ->
        YikeListItemCard(
            title = item.deck.name,
            summary = "${item.cardCount} 张卡片 · ${item.questionCount} 个问题",
            supporting = item.deck.description.ifBlank {
                if (item.dueQuestionCount > 0) {
                    "归档前有 ${item.dueQuestionCount} 题到期，恢复后会重新回到内容管理视图。"
                } else {
                    "恢复后会重新回到卡组列表。"
                }
            }
        ) {
            YikeSecondaryButton(
                text = "恢复卡组",
                onClick = { onRestore(item) },
                modifier = Modifier.weight(1f)
            )
            YikeDangerButton(
                text = "彻底删除",
                onClick = { onDelete(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 卡片区保留所属卡组名称，是为了让用户在恢复前知道内容会回到哪个层级。
 */
@Composable
private fun RecycleCardSection(
    items: List<ArchivedCardSummary>,
    onRestore: (ArchivedCardSummary) -> Unit,
    onDelete: (ArchivedCardSummary) -> Unit
) {
    if (items.isEmpty()) return
    items.forEach { item ->
        YikeListItemCard(
            title = item.card.title,
            summary = "${item.deckName} · ${item.questionCount} 个问题",
            supporting = item.card.description.ifBlank {
                if (item.dueQuestionCount > 0) {
                    "归档前有 ${item.dueQuestionCount} 题到期，恢复后可继续检索和编辑。"
                } else {
                    "恢复后会重新回到所属卡组的卡片列表。"
                }
            }
        ) {
            YikeSecondaryButton(
                text = "恢复卡片",
                onClick = { onRestore(item) },
                modifier = Modifier.weight(1f)
            )
            YikeDangerButton(
                text = "彻底删除",
                onClick = { onDelete(item) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}
