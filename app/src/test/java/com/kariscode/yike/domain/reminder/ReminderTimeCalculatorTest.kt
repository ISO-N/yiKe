package com.kariscode.yike.domain.reminder

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 提醒时间计算直接决定 WorkManager 的下一次触发时机，
 * 因此需要用固定时间覆盖“当天未到/当天已过/跨年”这些高风险边界。
 */
class ReminderTimeCalculatorTest {
    /**
     * 当天提醒时间未到时，应优先安排在当天，
     * 这样设置变更后的首次提醒不会被错误地推迟到明天。
     */
    @Test
    fun computeNextTriggerAt_beforeReminder_usesToday() {
        val now = Instant.parse("2026-03-15T10:00:00Z").toEpochMilli()

        val result = ReminderTimeCalculator.computeNextTriggerAt(
            nowEpochMillis = now,
            reminderHour = 20,
            reminderMinute = 30,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(Instant.parse("2026-03-15T20:30:00Z").toEpochMilli(), result)
    }

    /**
     * 当天提醒时间已过时必须顺延到次日同一时刻，
     * 以符合“每日固定时间”的产品语义。
     */
    @Test
    fun computeNextTriggerAt_afterReminder_usesTomorrow() {
        val now = Instant.parse("2026-03-15T21:00:00Z").toEpochMilli()

        val result = ReminderTimeCalculator.computeNextTriggerAt(
            nowEpochMillis = now,
            reminderHour = 20,
            reminderMinute = 30,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(Instant.parse("2026-03-16T20:30:00Z").toEpochMilli(), result)
    }

    /**
     * 跨年仍要保持本地时间语义正确，
     * 否则用户在年末修改提醒时可能得到错误的下一次触发日期。
     */
    @Test
    fun computeNextTriggerAt_crossYear_keepsLocalReminderTime() {
        val now = Instant.parse("2026-12-31T16:30:00Z").toEpochMilli()

        val result = ReminderTimeCalculator.computeNextTriggerAt(
            nowEpochMillis = now,
            reminderHour = 8,
            reminderMinute = 0,
            zoneId = ZoneId.of("UTC")
        )

        assertEquals(Instant.parse("2027-01-01T08:00:00Z").toEpochMilli(), result)
    }
}
