package com.kariscode.yike.feature.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.CollectFlowEffect
import com.kariscode.yike.ui.component.YikeAlertDialog
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeDangerConfirmationDialog
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikeLoadingBanner
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeInlineErrorMessage
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.format.formatPreviewDateTime
import com.kariscode.yike.ui.theme.LocalYikeSpacing
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 问题编辑页在正式保存之外补上本地草稿恢复，是为了把长时间编辑从“只能一次完成”放宽为“可中断后继续”。
 */
@Composable
fun QuestionEditorScreen(
    cardId: String,
    deckId: String?,
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val viewModel = koinViewModel<QuestionEditorViewModel>(
        parameters = { parametersOf(cardId, deckId) }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    /**
     * 返回键和顶部返回统一走 ViewModel，是为了确保导航离开前始终先执行同一套草稿补存语义。
     */
    BackHandler(onBack = viewModel::onExitAttempt)

    /**
     * 返回导航通过 effect 发出，是为了让“先补存，再离开”不会在状态里留下额外的一次性标记。
     */
    CollectFlowEffect(effectFlow = viewModel.effects) { effect ->
        when (effect) {
            QuestionEditorEffect.NavigateBack -> navigator.back()
        }
    }

    /**
     * 页面进入后台时触发补存，是为了覆盖系统回收和用户切应用前那段来不及等防抖结束的输入窗口。
     */
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    YikeFlowScaffold(
        title = "编辑卡片",
        subtitle = "先把卡片信息写清楚，再逐条维护问题和答案。",
        navigationAction = backNavigationAction(onClick = viewModel::onExitAttempt),
        actionText = if (uiState.isDraftSaving) "草稿保存中" else "保存草稿",
        onActionClick = if (uiState.hasPendingDraftChanges && !uiState.isSaving && !uiState.isDraftSaving) {
            viewModel::onSaveDraftClick
        } else {
            null
        }
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
            modifier = modifier,
            contentPadding = padding
        )
    }

    if (uiState.restoreDraftDialogVisible) {
        QuestionEditorRestoreDraftDialog(
            info = uiState.restoreDraftInfo,
            onRestore = viewModel::onRestoreDraftConfirm,
            onDiscard = viewModel::onDiscardDraftConfirm
        )
    }
}

/**
 * 编辑页主体独立出来后，可以直接验证加载、恢复、正式保存和本地草稿反馈等关键状态组合。
 */
@Composable
internal fun QuestionEditorContent(
    uiState: QuestionEditorUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddQuestion: () -> Unit,
    onSave: () -> Unit,
    onPromptChange: (String, String) -> Unit,
    onAnswerChange: (String, String) -> Unit,
    onDeleteQuestion: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    var pendingDeleteQuestionId by rememberSaveable { mutableStateOf<String?>(null) }
    YikeScrollableColumn(
        modifier = modifier.imePadding(),
        contentPadding = contentPadding
    ) {
        when {
            uiState.isLoading -> {
                YikeLoadingBanner(
                    title = "正在加载卡片内容",
                    description = "稍等一下，我们会把当前卡片、问题列表和可恢复草稿一起准备好。"
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
                    onDeleteQuestion = { questionId -> pendingDeleteQuestionId = questionId }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    YikeSecondaryButton(
                        text = "添加问题",
                        onClick = onAddQuestion,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving && !uiState.isDraftSaving
                    )
                    YikePrimaryButton(
                        text = if (uiState.isSaving) "保存中…" else "保存修改",
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !uiState.isSaving && !uiState.isDraftSaving
                    )
                }
            }
        }
    }
    val pendingDeleteDraft = uiState.questions.firstOrNull { draft -> draft.id == pendingDeleteQuestionId }
    if (pendingDeleteDraft != null) {
        YikeDangerConfirmationDialog(
            title = if (pendingDeleteDraft.isNew) "删除这条草稿问题？" else "删除这条正式问题？",
            description = if (pendingDeleteDraft.isNew) {
                "这会移除当前卡片中的这条草稿问题，不会影响其他问题。"
            } else {
                "这会在保存修改时删除当前卡片中的这条正式问题，不会删除整张卡片。"
            },
            confirmText = if (pendingDeleteDraft.isNew) "删除草稿问题" else "删除正式问题",
            onDismiss = { pendingDeleteQuestionId = null },
            onConfirm = {
                onDeleteQuestion(pendingDeleteDraft.id)
                pendingDeleteQuestionId = null
            }
        )
    }
}

/**
 * 反馈区把正式保存、草稿保存和恢复提示汇总到一处，是为了让用户始终知道“当前是本地暂存还是已正式生效”。
 */
@Composable
private fun QuestionEditorFeedback(
    uiState: QuestionEditorUiState
) {
    val statusText = when {
        uiState.errorMessage != null -> "保存前还需要处理"
        uiState.isDraftSaving -> "草稿保存中"
        uiState.hasUnsavedChanges && !uiState.hasPendingDraftChanges && uiState.lastDraftSavedAt != null -> "草稿已保存到本机"
        uiState.hasUnsavedChanges -> "有未正式保存修改"
        uiState.message != null -> "状态已更新"
        else -> null
    } ?: return

    val detailText = when {
        uiState.errorMessage != null -> uiState.errorMessage
        uiState.isDraftSaving -> "我们会把最新修改安全留在本机，下次回来可以继续编辑。"
        uiState.hasUnsavedChanges && !uiState.hasPendingDraftChanges && uiState.lastDraftSavedAt != null -> {
            "上次保存于 ${formatPreviewDateTime(uiState.lastDraftSavedAt)}，仍需点击“保存修改”才会正式生效。"
        }
        uiState.hasUnsavedChanges -> "当前修改会自动落到本机草稿，但还没有正式写入卡片。"
        else -> uiState.message
    }

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            YikeHeaderBlock(
                eyebrow = "编辑状态",
                title = statusText,
                subtitle = detailText.orEmpty()
            )
            YikeBadge(
                text = when {
                    uiState.errorMessage != null -> "需处理"
                    uiState.isDraftSaving -> "保存中"
                    uiState.hasUnsavedChanges -> "未正式提交"
                    else -> "已同步"
                }
            )
        }
    }
}

/**
 * 草稿恢复弹窗要求用户显式决策，是为了避免旧草稿在进入页面时悄悄覆盖数据库中的正式内容。
 */
@Composable
internal fun QuestionEditorRestoreDraftDialog(
    info: QuestionEditorRestoreDraftInfo?,
    onRestore: () -> Unit,
    onDiscard: () -> Unit
) {
    YikeAlertDialog(
        onDismissRequest = {},
        title = { Text("发现未提交草稿") },
        text = {
            Text(
                buildRestoreDraftDescription(info = info)
            )
        },
        confirmButton = {
            YikePrimaryButton(
                text = "恢复草稿",
                onClick = onRestore
            )
        },
        dismissButton = {
            YikeSecondaryButton(
                text = "丢弃草稿",
                onClick = onDiscard
            )
        }
    )
}

/**
 * 恢复说明在弹窗前统一拼装，是为了把时间、问题数和待删除数量稳定表达成一条自然语句。
 */
private fun buildRestoreDraftDescription(
    info: QuestionEditorRestoreDraftInfo?
): String {
    if (info == null) {
        return "这张卡片有一份上次未提交的草稿。你可以恢复继续编辑，也可以丢弃后从正式内容开始。"
    }
    return buildString {
        append("这张卡片在 ")
        append(formatPreviewDateTime(info.savedAt))
        append(" 留有一份草稿，包含 ")
        append(info.questionCount)
        append(" 条问题")
        if (info.deletedQuestionCount > 0) {
            append("，以及 ")
            append(info.deletedQuestionCount)
            append(" 个待删除问题")
        }
        append("。你可以恢复继续编辑，也可以丢弃后使用正式内容。")
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
            eyebrow = "卡片信息",
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
            eyebrow = "问题草稿",
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
                eyebrow = "问题 $index",
                title = "问题 $index",
                subtitle = "先保证题面明确，再补答案。"
            )
            YikeBadge(text = if (draft.isNew) "未正式保存" else "已存在")
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
            YikeInlineErrorMessage(message = message)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            YikeSecondaryButton(
                text = if (draft.isNew) "删除这条草稿问题" else "删除这条正式问题",
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
