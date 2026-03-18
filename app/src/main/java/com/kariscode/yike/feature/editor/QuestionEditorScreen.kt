package com.kariscode.yike.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 问题编辑页属于流内页面，因此保持聚焦式顶部返回与保存动作，不接入一级底部导航。
 */
@Composable
fun QuestionEditorScreen(
    cardId: String,
    deckId: String?,
    navigator: YikeNavigator,
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "编辑卡片",
        subtitle = "先把卡片信息写清楚，再逐条维护问题和答案。",
        navigationAction = backNavigationAction(navigator::back),
        actionText = if (uiState.isSaving) "保存中" else "保存",
        onActionClick = if (uiState.isSaving) null else viewModel::onSaveClick
    ) { padding ->
        QuestionEditorContent(
            uiState = uiState,
            onTitleChange = viewModel::onTitleChange,
            onDescriptionChange = viewModel::onDescriptionChange,
            onAddQuestion = viewModel::onAddQuestionClick,
            onSave = viewModel::onSaveClick,
            onPromptChange = viewModel::onQuestionPromptChange,
            onAnswerChange = viewModel::onQuestionAnswerChange,
            onDeleteQuestion = viewModel::onDeleteQuestionClick,
            modifier = modifier.padding(padding)
        )
    }
}

/**
 * 编辑页主体独立出来后，可以直接验证未保存、校验失败和保存成功等关键反馈状态。
 */
@Composable
private fun QuestionEditorContent(
    uiState: QuestionEditorUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onSave: () -> Unit,
    onPromptChange: (String, String) -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    YikeScrollableColumn(modifier = modifier) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在加载卡片内容",
                    description = "稍等一下，我们会把当前卡片和问题草稿载入到编辑器。"
                )
            }

            else -> {
                QuestionEditorFeedback(uiState = uiState)
                CardInfoSection(
                    title = uiState.title,
                    description = uiState.description,
                    onTitleChange = onTitleChange,
                    onDescriptionChange = onDescriptionChange
                )
                QuestionDraftSection(
                    drafts = uiState.questions,
                    onAddQuestion = onAddQuestion,
                    onPromptChange = onPromptChange,
                    onAnswerChange = onAnswerChange,
                    onDeleteQuestion = onDeleteQuestion
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    YikeSecondaryButton(
                        text = "添加问题",
                        onClick = onAddQuestion,
                        modifier = Modifier.weight(1f)
                    )
                    YikePrimaryButton(
                        text = if (uiState.isSaving) "保存中…" else "保存修改",
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving
                    )
                }
            }
        }
    }
}

/**
 * 反馈区把未保存、校验失败和保存结果压到同一层级，
 * 这样用户不需要在页面不同角落猜测当前编辑状态。
 */
@Composable
private fun QuestionEditorFeedback(
    uiState: QuestionEditorUiState
) {
    when {
        uiState.errorMessage != null -> {
            YikeStateBanner(
                title = "保存前还需要修正",
                description = uiState.errorMessage
            )
        }

        uiState.message != null -> {
            YikeStateBanner(
                title = "保存成功",
                description = uiState.message
            )
        }

        uiState.hasUnsavedChanges -> {
            YikeStateBanner(
                title = "有未保存修改",
                description = "你刚刚更新了卡片信息或问题草稿，记得保存后再离开。"
            )
        }
    }
}

/**
 * 卡片信息区与问题草稿区拆开后，用户能更快区分“这是卡片元信息”还是“这是具体复习问题”。
 */
@Composable
private fun CardInfoSection(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard {
        YikeHeaderBlock(
            eyebrow = "Card Info",
            title = if (title.isBlank()) "先给卡片起个标题" else title,
            subtitle = "卡片标题和说明越明确，复习时越容易快速进入语境。"
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                label = { Text("卡片标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                label = { Text("简短说明") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        }
    }
}

/**
 * 问题草稿区独立承载新增、删除和逐题校验，是为了让多问题编辑保持明确的局部反馈。
 */
@Composable
private fun QuestionDraftSection(
    drafts: List<QuestionDraft>,
    onAddQuestion: () -> Unit,
    onPromptChange: (String, String) -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onDeleteQuestion: (String) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        YikeHeaderBlock(
            eyebrow = "Question Drafts",
            title = "问题草稿",
            subtitle = "题面不能为空，答案允许先留空，后续复习会显示“无答案”。"
        )
        if (drafts.isEmpty()) {
            YikeStateBanner(
                title = "还没有问题草稿",
                description = "先添加第一条问题，把卡片真正变成可复习的内容。"
            ) {
                YikePrimaryButton(
                    text = "添加问题",
                    onClick = onAddQuestion,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            drafts.forEachIndexed { index, draft ->
                QuestionDraftCard(
                    index = index + 1,
                    draft = draft,
                    onPromptChange = { value -> onPromptChange(draft.id, value) },
                    onAnswerChange = { value -> onAnswerChange(draft.id, value) },
                    onDelete = { onDeleteQuestion(draft.id) }
                )
            }
        }
    }
}

/**
 * 单条问题草稿卡片承载题面、答案和删除动作，是为了让每条输入都拥有就近反馈而不是堆成一长页。
 */
@Composable
private fun QuestionDraftCard(
    index: Int,
    draft: QuestionDraft,
    onPromptChange: (String) -> Unit,
    onAnswerChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            YikeHeaderBlock(
                eyebrow = "Question $index",
                title = "问题 $index",
                subtitle = "先保证题面明确，再补答案。"
            )
            YikeBadge(text = if (draft.isNew) "未保存" else "已存在")
        }
        OutlinedTextField(
            value = draft.prompt,
            onValueChange = onPromptChange,
            label = { Text("题面") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        OutlinedTextField(
            value = draft.answer,
            onValueChange = onAnswerChange,
            label = { Text("答案（可空）") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        draft.validationMessage?.let { message ->
            Text(text = message)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            YikeSecondaryButton(
                text = "删除问题",
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
