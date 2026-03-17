package com.kariscode.yike.data.sync

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.backup.BackupDocument
import com.kariscode.yike.data.backup.BackupJson
import com.kariscode.yike.data.backup.BackupService
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json

/**
 * 局域网 HTTP 服务只在同步页期间短暂暴露备份快照，是为了让多设备传输复用现有备份恢复语义。
 */
class LanSyncHttpServer(
    private val deviceId: String,
    private val deviceName: String,
    private val backupService: BackupService,
    private val timeProvider: TimeProvider
) {
    private var engine: ApplicationEngine? = null

    /**
     * 服务端口固定后，NSD 广播与客户端请求就可以围绕同一地址协商，避免每次启动再做额外握手。
     */
    val port: Int = 9420

    /**
     * 同步页重复进入时允许幂等启动，是为了避免页面重建后把同一端口重复绑定成第二份服务。
     */
    fun start() {
        if (engine != null) {
            return
        }
        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            install(ContentNegotiation) {
                json(BackupJson.json)
            }
            routing {
                get("/lan-sync/manifest") {
                    val exportedAt = timeProvider.nowEpochMillis()
                    val document = backupService.exportDocument(exportedAtEpochMillis = exportedAt)
                    call.respond(document.toManifestPayload(exportedAt = exportedAt))
                }
                get("/lan-sync/backup") {
                    val jsonString = backupService.exportToJsonString()
                    call.respondText(
                        text = jsonString,
                        contentType = ContentType.Application.Json,
                        status = HttpStatusCode.OK
                    )
                }
                get("/{...}") {
                    call.respondText("Unsupported path: ${call.request.path()}", status = HttpStatusCode.NotFound)
                }
            }
        }.also { server ->
            server.start(wait = false)
        }
    }

    /**
     * 停止服务可以让同步能力在用户离开页面后立即收拢回离线优先状态。
     */
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
    }

    /**
     * 传给远端的摘要统一由备份文档投影而来，是为了让展示数量与实际将要恢复的内容保持一致。
     */
    private fun BackupDocument.toManifestPayload(exportedAt: Long): LanSyncManifestPayload = LanSyncManifestPayload(
        deviceId = deviceId,
        deviceName = deviceName,
        exportedAt = exportedAt,
        deckCount = decks.size,
        cardCount = cards.size,
        questionCount = questions.size
    )
}
