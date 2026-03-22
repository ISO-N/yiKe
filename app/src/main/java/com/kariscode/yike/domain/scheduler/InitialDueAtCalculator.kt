package com.kariscode.yike.domain.scheduler

import com.kariscode.yike.core.time.toLocalDate
import com.kariscode.yike.core.time.toStartOfDayEpochMillis
import java.time.ZoneId

/**
 * 新问题首次 dueAt 计算需要独立出来，原因是它会被编辑保存、备份恢复与测试共同复用；
 * 若散落在 UI 或 data 中，后续很容易出现多个入口计算不一致导致“今天创建的题目突然进入待复习”的漂移。
 */
object InitialDueAtCalculator {
    /**
     * 默认把新问题放到“明天开始可复习”，是为了让复习计划彻底脱离提醒时刻，
     * 并让今日待复习、逾期与下次调度全部收敛到统一的自然日语义。
     */
    fun compute(
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long = nowEpochMillis
        .toLocalDate(zoneId)
        .plusDays(1)
        .toStartOfDayEpochMillis(zoneId)
}

