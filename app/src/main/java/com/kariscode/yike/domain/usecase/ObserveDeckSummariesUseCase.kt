package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.domain.time.DefaultZoneId
import com.kariscode.yike.core.domain.time.toLocalDate
import com.kariscode.yike.core.domain.time.toStartOfDayEpochMillis
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
     * 卡组摘要里的 due 统计按自然日判定已经足够，因此这里固定使用“今天零点”作为查询参数，
     * 可以避免把不断变化的毫秒时间带进 Flow 订阅键，降低无意义的重建与困惑。
     */
    operator fun invoke(): Flow<List<DeckSummary>> =
        deckRepository.observeActiveDeckSummaries(
            timeProvider.nowEpochMillis()
                .toLocalDate(DefaultZoneId.current)
                .toStartOfDayEpochMillis(DefaultZoneId.current)
        )
}

