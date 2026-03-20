package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.QuestionEditorDraftLoadResult
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot

/**
 * 问题编辑草稿仓储只服务于编辑页恢复体验，是为了把“本地临时状态”的持久化边界和正式业务仓储隔离开。
 */
interface QuestionEditorDraftRepository {
    /**
     * 读取草稿时返回损坏标记而不是直接抛给页面，
     * 是为了让 UI 可以安全回退到正式内容，并给出明确但不阻塞的提示。
     */
    suspend fun loadDraft(cardId: String): QuestionEditorDraftLoadResult

    /**
     * 草稿保存只记录当前编辑快照，是为了保证自动保存与手动保存都遵循完全一致的恢复语义。
     */
    suspend fun saveDraft(snapshot: QuestionEditorDraftSnapshot)

    /**
     * 正式保存成功或用户明确放弃草稿后需要删除本地快照，
     * 否则旧草稿会在下次进入页面时继续干扰正式内容。
     */
    suspend fun deleteDraft(cardId: String)
}
