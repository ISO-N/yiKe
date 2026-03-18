package com.kariscode.yike.data.repository

import androidx.room.withTransaction
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.data.mapper.toEntity
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewRecord
import com.kariscode.yike.domain.model.ReviewSubmission
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import kotlinx.coroutines.withContext

/**
 * 复习提交流程需要同时读取当前题目、计算调度、更新题目并写入历史，
 * 因此实现必须集中在单一仓储中，才能稳定维护事务边界。
 */
class OfflineReviewRepository(
    private val database: YikeDatabase,
    private val questionDao: QuestionDao,
    private val reviewRecordDao: ReviewRecordDao,
    private val reviewScheduler: ReviewSchedulerV1,
    private val dispatchers: AppDispatchers,
    private val syncChangeRecorder: LanSyncChangeRecorder
) : ReviewRepository {
    /**
     * 复习页的读取只返回本卡片当前轮到期的问题，
     * 从源头固定“按卡片组织、一次处理一轮”的查询口径。
     */
    override suspend fun listDueQuestionsByCard(cardId: String, nowEpochMillis: Long): List<Question> =
        dispatchers.onIo {
            questionDao.listDueQuestionsByCard(
                cardId = cardId,
                activeStatus = QuestionEntity.STATUS_ACTIVE,
                nowEpochMillis = nowEpochMillis
            ).map { entity -> entity.toDomain() }
        }

    /**
     * 评分提交流程必须在一次事务中同时写入 Question 与 ReviewRecord，
     * 这样才能避免“阶段已变更但历史没记录”之类的半成功状态。
     */
    override suspend fun submitRating(
        questionId: String,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        responseTimeMs: Long?
    ): ReviewSubmission = withContext(dispatchers.io) {
        database.withTransaction {
            val currentEntity = questionDao.findById(questionId)
                ?: error("问题不存在，无法提交评分。")
            val currentQuestion = currentEntity.toDomain()
            val intervalStepCount = questionDao.findDeckIntervalStepCountByQuestionId(questionId)
                ?: ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT
            val scheduleResult = reviewScheduler.scheduleNext(
                currentStageIndex = currentQuestion.stageIndex,
                rating = rating,
                reviewedAtEpochMillis = reviewedAtEpochMillis,
                intervalStepCount = intervalStepCount
            )

            val updatedQuestion = currentQuestion.copy(
                stageIndex = scheduleResult.nextStageIndex,
                dueAt = scheduleResult.nextDueAtEpochMillis,
                lastReviewedAt = reviewedAtEpochMillis,
                reviewCount = currentQuestion.reviewCount + 1,
                lapseCount = currentQuestion.lapseCount + if (scheduleResult.isLapse) 1 else 0,
                updatedAt = reviewedAtEpochMillis
            )

            val reviewRecord = ReviewRecord(
                id = EntityIds.newReviewRecordId(),
                questionId = currentQuestion.id,
                rating = rating,
                oldStageIndex = currentQuestion.stageIndex,
                newStageIndex = updatedQuestion.stageIndex,
                oldDueAt = currentQuestion.dueAt,
                newDueAt = updatedQuestion.dueAt,
                reviewedAt = reviewedAtEpochMillis,
                responseTimeMs = responseTimeMs,
                note = ""
            )

            questionDao.upsertAll(listOf(updatedQuestion.toEntity()))
            reviewRecordDao.insert(reviewRecord.toEntity())
            syncChangeRecorder.recordQuestionUpsert(updatedQuestion)
            syncChangeRecorder.recordReviewRecordInsert(reviewRecord)

            ReviewSubmission(
                updatedQuestion = updatedQuestion,
                reviewRecord = reviewRecord
            )
        }
    }
}
