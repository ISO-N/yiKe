package com.kariscode.yike.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.time.TimeConstants
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionMasterySnapshot
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlin.math.ceil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val DEFAULT_RESPONSE_TIME_MS: Double = 15_000.0
private const val MIN_RESPONSE_TIME_MS: Double = 10_000.0

/**
 * 预览页的问题项提前携带熟练度，是为了让页面能直接标出今天更该优先处理的薄弱题目。
 */
data class TodayPreviewQuestionUiModel(
    val questionId: String,
    val prompt: String,
    val dueAt: Long,
    val mastery: QuestionMasterySnapshot
)

/**
 * 卡片分组把预览问题和估时放在一起，是为了让用户在进入复习前先建立章节级任务预期。
 */
data class TodayPreviewCardUiModel(
    val cardId: String,
    val cardTitle: String,
    val dueQuestionCount: Int,
    val estimatedMinutes: Int,
    val lowMasteryCount: Int,
    val questions: List<TodayPreviewQuestionUiModel>
)

/**
 * 卡组分组是预览页的主要组织层级，因为它最贴近用户实际决定“先复习哪一科/哪一主题”的方式。
 */
data class TodayPreviewDeckUiModel(
    val deckId: String,
    val deckName: String,
    val dueQuestionCount: Int,
    val estimatedMinutes: Int,
    val lowMasteryCount: Int,
    val cards: List<TodayPreviewCardUiModel>
)

/**
 * 预览页状态直接面向“开始前建立预期”的任务设计，
 * 因此汇总值、重点提示和分组列表都在 ViewModel 中统一准备。
 */
data class TodayPreviewUiState(
    val isLoading: Boolean,
    val totalDueQuestions: Int,
    val totalDueCards: Int,
    val totalDecks: Int,
    val estimatedMinutes: Int,
    val averageSecondsPerQuestion: Int,
    val lowMasteryCount: Int,
    val earliestDueAt: Long?,
    val deckGroups: List<TodayPreviewDeckUiModel>,
    val errorMessage: String?
)

/**
 * 预处理后的题目上下文缓存熟练度结果，是为了让分组、计数和预览项共用同一份计算，
 * 避免在同一轮刷新里对同一个问题重复做 3 到 4 次 snapshot。
 */
private data class ResolvedDueQuestion(
    val context: QuestionContext,
    val mastery: QuestionMasterySnapshot,
    val isLowMastery: Boolean
)

/**
 * 今日预览 ViewModel 同时整合 due 列表和最近响应时长，
 * 是为了让用户看到的任务总量与耗时估算来自同一轮数据读取。
 */
class TodayPreviewViewModel(
    private val studyInsightsRepository: StudyInsightsRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    /**
     * 预览刷新只保留最后一轮请求，是为了避免页面反复进入或手动刷新时堆积重复查询。
     */
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(
        TodayPreviewUiState(
            isLoading = true,
            totalDueQuestions = 0,
            totalDueCards = 0,
            totalDecks = 0,
            estimatedMinutes = 0,
            averageSecondsPerQuestion = (DEFAULT_RESPONSE_TIME_MS / 1000).toInt(),
            lowMasteryCount = 0,
            earliestDueAt = null,
            deckGroups = emptyList(),
            errorMessage = null
        )
    )
    val uiState: StateFlow<TodayPreviewUiState> = _uiState.asStateFlow()

    init {
        /**
         * 预览页进入即加载，是为了让首页跳转过来后不需要再额外点击一次才能看到任务规模。
         */
        refresh()
    }

    /**
     * 刷新时并行读取 due 题目和最近耗时摘要，能减少用户等待并保证估时基于近期真实表现。
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            runCatching {
                val now = timeProvider.nowEpochMillis()
                val (dueQuestions, analytics) = parallel(
                    first = { studyInsightsRepository.listDueQuestionContexts(now) },
                    second = { studyInsightsRepository.getReviewAnalytics(startEpochMillis = now - TimeConstants.WEEK_MILLIS) }
                )
                buildUiState(
                    dueQuestions = dueQuestions,
                    averageResponseTimeMs = analytics.averageResponseTimeMs
                )
            }.onSuccess { state ->
                _uiState.value = state
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: ErrorMessages.PREVIEW_LOAD_FAILED
                    )
                }
            }
        }
    }

    /**
     * 分组逻辑收口在 ViewModel 中，是为了让页面层只处理展示，不再承担 due 排序和估时换算。
     */
    private fun buildUiState(
        dueQuestions: List<QuestionContext>,
        averageResponseTimeMs: Double?
    ): TodayPreviewUiState {
        val resolvedResponseTimeMs = averageResponseTimeMs
            ?.coerceAtLeast(MIN_RESPONSE_TIME_MS)
            ?: DEFAULT_RESPONSE_TIME_MS
        val resolvedQuestions = dueQuestions.map(::resolveDueQuestion)
        val deckGroups = resolvedQuestions
            .groupBy { it.context.deckId }
            .map { (_, deckQuestions) -> buildDeckGroup(deckQuestions, resolvedResponseTimeMs) }
            .sortedWith(
                compareByDescending<TodayPreviewDeckUiModel> { it.dueQuestionCount }
                    .thenBy { deck ->
                        deck.cards.flatMap { card -> card.questions }.minOfOrNull(TodayPreviewQuestionUiModel::dueAt)
                    }
            )
        return TodayPreviewUiState(
            isLoading = false,
            totalDueQuestions = dueQuestions.size,
            totalDueCards = dueQuestions.map { it.question.cardId }.distinct().size,
            totalDecks = deckGroups.size,
            estimatedMinutes = estimateMinutes(dueQuestions.size, resolvedResponseTimeMs),
            averageSecondsPerQuestion = ceil(resolvedResponseTimeMs / 1000.0).toInt(),
            lowMasteryCount = resolvedQuestions.count(ResolvedDueQuestion::isLowMastery),
            earliestDueAt = dueQuestions.minOfOrNull { it.question.dueAt },
            deckGroups = deckGroups,
            errorMessage = null
        )
    }

    /**
     * 卡组分组内部继续按卡片组织，是为了贴合“先决定学哪科，再决定做哪张卡”的使用顺序。
     */
    private fun buildDeckGroup(
        questions: List<ResolvedDueQuestion>,
        averageResponseTimeMs: Double
    ): TodayPreviewDeckUiModel {
        val cards = questions.groupBy { it.context.question.cardId }
            .map { (cardId, cardQuestions) ->
                val previewQuestions = cardQuestions
                    .sortedBy { it.context.question.dueAt }
                    .take(3)
                    .map { question ->
                        TodayPreviewQuestionUiModel(
                            questionId = question.context.question.id,
                            prompt = question.context.question.prompt,
                            dueAt = question.context.question.dueAt,
                            mastery = question.mastery
                        )
                    }
                TodayPreviewCardUiModel(
                    cardId = cardId,
                    cardTitle = cardQuestions.first().context.cardTitle,
                    dueQuestionCount = cardQuestions.size,
                    estimatedMinutes = estimateMinutes(cardQuestions.size, averageResponseTimeMs),
                    lowMasteryCount = cardQuestions.count(ResolvedDueQuestion::isLowMastery),
                    questions = previewQuestions
                )
            }
            .sortedByDescending(TodayPreviewCardUiModel::dueQuestionCount)
        return TodayPreviewDeckUiModel(
            deckId = questions.first().context.deckId,
            deckName = questions.first().context.deckName,
            dueQuestionCount = questions.size,
            estimatedMinutes = estimateMinutes(questions.size, averageResponseTimeMs),
            lowMasteryCount = questions.count(ResolvedDueQuestion::isLowMastery),
            cards = cards
        )
    }

    /**
     * 估时采用向上取整分钟，是为了让用户拿到更保守的预期，减少“实际比预览更久”的挫败感。
     */
    private fun estimateMinutes(questionCount: Int, averageResponseTimeMs: Double): Int {
        if (questionCount <= 0) return 0
        return maxOf(1, ceil(questionCount * averageResponseTimeMs / 60_000.0).toInt())
    }

    /**
     * 同一题目的熟练度与低熟练度标签在进入分组前先算好，是为了避免统计与预览项重复调用 snapshot。
     */
    private fun resolveDueQuestion(context: QuestionContext): ResolvedDueQuestion {
        val mastery = QuestionMasteryCalculator.snapshot(context.question)
        return ResolvedDueQuestion(
            context = context,
            mastery = mastery,
            isLowMastery = mastery.level == QuestionMasteryLevel.NEW || mastery.level == QuestionMasteryLevel.LEARNING
        )
    }

    companion object {
        /**
         * 工厂显式注入洞察仓储和时间源，是为了让预览页在测试中可以稳定替换真实依赖。
         */
        fun factory(
            studyInsightsRepository: StudyInsightsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            TodayPreviewViewModel(
                studyInsightsRepository = studyInsightsRepository,
                timeProvider = timeProvider
            )
        }
    }
}
