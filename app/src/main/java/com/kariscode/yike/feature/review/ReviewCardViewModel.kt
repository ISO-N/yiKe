package com.kariscode.yike.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 复习页状态显式建模为“加载/答题/提交/完成/退出确认”，
 * 是为了把页面流程收敛到单一状态机，避免 Composable 里散落条件分支。
 */
data class ReviewCardUiState(
    val cardId: String,
    val cardTitle: String,
    val isLoading: Boolean,
    val totalCount: Int,
    val completedCount: Int,
    val currentQuestion: ReviewQuestionUiModel?,
    val answerVisible: Boolean,
    val isSubmitting: Boolean,
    val isCompleted: Boolean,
    val errorMessage: String?,
    val exitConfirmationVisible: Boolean
)

/**
 * 复习页 UI 只需要展示当前题目的必要信息，
 * 因而单独定义 UI 模型可避免页面直接依赖完整领域对象。
 */
data class ReviewQuestionUiModel(
    val questionId: String,
    val prompt: String,
    val answerText: String,
    val stageIndex: Int
)

/**
 * 一次性导航效果通过 SharedFlow 暴露，能让页面渲染状态与导航副作用彼此解耦。
 */
sealed interface ReviewCardEffect {
    data object NavigateToQueue : ReviewCardEffect
    data object NavigateHome : ReviewCardEffect
}

/**
 * ReviewCardViewModel 负责把“加载本卡问题 -> 逐题评分 -> 本卡完成”收敛到单一路径，
 * 这样中途退出、失败重试和完成跳转都能有一致状态来源。
 */
class ReviewCardViewModel(
    private val cardId: String,
    private val cardRepository: CardRepository,
    private val reviewRepository: ReviewRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        ReviewCardUiState(
            cardId = cardId,
            cardTitle = "",
            isLoading = true,
            totalCount = 0,
            completedCount = 0,
            currentQuestion = null,
            answerVisible = false,
            isSubmitting = false,
            isCompleted = false,
            errorMessage = null,
            exitConfirmationVisible = false
        )
    )
    val uiState: StateFlow<ReviewCardUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReviewCardEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReviewCardEffect> = _effects.asSharedFlow()

    private var pendingQuestions: List<ReviewQuestionUiModel> = emptyList()
    private var questionPresentedAtEpochMillis: Long = 0L

    init {
        /**
         * 页面进入即根据 cardId 重新加载本卡到期问题，
         * 这样在进程重建后仍能恢复到可理解的“继续本卡剩余问题”状态。
         */
        loadSession()
    }

    /**
     * 加载本卡标题与当次到期问题；若已无待复习内容，则返回队列继续决定下一步。
     */
    fun loadSession() {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                exitConfirmationVisible = false
            )
        }
        viewModelScope.launch {
            runCatching {
                val now = timeProvider.nowEpochMillis()
                val card = cardRepository.findById(cardId) ?: error("这张卡片不存在")
                val dueQuestions = reviewRepository.listDueQuestionsByCard(cardId = cardId, nowEpochMillis = now)
                card.title to dueQuestions
            }.onSuccess { (cardTitle, dueQuestions) ->
                if (dueQuestions.isEmpty()) {
                    _effects.tryEmit(ReviewCardEffect.NavigateToQueue)
                    return@onSuccess
                }
                pendingQuestions = dueQuestions.map { question ->
                    ReviewQuestionUiModel(
                        questionId = question.id,
                        prompt = question.prompt,
                        answerText = question.answer.ifBlank { "无答案" },
                        stageIndex = question.stageIndex
                    )
                }
                questionPresentedAtEpochMillis = timeProvider.nowEpochMillis()
                _uiState.update {
                    it.copy(
                        cardTitle = cardTitle,
                        isLoading = false,
                        totalCount = pendingQuestions.size,
                        completedCount = 0,
                        currentQuestion = pendingQuestions.firstOrNull(),
                        answerVisible = false,
                        isSubmitting = false,
                        isCompleted = false,
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "加载失败，请重试"
                    )
                }
            }
        }
    }

    /**
     * 评分前必须先显示答案，因此这里单独维护显式动作，
     * 避免页面通过隐式条件直接开放评分按钮。
     */
    fun onRevealAnswerClick() {
        _uiState.update { it.copy(answerVisible = true, errorMessage = null) }
    }

    /**
     * 评分成功后立即推进到下一题或完成态，确保“评分一次即完成一次处理”的规则在 UI 层同步生效。
     */
    fun onRateClick(rating: ReviewRating) {
        val currentQuestion = _uiState.value.currentQuestion ?: return
        if (!_uiState.value.answerVisible || _uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val reviewedAt = timeProvider.nowEpochMillis()
                reviewRepository.submitRating(
                    questionId = currentQuestion.questionId,
                    rating = rating,
                    reviewedAtEpochMillis = reviewedAt,
                    responseTimeMs = (reviewedAt - questionPresentedAtEpochMillis).coerceAtLeast(0L)
                )
            }.onSuccess {
                val nextCompletedCount = _uiState.value.completedCount + 1
                pendingQuestions = pendingQuestions.drop(1)
                if (pendingQuestions.isEmpty()) {
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            completedCount = nextCompletedCount,
                            currentQuestion = null,
                            answerVisible = false,
                            isCompleted = true,
                            errorMessage = null
                        )
                    }
                } else {
                    questionPresentedAtEpochMillis = timeProvider.nowEpochMillis()
                    _uiState.update { state ->
                        state.copy(
                            isSubmitting = false,
                            completedCount = nextCompletedCount,
                            currentQuestion = pendingQuestions.first(),
                            answerVisible = false,
                            errorMessage = null
                        )
                    }
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        errorMessage = "记录失败，请重试"
                    )
                }
            }
        }
    }

    /**
     * 未完成本卡时返回需要二次确认，
     * 这样用户能明确知道未评分的问题不会计入完成，避免误操作焦虑。
     */
    fun onExitAttempt() {
        val state = _uiState.value
        if (state.isCompleted || state.currentQuestion == null) {
            _effects.tryEmit(ReviewCardEffect.NavigateHome)
            return
        }
        _uiState.update { it.copy(exitConfirmationVisible = true) }
    }

    /**
     * 取消退出确认时恢复当前复习上下文，
     * 避免用户误触返回后丢失继续操作的节奏。
     */
    fun onDismissExitConfirmation() {
        _uiState.update { it.copy(exitConfirmationVisible = false) }
    }

    /**
     * 确认退出后直接回首页，让用户重新从总入口理解当前进度。
     */
    fun onConfirmExit() {
        _uiState.update { it.copy(exitConfirmationVisible = false) }
        _effects.tryEmit(ReviewCardEffect.NavigateHome)
    }

    /**
     * 本卡完成后继续下一张应回到队列页统一决策，
     * 这样下一张卡的选择口径仍然集中在 ReviewQueue 中。
     */
    fun onContinueNextCardClick() {
        _effects.tryEmit(ReviewCardEffect.NavigateToQueue)
    }

    /**
     * 完成态返回首页为用户提供明确收尾路径，
     * 避免停留在已结束的流程页面产生“是否还需要操作”的困惑。
     */
    fun onBackHomeClick() {
        _effects.tryEmit(ReviewCardEffect.NavigateHome)
    }

    companion object {
        /**
         * 工厂显式注入 cardId 与依赖，是为了保持 ViewModel 可测试且不依赖全局单例。
         */
        fun factory(
            cardId: String,
            cardRepository: CardRepository,
            reviewRepository: ReviewRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReviewCardViewModel(
                    cardId = cardId,
                    cardRepository = cardRepository,
                    reviewRepository = reviewRepository,
                    timeProvider = timeProvider
                ) as T
            }
        }
    }
}
