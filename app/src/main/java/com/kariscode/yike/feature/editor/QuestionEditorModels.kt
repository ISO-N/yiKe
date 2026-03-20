package com.kariscode.yike.feature.editor

/**
 * 问题草稿以 UI 状态形式存在，是为了让“未保存修改”不直接污染数据库，
 * 同时保证多问题编辑可以一次性校验与保存。
 */
data class QuestionDraft(
    val id: String,
    val prompt: String,
    val answer: String,
    val isNew: Boolean,
    val validationMessage: String? = null
)

/**
 * 恢复提示单独抽成摘要，是为了让页面在不提前覆盖正式内容的前提下，
 * 仍能把“这份草稿大概是什么”准确传达给用户。
 */
data class QuestionEditorRestoreDraftInfo(
    val savedAt: Long,
    val questionCount: Int,
    val deletedQuestionCount: Int
)

/**
 * 编辑页状态集中管理卡片字段与问题草稿，原因是：
 * - 标题/题面校验必须在同一保存动作内完成
 * - 删除/新增问题需要明确的本地草稿语义
 */
data class QuestionEditorUiState(
    val cardId: String,
    val deckId: String?,
    val isLoading: Boolean,
    val title: String,
    val description: String,
    val questions: List<QuestionDraft>,
    val hasUnsavedChanges: Boolean,
    val hasPendingDraftChanges: Boolean,
    val isSaving: Boolean,
    val isDraftSaving: Boolean,
    val lastDraftSavedAt: Long?,
    val restoreDraftDialogVisible: Boolean,
    val restoreDraftInfo: QuestionEditorRestoreDraftInfo?,
    val message: String?,
    val errorMessage: String?
)

