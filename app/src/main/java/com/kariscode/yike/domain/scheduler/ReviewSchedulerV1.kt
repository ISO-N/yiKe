package com.kariscode.yike.domain.scheduler

import com.kariscode.yike.domain.model.ReviewRating
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ReviewSchedulerV1 作为首版默认调度器，目标是提供简单可解释且可测试的节奏，
 * 避免在 MVP 阶段引入过度复杂的曲线导致后续维护成本过高。
 */
class ReviewSchedulerV1(
    private val intervalDaysByStage: List<Int> = DEFAULT_INTERVAL_DAYS_BY_STAGE
) {
    /**
     * 根据当前阶段与评分，计算下一阶段与下一次 dueAt。
     * 该方法保持纯计算，便于单元测试覆盖边界并确保 UI/DAO 不承担调度规则。
     */
    fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long
    ): ReviewScheduleResult {
        val maxStageIndex = intervalDaysByStage.lastIndex
        val boundedCurrentStage = currentStageIndex.coerceIn(0, maxStageIndex)

        val nextStageIndex = when (rating) {
            ReviewRating.AGAIN -> 0
            ReviewRating.HARD -> (boundedCurrentStage - 1).coerceAtLeast(0)
            ReviewRating.GOOD -> (boundedCurrentStage + 1).coerceAtMost(maxStageIndex)
            ReviewRating.EASY -> (boundedCurrentStage + 2).coerceAtMost(maxStageIndex)
        }

        val intervalDays = intervalDaysByStage[nextStageIndex]
        val nextDueAt = Instant.ofEpochMilli(reviewedAtEpochMillis)
            .plus(intervalDays.toLong(), ChronoUnit.DAYS)
            .toEpochMilli()

        return ReviewScheduleResult(
            nextStageIndex = nextStageIndex,
            nextDueAtEpochMillis = nextDueAt,
            isLapse = rating == ReviewRating.AGAIN,
            intervalDays = intervalDays
        )
    }

    /**
     * 将默认间隔数组常量化可保证调度口径稳定，
     * 并为后续“用户自定义曲线”预留替换入口。
     */
    companion object {
        val DEFAULT_INTERVAL_DAYS_BY_STAGE: List<Int> = listOf(1, 2, 4, 7, 15, 30, 90, 180)
    }
}

/**
 * 调度结果以结构化模型返回，便于在评分事务中以同一结果同时更新 Question 字段与写入 ReviewRecord。
 */
data class ReviewScheduleResult(
    val nextStageIndex: Int,
    val nextDueAtEpochMillis: Long,
    val isLapse: Boolean,
    val intervalDays: Int
)

