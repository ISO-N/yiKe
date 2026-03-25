package com.kariscode.yike.feature.home

import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.TodayReviewSummary

/**
 * 首页需要明确区分“还有待复习”“今天已清空”“还没有任何内容”，
 * 因此把状态归类逻辑集中到单点，避免 UI 再次根据零散字段现场推断。
 */
object HomeStateFactory {
    /**
     * 加载成功后统一在这里决定首页内容模式，
     * 可以保证标题、按钮和空状态说明都围绕同一判断口径收敛。
     */
    fun success(
        state: HomeUiState,
        summary: TodayReviewSummary,
        recentDecks: List<DeckSummary>
    ): HomeUiState = state.copy(
        isLoading = false,
        summary = summary,
        recentDecks = recentDecks,
        contentMode = resolveContentMode(
            summary = summary,
            recentDecks = recentDecks
        ),
        errorMessage = null
    )

    /**
     * 刷新失败时保留默认空态模式，但显式清空数据快照，
     * 这样错误分支不会混入上一轮成功态残留的统计信息。
     */
    fun loadFailed(
        state: HomeUiState,
        errorMessage: String
    ): HomeUiState = state.copy(
        isLoading = false,
        summary = TodayReviewSummary(
            dueCardCount = 0,
            dueQuestionCount = 0
        ),
        recentDecks = emptyList(),
        contentMode = HomeContentMode.CONTENT_EMPTY,
        streakDays = 0,
        highestAchievement = null,
        errorMessage = errorMessage
    )

    /**
     * 首页模式优先按“今天是否还有待复习”判断，
     * 只有确认今日已无任务时，才继续区分是“已完成”还是“还没有内容”。
     */
    private fun resolveContentMode(
        summary: TodayReviewSummary,
        recentDecks: List<DeckSummary>
    ): HomeContentMode = when {
        summary.dueQuestionCount > 0 -> HomeContentMode.REVIEW_READY
        recentDecks.isNotEmpty() -> HomeContentMode.REVIEW_CLEARED
        else -> HomeContentMode.CONTENT_EMPTY
    }
}
