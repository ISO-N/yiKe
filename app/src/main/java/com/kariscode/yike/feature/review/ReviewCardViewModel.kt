package com.kariscode.yike.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import com.kariscode.yike.domain.usecase.LoadReviewCardSessionUseCase
import com.kariscode.yike.domain.usecase.SubmitReviewRatingUseCase
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val sessionStartedAtEpochMillis: Long?,
    val ratingCounts: Map<ReviewRating, Int>,
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
    val stageIndex: Int,
    val plannedIntervalDays: Int,
    val overdueBadgeText: String?,
    val overdueHintText: String?,
    val needsReinforcement: Boolean,
    val ratingHints: List<ReviewRatingHintUiModel>
)

/**
 * 评分提示模型提前把“按钮文案 + 下次间隔暗示”绑在一起，是为了让页面不用重复推导调度结果。
 */
data class ReviewRatingHintUiModel(
    val rating: ReviewRating,
    val title: String,
    val intervalHint: String,
    val stageHint: String
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
    private val loadReviewCardSessionUseCase: LoadReviewCardSessionUseCase,
    private val submitReviewRatingUseCase: SubmitReviewRatingUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {
    /**
     * 复习前提示与实际评分共用同一调度器，是为了让“界面解释”和“真正落库”保持同一口径。
     */
    private val overduePreviewScheduler = ReviewSchedulerV1()

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
            sessionStartedAtEpochMillis = null,
            ratingCounts = ReviewRating.entries.associateWith { 0 },
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
        launchResult(
            action = {
                loadReviewCardSessionUseCase(
                    cardId = cardId,
                    nowEpochMillis = timeProvider.nowEpochMillis()
                )
            },
            onSuccess = { session ->
                if (session.dueQuestions.isEmpty()) {
                    _effects.tryEmit(ReviewCardEffect.NavigateToQueue)
                } else {
                    val presentedAt = timeProvider.nowEpochMillis()
                    pendingQuestions = session.dueQuestions.map { question ->
                        question.toReviewQuestionUiModel(
                            scheduler = overduePreviewScheduler,
                            nowEpochMillis = presentedAt,
                            intervalStepCount = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT
                        )
                    }
                    questionPresentedAtEpochMillis = presentedAt
                    _uiState.update {
                        it.copy(
                            cardTitle = session.cardTitle,
                            isLoading = false,
                            totalCount = pendingQuestions.size,
                            completedCount = 0,
                            currentQuestion = pendingQuestions.firstOrNull(),
                            answerVisible = false,
                            isSubmitting = false,
                            isCompleted = false,
                            sessionStartedAtEpochMillis = presentedAt,
                            ratingCounts = ReviewRating.entries.associateWith { 0 },
                            errorMessage = null
                        )
                    }
                }
            },
            onFailure = { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.userMessageOr(ErrorMessages.REVIEW_LOAD_FAILED)
                    )
                }
            }
        )
    }

    /**
     * 评分前必须先显示答案，因此这里单独维护显式动作，
     * 避免页面通过隐式条件直接开放评分按钮。
     */
    fun onRevealAnswerClick() {
        _uiState.update { it.copy(answerVisible = true, errorMessage = null) }
    }

    /**
     * 展开答案后允许用户回到“继续先想一遍”的状态，
     * 是为了给不想立刻评分的场景留出回旋空间，而不是只能硬着头皮继续提交。
     */
    fun onHideAnswerClick() {
        _uiState.update { state ->
            if (state.isSubmitting) {
                state
            } else {
                state.copy(answerVisible = false, errorMessage = null)
            }
        }
    }

    /**
     * 评分成功后立即推进到下一题或完成态，确保“评分一次即完成一次处理”的规则在 UI 层同步生效。
     */
    fun onRateClick(rating: ReviewRating) {
        val currentQuestion = _uiState.value.currentQuestion ?: return
        if (!_uiState.value.answerVisible || _uiState.value.isSubmitting) return

        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        launchResult(
            action = {
                val reviewedAt = timeProvider.nowEpochMillis()
                submitReviewRatingUseCase(
                    questionId = currentQuestion.questionId,
                    rating = rating,
                    reviewedAtEpochMillis = reviewedAt,
                    responseTimeMs = (reviewedAt - questionPresentedAtEpochMillis).coerceAtLeast(0L)
                )
            },
            onSuccess = {
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
                            ratingCounts = state.ratingCounts.increment(rating),
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
                            ratingCounts = state.ratingCounts.increment(rating),
                            errorMessage = null
                        )
                    }
                }
            },
            onFailure = {
                _uiState.update { state ->
                    state.copy(
                        isSubmitting = false,
                        errorMessage = ErrorMessages.REVIEW_RECORD_FAILED
                    )
                }
            }
        )
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
        ): ViewModelProvider.Factory = typedViewModelFactory {
            ReviewCardViewModel(
                cardId = cardId,
                loadReviewCardSessionUseCase = LoadReviewCardSessionUseCase(
                    cardRepository = cardRepository,
                    reviewRepository = reviewRepository
                ),
                submitReviewRatingUseCase = SubmitReviewRatingUseCase(
                    reviewRepository = reviewRepository
                ),
                timeProvider = timeProvider
            )
        }
    }
}

/**
 * 复习题目在进入页面时就完成过期提示组装，是为了让 Composable 只专注展示而不重复推导调度语义。
 */
private fun Question.toReviewQuestionUiModel(
    scheduler: ReviewSchedulerV1,
    nowEpochMillis: Long,
    intervalStepCount: Int
): ReviewQuestionUiModel {
    val overdueAssessment = scheduler.assessOverdueState(
        currentStageIndex = stageIndex,
        dueAtEpochMillis = dueAt,
        reviewedAtEpochMillis = nowEpochMillis,
        intervalStepCount = intervalStepCount,
        zoneId = ZoneId.systemDefault()
    )
    val overdueBadgeText = overdueAssessment.overdueDays
        .takeIf { overdueDays -> overdueDays > 0 }
        ?.let { overdueDays -> "已过期 ${overdueDays} 天" }
    val overdueHintText = when {
        overdueBadgeText == null -> null
        overdueAssessment.hasDecay -> "已偏离原计划较久，这次会先按重新巩固处理。"
        else -> "虽然已经过期，但尚未偏离完整周期，这次仍按当前阶段处理。"
    }

    return ReviewQuestionUiModel(
        questionId = id,
        prompt = prompt,
        answerText = answer.ifBlank { "无答案" },
        stageIndex = stageIndex,
        plannedIntervalDays = overdueAssessment.plannedIntervalDays,
        overdueBadgeText = overdueBadgeText,
        overdueHintText = overdueHintText,
        needsReinforcement = overdueAssessment.hasDecay,
        ratingHints = ReviewRating.entries.map { rating ->
            val scheduleResult = scheduler.scheduleNext(
                currentStageIndex = stageIndex,
                rating = rating,
                reviewedAtEpochMillis = nowEpochMillis,
                dueAtEpochMillis = dueAt,
                intervalStepCount = intervalStepCount,
                zoneId = ZoneId.systemDefault()
            )
            ReviewRatingHintUiModel(
                rating = rating,
                title = rating.displayLabel(),
                intervalHint = buildReviewIntervalHint(scheduleResult.intervalDays),
                stageHint = "下一阶段 ${scheduleResult.nextStageIndex}"
            )
        }
    )
}

/**
 * 评分计数统一走 map 更新，是为了让完成态摘要和提交流程共享同一份分布数据来源。
 */
private fun Map<ReviewRating, Int>.increment(rating: ReviewRating): Map<ReviewRating, Int> =
    toMutableMap().apply {
        this[rating] = (this[rating] ?: 0) + 1
    }

/**
 * 下次复习提示压缩成短句，是为了让评分决策在手机宽度下仍然能一眼比较不同档位差异。
 */
private fun buildReviewIntervalHint(intervalDays: Int): String = when (intervalDays) {
    0 -> "今天内再看一次"
    1 -> "明天再看"
    else -> "$intervalDays 天后再看"
}

/**
 * 评分文案集中定义，是为了让按钮标题、完成态摘要和测试断言共用同一套命名。
 */
private fun ReviewRating.displayLabel(): String = when (this) {
    ReviewRating.AGAIN -> "完全不会"
    ReviewRating.HARD -> "有印象"
    ReviewRating.GOOD -> "基本会"
    ReviewRating.EASY -> "很轻松"
}

