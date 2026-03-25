package com.kariscode.yike.core.domain.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 将 epoch millis 的常用时间转换集中成扩展方法，
 * 是为了避免 feature/domain/data 各处重复写 `Instant.ofEpochMilli(...).atZone(...)` 模板，
 * 同时把“默认使用系统时区”这一隐含约定收敛到单点，降低后续修改成本。
 */
fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)

/**
 * 统一按本地日期口径计算 LocalDate，
 * 这样 streak、提醒和初始到期时间等规则在不同入口都不会因为各自实现细节而产生漂移。
 */
fun Long.toLocalDate(
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate = LocalDate.ofInstant(toInstant(), zoneId)

/**
 * 把本地日期起点统一收敛成共享扩展，是为了让调度、逾期和展示在“自然日从哪里开始”上保持单一口径。
 */
fun LocalDate.toStartOfDayEpochMillis(
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = atStartOfDay(zoneId).toInstant().toEpochMilli()

/**
 * 页面展示层经常需要把时间戳转换为本地时间文本，
 * 抽出 LocalDateTime 版本可让 UI 层复用同一条转换路径并减少样板代码。
 */
fun Long.toLocalDateTime(
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDateTime = LocalDateTime.ofInstant(toInstant(), zoneId)


