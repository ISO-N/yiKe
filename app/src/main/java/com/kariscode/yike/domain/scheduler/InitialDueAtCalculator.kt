package com.kariscode.yike.domain.scheduler

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 新问题首次 dueAt 计算需要独立出来，原因是它会被编辑保存、备份恢复与测试共同复用；
 * 若散落在 UI 或 data 中，后续很容易出现多个入口计算不一致导致“今天创建的题目突然进入待复习”的漂移。
 */
object InitialDueAtCalculator {
    /**
     * 选择“明天的固定提醒时间”作为默认 dueAt，可让用户形成稳定节奏；
     * 若传入的提醒时间非法，则回退到明天 00:00，保证 dueAt 始终可计算。
     */
    fun compute(
        nowEpochMillis: Long,
        reminderHour: Int,
        reminderMinute: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val nowInstant = Instant.ofEpochMilli(nowEpochMillis)
        val today = LocalDate.ofInstant(nowInstant, zoneId)
        val targetDate = today.plusDays(1)

        val safeTime = runCatching { LocalTime.of(reminderHour, reminderMinute) }
            .getOrElse { LocalTime.MIDNIGHT }

        return targetDate
            .atTime(safeTime)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}

