package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.coroutine.parallel
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository

/**
 * 首页概览单独收口成用例，是为了让“今日待复习统计 + 最近卡组”始终按同一时刻的一组查询条件返回。
 */
class GetHomeOverviewUseCase(
    private val questionRepository: QuestionRepository,
    private val deckRepository: DeckRepository
) {
    /**
     * 首页只暴露用户下一步要做什么，因此这里直接返回概览结果，避免 ViewModel 重复编排并发查询细节。
     */
    suspend operator fun invoke(
        nowEpochMillis: Long,
        recentDeckLimit: Int
    ): HomeOverview {
        val (summary, recentDecks) = parallel(
            first = { questionRepository.getTodayReviewSummary(nowEpochMillis) },
            second = {
                deckRepository.listRecentActiveDeckSummaries(
                    nowEpochMillis = nowEpochMillis,
                    limit = recentDeckLimit
                )
            }
        )
        return HomeOverview(
            summary = summary,
            recentDecks = recentDecks
        )
    }
}

/**
 * 首页概览结果显式聚合统计与最近卡组，是为了让调用方只处理“结果是什么”，而不再关心它由几路仓储拼出。
 */
data class HomeOverview(
    val summary: TodayReviewSummary,
    val recentDecks: List<DeckSummary>
)

