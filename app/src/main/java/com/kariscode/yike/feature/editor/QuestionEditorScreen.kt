package com.kariscode.yike.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
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
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 编辑页将来会承载“卡片信息 + 多问题编辑”的复杂表单状态；
 * 先建立页面壳可以让后续的表单状态机与保存用例有固定承载点，避免把校验规则散落到 Composable。
 */
@Composable
fun QuestionEditorScreen(
    cardId: String,
    deckId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<QuestionEditorViewModel>(
        factory = QuestionEditorViewModel.factory(
            cardId = cardId,
            deckId = deckId,
            cardRepository = container.cardRepository,
            questionRepository = container.questionRepository,
            appSettingsRepository = container.appSettingsRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "问题编辑",
                navigationAction = NavigationAction(label = "返回", onClick = onBack)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                Text("加载中…")
                return@Column
            }

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text("卡片标题") },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("卡片描述") }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::onAddQuestionClick) {
                    Text("新增问题")
                }
                Button(onClick = viewModel::onSaveClick, enabled = !uiState.isSaving) {
                    Text(if (uiState.isSaving) "保存中…" else "保存")
                }
            }

            uiState.errorMessage?.let { Text(it) }
                ?: uiState.message?.let { Text(it) }

            if (uiState.questions.isEmpty()) {
                Text("暂无问题。点击“新增问题”开始录入。")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(items = uiState.questions, key = { it.id }) { q ->
                        QuestionDraftItem(
                            draft = q,
                            onPromptChange = { value -> viewModel.onQuestionPromptChange(q.id, value) },
                            onAnswerChange = { value -> viewModel.onQuestionAnswerChange(q.id, value) },
                            onDelete = { viewModel.onDeleteQuestionClick(q.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个问题草稿组件保持轻量，避免在列表中引入复杂状态导致编辑交互难以维护。
 */
@Composable
private fun QuestionDraftItem(
    draft: QuestionDraft,
    onPromptChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.prompt,
            onValueChange = onPromptChange,
            label = { Text("题面") }
        )
        OutlinedTextField(
            value = draft.answer,
            onValueChange = onAnswerChange,
            label = { Text("答案（可空）") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onDelete) { Text("删除") }
            if (draft.validationMessage != null) {
                Text(draft.validationMessage)
            }
        }
    }
}
