package com.kariscode.yike.ui.format

import com.kariscode.yike.core.domain.time.toInstant
import com.kariscode.yike.core.domain.time.toLocalDate
import com.kariscode.yike.core.domain.time.toLocalDateTime
import com.kariscode.yike.core.domain.time.DefaultZoneId
import com.kariscode.yike.core.domain.time.TimeTextFormatter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 页面展示时间统一走同一 formatter，是为了让设置页与备份页对"本地时间"的表达保持一致，
 * 后续若要改展示格式时也只需要调整一个位置。
 */
fun formatLocalDateTime(epochMillis: Long, zoneId: ZoneId = DefaultZoneId.current): String =
    epochMillis
        .toLocalDateTime(zoneId)
        .toString()

/**
 * 统一管理 UI 展示用的日期时间格式化器。
 */
object UiDateTimeFormatters {
    /**
     * 预览类页面刻意省去年份，是为了让移动端首屏优先承载“今天先做什么”而不是冗余时间上下文。
     */
    val PREVIEW_DATE = DateTimeFormatter.ofPattern("M 月 d 日 HH:mm")

    /**
     * 自然日调度只强调“哪一天需要复习”，因此到期信息单独保留到月日，避免把 00:00 误读成提醒时刻。
     */
    val PREVIEW_DAY = DateTimeFormatter.ofPattern("M 月 d 日")
}

/**
 * 预览类时间统一格式化到月日时分，是为了让搜索结果、今日预览和后续复习入口对“本地到期时间”保持同一口径，
 * 避免页面各自重复写 `Instant -> ZoneId -> Formatter` 的模板。
 */
fun formatPreviewDateTime(epochMillis: Long, zoneId: ZoneId = DefaultZoneId.current): String =
    epochMillis
        .toInstant()
        .atZone(zoneId)
        .format(UiDateTimeFormatters.PREVIEW_DATE)

/**
 * 到期日期统一格式化为月日文本，是为了让自然日调度在所有入口都表达成“哪一天该复习”，而不是实现细节上的零点。
 */
fun formatPreviewDay(epochMillis: Long, zoneId: ZoneId = DefaultZoneId.current): String =
    epochMillis
        .toLocalDate(zoneId)
        .format(UiDateTimeFormatters.PREVIEW_DAY)

/**
 * 提醒时间统一走共享格式化入口，是为了让设置页、提醒说明和后续可能出现的提醒摘要保持同一显示口径。
 */
fun formatReminderTime(hour: Int, minute: Int): String = TimeTextFormatter.formatHourMinute(hour, minute)

