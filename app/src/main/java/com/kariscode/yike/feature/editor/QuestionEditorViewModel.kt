package com.kariscode.yike.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.coroutine.parallel3
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.SuccessMessages
import com.kariscode.yike.core.viewmodel.launchResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionEditorDraftItemSnapshot
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 编辑页 ViewModel 负责把“表单草稿 -> 本地草稿恢复 -> 领域模型 -> 仓储写入”收敛到单一位置，
 * 避免页面层直接处理草稿持久化、dueAt 初始化与删除列表回填等高耦合细节。
 */
class QuestionEditorViewModel(
    private val cardId: String,
    private val deckId: String?,
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val questionEditorDraftRepository: QuestionEditorDraftRepository,
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
            hasPendingDraftChanges = false,
            isSaving = false,
            isDraftSaving = false,
            lastDraftSavedAt = null,
            restoreDraftDialogVisible = false,
            restoreDraftInfo = null,
            message = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<QuestionEditorUiState> = _uiState.asStateFlow()

    private var loadedCard: Card? = null
    private var loadedQuestionsById: Map<String, Question> = emptyMap()
    private val deletedQuestionIds = linkedSetOf<String>()
    private var pendingRestoreDraftSnapshot: QuestionEditorDraftSnapshot? = null

    init {
        /**
         * 首次进入需要同时比对正式内容和本地草稿，
         * 才能在不静默覆盖数据库内容的前提下做出准确恢复决策。
         */
        viewModelScope.launch { reloadFromStorage() }
    }

    /**
     * 标题变更同时驱动自动保存调度，是为了让用户修订卡片元信息后即使被系统回收也能尽量续上工作。
     */
    fun onTitleChange(value: String) {
        updateDirtyState { state ->
            state.copy(title = value)
        }
    }

    /**
     * 描述进入同一套脏状态与草稿保存链路，是为了让卡片说明的恢复体验和题面编辑保持一致。
     */
    fun onDescriptionChange(value: String) {
        updateDirtyState { state ->
            state.copy(description = value)
        }
    }

    /**
     * 新题目先生成临时草稿 ID，是为了让尚未正式落库的问题也能被自动保存和再次恢复。
     */
    fun onAddQuestionClick() {
        val tempId = EntityIds.newTempDraftId()
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
     * 题面改动会清空旧校验提示，是为了避免自动保存后的局部修正仍被过期错误状态遮住。
     */
    fun onQuestionPromptChange(questionId: String, value: String) {
        updateQuestionDraft(questionId) { draft ->
            draft.copy(prompt = value, validationMessage = null)
        }
    }

    /**
     * 答案允许为空但仍应参与草稿恢复，是为了兼容“先录题面、稍后补答案”的真实编辑节奏。
     */
    fun onQuestionAnswerChange(questionId: String, value: String) {
        updateQuestionDraft(questionId) { draft ->
            draft.copy(answer = value, validationMessage = null)
        }
    }

    /**
     * 删除分成“待正式删除”和“直接丢弃新草稿”两类记录，是为了让恢复后的正式保存仍能保持一次提交的语义。
     */
    fun onDeleteQuestionClick(questionId: String) {
        val currentDraft = _uiState.value.questions.firstOrNull { draft -> draft.id == questionId } ?: return
        if (!currentDraft.isNew) {
            deletedQuestionIds.add(questionId)
        }
        updateDirtyState { state ->
            state.copy(
                questions = state.questions.filterNot { draft -> draft.id == questionId }
            )
        }
    }

    /**
     * 手动保存草稿提供显式心理预期，是为了让用户在长时间编辑前能够主动确认“这份内容已经落在本机”。
     */
    fun onSaveDraftClick() {
        launchResult(
            action = {
                persistCurrentDraft()
            },
            onSuccess = { savedAt ->
                _uiState.update { state ->
                    state.copy(
                        isDraftSaving = false,
                        hasPendingDraftChanges = false,
                        lastDraftSavedAt = savedAt,
                        message = SuccessMessages.DRAFT_SAVED,
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update { state ->
                    state.copy(
                        isDraftSaving = false,
                        message = null,
                        errorMessage = ErrorMessages.DRAFT_SAVE_FAILED
                    )
                }
            }
        )
    }

    /**
     * 恢复草稿只在用户明确确认后执行，是为了尊重“正式内容优先展示、草稿需显式接管”的交互约定。
     */
    fun onRestoreDraftConfirm() {
        val snapshot = pendingRestoreDraftSnapshot ?: return
        deletedQuestionIds.clear()
        deletedQuestionIds.addAll(snapshot.deletedQuestionIds)
        pendingRestoreDraftSnapshot = null
        _uiState.update { state ->
            state.copy(
                title = snapshot.title,
                description = snapshot.description,
                questions = snapshot.questions.map { item -> item.toDraft() },
                hasUnsavedChanges = true,
                hasPendingDraftChanges = false,
                isDraftSaving = false,
                lastDraftSavedAt = snapshot.savedAt,
                restoreDraftDialogVisible = false,
                restoreDraftInfo = null,
                message = SuccessMessages.DRAFT_RESTORED,
                errorMessage = null
            )
        }
    }

    /**
     * 放弃草稿后立即删除本地快照，是为了避免用户已经明确拒绝的旧编辑再次反复打断页面进入。
     */
    fun onDiscardDraftConfirm() {
        launchResult(
            action = {
                questionEditorDraftRepository.deleteDraft(cardId)
            },
            onSuccess = {
                pendingRestoreDraftSnapshot = null
                _uiState.update { state ->
                    state.copy(
                        restoreDraftDialogVisible = false,
                        restoreDraftInfo = null,
                        lastDraftSavedAt = null,
                        message = null,
                        errorMessage = null
                    )
                }
            },
            onFailure = {
                _uiState.update { state ->
                    state.copy(
                        errorMessage = ErrorMessages.DRAFT_SAVE_FAILED,
                        message = null
                    )
                }
            }
        )
    }

    /**
     * 正式保存仍然守住统一校验入口，是为了避免多题编辑出现“部分写入成功、部分留在草稿里”的分叉状态。
     */
    fun onSaveClick() {
        val state = _uiState.value
        val trimmedTitle = state.title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = ErrorMessages.TITLE_REQUIRED)
            }
            return
        }
        val validatedQuestions = state.questions.map { draft ->
            if (draft.prompt.trim().isBlank()) {
                draft.copy(validationMessage = ErrorMessages.QUESTION_CONTENT_REQUIRED)
            } else {
                draft
            }
        }
        if (validatedQuestions.any { draft -> draft.validationMessage != null }) {
            _uiState.update { current ->
                current.copy(
                    questions = validatedQuestions,
                    errorMessage = ErrorMessages.VALIDATION_ERROR
                )
            }
            return
        }
        val card = loadedCard
        if (card == null) {
            _uiState.update { current ->
                current.copy(errorMessage = ErrorMessages.CARD_NOT_FOUND)
            }
            return
        }

        _uiState.update { current ->
            current.copy(
                isSaving = true,
                errorMessage = null,
                message = null
            )
        }
        launchResult(
            action = {
                persistOfficialChanges(
                    state = state,
                    card = card,
                    trimmedTitle = trimmedTitle
                )
                reloadFromStorage(successMessage = SuccessMessages.SAVED)
            },
            onSuccess = {},
            onFailure = {
                _uiState.update { current ->
                    current.copy(
                        isSaving = false,
                        isDraftSaving = false,
                        message = null,
                        errorMessage = ErrorMessages.SAVE_FAILED
                    )
                }
            }
        )
    }

    /**
     * 重新加载正式内容时同时检查本地草稿，是为了把“正式状态”和“可恢复状态”放在同一时刻决策，避免状态错位。
     */
    private suspend fun reloadFromStorage(successMessage: String? = null) {
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                isSaving = false,
                isDraftSaving = false,
                errorMessage = null,
                message = null
            )
        }
        val (card, questions, draftResult) = parallel3(
            first = { cardRepository.findById(cardId) },
            second = { questionRepository.listByCard(cardId) },
            third = { questionEditorDraftRepository.loadDraft(cardId) }
        )
        if (card == null) {
            pendingRestoreDraftSnapshot = null
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    isSaving = false,
                    isDraftSaving = false,
                    errorMessage = ErrorMessages.CARD_NOT_FOUND,
                    message = null
                )
            }
            return
        }
        loadedCard = card
        loadedQuestionsById = questions.associateBy { question -> question.id }
        deletedQuestionIds.clear()
        pendingRestoreDraftSnapshot = draftResult.draft
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                title = card.title,
                description = card.description,
                questions = questions.map { question -> question.toDraft() },
                hasUnsavedChanges = false,
                hasPendingDraftChanges = false,
                isSaving = false,
                isDraftSaving = false,
                lastDraftSavedAt = draftResult.draft?.savedAt,
                restoreDraftDialogVisible = draftResult.draft != null,
                restoreDraftInfo = draftResult.draft?.toRestoreInfo(),
                message = successMessage ?: if (draftResult.wasCorrupted) SuccessMessages.DRAFT_CORRUPTED_RESET else null,
                errorMessage = null
            )
        }
    }

    /**
     * 正式保存链路单独抽成私有入口，是为了让“保存内容”和“删除草稿”始终作为一个完整事务意图执行。
     */
    private suspend fun persistOfficialChanges(
        state: QuestionEditorUiState,
        card: Card,
        trimmedTitle: String
    ) {
        val now = timeProvider.nowEpochMillis()
        val settings = appSettingsRepository.getSettings()
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
        val questionsToUpsert = state.questions.map { draft ->
            draft.toQuestion(
                cardId = cardId,
                nowEpochMillis = now,
                initialDueAt = initialDueAt,
                original = loadedQuestionsById[draft.id]
            )
        }
        questionRepository.upsertAll(questionsToUpsert)
        questionRepository.deleteAll(deletedQuestionIds)
        questionEditorDraftRepository.deleteDraft(cardId)
        deletedQuestionIds.clear()
        pendingRestoreDraftSnapshot = null
    }

    /**
     * 输入后统一标记“既有未正式保存改动，也有待落盘草稿”，是为了让自动保存和正式保存共享同一份真实状态来源。
     */
    private fun updateDirtyState(transform: (QuestionEditorUiState) -> QuestionEditorUiState) {
        _uiState.update { state ->
            transform(state).copy(
                hasUnsavedChanges = true,
                hasPendingDraftChanges = true,
                message = null,
                errorMessage = null
            )
        }
    }

    /**
     * 问题草稿更新收口后，题面和答案输入不必各自维护按 id 重建列表与触发自动保存的重复模板。
     */
    private fun updateQuestionDraft(
        questionId: String,
        transform: (QuestionDraft) -> QuestionDraft
    ) {
        updateDirtyState { state ->
            state.copy(
                questions = state.questions.map { draft ->
                    if (draft.id == questionId) transform(draft) else draft
                }
            )
        }
    }

    /**
     * 草稿保存完成后只清空“待落盘”的标记，不清空“未正式保存”的标记，
     * 是为了让页面继续准确表达“本地可恢复”和“数据库已提交”这两个不同层次。
     */
    private suspend fun persistCurrentDraft(): Long {
        if (!shouldPersistCurrentDraft()) {
            return _uiState.value.lastDraftSavedAt ?: timeProvider.nowEpochMillis()
        }
        _uiState.update { state ->
            state.copy(
                isDraftSaving = true,
                errorMessage = null,
                message = null
            )
        }
        val snapshot = buildDraftSnapshot()
        questionEditorDraftRepository.saveDraft(snapshot)
        return snapshot.savedAt
    }

    /**
     * 草稿是否需要落盘由单点判断，是为了让自动保存、手动保存和返回补存遵循完全一致的前置条件。
     */
    private fun shouldPersistCurrentDraft(): Boolean {
        val state = _uiState.value
        return !state.isLoading &&
            !state.restoreDraftDialogVisible &&
            !state.isSaving &&
            state.hasUnsavedChanges &&
            state.hasPendingDraftChanges
    }

    /**
     * 草稿快照在 ViewModel 内统一构造，是为了把删除列表、临时题目和当前卡片元信息始终按同一口径持久化。
     */
    private fun buildDraftSnapshot(): QuestionEditorDraftSnapshot = QuestionEditorDraftSnapshot(
        cardId = cardId,
        title = _uiState.value.title,
        description = _uiState.value.description,
        questions = _uiState.value.questions.map { draft ->
            QuestionEditorDraftItemSnapshot(
                id = draft.id,
                prompt = draft.prompt,
                answer = draft.answer,
                isNew = draft.isNew
            )
        },
        deletedQuestionIds = deletedQuestionIds.toList(),
        savedAt = timeProvider.nowEpochMillis()
    )

    /**
     * 正式问题统一映射为编辑草稿，是为了让首次载入和正式保存后的回填共享同一份字段转换语义。
     */
    private fun Question.toDraft(): QuestionDraft = QuestionDraft(
        id = id,
        prompt = prompt,
        answer = answer,
        isNew = false,
        validationMessage = null
    )

    /**
     * 恢复提示摘要在 ViewModel 内预先计算，是为了让页面专注展示，而不必临时推导问题数量和删除数量。
     */
    private fun QuestionEditorDraftSnapshot.toRestoreInfo(): QuestionEditorRestoreDraftInfo =
        QuestionEditorRestoreDraftInfo(
            savedAt = savedAt,
            questionCount = questions.size,
            deletedQuestionCount = deletedQuestionIds.size
        )

    /**
     * 草稿条目回填为 UI 草稿时只保留恢复必需字段，是为了避免把旧校验提示误当成业务状态再次展示。
     */
    private fun QuestionEditorDraftItemSnapshot.toDraft(): QuestionDraft = QuestionDraft(
        id = id,
        prompt = prompt,
        answer = answer,
        isNew = isNew,
        validationMessage = null
    )

    /**
     * 正式保存时统一由草稿生成领域模型，可把“新增/旧数据缺失/普通编辑”三种分支的默认字段收敛到一处。
     */
    private fun QuestionDraft.toQuestion(
        cardId: String,
        nowEpochMillis: Long,
        initialDueAt: Long,
        original: Question?
    ): Question {
        val trimmedPrompt = prompt.trim()
        if (!isNew && original != null) {
            return original.copy(
                prompt = trimmedPrompt,
                answer = answer,
                updatedAt = nowEpochMillis
            )
        }
        return Question(
            id = if (isNew) EntityIds.newQuestionId() else id,
            cardId = cardId,
            prompt = trimmedPrompt,
            answer = answer,
            tags = emptyList(),
            status = QuestionStatus.ACTIVE,
            stageIndex = 0,
            dueAt = initialDueAt,
            lastReviewedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            createdAt = original?.createdAt ?: nowEpochMillis,
            updatedAt = nowEpochMillis
        )
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
            questionEditorDraftRepository: QuestionEditorDraftRepository,
            appSettingsRepository: AppSettingsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            QuestionEditorViewModel(
                cardId = cardId,
                deckId = deckId,
                cardRepository = cardRepository,
                questionRepository = questionRepository,
                questionEditorDraftRepository = questionEditorDraftRepository,
                appSettingsRepository = appSettingsRepository,
                timeProvider = timeProvider
            )
        }
    }
}
