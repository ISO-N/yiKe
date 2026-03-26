package com.kariscode.yike.feature.editor

/**
 * 编辑页 reducer 把“事件 -> 状态”映射收口为纯函数，是为了让脏状态标记和草稿保存调度仍留在 ViewModel，
 * 同时避免输入事件到处散落 `copy(...)` 细节而逐渐产生字段遗漏。
 */
internal object QuestionEditorStateReducer {
    /**
     * 标题变更只改动标题字段，是为了让“标脏 + 调度自动保存”的策略由 ViewModel 统一控制。
     */
    fun titleChanged(state: QuestionEditorUiState, value: String): QuestionEditorUiState =
        state.copy(title = value)

    /**
     * 描述变更保持与标题一致的更新粒度，是为了让不同字段的编辑体验在保存语义上保持对齐。
     */
    fun descriptionChanged(state: QuestionEditorUiState, value: String): QuestionEditorUiState =
        state.copy(description = value)

    /**
     * 新增题目以追加方式生成草稿，是为了保持用户输入顺序与 UI 列表顺序一致。
     */
    fun questionAdded(state: QuestionEditorUiState, draft: QuestionDraft): QuestionEditorUiState =
        state.copy(questions = state.questions + draft)

    /**
     * 更新单个题目草稿时按 id 精确替换，是为了避免在多题编辑时把其它输入误覆盖。
     */
    fun questionUpdated(
        state: QuestionEditorUiState,
        questionId: String,
        transform: (QuestionDraft) -> QuestionDraft
    ): QuestionEditorUiState =
        state.copy(
            questions = state.questions.map { draft ->
                if (draft.id == questionId) transform(draft) else draft
            }
        )

    /**
     * 删除题目草稿只从列表剔除，是为了把“是否需要落库删除”留给 ViewModel 管理（需要区分新草稿与已存在题目）。
     */
    fun questionDeleted(state: QuestionEditorUiState, questionId: String): QuestionEditorUiState =
        state.copy(questions = state.questions.filterNot { draft -> draft.id == questionId })
}

