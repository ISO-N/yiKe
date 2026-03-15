package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * 设置仓储接口存在的原因是隔离“设置语义”与“持久化方式”，
 * 这样提醒、备份、迁移等用例只依赖同一接口，不会把 DataStore 细节泄漏到业务层。
 */
interface AppSettingsRepository {
    /**
     * 以 Flow 暴露设置能让设置页和 Worker 等组件在配置变化时自然同步，
     * 避免通过“手动刷新”维护多处状态副本导致提醒逻辑滞后。
     */
    fun observeSettings(): Flow<AppSettings>

    /**
     * 将提醒开关写入封装为仓储方法，便于在未来把“写入后重建提醒任务”作为同一用例的一部分编排，
     * 而不是让 UI 层直接操作 DataStore key。
     */
    suspend fun setDailyReminderEnabled(enabled: Boolean)

    /**
     * 提醒时间拆分为 hour/minute 是为了让时间计算保持简单稳定，
     * 同时避免以字符串存储导致解析错误影响提醒重建。
     */
    suspend fun setDailyReminderTime(hour: Int, minute: Int)

    /**
     * schemaVersion 作为数据代际标识需要集中维护，
     * 否则备份恢复或未来迁移时难以判断当前数据结构处于哪个阶段。
     */
    suspend fun setSchemaVersion(schemaVersion: Int)

    /**
     * 最近备份时间用于备份页展示与后续恢复协同；
     * 允许写入 null 是为了在用户清理数据或恢复失败时显式回退到“未知/未备份”状态。
     */
    suspend fun setBackupLastAt(epochMillis: Long?)
}
