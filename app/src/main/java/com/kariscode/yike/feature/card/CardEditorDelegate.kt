package com.kariscode.yike.feature.card

import androidx.lifecycle.ViewModel
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.SuccessMessages
import com.kariscode.yike.core.ui.viewmodel.launchStateResult
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.usecase.CardSaveRequest
import com.kariscode.yike.domain.usecase.CardSaveResult
import com.kariscode.yike.domain.usecase.SaveCardUseCase
import com.kariscode.yike.feature.common.TextMetadataDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * 卡片编辑器逻辑独立成 delegate，是为了把“草稿状态机 + 保存校验”从列表页主流程里剥离，
 * 让 CardListViewModel 更专注于“加载列表/响应删除/触发摘要刷新”等协调职责。
 */
internal class CardEditorDelegate(
    private val deckId: String,
    private val saveCardUseCase: SaveCardUseCase,
    private val state: MutableStateFlow<CardListUiState>,
    private val viewModel: ViewModel
) {
    /**
     * 新建入口统一走同一份草稿结构，是为了让保存校验只维护一套逻辑。
     */
    fun onCreateCardClick() {
        openEditor(TextMetadataDraft(entityId = null, primaryValue = "", secondaryValue = ""))
    }

    /**
     * 编辑入口把现有字段写入草稿，是为了避免 UI 自己再维护一份副本而出现写回错位。
     */
    fun onEditCardClick(item: CardSummary) {
        openEditor(
            TextMetadataDraft(
                entityId = item.card.id,
                primaryValue = item.card.title,
                secondaryValue = item.card.description
            )
        )
    }

    /**
     * 标题属于必填字段，输入变更时清理旧校验提示可以避免“已修复但仍显示错误”的错觉。
     */
    fun onDraftTitleChange(value: String) {
        updateEditor { it.updatePrimaryValue(value) }
    }

    /**
     * 描述不参与必填校验，但仍需纳入草稿，以保证保存读取到一致快照。
     */
    fun onDraftDescriptionChange(value: String) {
        updateEditor { it.updateSecondaryValue(value) }
    }

    /**
     * 关闭编辑器直接丢弃草稿，是为了明确传达“未保存不落库”的交互语义。
     */
    fun onDismissEditor() {
        state.update(CardListStateReducer::dismissEditor)
    }

    /**
     * 保存时统一注入 deckId 与时间语义，是为了让创建/编辑都落在同一条仓储写入路径上。
     */
    fun onConfirmSave() {
        val editor = state.value.editor ?: return
        val trimmedTitle = editor.primaryValue.trim()
        if (trimmedTitle.isBlank()) {
            state.update { current ->
                CardListStateReducer.updateEditor(current) {
                    it.withValidationMessage(ErrorMessages.TITLE_REQUIRED)
                }
            }
            return
        }

        viewModel.launchStateResult(state = state) {
            action {
                saveCardUseCase(
                    CardSaveRequest(
                        cardId = editor.entityId,
                        deckId = deckId,
                        title = trimmedTitle,
                        description = editor.secondaryValue
                    )
                )
            }
            onSuccess { current, result ->
                val successMessage = if (result is CardSaveResult.Created) {
                    SuccessMessages.CARD_CREATED
                } else {
                    SuccessMessages.CARD_UPDATED
                }
                CardListStateReducer.saveSucceeded(
                    state = current,
                    successMessage = successMessage
                )
            }
            onFailure { current, _ ->
                CardListStateReducer.mutationFailed(current, ErrorMessages.SAVE_FAILED)
            }
        }
    }

    /**
     * 打开编辑器时统一清空旧反馈，是为了让创建和编辑都从同一个干净状态开始。
     */
    private fun openEditor(editor: TextMetadataDraft) {
        state.update { current -> CardListStateReducer.openEditor(current, editor) }
    }

    /**
     * 草稿更新集中在单点，是为了避免多个输入入口重复 editor 判空模板并漏掉校验提示清理。
     */
    private fun updateEditor(transform: (TextMetadataDraft) -> TextMetadataDraft) {
        state.update { current -> CardListStateReducer.updateEditor(current, transform) }
    }
}

