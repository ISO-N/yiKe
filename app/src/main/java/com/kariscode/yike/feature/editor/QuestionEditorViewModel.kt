package com.kariscode.yike.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 编辑页 ViewModel 负责把“表单草稿 -> 领域模型 -> 仓储写入”收敛到单一位置，
 * 避免页面层直接处理 dueAt 初始化与调度字段，确保规则可复用且可测试。
 */
class QuestionEditorViewModel(
    private val cardId: String,
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        QuestionEditorUiState(
            cardId = cardId,
            deckId = deckId,
            isLoading = true,
            title = "",
            description = "",
            questions = emptyList(),
            hasUnsavedChanges = false,
            isSaving = false,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<QuestionEditorUiState> = _uiState.asStateFlow()

    private var loadedCard: Card? = null
    private var loadedQuestionsById: Map<String, Question> = emptyMap()
    private val deletedQuestionIds = linkedSetOf<String>()

    init {
        /**
         * 首版用一次性加载方式避免“数据库流更新覆盖本地草稿”的复杂冲突，
         * 等编辑体验稳定后再考虑增量同步。
         */
        viewModelScope.launch { reloadFromStorage() }
    }

    /**
     * 标题是必填字段，因此变更需要清空错误提示，避免用户修正后仍看到旧错误。
     */
    fun onTitleChange(value: String) {
        updateDirtyState { state ->
            state.copy(title = value)
        }
    }

    /**
     * 描述不参与必填校验，但仍需要清空错误提示以避免用户误以为保存仍失败。
     */
    fun onDescriptionChange(value: String) {
        updateDirtyState { state ->
            state.copy(description = value)
        }
    }

    /**
     * 新增问题草稿用临时 ID 区分，以便在保存前支持删除、编辑等操作而不依赖数据库主键生成。
     */
    fun onAddQuestionClick() {
        val tempId = "temp_${UUID.randomUUID()}"
        updateDirtyState { state ->
            state.copy(
                questions = state.questions + QuestionDraft(
                    id = tempId,
                    prompt = "",
                    answer = "",
                    isNew = true
                )
            )
        }
    }

    /**
     * 题面是必填字段，因此变更需要清除题面校验提示以便用户逐步修正。
     */
    fun onQuestionPromptChange(questionId: String, value: String) {
        updateQuestionDraft(questionId) { draft ->
            draft.copy(prompt = value, validationMessage = null)
        }
    }

    /**
     * 答案允许为空，但仍作为可编辑字段进入草稿，以确保保存行为可预测。
     */
    fun onQuestionAnswerChange(questionId: String, value: String) {
        updateQuestionDraft(questionId) { draft ->
            draft.copy(answer = value, validationMessage = null)
        }
    }

    /**
     * 删除问题分为两类：已存在的问题需要记录待删除 ID；新草稿则直接移除即可。
     */
    fun onDeleteQuestionClick(questionId: String) {
        val current = _uiState.value.questions
        val draft = current.firstOrNull { it.id == questionId } ?: return
        if (!draft.isNew) deletedQuestionIds.add(questionId)
        updateDirtyState { state ->
            state.copy(questions = current.filterNot { q -> q.id == questionId })
        }
    }

    /**
     * 保存动作会统一校验卡片标题与所有问题题面，并在通过后执行写入；
     * 这样才能保证“多问题编辑”不会出现只保存了一半的问题。
     */
    fun onSaveClick() {
        val state = _uiState.value
        val trimmedTitle = state.title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "卡片标题不能为空") }
            return
        }

        val withPromptValidation = state.questions.map { draft ->
            val trimmedPrompt = draft.prompt.trim()
            if (trimmedPrompt.isBlank()) draft.copy(validationMessage = "题面不能为空") else draft
        }
        if (withPromptValidation.any { it.validationMessage != null }) {
            _uiState.update { it.copy(questions = withPromptValidation, errorMessage = "请修正校验错误后再保存") }
            return
        }

        viewModelScope.launch {
            val card = loadedCard
            if (card == null) {
                _uiState.update { it.copy(errorMessage = "卡片不存在或加载失败") }
                return@launch
            }

            _uiState.update { it.copy(isSaving = true, errorMessage = null, message = null) }

            runCatching {
                val now = timeProvider.nowEpochMillis()
                val settings = appSettingsRepository.observeSettings().first()
                val initialDueAt = InitialDueAtCalculator.compute(
                    nowEpochMillis = now,
                    reminderHour = settings.dailyReminderHour,
                    reminderMinute = settings.dailyReminderMinute
                )

                val updatedCard = card.copy(
                    title = trimmedTitle,
                    description = state.description,
                    updatedAt = now
                )
                cardRepository.upsert(updatedCard)

                val toUpsert = state.questions.map { draft ->
                    if (draft.isNew) {
                        Question(
                            id = "q_${UUID.randomUUID()}",
                            cardId = cardId,
                            prompt = draft.prompt.trim(),
                            answer = draft.answer,
                            tags = emptyList(),
                            status = QuestionStatus.ACTIVE,
                            stageIndex = 0,
                            dueAt = initialDueAt,
                            lastReviewedAt = null,
                            reviewCount = 0,
                            lapseCount = 0,
                            createdAt = now,
                            updatedAt = now
                        )
                    } else {
                        val original = loadedQuestionsById[draft.id]
                        if (original == null) {
                            Question(
                                id = draft.id,
                                cardId = cardId,
                                prompt = draft.prompt.trim(),
                                answer = draft.answer,
                                tags = emptyList(),
                                status = QuestionStatus.ACTIVE,
                                stageIndex = 0,
                                dueAt = initialDueAt,
                                lastReviewedAt = null,
                                reviewCount = 0,
                                lapseCount = 0,
                                createdAt = now,
                                updatedAt = now
                            )
                        } else {
                            original.copy(
                                prompt = draft.prompt.trim(),
                                answer = draft.answer,
                                updatedAt = now
                            )
                        }
                    }
                }

                questionRepository.upsertAll(toUpsert)
                deletedQuestionIds.forEach { id -> questionRepository.delete(id) }
                deletedQuestionIds.clear()
                reloadFromStorage()
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, hasUnsavedChanges = false, message = "已保存") }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = null,
                        errorMessage = "保存失败，请稍后重试"
                    )
                }
            }
        }
    }

    /**
     * 重新加载是为了把“保存后的最终落库状态”回填到草稿，避免 UI 与存储发生偏差。
     */
    private suspend fun reloadFromStorage() {
        _uiState.update { it.copy(isLoading = true) }

        val card = cardRepository.findById(cardId)
        if (card == null) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "卡片不存在") }
            return
        }
        loadedCard = card

        val questions = questionRepository.listByCard(cardId)
        loadedQuestionsById = questions.associateBy { it.id }

        _uiState.update {
            it.copy(
                isLoading = false,
                title = card.title,
                description = card.description,
                questions = questions.map { q ->
                    QuestionDraft(
                        id = q.id,
                        prompt = q.prompt,
                        answer = q.answer,
                        isNew = false,
                        validationMessage = null
                    )
                },
                hasUnsavedChanges = false,
                message = null,
                errorMessage = null
            )
        }
    }

    /**
     * 输入后统一标记脏状态并清空旧反馈，是为了让各字段编辑遵循同一套“可继续修正”的交互规则。
     */
    private fun updateDirtyState(transform: (QuestionEditorUiState) -> QuestionEditorUiState) {
        _uiState.update { state ->
            transform(state).copy(
                hasUnsavedChanges = true,
                message = null,
                errorMessage = null
            )
        }
    }

    /**
     * 问题草稿更新集中到单点，是为了把按 id 定位并重建 `questions` 列表的模板从各个输入入口里移除。
     */
    private fun updateQuestionDraft(
        questionId: String,
        transform: (QuestionDraft) -> QuestionDraft
    ) {
        updateDirtyState { state ->
            state.copy(
                questions = state.questions.map { draft ->
                    if (draft.id != questionId) draft else transform(draft)
                }
            )
        }
    }

    companion object {
        /**
         * 工厂通过路由参数注入 cardId/deckId，并注入仓储与时间依赖，
         * 以保持 ViewModel 与 Android framework 解耦。
         */
        fun factory(
            cardId: String,
            deckId: String?,
            cardRepository: CardRepository,
            questionRepository: QuestionRepository,
            appSettingsRepository: AppSettingsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            QuestionEditorViewModel(
                cardId = cardId,
                deckId = deckId,
                cardRepository = cardRepository,
                questionRepository = questionRepository,
                appSettingsRepository = appSettingsRepository,
                timeProvider = timeProvider
            )
        }
    }
}
