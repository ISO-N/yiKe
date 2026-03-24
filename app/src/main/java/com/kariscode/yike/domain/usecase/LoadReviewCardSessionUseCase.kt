package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.error.CardNotFoundException
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.ReviewRepository

/**
 * 复习会话加载单独落在用例层，是为了把“校验卡片存在 + 查询本卡到期问题”的业务边界从 ViewModel 中移出去。
 */
class LoadReviewCardSessionUseCase(
    private val cardRepository: CardRepository,
    private val reviewRepository: ReviewRepository
) {
    /**
     * 复习页只应感知本卡标题与当前轮次问题，因此用例直接返回这两部分结果，隐藏底层查询来源。
     */
    suspend operator fun invoke(
        cardId: String,
        nowEpochMillis: Long
    ): ReviewCardSession {
        val card = cardRepository.findById(cardId) ?: throw CardNotFoundException(cardId)
        val dueQuestions = reviewRepository.listDueQuestionsByCard(
            cardId = cardId,
            nowEpochMillis = nowEpochMillis
        )
        return ReviewCardSession(
            cardTitle = card.title,
            dueQuestions = dueQuestions
        )
    }
}

/**
 * 复习会话返回值只保留页面推进所需字段，是为了让后续即使底层仓储结构变化，也不必把变化泄漏给 UI。
 */
data class ReviewCardSession(
    val cardTitle: String,
    val dueQuestions: List<Question>
)
