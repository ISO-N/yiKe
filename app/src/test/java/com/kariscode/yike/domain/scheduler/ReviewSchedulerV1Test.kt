package com.kariscode.yike.domain.scheduler

import com.kariscode.yike.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调度器测试的存在是为了把“阶段边界/跳级/重置”这些最易漂移的规则固定下来，
 * 避免后续 UI 或事务实现调整时悄悄改坏调度节奏。
 */
class ReviewSchedulerV1Test {
    /**
     * AGAIN 必须重置到 0 并标记 lapse，以便写库事务正确递增 lapseCount。
     */
    @Test
    fun again_resetsToStage0_andMarksLapse() {
        val scheduler = ReviewSchedulerV1()
        val reviewedAt = 0L

        val result = scheduler.scheduleNext(
            currentStageIndex = 4,
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = reviewedAt
        )

        assertEquals(0, result.nextStageIndex)
        assertTrue(result.isLapse)
        assertEquals(1, result.intervalDays)
        assertEquals(86_400_000L, result.nextDueAtEpochMillis)
    }

    /**
     * EASY 需要跳两级但不能越过最大阶段，以保证阶段边界稳定。
     */
    @Test
    fun easy_jumpsTwoStages_butCapsAtMaxStage() {
        val scheduler = ReviewSchedulerV1()
        val reviewedAt = 0L

        val result = scheduler.scheduleNext(
            currentStageIndex = 6,
            rating = ReviewRating.EASY,
            reviewedAtEpochMillis = reviewedAt
        )

        assertEquals(7, result.nextStageIndex)
        assertEquals(180, result.intervalDays)
        assertEquals(180L * 86_400_000L, result.nextDueAtEpochMillis)
    }

    /**
     * HARD 在 stage=0 时仍需保持为 0，避免出现负阶段导致数组越界或错误 dueAt。
     */
    @Test
    fun hard_atStage0_staysAt0() {
        val scheduler = ReviewSchedulerV1()
        val reviewedAt = 123L

        val result = scheduler.scheduleNext(
            currentStageIndex = 0,
            rating = ReviewRating.HARD,
            reviewedAtEpochMillis = reviewedAt
        )

        assertEquals(0, result.nextStageIndex)
        assertEquals(1, result.intervalDays)
        assertEquals(reviewedAt + 86_400_000L, result.nextDueAtEpochMillis)
    }
}

