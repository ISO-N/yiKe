package com.kariscode.yike.data.reminder

import com.kariscode.yike.domain.model.AppSettings

/**
 * 提醒执行链路通过调度接口重建下一次任务，便于测试替换为记录型假实现。
 */
interface ReminderSyncScheduler {
    /**
     * 当调用方只知道“设置已落盘”而不持有快照时，可直接从仓储重建提醒。
     */
    suspend fun syncReminderFromRepository()

    /**
     * 根据给定设置重建下一次提醒，是 Worker 与设置页共享的核心动作。
     */
    fun syncReminder(settings: AppSettings)
}
