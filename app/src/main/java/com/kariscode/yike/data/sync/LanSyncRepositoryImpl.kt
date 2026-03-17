package com.kariscode.yike.data.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.domain.model.LanSyncSnapshot
import com.kariscode.yike.domain.model.LocalSyncSnapshot
import com.kariscode.yike.domain.model.SyncDevice
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.LanSyncRepository
import java.util.Locale
import kotlinx.coroutines.flow.Flow

/**
 * 局域网同步仓储把本机服务、设备发现和备份恢复编排在一起，
 * 是为了让页面层始终围绕统一的“覆盖同步”语义工作。
 */
class LanSyncRepositoryImpl(
    context: Context,
    private val backupService: BackupService,
    private val appSettingsRepository: AppSettingsRepository,
    timeProvider: com.kariscode.yike.core.time.TimeProvider
) : LanSyncRepository {
    private val localDeviceId: String = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: "unknown-device"
    private val localDeviceName: String = buildLocalDeviceName()
    private val serviceName: String = "yike-${localDeviceId.takeLast(6)}"
    private val httpServer = LanSyncHttpServer(
        deviceId = localDeviceId,
        deviceName = localDeviceName,
        backupService = backupService,
        timeProvider = timeProvider
    )
    private val nsdService = LanSyncNsdService(context = context, timeProvider = timeProvider)
    private val httpClient = LanSyncHttpClient()
    private var isRunning: Boolean = false

    override fun observeDevices(): Flow<List<SyncDevice>> = nsdService.devices

    override fun getLocalDeviceName(): String = localDeviceName

    /**
     * 同步会话按需启动可以把网络暴露窗口压缩到最小，并确保 NSD 与 HTTP 服务生命周期一致。
     */
    override suspend fun start() {
        if (isRunning) {
            return
        }
        httpServer.start()
        nsdService.registerService(serviceName = serviceName, port = httpServer.port)
        nsdService.startDiscovery()
        isRunning = true
    }

    /**
     * 页面销毁时统一收口网络组件，避免同步功能在后台继续发现设备或响应局域网请求。
     */
    override suspend fun stop() {
        if (!isRunning) {
            return
        }
        nsdService.stopDiscovery()
        nsdService.unregisterService()
        httpServer.stop()
        isRunning = false
    }

    /**
     * 本机快照直接复用备份文档统计，是为了保证同步前看到的数量与真正被覆盖的数据范围一致。
     */
    override suspend fun getLocalSnapshot(): LocalSyncSnapshot {
        val document = backupService.exportDocument()
        val settings = appSettingsRepository.getSettings()
        return LocalSyncSnapshot(
            deckCount = document.decks.size,
            cardCount = document.cards.size,
            questionCount = document.questions.size,
            lastBackupAt = settings.backupLastAt
        )
    }

    /**
     * 远端摘要获取与本地快照使用相同字段口径，可以让冲突提示更容易横向比较。
     */
    override suspend fun fetchRemoteSnapshot(device: SyncDevice): LanSyncSnapshot =
        httpClient.fetchManifest(device)

    /**
     * 真正同步时继续走既有备份恢复，是为了复用事务、校验与失败回滚，而不是再维护一套写库路径。
     */
    override suspend fun importFromDevice(device: SyncDevice) {
        val jsonString = httpClient.fetchBackupJson(device)
        backupService.restoreFromJsonString(jsonString)
    }

    /**
     * 本机显示名保留品牌和系统型号，是为了让多台设备出现在同一局域网时更容易被用户区分。
     */
    private fun buildLocalDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        return "$manufacturer ${Build.MODEL}"
    }
}
