package com.kariscode.yike.data.webconsole

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.repository.AppSettingsRepository
import kotlinx.coroutines.withContext

/**
 * 设置与备份服务把高风险维护动作收拢在单点，
 * 是为了让网页后台后续补反馈层级和危险确认时只维护一条维护工作区边界。
 */
internal class WebConsoleMaintenanceWorkspaceService(
    private val appSettingsRepository: AppSettingsRepository,
    private val backupService: BackupService,
    private val reminderScheduler: ReminderScheduler,
    private val dispatchers: AppDispatchers,
    private val payloadMapper: WebConsoleWorkspacePayloadMapper
) {
    /**
     * 设置读取统一回转为网页 payload，是为了让桌面端始终和手机端共享同一份配置来源。
     */
    suspend fun getSettings(): WebConsoleSettingsPayload = withContext(dispatchers.io) {
        payloadMapper.toSettingsPayload(appSettingsRepository.getSettings())
    }

    /**
     * 设置更新复用现有提醒同步路径，是为了避免网页端写入后遗漏后台提醒重建。
     */
    suspend fun updateSettings(request: WebConsoleUpdateSettingsRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val current = appSettingsRepository.getSettings()
        val themeMode = ThemeMode.entries.firstOrNull { mode -> mode.name == request.themeMode } ?: current.themeMode
        val updated = current.copy(
            dailyReminderEnabled = request.dailyReminderEnabled,
            dailyReminderHour = request.dailyReminderHour,
            dailyReminderMinute = request.dailyReminderMinute,
            themeMode = themeMode
        )
        appSettingsRepository.setSettings(updated)
        reminderScheduler.syncReminder(updated)
        WebConsoleMutationPayload(message = "设置已保存")
    }

    /**
     * 备份导出继续走现有 JSON 生成链，是为了避免网页端再维护第二套备份格式。
     */
    suspend fun exportBackup(): WebConsoleBackupExportPayload = withContext(dispatchers.io) {
        WebConsoleBackupExportPayload(
            fileName = backupService.createSuggestedFileName(),
            content = backupService.exportToJsonString()
        )
    }

    /**
     * 恢复后立刻重建提醒，是为了让浏览器上传备份与手机本地导入共享同一恢复完成态。
     */
    suspend fun restoreBackup(request: WebConsoleBackupRestoreRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        require(request.content.isNotBlank()) { "请选择有效的备份文件后再恢复" }
        backupService.restoreFromJsonString(request.content)
        reminderScheduler.syncReminderFromRepository()
        WebConsoleMutationPayload(message = "备份已恢复，页面数据已同步更新")
    }
}

