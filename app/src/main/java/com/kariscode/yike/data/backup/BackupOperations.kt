package com.kariscode.yike.data.backup

import android.net.Uri

/**
 * 备份页编排层只依赖最小备份操作接口，
 * 这样测试可以替换真实文件与数据库依赖而专注验证业务流程。
 */
interface BackupOperations {
    /**
     * 导出文件名由底层服务统一生成，以保持页面和文件落地规则一致。
     */
    fun createSuggestedFileName(): String

    /**
     * 导出到 Uri 是设置页真正触发的高风险动作，因此保留显式接口。
     */
    suspend fun exportToUri(uri: Uri)

    /**
     * 从 Uri 恢复是备份页覆盖本地数据的核心入口，接口化后便于测试成功与失败路径。
     */
    suspend fun restoreFromUri(uri: Uri)
}
