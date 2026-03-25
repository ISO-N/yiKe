package com.kariscode.yike.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.domain.coroutine.parallel
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeConstants
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.domain.time.toLocalDate
import com.kariscode.yike.core.ui.viewmodel.restartStateResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 时间范围显式建模，是为了让统计页切换口径时保持清晰状态，而不是依赖多个分散布尔值。
 */
enum class AnalyticsRange(
    val label: String
) {
    LAST_7_DAYS(label = "近 7 天"),
    LAST_30_DAYS(label = "近 30 天"),
    ALL_TIME(label = "全部时间")
}

/**
 * 分布条模型把数量和比例一起准备好，是为了让 UI 可以直接渲染，而不再重复换算百分比。
 */
data class AnalyticsDistributionUiModel(
    val label: String,
    val count: Int,
    val ratio: Float
)

/**
 * 卡组统计模型提前格式化为页面需要的信息，是为了让结论区更容易选出当前最需要关注的主题。
 */
data class AnalyticsDeckUiModel(
    val deckId: String,
    val deckName: String,
    val reviewCount: Int,
    val forgettingRatePercent: Int,
    val averageResponseSeconds: Int
)

/**
 * 统计页状态同时承载时间范围、指标和结论，是为了让页面切换筛选后仍保持完整上下文。
 */
data class AnalyticsUiState(
    val isLoading: Boolean,
    val selectedRange: AnalyticsRange,
    val streakDays: Int,
    val totalReviews: Int,
    val averageResponseSeconds: Int,
    val forgettingRatePercent: Int,
    val distributions: List<AnalyticsDistributionUiModel>,
    val deckBreakdowns: List<AnalyticsDeckUiModel>,
    val conclusion: String?,
    val errorMessage: String?
)

/**
 * 统计页 ViewModel 把时间范围切换和多路聚合查询集中管理，
 * 是为了避免页面自己同时维护口径、并发和结论文案。
 */
class AnalyticsViewModel(
    private val studyInsightsRepository: StudyInsightsRepository,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {
    /**
     * 刷新任务单独持有引用，是为了在用户快速切换统计范围时取消旧查询，避免陈旧结果回写。
     */
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow(
        AnalyticsUiState(
            isLoading = true,
            selectedRange = AnalyticsRange.LAST_7_DAYS,
            streakDays = 0,
            totalReviews = 0,
            averageResponseSeconds = 0,
            forgettingRatePercent = 0,
            distributions = emptyList(),
            deckBreakdowns = emptyList(),
            conclusion = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        /**
         * 首次进入默认展示最近 7 天，是为了先回答用户最关心的“这周状态怎么样”。
         */
        refresh()
    }

    /**
     * 时间范围切换立即触发重载，是为了让筛选状态与指标结果保持同步，不出现“标签已切但数据未换”的错觉。
     */
    fun onRangeSelected(range: AnalyticsRange) {
        if (_uiState.value.selectedRange == range) return
        _uiState.update { it.copy(selectedRange = range) }
        refresh()
    }

    /**
     * 刷新时并行读取统计摘要和时间戳序列，能把聚合 IO 与 streak 计算串成同一轮稳定结果。
     */
    fun refresh() {
        refreshJob = restartStateResult(
            state = _uiState,
            previousJob = refreshJob,
            action = {
                val startEpochMillis = _uiState.value.selectedRange.toStartEpochMillis(timeProvider.nowEpochMillis())
                parallel(
                    first = { studyInsightsRepository.getReviewAnalytics(startEpochMillis = startEpochMillis) },
                    second = { studyInsightsRepository.listReviewTimestamps(startEpochMillis = startEpochMillis) }
                )
            },
            onStart = { it.copy(isLoading = true, errorMessage = null) },
            onSuccess = { _, (analytics, timestamps) ->
                val state = buildUiState(analytics, timestamps)
                state
            },
            onFailure = { state, throwable ->
                state.copy(
                    isLoading = false,
                    errorMessage = throwable.userMessageOr(ErrorMessages.ANALYTICS_LOAD_FAILED)
                )
            }
        )
    }

    /**
     * 统计摘要统一转换为页面状态，是为了让结论、分布和重点卡组都基于同一份口径构建。
     */
    private fun buildUiState(
        analytics: ReviewAnalyticsSnapshot,
        timestamps: List<Long>
    ): AnalyticsUiState {
        val deckBreakdowns = analytics.deckBreakdowns.take(3).map { deck ->
            AnalyticsDeckUiModel(
                deckId = deck.deckId,
                deckName = deck.deckName,
                reviewCount = deck.reviewCount,
                forgettingRatePercent = (deck.forgettingRate * 100).toInt(),
                averageResponseSeconds = ((deck.averageResponseTimeMs ?: 0.0) / 1000.0).toInt()
            )
        }
        return _uiState.value.copy(
            isLoading = false,
            streakDays = calculateStreakDays(timestamps),
            totalReviews = analytics.totalReviews,
            averageResponseSeconds = ((analytics.averageResponseTimeMs ?: 0.0) / 1000.0).toInt(),
            forgettingRatePercent = (analytics.forgettingRate * 100).toInt(),
            distributions = analytics.toDistributionModels(),
            deckBreakdowns = deckBreakdowns,
            conclusion = buildConclusion(deckBreakdowns),
            errorMessage = null
        )
    }

    /**
     * 连续学习按本地日期计算，并允许最新记录落在“今天或昨天”，是为了避免用户在当天尚未学习时过早被判定断档。
     */
    private fun calculateStreakDays(timestamps: List<Long>): Int {
        val reviewedDates = timestamps
            .map { it.toLocalDate(zoneId) }
            .toSet()
        val latestDate = reviewedDates.maxOrNull() ?: return 0
        val today = timeProvider.nowEpochMillis().toLocalDate(zoneId)
        if (latestDate.isBefore(today.minusDays(1))) return 0

        var streak = 0
        var expectedDate = latestDate
        while (expectedDate in reviewedDates) {
            streak += 1
            expectedDate = expectedDate.minusDays(1)
        }
        return streak
    }

    /**
     * 结论优先指出遗忘率最高的卡组，是为了把统计页从“看完数字就结束”推进到“看完就知道下一步做什么”。
     */
    private fun buildConclusion(deckBreakdowns: List<AnalyticsDeckUiModel>): String? {
        val focusDeck = deckBreakdowns.maxByOrNull(AnalyticsDeckUiModel::forgettingRatePercent)
        return focusDeck?.let { deck ->
            "困难题主要集中在${deck.deckName}，建议先看今日预览，再优先处理这组内容。"
        }
    }

    /**
     * 分布条按总次数换算比例，是为了让所有评分都能在同一尺度下比较，而不是单看绝对数。
     */
    private fun ReviewAnalyticsSnapshot.toDistributionModels(): List<AnalyticsDistributionUiModel> {
        val total = totalReviews.coerceAtLeast(1)
        return listOf(
            AnalyticsDistributionUiModel(label = "AGAIN", count = againCount, ratio = againCount.toFloat() / total),
            AnalyticsDistributionUiModel(label = "HARD", count = hardCount, ratio = hardCount.toFloat() / total),
            AnalyticsDistributionUiModel(label = "GOOD", count = goodCount, ratio = goodCount.toFloat() / total),
            AnalyticsDistributionUiModel(label = "EASY", count = easyCount, ratio = easyCount.toFloat() / total)
        )
    }

    /**
     * 时间范围换算集中在枚举扩展里，是为了让切换口径时只维护一处时间边界。
     */
    private fun AnalyticsRange.toStartEpochMillis(nowEpochMillis: Long): Long? = when (this) {
        AnalyticsRange.LAST_7_DAYS -> nowEpochMillis - 7L * TimeConstants.DAY_MILLIS
        AnalyticsRange.LAST_30_DAYS -> nowEpochMillis - 30L * TimeConstants.DAY_MILLIS
        AnalyticsRange.ALL_TIME -> null
    }

    companion object {
        /**
         * 工厂显式注入洞察仓储和时间源，是为了让统计页在测试里能稳定复现不同时段结果。
         */
        fun factory(
            studyInsightsRepository: StudyInsightsRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            AnalyticsViewModel(
                studyInsightsRepository = studyInsightsRepository,
                timeProvider = timeProvider
            )
        }
    }
}

