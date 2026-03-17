package com.kariscode.yike.data.sync

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json

/**
 * 局域网 HTTP 服务只负责暴露协议入口和端口生命周期，
 * 是为了让仓储继续掌握同步语义，而不是把配对与应用逻辑塞进路由闭包里。
 */
class LanSyncHttpServer(
    private val portAllocator: LanSyncPortAllocator,
    private val onHello: suspend () -> LanSyncHelloResponse,
    private val onPairInit: suspend (LanSyncPairInitRequest) -> LanSyncPairInitResponse,
    private val onPing: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    private val onPullChanges: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    private val onPushChanges: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    private val onAck: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse
) : LanSyncTransportServer {
    private var engine: ApplicationEngine? = null
    override var port: Int = LanSyncConfig.PORT_RANGE_START
        private set

    /**
     * 启动时动态选择端口，是为了把端口冲突在本机服务绑定前就显式化，而不是等注册 NSD 后才发现失败。
     */
    override fun start() {
        if (engine != null) {
            return
        }
        port = portAllocator.findAvailablePort()
        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            configureLanSyncRoutes(
                onHello = onHello,
                onPairInit = onPairInit,
                onPing = onPing,
                onPullChanges = onPullChanges,
                onPushChanges = onPushChanges,
                onAck = onAck
            )
        }.also { server ->
            server.start(wait = false)
        }
    }

    /**
     * 页面退出时统一停掉服务，是为了把局域网暴露窗口压回到用户明确使用该功能的这段时间。
     */
    override fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
    }
}

/**
 * 路由配置抽成独立函数后，生产环境和 test host 可以复用同一套协议入口，避免两边定义逐渐漂移。
 */
internal fun Application.configureLanSyncRoutes(
    onHello: suspend () -> LanSyncHelloResponse,
    onPairInit: suspend (LanSyncPairInitRequest) -> LanSyncPairInitResponse,
    onPing: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    onPullChanges: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    onPushChanges: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse,
    onAck: suspend (LanSyncProtectedRequest) -> LanSyncProtectedResponse
) {
    install(ContentNegotiation) {
        json(LanSyncJson.json)
    }
    routing {
        get("/lan-sync/v2/hello") {
            call.respond(onHello())
        }
        post("/lan-sync/v2/pair/init") {
            call.respond(onPairInit(call.receive()))
        }
        post("/lan-sync/v2/ping") {
            call.respond(onPing(call.receive()))
        }
        post("/lan-sync/v2/changes/pull") {
            call.respond(onPullChanges(call.receive()))
        }
        post("/lan-sync/v2/changes/push") {
            call.respond(onPushChanges(call.receive()))
        }
        post("/lan-sync/v2/sync/ack") {
            call.respond(onAck(call.receive()))
        }
        get("/{...}") {
            call.respondText("Unsupported path: ${call.request.path()}", status = HttpStatusCode.NotFound)
        }
    }
}
