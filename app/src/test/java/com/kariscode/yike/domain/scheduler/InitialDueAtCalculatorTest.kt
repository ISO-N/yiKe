package com.kariscode.yike.domain.scheduler

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 新问题初始 dueAt 计算是内容管理与调度衔接的关键边界；
 * 通过测试固定“明天提醒时间/非法时间回退”的行为，避免出现当天创建就进入待复习的体验回归。
 */
class InitialDueAtCalculatorTest {
    /**
     * 使用固定时区与固定 now 断言结果，保证测试不受运行机器时区影响。
     */
    @Test
    fun computesTomorrowAtReminderTime_inGivenZone() {
        val now = Instant.parse("2026-03-15T10:00:00Z").toEpochMilli()
        val zoneId = ZoneId.of("UTC")

        val dueAt = InitialDueAtCalculator.compute(
            nowEpochMillis = now,
            reminderHour = 20,
            reminderMinute = 0,
            zoneId = zoneId
        )

        val expected = Instant.parse("2026-03-16T20:00:00Z").toEpochMilli()
        assertEquals(expected, dueAt)
    }

    /**
     * 当提醒时间非法时必须回退到明天 00:00，保证 dueAt 始终可计算且不会抛异常。
     */
    @Test
    fun invalidReminderTime_fallsBackToMidnight() {
        val now = Instant.parse("2026-03-15T10:00:00Z").toEpochMilli()
        val zoneId = ZoneId.of("UTC")

        val dueAt = InitialDueAtCalculator.compute(
            nowEpochMillis = now,
            reminderHour = 99,
            reminderMinute = 0,
            zoneId = zoneId
        )

        val expected = Instant.parse("2026-03-16T00:00:00Z").toEpochMilli()
        assertEquals(expected, dueAt)
    }
}

