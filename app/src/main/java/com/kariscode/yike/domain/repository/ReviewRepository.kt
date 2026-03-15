package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewSubmission

/**
 * ReviewRepository 收敛复习页专属的数据口径与事务写入，
 * 这样评分流程就不会把事务、调度与查询条件散落到多个仓储和 ViewModel 中。
 */
interface ReviewRepository {
    /**
     * 复习页只应加载当前卡片本轮到期的问题，
     * 以满足“按卡片组织、逐题推进”的产品约束。
     */
    suspend fun listDueQuestionsByCard(cardId: String, nowEpochMillis: Long): List<Question>

    /**
     * 评分提交必须以事务语义暴露，
     * 否则一旦 `Question` 与 `ReviewRecord` 分别成功/失败，就会产生不可恢复的数据漂移。
     */
    suspend fun submitRating(
        questionId: String,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        responseTimeMs: Long?
    ): ReviewSubmission
}
