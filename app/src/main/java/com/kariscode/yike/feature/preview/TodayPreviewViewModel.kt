package com.kariscode.yike.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.coroutine.parallel
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.core.message.userMessageOr
import com.kariscode.yike.core.time.TimeConstants
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.core.viewmodel.restartStateResult
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.QuestionMasterySnapshot
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
        refreshJob = restartStateResult(
            state = _uiState,
            previousJob = refreshJob,
            action = {
                val now = timeProvider.nowEpochMillis()
                parallel(
                    first = { studyInsightsRepository.listDueQuestionContexts(now) },
                    second = { studyInsightsRepository.getReviewAnalytics(startEpochMillis = now - TimeConstants.WEEK_MILLIS) }
                )
            },
            onStart = { it.copy(isLoading = true, errorMessage = null) },
            onSuccess = { _, (dueQuestions, analytics) ->
                val state = TodayPreviewUiStateBuilder.build(
                    dueQuestions = dueQuestions,
                    averageResponseTimeMs = analytics.averageResponseTimeMs
                )
                state
            },
            onFailure = { state, throwable ->
                state.copy(
                    isLoading = false,
                    errorMessage = throwable.userMessageOr(ErrorMessages.PREVIEW_LOAD_FAILED)
                )
            }
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
