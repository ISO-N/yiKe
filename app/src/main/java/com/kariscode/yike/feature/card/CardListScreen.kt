package com.kariscode.yike.feature.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 卡片列表页作为内容管理层级的一部分，先固定“deckId -> 卡片列表 -> 编辑页”的导航关系，
 * 能保证后续在数据层实现级联与归档策略时，UI 不需要改路由就能逐步替换实现。
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

    Scaffold(
        topBar = {
            val title = uiState.deckName?.let { "卡片列表 - $it" } ?: "卡片列表"
            YikeTopAppBar(title = title, navigationAction = NavigationAction(label = "返回", onClick = onBack))
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onCreateCardClick) {
                Text("新建")
            }
        },
        modifier = modifier
    ) { padding ->
        CardListContent(
            uiState = uiState,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * 列表页主体拆分为单独的 Composable，便于后续增加空/错/加载态而不破坏 Scaffold 结构。
 */
@Composable
private fun CardListContent(
    uiState: CardListUiState,
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
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.isLoading) {
            Text("加载中…")
        } else if (uiState.items.isEmpty()) {
            Text("暂无卡片。点击右下角“新建”创建第一张卡片。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = uiState.items, key = { it.card.id }) { item ->
                    CardSummaryRow(
                        item = item,
                        onOpenEditor = { onOpenEditor(item.card.id) },
                        onEditMeta = { onEditCardMeta(item) },
                        onArchive = { onArchive(item) },
                        onDelete = { onDelete(item) }
                    )
                }
            }
        }

        if (uiState.message != null) {
            Text(uiState.message)
        }
    }

    if (uiState.editor != null) {
        AlertDialog(
            onDismissRequest = onDismissEditor,
            title = { Text(if (uiState.editor.cardId == null) "新建卡片" else "编辑卡片") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.editor.title,
                        onValueChange = onTitleChange,
                        label = { Text("标题") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.editor.description,
                        onValueChange = onDescriptionChange,
                        label = { Text("描述") }
                    )
                    if (uiState.editor.validationMessage != null) {
                        Text(uiState.editor.validationMessage)
                    }
                }
            },
            confirmButton = { Button(onClick = onConfirmSave) { Text("保存") } },
            dismissButton = { TextButton(onClick = onDismissEditor) { Text("取消") } }
        )
    }

    if (uiState.pendingDelete != null) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("确认删除") },
            text = { Text("删除会级联清理该卡片下的问题与复习记录。该操作不可恢复。") },
            confirmButton = { Button(onClick = onConfirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("取消") } }
        )
    }
}

/**
 * 单行组件只负责展示与点击入口，避免把业务逻辑塞进列表项导致状态分散难以维护。
 */
@Composable
private fun CardSummaryRow(
    item: CardSummary,
    onOpenEditor: () -> Unit,
    onEditMeta: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.card.title)
        Text("题目：${item.questionCount}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onOpenEditor) { Text("题目") }
            TextButton(onClick = onEditMeta) { Text("编辑") }
            TextButton(onClick = onArchive) { Text("归档") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
