package com.kariscode.yike.data.webconsole

import io.ktor.http.Cookie
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json

/**
 * 网页后台 HTTP 服务只负责暴露网页资源和 API 入口，
 * 是为了把鉴权和业务规则继续留在仓储里，而不是散落在路由闭包中。
 */
internal class WebConsoleHttpServer(
    private val portAllocator: WebConsolePortAllocator,
    private val assetLoader: WebConsoleAssetLoader,
    private val handler: WebConsoleApiHandler
) {
    private var engine: ApplicationEngine? = null
    var port: Int = WebConsolePortAllocator.PORT_RANGE_START
        private set

    /**
     * 服务启动时动态挑选端口，是为了在局域网控制台与其他本地服务并存时继续保持可预测的回退行为。
     */
    fun start() {
        if (engine != null) {
            return
        }
        port = portAllocator.findAvailablePort()
        engine = embeddedServer(CIO, host = "0.0.0.0", port = port) {
            configureWebConsoleRoutes(
                assetLoader = assetLoader,
                handler = handler
            )
        }.also { server ->
            server.start(wait = false)
        }
    }

    /**
     * 停止时统一回收端口，是为了让用户关闭网页后台后能立即把对外暴露窗口收回来。
     */
    fun stop() {
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 1_000)
        engine = null
    }
}

/**
 * 路由配置独立出来后，生产环境与测试环境可以共用同一套登录和鉴权行为，避免协议漂移。
 */
internal fun Application.configureWebConsoleRoutes(
    assetLoader: WebConsoleAssetLoader,
    handler: WebConsoleApiHandler
) {
    install(ContentNegotiation) {
        json(WebConsoleJson.json)
    }
    routing {
        post("/api/web-console/v1/auth/login") {
            val remoteHost = call.request.origin.remoteHost
            if (!remoteHost.isAllowedLocalNetworkHost()) {
                call.respondText("Forbidden", status = HttpStatusCode.Forbidden)
                return@post
            }
            val request = call.receive<WebConsoleLoginRequest>()
            val sessionId = handler.login(request.code, remoteHost)
            if (sessionId == null) {
                call.respondText("访问码不正确", status = HttpStatusCode.Unauthorized)
                return@post
            }
            call.response.cookies.append(
                Cookie(
                    name = SESSION_COOKIE_NAME,
                    value = sessionId,
                    path = "/",
                    httpOnly = true,
                    maxAge = (WebConsoleRuntime.SESSION_TTL_MILLIS / 1000L).toInt()
                )
            )
            call.respond(HttpStatusCode.NoContent)
        }

        post("/api/web-console/v1/auth/logout") {
            handler.logout(call.request.cookies[SESSION_COOKIE_NAME])
            call.response.cookies.appendExpired(SESSION_COOKIE_NAME, path = "/")
            call.respond(HttpStatusCode.NoContent)
        }

        get("/api/web-console/v1/session") {
            val session = call.requireSession(handler) ?: return@get
            call.respond(session)
        }

        get("/api/web-console/v1/dashboard") {
            if (call.requireSession(handler) == null) return@get
            call.respond(handler.getDashboard())
        }

        get("/api/web-console/v1/decks") {
            if (call.requireSession(handler) == null) return@get
            call.respond(handler.listDecks())
        }

        post("/api/web-console/v1/decks/upsert") {
            if (call.requireSession(handler) == null) return@post
            call.respond(handler.upsertDeck(call.receive()))
        }

        post("/api/web-console/v1/decks/archive") {
            if (call.requireSession(handler) == null) return@post
            val request = call.receive<WebConsoleArchiveRequest>()
            call.respond(handler.archiveDeck(request.id, request.archived))
        }

        get("/api/web-console/v1/cards") {
            if (call.requireSession(handler) == null) return@get
            val deckId = call.parameters["deckId"]
            if (deckId.isNullOrBlank()) {
                call.respondText("缺少 deckId", status = HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(handler.listCards(deckId))
        }

        post("/api/web-console/v1/cards/upsert") {
            if (call.requireSession(handler) == null) return@post
            call.respond(handler.upsertCard(call.receive()))
        }

        post("/api/web-console/v1/cards/archive") {
            if (call.requireSession(handler) == null) return@post
            val request = call.receive<WebConsoleArchiveRequest>()
            call.respond(handler.archiveCard(request.id, request.archived))
        }

        get("/api/web-console/v1/questions") {
            if (call.requireSession(handler) == null) return@get
            val cardId = call.parameters["cardId"]
            if (cardId.isNullOrBlank()) {
                call.respondText("缺少 cardId", status = HttpStatusCode.BadRequest)
                return@get
            }
            call.respond(handler.listQuestions(cardId))
        }

        post("/api/web-console/v1/questions/upsert") {
            if (call.requireSession(handler) == null) return@post
            call.respond(handler.upsertQuestion(call.receive()))
        }

        post("/api/web-console/v1/questions/delete") {
            if (call.requireSession(handler) == null) return@post
            val request = call.receive<WebConsoleDeleteRequest>()
            call.respond(handler.deleteQuestion(request.id))
        }

        post("/api/web-console/v1/search") {
            if (call.requireSession(handler) == null) return@post
            call.respond(handler.search(call.receive()))
        }

        get("/api/web-console/v1/analytics") {
            if (call.requireSession(handler) == null) return@get
            call.respond(handler.getAnalytics())
        }

        get("/api/web-console/v1/settings") {
            if (call.requireSession(handler) == null) return@get
            call.respond(handler.getSettings())
        }

        post("/api/web-console/v1/settings/update") {
            if (call.requireSession(handler) == null) return@post
            call.respond(handler.updateSettings(call.receive()))
        }

        get("/api/web-console/v1/backup/export") {
            if (call.requireSession(handler) == null) return@get
            val payload = handler.exportBackup()
            call.respondText(
                text = payload.content,
                contentType = ContentType.Application.Json,
                status = HttpStatusCode.OK
            )
        }

        get("/{...}") {
            val requestedPath = call.request.path().removePrefix("/")
            val asset = assetLoader.load(requestedPath) ?: assetLoader.load("/")
            if (asset == null) {
                call.respondText("Not Found", status = HttpStatusCode.NotFound)
                return@get
            }
            call.respondBytes(asset.bytes, contentType = asset.contentType)
        }
    }
}

/**
 * 会话校验放在统一扩展里，是为了让每个 API 路由只表达业务意图，不重复拼装 Cookie 与来源守卫。
 */
private suspend fun io.ktor.server.application.ApplicationCall.requireSession(
    handler: WebConsoleApiHandler
): WebConsoleSessionPayload? {
    val remoteHost = request.origin.remoteHost
    if (!remoteHost.isAllowedLocalNetworkHost()) {
        respondText("Forbidden", status = HttpStatusCode.Forbidden)
        return null
    }
    val sessionId = request.cookies[SESSION_COOKIE_NAME]
    if (sessionId == null) {
        respondText("未登录", status = HttpStatusCode.Unauthorized)
        return null
    }
    val session = handler.resolveSession(sessionId, remoteHost)
    if (session == null) {
        response.cookies.appendExpired(SESSION_COOKIE_NAME, path = "/")
        respondText("登录已失效", status = HttpStatusCode.Unauthorized)
        return null
    }
    return session
}

/**
 * 只允许来自本地网络的访问，是为了继续保持“局域网/热点显式开启”的产品边界，不把控制台暴露给外部网络。
 */
internal fun String.isAllowedLocalNetworkHost(): Boolean = runCatching {
    val address = java.net.InetAddress.getByName(this)
    when {
        address.isLoopbackAddress -> true
        address.isSiteLocalAddress -> true
        address.isLinkLocalAddress -> true
        address is java.net.Inet6Address -> {
            val firstByte = address.address.firstOrNull()?.toInt() ?: return@runCatching false
            (firstByte and 0xFE) == 0xFC
        }
        else -> false
    }
}.getOrDefault(false)

/**
 * 归档接口只需要传递对象 id 和目标状态，是为了让前端切换归档时保持最小请求体。
 */
@kotlinx.serialization.Serializable
internal data class WebConsoleArchiveRequest(
    val id: String,
    val archived: Boolean
)

/**
 * 删除接口只需要稳定 id，是为了把危险动作继续收敛为“显式选择具体对象”的最小协议。
 */
@kotlinx.serialization.Serializable
internal data class WebConsoleDeleteRequest(
    val id: String
)

private const val SESSION_COOKIE_NAME = "yike_web_session"
