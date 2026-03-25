package com.kariscode.yike.domain.scheduler

import com.kariscode.yike.core.domain.time.toLocalDate
import com.kariscode.yike.core.domain.time.toStartOfDayEpochMillis
import com.kariscode.yike.domain.model.ReviewRating
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * ReviewSchedulerV1 作为首版默认调度器，目标是提供简单可解释且可测试的节奏，
 * 避免在 MVP 阶段引入过度复杂的曲线导致后续维护成本过高。
 */
class ReviewSchedulerV1(
    private val intervalDaysByStage: List<Int> = DEFAULT_INTERVAL_DAYS_BY_STAGE
) : ReviewScheduler {
    /**
     * 保留旧签名的便捷重载，是为了让纯调度测试和轻量调用方在不关心过期衰减时仍能直接复用默认配置，
     * 同时避免每个测试都重复传入空 dueAt 与默认步数。
     */
    fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        zoneId: ZoneId
    ): ReviewScheduleResult = scheduleNext(
        currentStageIndex = currentStageIndex,
        rating = rating,
        reviewedAtEpochMillis = reviewedAtEpochMillis,
        dueAtEpochMillis = null,
        intervalStepCount = DEFAULT_INTERVAL_STEP_COUNT,
        zoneId = zoneId
    )

    /**
     * 过期相关测试经常只需要显式提供 dueAt，因此继续提供“带 dueAt、默认步数”的重载，
     * 可以把测试关注点收敛在过期规则本身，而不是重复样板参数。
     */
    fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        dueAtEpochMillis: Long?,
        zoneId: ZoneId
    ): ReviewScheduleResult = scheduleNext(
        currentStageIndex = currentStageIndex,
        rating = rating,
        reviewedAtEpochMillis = reviewedAtEpochMillis,
        dueAtEpochMillis = dueAtEpochMillis,
        intervalStepCount = DEFAULT_INTERVAL_STEP_COUNT,
        zoneId = zoneId
    )

    /**
     * 卡组裁剪步数但不参与过期衰减时，测试与调用方只需要显式声明步数，
     * 因此补充“带步数、默认无 dueAt”的重载来保持调用意图清晰。
     */
    fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        intervalStepCount: Int,
        zoneId: ZoneId
    ): ReviewScheduleResult = scheduleNext(
        currentStageIndex = currentStageIndex,
        rating = rating,
        reviewedAtEpochMillis = reviewedAtEpochMillis,
        dueAtEpochMillis = null,
        intervalStepCount = intervalStepCount,
        zoneId = zoneId
    )

    /**
     * 根据当前阶段与评分，计算下一阶段与下一次 dueAt。
     * 该方法保持纯计算，便于单元测试覆盖边界并确保 UI/DAO 不承担调度规则。
     */
    override fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        dueAtEpochMillis: Long?,
        intervalStepCount: Int,
        zoneId: ZoneId
    ): ReviewScheduleResult {
        val normalizedIntervalStepCount = normalizeIntervalStepCount(
            intervalStepCount = intervalStepCount,
            maxAllowedStepCount = intervalDaysByStage.size
        )
        val effectiveIntervalDaysByStage = intervalDaysByStage.take(normalizedIntervalStepCount)
        val overdueAssessment = assessOverdueState(
            currentStageIndex = currentStageIndex,
            dueAtEpochMillis = dueAtEpochMillis,
            reviewedAtEpochMillis = reviewedAtEpochMillis,
            intervalStepCount = normalizedIntervalStepCount,
            zoneId = zoneId
        )
        val maxStageIndex = effectiveIntervalDaysByStage.lastIndex
        val nextStageIndex = when (rating) {
            ReviewRating.AGAIN -> 0
            ReviewRating.HARD -> (overdueAssessment.effectiveStageIndex - 1).coerceAtLeast(0)
            ReviewRating.GOOD -> (overdueAssessment.effectiveStageIndex + 1).coerceAtMost(maxStageIndex)
            ReviewRating.EASY -> (overdueAssessment.effectiveStageIndex + 2).coerceAtMost(maxStageIndex)
        }

        val intervalDays = effectiveIntervalDaysByStage[nextStageIndex]
        val nextDueAt = reviewedAtEpochMillis
            .toLocalDate(zoneId)
            .plusDays(intervalDays.toLong())
            .toStartOfDayEpochMillis(zoneId)

        return ReviewScheduleResult(
            nextStageIndex = nextStageIndex,
            nextDueAtEpochMillis = nextDueAt,
            isLapse = rating == ReviewRating.AGAIN,
            intervalDays = intervalDays,
            effectiveStageIndex = overdueAssessment.effectiveStageIndex,
            overdueDays = overdueAssessment.overdueDays,
            overdueRatio = overdueAssessment.overdueRatio,
            decayLevel = overdueAssessment.decayLevel
        )
    }

    /**
     * 过期状态评估单独暴露成纯函数，是为了让复习页提示与最终调度共用同一口径，
     * 避免“界面说会衰减、真正提交时却按另一套规则计算”的解释偏差。
     */
    override fun assessOverdueState(
        currentStageIndex: Int,
        dueAtEpochMillis: Long?,
        reviewedAtEpochMillis: Long,
        intervalStepCount: Int,
        zoneId: ZoneId
    ): ReviewOverdueAssessment {
        val effectiveIntervalDaysByStage = intervalDaysByStage.take(
            normalizeIntervalStepCount(
                intervalStepCount = intervalStepCount,
                maxAllowedStepCount = intervalDaysByStage.size
            )
        )
        val maxStageIndex = effectiveIntervalDaysByStage.lastIndex
        val boundedCurrentStage = currentStageIndex.coerceIn(0, maxStageIndex)
        val plannedIntervalDays = effectiveIntervalDaysByStage[boundedCurrentStage].coerceAtLeast(1)
        val overdueDays = dueAtEpochMillis
            ?.let { dueAtEpochMillis.toLocalDate(zoneId).until(reviewedAtEpochMillis.toLocalDate(zoneId), ChronoUnit.DAYS) }
            ?.coerceAtLeast(0L)
            ?.toInt()
            ?: 0
        val overdueRatio = overdueDays.toDouble() / plannedIntervalDays.toDouble()
        val effectiveStageIndex = decayStageByOverdue(
            currentStageIndex = boundedCurrentStage,
            overdueRatio = overdueRatio
        )

        return ReviewOverdueAssessment(
            boundedCurrentStageIndex = boundedCurrentStage,
            effectiveStageIndex = effectiveStageIndex,
            plannedIntervalDays = plannedIntervalDays,
            overdueDays = overdueDays,
            overdueRatio = overdueRatio,
            decayLevel = (boundedCurrentStage - effectiveStageIndex).coerceAtLeast(0)
        )
    }

    /**
     * 过期评估默认走完整 8 段曲线，是为了兼容现有测试和调用方在未显式裁剪卡组步数时的默认预期。
     */
    fun assessOverdueState(
        currentStageIndex: Int,
        dueAtEpochMillis: Long?,
        reviewedAtEpochMillis: Long,
        zoneId: ZoneId
    ): ReviewOverdueAssessment = assessOverdueState(
        currentStageIndex = currentStageIndex,
        dueAtEpochMillis = dueAtEpochMillis,
        reviewedAtEpochMillis = reviewedAtEpochMillis,
        intervalStepCount = DEFAULT_INTERVAL_STEP_COUNT,
        zoneId = zoneId
    )

    /**
     * 极端过期时将高阶段卡片直接拉回低阶段，是为了避免“拖了很久但仍被当成稳定掌握”继续放大失真。
     */
    private fun decayStageByOverdue(
        currentStageIndex: Int,
        overdueRatio: Double
    ): Int = when {
        overdueRatio < 1.0 -> currentStageIndex
        overdueRatio < 2.0 -> (currentStageIndex - 1).coerceAtLeast(0)
        overdueRatio < 4.0 -> (currentStageIndex - 2).coerceAtLeast(0)
        currentStageIndex >= 3 -> 0
        else -> currentStageIndex.coerceAtMost(1)
    }

    /**
     * 将默认间隔数组常量化可保证调度口径稳定，
     * 并为后续“用户自定义曲线”预留替换入口。
     */
    companion object {
        val DEFAULT_INTERVAL_DAYS_BY_STAGE: List<Int> = listOf(1, 2, 4, 7, 15, 30, 90, 180)
        const val MIN_INTERVAL_STEP_COUNT: Int = 1
        const val DEFAULT_INTERVAL_STEP_COUNT: Int = 8
        const val MAX_INTERVAL_STEP_COUNT: Int = 8

        /**
         * 卡组只允许裁剪默认序列长度而不允许自定义天数，是为了先满足“短期卡组不需要拉满 8 段”
         * 的诉求，同时继续复用同一套已验证的间隔曲线。
         */
        fun normalizeIntervalStepCount(
            intervalStepCount: Int,
            maxAllowedStepCount: Int = MAX_INTERVAL_STEP_COUNT
        ): Int = intervalStepCount.coerceIn(
            minimumValue = MIN_INTERVAL_STEP_COUNT,
            maximumValue = maxAllowedStepCount.coerceAtMost(MAX_INTERVAL_STEP_COUNT)
        )
    }
}

/**
 * 调度结果以结构化模型返回，便于在评分事务中以同一结果同时更新 Question 字段与写入 ReviewRecord。
 */
data class ReviewScheduleResult(
    val nextStageIndex: Int,
    val nextDueAtEpochMillis: Long,
    val isLapse: Boolean,
    val intervalDays: Int,
    val effectiveStageIndex: Int,
    val overdueDays: Int,
    val overdueRatio: Double,
    val decayLevel: Int
)

/**
 * 过期评估结果显式返回衰减前后阶段，是为了让调度器、测试与 UI 提示都能引用同一份解释数据。
 */
data class ReviewOverdueAssessment(
    val boundedCurrentStageIndex: Int,
    val effectiveStageIndex: Int,
    val plannedIntervalDays: Int,
    val overdueDays: Int,
    val overdueRatio: Double,
    val decayLevel: Int
) {
    /**
     * 只把真正发生阶段回落视为“需要重新巩固”，是为了避免轻微过期也被 UI 渲染成高压提示。
     */
    val hasDecay: Boolean
        get() = decayLevel > 0
}


