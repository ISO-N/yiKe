package com.kariscode.yike.data.sync

import com.kariscode.yike.data.backup.BackupJson
import com.kariscode.yike.domain.model.LanSyncSnapshot
import com.kariscode.yike.domain.model.SyncDevice
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json

private const val LAN_SYNC_MANIFEST_PATH: String = "/lan-sync/manifest"
private const val LAN_SYNC_BACKUP_PATH: String = "/lan-sync/backup"

/**
 * HTTP 客户端封装在单独组件内，是为了让同步仓储只关心“从设备拿到什么”，不关心请求拼装细节。
 */
class LanSyncHttpClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(BackupJson.json)
        }
    }

    /**
     * 远端摘要先行获取，可以让页面在真正下载整份备份前完成冲突判断和用户确认。
     */
    suspend fun fetchManifest(device: SyncDevice): LanSyncSnapshot {
        val payload = client.get(device.buildUrl(path = LAN_SYNC_MANIFEST_PATH)).body<LanSyncManifestPayload>()
        return LanSyncSnapshot(
            deviceId = payload.deviceId,
            deviceName = payload.deviceName,
            exportedAt = payload.exportedAt,
            deckCount = payload.deckCount,
            cardCount = payload.cardCount,
            questionCount = payload.questionCount
        )
    }

    /**
     * 真正同步时直接拉取远端备份 JSON，是为了继续复用既有恢复流程而不是引入第二套写库协议。
     */
    suspend fun fetchBackupJson(device: SyncDevice): String =
        client.get(device.buildUrl(path = LAN_SYNC_BACKUP_PATH)).body()

    /**
     * 同步组件在应用生命周期内会被重复复用，因此显式关闭客户端可以避免连接资源滞留。
     */
    fun close() {
        client.close()
    }

    /**
     * 地址拼装集中后，IPv4 与端口格式可以统一维护，避免多个请求端点各自硬编码 URL 模板。
     */
    private fun SyncDevice.buildUrl(path: String): String = "http://$hostAddress:$port$path"
}
