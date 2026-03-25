package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.error.InvalidReviewSubmissionException
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewSubmission
import com.kariscode.yike.domain.repository.ReviewRepository

/**
 * 评分提交封装成用例，是为了让复习页只表达“用户给了什么评分”，而不是自己拼装仓储写入参数。
 */
class SubmitReviewRatingUseCase(
    private val reviewRepository: ReviewRepository,
    private val nowEpochMillisProvider: () -> Long = { System.currentTimeMillis() }
) {
    /**
     * 响应时长和评分结果一起进入用例，是为了把评分落库的输入边界稳定在一处，便于后续补校验或审计。
     */
    suspend operator fun invoke(
        questionId: String,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        responseTimeMs: Long
    ): ReviewSubmission {
        require(questionId.isNotBlank()) { "questionId 不能为空" }
        if (reviewedAtEpochMillis > nowEpochMillisProvider()) {
            throw InvalidReviewSubmissionException("reviewedAt 不能晚于当前时间")
        }
        if (responseTimeMs < 0L) {
            throw InvalidReviewSubmissionException("responseTimeMs 不能为负数")
        }
        return reviewRepository.submitRating(
            questionId = questionId,
            rating = rating,
            reviewedAtEpochMillis = reviewedAtEpochMillis,
            responseTimeMs = responseTimeMs
        )
    }
}
