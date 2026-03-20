package com.kariscode.yike.domain.model

/**
 * 编辑页草稿快照独立于正式 Question/Card 模型，是为了让“尚未提交”的输入能够本地恢复，
 * 同时不把临时状态误当成正式业务数据参与同步、备份或调度。
 */
data class QuestionEditorDraftSnapshot(
    val cardId: String,
    val title: String,
    val description: String,
    val questions: List<QuestionEditorDraftItemSnapshot>,
    val deletedQuestionIds: List<String>,
    val savedAt: Long
)

/**
 * 单条问题草稿拆成独立快照项，是为了让新增题目的临时 ID 和删除前的未完成输入都能稳定往返持久化。
 */
data class QuestionEditorDraftItemSnapshot(
    val id: String,
    val prompt: String,
    val answer: String,
    val isNew: Boolean
)

/**
 * 草稿读取结果显式携带“是否发现损坏文件”，是为了让页面在安全回退到正式内容的同时，
 * 仍能向用户解释为什么没有恢复出上一份草稿。
 */
data class QuestionEditorDraftLoadResult(
    val draft: QuestionEditorDraftSnapshot?,
    val wasCorrupted: Boolean
)
