package com.kariscode.yike.domain.scheduler

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新问题初始 dueAt 计算是内容管理与调度衔接的关键边界；
 * 通过测试固定“明天开始可复习”的行为，避免出现当天创建就进入待复习的体验回归。
 */
class InitialDueAtCalculatorTest {
    /**
     * 使用固定时区与固定 now 断言结果，保证自然日推进不受运行机器时区影响。
     */
    @Test
    fun computesTomorrowStartOfDay_inGivenZone() {
        val now = Instant.parse("2026-03-15T10:00:00Z").toEpochMilli()
        val zoneId = ZoneId.of("UTC")

        val dueAt = InitialDueAtCalculator.compute(
            nowEpochMillis = now,
            zoneId = zoneId
        )

        val expected = Instant.parse("2026-03-16T00:00:00Z").toEpochMilli()
        assertEquals(expected, dueAt)
    }

    /**
     * 即使当前时间已经很晚，初始 dueAt 也应继续落到下一自然日的起点，避免零散小时影响调度语义。
     */
    @Test
    fun computesTomorrowStartOfDay_evenWhenNowIsNearMidnight() {
        val now = Instant.parse("2026-03-15T23:59:00Z").toEpochMilli()
        val zoneId = ZoneId.of("UTC")

        val dueAt = InitialDueAtCalculator.compute(
            nowEpochMillis = now,
            zoneId = zoneId
        )

        val expected = Instant.parse("2026-03-16T00:00:00Z").toEpochMilli()
        assertEquals(expected, dueAt)
    }
}

