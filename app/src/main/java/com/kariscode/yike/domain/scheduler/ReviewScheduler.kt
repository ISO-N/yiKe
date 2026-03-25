package com.kariscode.yike.domain.scheduler

import com.kariscode.yike.domain.model.ReviewRating
import java.time.ZoneId

/**
 * 复习调度抽象成接口，是为了让页面、仓储和分析逻辑依赖“调度能力”而非具体算法版本，
 * 这样未来切到 SM-2 或自适应算法时可以逐步替换而不需要改穿整条调用链。
 */
interface ReviewScheduler {
    /**
     * 评分落库前需要拿到下一次调度结果，因此接口必须直接暴露纯计算入口，
     * 才能让不同算法实现保持同一事务边界。
     */
    fun scheduleNext(
        currentStageIndex: Int,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        dueAtEpochMillis: Long?,
        intervalStepCount: Int,
        zoneId: ZoneId
    ): ReviewScheduleResult

    /**
     * 过期评估单独暴露，是为了让 UI 提示和实际调度继续共享同一解释口径，
     * 避免引入多算法后提示和落库结果开始漂移。
     */
    fun assessOverdueState(
        currentStageIndex: Int,
        dueAtEpochMillis: Long?,
        reviewedAtEpochMillis: Long,
        intervalStepCount: Int,
        zoneId: ZoneId
    ): ReviewOverdueAssessment
}
