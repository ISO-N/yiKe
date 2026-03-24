package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 卡组熟练度摘要进入用例层后，卡片页只需要消费聚合结果，
 * 不必再同时维护“如何检索问题”和“如何换算熟练度分布”两套业务知识。
 */
class GetDeckCardMasterySummaryUseCase(
    private val studyInsightsRepository: StudyInsightsRepository
) {
    /**
     * 摘要直接按 deckId 返回，是为了让卡片页和未来的卡组洞察入口共享同一套聚合口径。
     */
    suspend operator fun invoke(deckId: String): DeckMasterySummarySnapshot {
        val questions = studyInsightsRepository.searchQuestionContexts(
            filters = QuestionQueryFilters(
                deckId = deckId,
                status = QuestionStatus.ACTIVE
            )
        )
        var newCount = 0
        var learningCount = 0
        var familiarCount = 0
        var masteredCount = 0
        questions.forEach { context ->
            when (QuestionMasteryCalculator.snapshot(context.question).level) {
                QuestionMasteryLevel.NEW -> newCount += 1
                QuestionMasteryLevel.LEARNING -> learningCount += 1
                QuestionMasteryLevel.FAMILIAR -> familiarCount += 1
                QuestionMasteryLevel.MASTERED -> masteredCount += 1
            }
        }
        return DeckMasterySummarySnapshot(
            totalQuestions = questions.size,
            newCount = newCount,
            learningCount = learningCount,
            familiarCount = familiarCount,
            masteredCount = masteredCount
        )
    }
}

/**
 * 熟练度摘要在领域层单独建模后，页面模型可以按需要自由投影，而不必反向依赖业务计算代码。
 */
data class DeckMasterySummarySnapshot(
    val totalQuestions: Int,
    val newCount: Int,
    val learningCount: Int,
    val familiarCount: Int,
    val masteredCount: Int
)
