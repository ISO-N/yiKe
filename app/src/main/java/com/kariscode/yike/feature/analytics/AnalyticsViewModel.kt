package com.kariscode.yike.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.domain.coroutine.parallel3
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeConstants
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.domain.time.calculateStudyStreakDays
import com.kariscode.yike.core.ui.viewmodel.restartStateResult
import com.kariscode.yike.domain.model.StreakAchievementUnlock
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot
import com.kariscode.yike.domain.model.StageAgainRatioSnapshot
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import java.time.Instant
import java.time.LocalDate
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
 * 热力图单元只保留日期与活跃度等级，是为了让 UI 直接映射为颜色，不必在绘制时再反复计算阈值。
 */
data class AnalyticsHeatmapCellUiModel(
    val date: LocalDate,
    val reviewCount: Int,
    val level: Int
)

/**
 * 遗忘曲线按 stage 分组输出 AGAIN 比例，是为了让图表能够直观看到“越往后越稳定/越往前越容易忘”。
 */
data class AnalyticsStageAgainUiModel(
    val stageIndex: Int,
    val reviewCount: Int,
    val againRatio: Float
)

/**
 * 未来到期预测按天输出 due 数量，是为了让用户提前判断未来几天的压力峰值。
 */
data class AnalyticsDueForecastUiModel(
    val date: LocalDate,
    val label: String,
    val dueCount: Int
)

/**
 * 统计页状态同时承载时间范围、指标和结论，是为了让页面切换筛选后仍保持完整上下文。
 */
data class AnalyticsUiState(
    val isLoading: Boolean,
    val selectedRange: AnalyticsRange,
    val streakDays: Int,
    val streakAchievementUnlocks: List<StreakAchievementUnlock>,
    val heatmapCells: List<AnalyticsHeatmapCellUiModel>,
    val totalReviews: Int,
    val averageResponseSeconds: Int,
    val forgettingRatePercent: Int,
    val distributions: List<AnalyticsDistributionUiModel>,
    val stageAgainCurve: List<AnalyticsStageAgainUiModel>,
    val dueForecast: List<AnalyticsDueForecastUiModel>,
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
    private val appSettingsRepository: AppSettingsRepository,
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
            streakAchievementUnlocks = emptyList(),
            heatmapCells = emptyList(),
            totalReviews = 0,
            averageResponseSeconds = 0,
            forgettingRatePercent = 0,
            distributions = emptyList(),
            stageAgainCurve = emptyList(),
            dueForecast = emptyList(),
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

        /**
         * 成就记录来自设置仓储并可能由同步/恢复回放更新，因此持续订阅可让统计页次级区域实时反映进度。
         */
        viewModelScope.launch {
            appSettingsRepository.observeSettings().collect { settings ->
                _uiState.update { state ->
                    state.copy(streakAchievementUnlocks = settings.streakAchievementUnlocks)
                }
            }
        }
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
                val (analytics, timestamps, stageRatios) = parallel3(
                    first = { studyInsightsRepository.getReviewAnalytics(startEpochMillis = startEpochMillis) },
                    second = { studyInsightsRepository.listReviewTimestamps(startEpochMillis = null) },
                    third = { studyInsightsRepository.listStageAgainRatios(startEpochMillis = startEpochMillis) }
                )
                val dueForecast = loadDueForecast(nowEpochMillis = timeProvider.nowEpochMillis())
                AnalyticsRefreshSnapshot(
                    analytics = analytics,
                    reviewTimestamps = timestamps,
                    stageRatios = stageRatios,
                    dueForecast = dueForecast
                )
            },
            onStart = { it.copy(isLoading = true, errorMessage = null) },
            onSuccess = { _, snapshot ->
                val state = buildUiState(snapshot)
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
        snapshot: AnalyticsRefreshSnapshot
    ): AnalyticsUiState {
        val analytics = snapshot.analytics
        val timestamps = snapshot.reviewTimestamps
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
            streakDays = calculateStudyStreakDays(
                reviewTimestamps = timestamps,
                nowEpochMillis = timeProvider.nowEpochMillis(),
                zoneId = zoneId
            ),
            heatmapCells = buildHeatmapCells(
                reviewTimestamps = timestamps,
                nowEpochMillis = timeProvider.nowEpochMillis()
            ),
            totalReviews = analytics.totalReviews,
            averageResponseSeconds = ((analytics.averageResponseTimeMs ?: 0.0) / 1000.0).toInt(),
            forgettingRatePercent = (analytics.forgettingRate * 100).toInt(),
            distributions = analytics.toDistributionModels(),
            stageAgainCurve = buildStageAgainCurve(snapshot.stageRatios),
            dueForecast = snapshot.dueForecast,
            deckBreakdowns = deckBreakdowns,
            conclusion = buildConclusion(deckBreakdowns),
            errorMessage = null
        )
    }

    /**
     * 热力图只展示最近 52 周，是为了保证网格规模稳定且信息密度恰好适合手机阅读。
     */
    private fun buildHeatmapCells(
        reviewTimestamps: List<Long>,
        nowEpochMillis: Long
    ): List<AnalyticsHeatmapCellUiModel> {
        val today = localDateFor(nowEpochMillis)
        val startDate = today.minusDays(363)
        val countsByDate = reviewTimestamps
            .asSequence()
            .map(::localDateFor)
            .filter { date -> !date.isBefore(startDate) && !date.isAfter(today) }
            .groupingBy { date -> date }
            .eachCount()
        return (0L..363L).map { offset ->
            val date = startDate.plusDays(offset)
            val count = countsByDate[date] ?: 0
            AnalyticsHeatmapCellUiModel(
                date = date,
                reviewCount = count,
                level = heatmapLevel(count)
            )
        }
    }

    /**
     * 热力图阈值沿用 issue 建议，直接在 ViewModel 固定可以避免 UI 层漂移或重复写判断。
     */
    private fun heatmapLevel(count: Int): Int = when {
        count <= 0 -> 0
        count <= 5 -> 1
        count <= 15 -> 2
        count <= 30 -> 3
        else -> 4
    }

    /**
     * 遗忘曲线至少输出 0..7 的 stage，是为了让图表在数据不足时仍然保持稳定形态。
     */
    private fun buildStageAgainCurve(rows: List<StageAgainRatioSnapshot>): List<AnalyticsStageAgainUiModel> {
        val byStage = rows.associateBy(StageAgainRatioSnapshot::stageIndex)
        val maxStage = maxOf(7, rows.maxOfOrNull(StageAgainRatioSnapshot::stageIndex) ?: 0)
        return (0..maxStage).map { stageIndex ->
            val snapshot = byStage[stageIndex]
            val total = snapshot?.reviewCount ?: 0
            val again = snapshot?.againCount ?: 0
            AnalyticsStageAgainUiModel(
                stageIndex = stageIndex,
                reviewCount = total,
                againRatio = if (total <= 0) 0f else again.toFloat() / total.toFloat()
            )
        }
    }

    /**
     * 未来 7 天到期预测基于本地日期分桶，是为了让“今天/明天”这类时间感知与用户日历保持一致。
     */
    private suspend fun loadDueForecast(nowEpochMillis: Long): List<AnalyticsDueForecastUiModel> {
        val today = localDateFor(nowEpochMillis)
        val startEpochMillis = startOfDayEpochMillis(today)
        val endEpochMillis = startOfDayEpochMillis(today.plusDays(7))
        val dueAts = studyInsightsRepository.listUpcomingDueAts(
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis
        )
        val countsByDate = dueAts
            .asSequence()
            .map(::localDateFor)
            .groupingBy { date -> date }
            .eachCount()
        return (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            AnalyticsDueForecastUiModel(
                date = date,
                label = "${date.monthValue}/${date.dayOfMonth}",
                dueCount = countsByDate[date] ?: 0
            )
        }
    }

    /**
     * 统一把 EpochMillis 映射成本地日期，是为了让热力图、连续学习天数与到期预测共享同一时区口径。
     */
    private fun localDateFor(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    /**
     * 本地日期转当天开始时间，是为了让 dueAt 范围查询不会因为“当前时间点”截断今天剩余的到期量。
     */
    private fun startOfDayEpochMillis(date: LocalDate): Long =
        date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    /**
     * 刷新需要的多路数据显式建模，是为了让成功回调只关心“怎么构建 UI state”，而不再管理返回值拆包。
     */
    private data class AnalyticsRefreshSnapshot(
        val analytics: ReviewAnalyticsSnapshot,
        val reviewTimestamps: List<Long>,
        val stageRatios: List<StageAgainRatioSnapshot>,
        val dueForecast: List<AnalyticsDueForecastUiModel>
    )

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

}

