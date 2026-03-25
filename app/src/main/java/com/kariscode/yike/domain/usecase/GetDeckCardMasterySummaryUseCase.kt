package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.model.DeckMasterySummarySnapshot
import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 卡组熟练度摘要进入用例层后，卡片页只需要消费聚合结果，
 * 不必再同时维护“如何检索问题”和“如何换算熟练度分布”两套业务知识。
 */
class GetDeckCardMasterySummaryUseCase(
    private val studyInsightsRepository: StudyInsightsRepository
) {
    /**
     * 摘要直接按 deckId 返回，是为了让卡片页和未来的卡组洞察入口共享同一套聚合口径，
     * 同时把“如何统计熟练度”继续收口在仓储边界之内。
     */
    suspend operator fun invoke(deckId: String): DeckMasterySummarySnapshot =
        studyInsightsRepository.getDeckMasterySummary(deckId)
}
