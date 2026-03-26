package com.kariscode.yike.feature.home

import androidx.lifecycle.ViewModel
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.domain.time.DefaultZoneId
import com.kariscode.yike.core.domain.time.calculateStudyStreakDays
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.StreakAchievement
import com.kariscode.yike.domain.model.highestUnlockedAchievement
import com.kariscode.yike.domain.model.unlockForStreakDays
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.usecase.GetHomeOverviewUseCase
import com.kariscode.yike.domain.usecase.HomeOverview
import com.kariscode.yike.core.domain.coroutine.parallel3
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 首页内容模式需要直接表达“今天该复习 / 今天已清空 / 还没有内容”，
 * 否则 UI 很容易把“无任务”和“无数据”混成同一种空态。
 */
enum class HomeContentMode {
    REVIEW_READY,
    REVIEW_CLEARED,
    CONTENT_EMPTY
}

/**
 * 首页状态显式区分加载/成功/错误，并补充最近卡组入口，
 * 这样首页既能回答“今天要复习什么”，也能回答“接下来去哪里继续维护内容”。
 */
data class HomeUiState(
    val isLoading: Boolean,
    val summary: TodayReviewSummary,
    val recentDecks: List<DeckSummary>,
    val contentMode: HomeContentMode,
    val streakDays: Int,
    val highestAchievement: StreakAchievement?,
    val errorMessage: String?
)

/**
 * 首页 ViewModel 把复习概览和最近卡组查询收敛在一起，
 * 是为了避免页面自己发起多路查询后再拼装状态，导致错误反馈分叉。
 */
class HomeViewModel(
    private val getHomeOverviewUseCase: GetHomeOverviewUseCase,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val timeProvider: TimeProvider,
    private val zoneId: ZoneId = DefaultZoneId.current
) : ViewModel() {
    /**
     * 刷新需要把“概览 + streak + 最高徽章”组合成一份一致结果，
     * 抽成局部模型是为了让 `launchResult` 的 onSuccess 保持纯 UI 回写，不再混入 suspend 写入动作。
     */
    private data class HomeRefreshResult(
        val overview: HomeOverview,
        val streakDays: Int,
        val highestAchievement: StreakAchievement?
    )

    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            summary = TodayReviewSummary(
                dueCardCount = 0,
                dueQuestionCount = 0
            ),
            recentDecks = emptyList(),
            contentMode = HomeContentMode.CONTENT_EMPTY,
            streakDays = 0,
            highestAchievement = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        /**
         * 首次进入即加载首页所需的全部真实信息，保证用户不用额外操作就能看到主路径入口。
         */
        refresh()
    }

    /**
     * 刷新入口同时重算待复习概览和最近卡组，是为了让错误态重试后立即恢复完整首页。
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        launchResult(
            action = {
                val nowEpochMillis = timeProvider.nowEpochMillis()
                val (overview, timestamps, settings) = parallel3(
                    first = {
                        getHomeOverviewUseCase(
                            nowEpochMillis = nowEpochMillis,
                            recentDeckLimit = 3
                        )
                    },
                    second = { studyInsightsRepository.listReviewTimestamps(startEpochMillis = null) },
                    third = { appSettingsRepository.getSettings() }
                )

                val streakDays = calculateStudyStreakDays(
                    reviewTimestamps = timestamps,
                    nowEpochMillis = nowEpochMillis,
                    zoneId = zoneId
                )
                val unlocks = settings.streakAchievementUnlocks.unlockForStreakDays(
                    streakDays = streakDays,
                    nowEpochMillis = nowEpochMillis
                )
                if (unlocks != settings.streakAchievementUnlocks) {
                    // 写入解锁记录放在 refresh 主协程里执行，保证备份与同步链路能拿到最新进度。
                    appSettingsRepository.setSettings(settings.copy(streakAchievementUnlocks = unlocks))
                }
                HomeRefreshResult(
                    overview = overview,
                    streakDays = streakDays,
                    highestAchievement = unlocks.highestUnlockedAchievement()
                )
            },
            onSuccess = { result ->
                _uiState.update {
                    HomeStateFactory.success(
                        state = it,
                        summary = result.overview.summary,
                        recentDecks = result.overview.recentDecks
                    ).copy(
                        streakDays = result.streakDays,
                        highestAchievement = result.highestAchievement
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    HomeStateFactory.loadFailed(
                        state = it,
                        errorMessage = throwable.userMessageOr(ErrorMessages.HOME_LOAD_FAILED)
                    )
                }
            }
        )
    }

}

