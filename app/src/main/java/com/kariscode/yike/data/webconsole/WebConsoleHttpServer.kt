package com.kariscode.yike.data.webconsole

import io.ktor.http.Cookie
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException

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
    install(StatusPages) {
        /**
         * 统一把参数和校验异常转换成稳定文案，是为了让网页控制台能直接展示业务错误，而不是回落到模糊的 500 失败。
         */
        exception<IllegalArgumentException> { call, throwable ->
            call.respondText(
                throwable.message ?: "请求参数不合法",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<IllegalStateException> { call, throwable ->
            call.respondText(
                throwable.message ?: "当前操作无法完成",
                status = HttpStatusCode.BadRequest
            )
        }
        exception<SerializationException> { call, _ ->
            call.respondText(
                "请求内容格式不正确",
                status = HttpStatusCode.BadRequest
            )
        }
    }
    routing {
        registerAuthRoutes(handler)
        registerStudyRoutes(handler)
        registerContentRoutes(handler)
        registerSettingsRoutes(handler)
        registerAssetRoutes(assetLoader)
    }
}

/**
 * 认证路由单独注册，是为了让登录和会话守卫的边界在后续继续扩展时不被内容工作区路由冲淡。
 */
private fun Route.registerAuthRoutes(handler: WebConsoleApiHandler) {
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
        call.clearSessionCookie()
        call.respond(HttpStatusCode.NoContent)
    }

    get("/api/web-console/v1/session") {
        val session = call.requireSession(handler) ?: return@get
        call.respond(session)
    }
}

/**
 * 学习工作区路由集中注册，是为了让正式复习和自由练习的会话边界继续作为一组协作协议维护。
 */
private fun Route.registerStudyRoutes(handler: WebConsoleApiHandler) {
    get("/api/web-console/v1/study/workspace") {
        if (call.requireSession(handler) == null) return@get
        call.respond(handler.getStudyWorkspace(call.requireSessionId()))
    }

    get("/api/web-console/v1/study/session") {
        if (call.requireSession(handler) == null) return@get
        val payload = handler.getStudySession(call.requireSessionId())
        if (payload == null) {
            call.respond(HttpStatusCode.NoContent)
            return@get
        }
        call.respond(payload)
    }

    post("/api/web-console/v1/study/review/start") {
        if (call.requireSession(handler) == null) return@post
        call.respond(handler.startReviewSession(call.requireSessionId()))
    }

    post("/api/web-console/v1/study/answer/reveal") {
        if (call.requireSession(handler) == null) return@post
        call.respond(handler.revealStudyAnswer(call.requireSessionId()))
    }

    post("/api/web-console/v1/study/review/rate") {
        if (call.requireSession(handler) == null) return@post
        call.respond(
            handler.submitReviewRating(
                sessionId = call.requireSessionId(),
                request = call.receive()
            )
        )
    }

    post("/api/web-console/v1/study/review/next-card") {
        if (call.requireSession(handler) == null) return@post
        call.respond(handler.continueReviewSession(call.requireSessionId()))
    }

    post("/api/web-console/v1/study/practice/start") {
        if (call.requireSession(handler) == null) return@post
        call.respond(
            handler.startPracticeSession(
                sessionId = call.requireSessionId(),
                request = call.receive()
            )
        )
    }

    post("/api/web-console/v1/study/practice/navigate") {
        if (call.requireSession(handler) == null) return@post
        call.respond(
            handler.navigatePracticeSession(
                sessionId = call.requireSessionId(),
                request = call.receive()
            )
        )
    }

    post("/api/web-console/v1/study/session/end") {
        if (call.requireSession(handler) == null) return@post
        call.respond(handler.endStudySession(call.requireSessionId()))
    }
}

/**
 * 内容与统计路由聚合注册，是为了让富后台工作区扩展时可以按对象管理边界稳定增减接口。
 */
private fun Route.registerContentRoutes(handler: WebConsoleApiHandler) {
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
}

/**
 * 设置和备份路由集中放在单点，是为了把高风险配置操作维持在统一反馈和回滚边界里。
 */
private fun Route.registerSettingsRoutes(handler: WebConsoleApiHandler) {
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
        call.response.header(
            HttpHeaders.ContentDisposition,
            """attachment; filename="${payload.fileName}""""
        )
        call.respondText(
            text = payload.content,
            contentType = ContentType.Application.Json,
            status = HttpStatusCode.OK
        )
    }

    post("/api/web-console/v1/backup/restore") {
        if (call.requireSession(handler) == null) return@post
        call.respond(handler.restoreBackup(call.receive()))
    }
}

/**
 * 静态资源路由单独挂载，是为了让前端目录结构升级后仍保持与 API 路由完全分离。
 */
private fun Route.registerAssetRoutes(assetLoader: WebConsoleAssetLoader) {
    get("/") {
        val asset = assetLoader.load("/")
        if (asset == null) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondBytes(asset.bytes, contentType = asset.contentType)
    }

    get("/{asset...}") {
        val requestedPath = call.parameters.getAll("asset").orEmpty().joinToString("/")
        val asset = assetLoader.load(requestedPath) ?: assetLoader.load("/")
        if (asset == null) {
            call.respondText("Not Found", status = HttpStatusCode.NotFound)
            return@get
        }
        call.respondBytes(asset.bytes, contentType = asset.contentType)
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
        clearSessionCookie()
        respondText("登录已失效", status = HttpStatusCode.Unauthorized)
        return null
    }
    return session
}

/**
 * 学习工作区在通过鉴权后仍需要稳定拿到当前 Cookie 对应的会话 id，
 * 是为了让业务层把学习进度准确挂回同一个浏览器登录上下文。
 */
private fun io.ktor.server.application.ApplicationCall.requireSessionId(): String =
    request.cookies[SESSION_COOKIE_NAME] ?: error("未登录")

/**
 * 失效会话统一用显式过期 Cookie 覆盖，是为了避免各路由各自处理清理逻辑后出现行为漂移。
 */
private fun io.ktor.server.application.ApplicationCall.clearSessionCookie() {
    response.cookies.append(
        Cookie(
            name = SESSION_COOKIE_NAME,
            value = "",
            path = "/",
            maxAge = 0
        )
    )
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
