package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow

/**
 * 卡片列表观察抽成用例后，页面层只保留 deckId 上下文，不必再自己拼 now 去触发统计口径。
 */
class ObserveCardSummariesUseCase(
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 读取时统一注入当前时间，是为了让卡片列表的到期题统计和首页/预览继续保持一致。
     */
    operator fun invoke(deckId: String): Flow<List<CardSummary>> =
        cardRepository.observeActiveCardSummaries(deckId, timeProvider.nowEpochMillis())
}

