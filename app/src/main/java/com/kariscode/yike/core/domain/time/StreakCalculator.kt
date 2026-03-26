package com.kariscode.yike.core.domain.time

import java.time.ZoneId

/**
 * 连续学习天数的计算收敛成共享函数，是为了让首页、统计页与成就解锁都围绕同一条口径推进，
 * 避免不同页面各自实现时在“时区、本地自然日边界、允许昨天断档”等细节上产生漂移。
 */
fun calculateStudyStreakDays(
    reviewTimestamps: List<Long>,
    nowEpochMillis: Long,
    zoneId: ZoneId = DefaultZoneId.current
): Int {
    val reviewedDates = reviewTimestamps
        .map { timestamp -> timestamp.toLocalDate(zoneId) }
        .toSet()
    val latestDate = reviewedDates.maxOrNull() ?: return 0
    val today = nowEpochMillis.toLocalDate(zoneId)
    if (latestDate.isBefore(today.minusDays(1))) {
        return 0
    }

    var streak = 0
    var expectedDate = latestDate
    while (expectedDate in reviewedDates) {
        streak += 1
        expectedDate = expectedDate.minusDays(1)
    }
    return streak
}

