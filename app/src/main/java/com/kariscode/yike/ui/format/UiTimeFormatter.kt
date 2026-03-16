package com.kariscode.yike.ui.format

import com.kariscode.yike.core.time.TimeTextFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 页面展示时间统一走同一 formatter，是为了让设置页与备份页对"本地时间"的表达保持一致，
 * 后续若要改展示格式时也只需要调整一个位置。
 */
fun formatLocalDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .toLocalDateTime()
        .toString()

/**
 * 统一管理 UI 展示用的日期时间格式化器。
 */
object UiDateTimeFormatters {
    val PREVIEW_DATE = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")
}

/**
 * 预览类时间统一格式化到月日时分，是为了让搜索结果、今日预览和后续复习入口对“本地到期时间”保持同一口径，
 * 避免页面各自重复写 `Instant -> ZoneId -> Formatter` 的模板。
 */
fun formatPreviewDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .format(UiDateTimeFormatters.PREVIEW_DATE)

/**
 * 提醒时间统一走共享格式化入口，是为了让设置页、提醒说明和后续可能出现的提醒摘要保持同一显示口径。
 */
fun formatReminderTime(hour: Int, minute: Int): String = TimeTextFormatter.formatHourMinute(hour, minute)
