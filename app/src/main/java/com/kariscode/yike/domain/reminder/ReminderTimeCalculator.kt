package com.kariscode.yike.domain.reminder

import com.kariscode.yike.core.domain.time.DefaultZoneId
import com.kariscode.yike.core.domain.time.toLocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 提醒时间计算保持为纯函数，是为了把“今天已过则排到明天”这类核心规则固定在可测试位置，
 * 避免被 WorkManager 注册代码或页面逻辑各自重写后出现偏差。
 */
object ReminderTimeCalculator {
    /**
     * 返回下一次本地提醒触发时间；若时间参数非法，则回退到当日/次日 20:00，
     * 以保证提醒始终有一个稳定可解释的默认语义。
     */
    fun computeNextTriggerAt(
        nowEpochMillis: Long,
        reminderHour: Int,
        reminderMinute: Int,
        zoneId: ZoneId = DefaultZoneId.current
    ): Long {
        val today = nowEpochMillis.toLocalDate(zoneId)
        val reminderTime = runCatching { LocalTime.of(reminderHour, reminderMinute) }
            .getOrElse { LocalTime.of(20, 0) }

        val todayReminder = today.atTime(reminderTime).atZone(zoneId).toInstant()
        val targetDate = if (todayReminder.toEpochMilli() > nowEpochMillis) today else today.plusDays(1)

        return targetDate
            .atTime(reminderTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}

