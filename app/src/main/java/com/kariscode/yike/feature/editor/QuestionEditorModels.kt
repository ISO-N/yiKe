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
    val validationMessage: String?
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
    val isSaving: Boolean,
    val message: String?,
    val errorMessage: String?
)

