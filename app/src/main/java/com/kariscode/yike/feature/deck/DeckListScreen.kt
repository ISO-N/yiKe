package com.kariscode.yike.feature.deck

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
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 卡组列表页在首版先落导航与页面壳，目的是让后续的数据层接入可以沿着固定路由逐步替换占位状态，
 * 避免一边写 Room/Repository，一边临时改路由导致返工。
 */
@Composable
fun DeckListScreen(
    onBack: () -> Unit,
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

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "卡组列表",
                navigationAction = NavigationAction(label = "返回", onClick = onBack)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onCreateDeckClick) {
                Text("新建")
            }
        },
        modifier = modifier
    ) { padding ->
        DeckListContent(
            uiState = uiState,
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * 将页面内容拆分为独立 Composable 可以让 UI 与导航壳解耦，
 * 未来接入更多状态（空/错/加载）时不需要改动顶层 Scaffold 结构。
 */
@Composable
private fun DeckListContent(
    uiState: DeckListUiState,
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
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (uiState.isLoading) {
            Text("加载中…")
        } else if (uiState.items.isEmpty()) {
            Text("暂无卡组。点击右下角“新建”创建第一个卡组。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items = uiState.items, key = { it.deck.id }) { item ->
                    DeckSummaryRow(
                        item = item,
                        onOpen = { onOpenDeck(item.deck.id) },
                        onEdit = { onEditDeck(item) },
                        onArchive = { onToggleArchive(item) },
                        onDelete = { onDeleteDeck(item) }
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
            title = { Text(if (uiState.editor.deckId == null) "新建卡组" else "编辑卡组") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = uiState.editor.name,
                        onValueChange = onNameChange,
                        label = { Text("名称") },
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
            text = {
                Text("删除会级联清理该卡组下的卡片、问题与复习记录。该操作不可恢复。")
            },
            confirmButton = { Button(onClick = onConfirmDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("取消") } }
        )
    }
}

/**
 * 单行展示组件保持最小职责：只负责展示与触发点击，
 * 这样后续替换为更复杂的 DesignSystem 组件时不会影响页面状态结构。
 */
@Composable
private fun DeckSummaryRow(
    item: DeckSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(item.deck.name)
        Text("卡片：${item.cardCount}  题目：${item.questionCount}")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onOpen) { Text("进入") }
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(onClick = onArchive) { Text("归档") }
            TextButton(onClick = onDelete) { Text("删除") }
        }
    }
}
