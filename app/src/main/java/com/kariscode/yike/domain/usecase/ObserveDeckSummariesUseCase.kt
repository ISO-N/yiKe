package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow

/**
 * 卡组列表观察抽成用例后，页面层只需要关心“当前应该展示哪些卡组”，
 * 而不必再直接处理“此刻 now 应该如何参与仓储查询”的细节。
 */
class ObserveDeckSummariesUseCase(
    private val deckRepository: DeckRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 读取时固定使用当前时间，是为了让首页、卡组页和预览页继续共享同一套到期统计口径。
     */
    operator fun invoke(): Flow<List<DeckSummary>> =
        deckRepository.observeActiveDeckSummaries(timeProvider.nowEpochMillis())
}
