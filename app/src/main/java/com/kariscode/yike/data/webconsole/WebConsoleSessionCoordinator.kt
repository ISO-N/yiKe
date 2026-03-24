package com.kariscode.yike.data.webconsole

import android.content.Context
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.WebConsoleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 网页后台会话协作者把服务生命周期、访问码与浏览器登录边界集中到一处，
 * 是为了让仓储外观不再同时承担运行时与工作区编排两类职责。
 */
internal class WebConsoleSessionCoordinator(
    context: Context,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    handler: WebConsoleApiHandler
) {
    val runtime = WebConsoleRuntime(timeProvider = timeProvider)

    private val addressProvider = WebConsoleAddressProvider()
    private val httpServer = WebConsoleHttpServer(
        portAllocator = WebConsolePortAllocator(),
        assetLoader = WebConsoleAssetLoader(context.applicationContext),
        handler = handler
    )

    /**
     * 运行时状态从协作者直接暴露，是为了让手机页、前台服务和仓储外观仍围绕同一份状态源观察。
     */
    fun observeState(): Flow<WebConsoleState> = runtime.state

    /**
     * 服务启动继续由统一入口完成，是为了避免地址探测、端口绑定和访问码初始化再次分散到多个类里。
     */
    suspend fun startServer() = withContext(dispatchers.io) {
        if (runtime.state.value.isRunning) return@withContext
        runtime.markStarting()
        runCatching {
            httpServer.start()
            val addresses = addressProvider.getAccessibleAddresses(httpServer.port)
            require(addresses.isNotEmpty()) { "未检测到可用于局域网访问的地址，请确认 Wi‑Fi 或热点已开启" }
            runtime.activate(port = httpServer.port, addresses = addresses)
        }.onFailure { throwable ->
            httpServer.stop()
            runtime.markFailure(throwable.message ?: "网页后台启动失败")
            throw throwable
        }
    }

    /**
     * 停止时统一清空运行时，是为了继续锁住“服务停了，所有浏览器上下文都必须失效”的安全边界。
     */
    suspend fun stopServer() = withContext(dispatchers.io) {
        httpServer.stop()
        runtime.deactivate()
    }

    /**
     * 访问码刷新保持在协作者内完成，是为了保证会话与学习快照一起在同一事务语义下失效。
     */
    suspend fun refreshAccessCode() = withContext(dispatchers.io) {
        runtime.rotateAccessCode()
    }

    /**
     * 登录只接受局域网来源与当前访问码，是为了继续维持网页后台的本地可信边界。
     */
    suspend fun login(code: String, remoteHost: String): String? = withContext(dispatchers.io) {
        if (!remoteHost.isAllowedLocalNetworkHost()) return@withContext null
        if (!runtime.matchesAccessCode(code.trim())) return@withContext null
        runtime.createSession()
    }

    /**
     * 主动退出仅清理当前会话，是为了让同一浏览器的其他标签页仍按独立登录边界工作。
     */
    suspend fun logout(sessionId: String?) = withContext(dispatchers.io) {
        runtime.removeSession(sessionId)
    }

    /**
     * 会话解析顺带续期和来源校验，是为了让路由层始终只关心是否放行，而不碰运行时细节。
     */
    suspend fun resolveSession(sessionId: String, remoteHost: String): WebConsoleSessionPayload? =
        withContext(dispatchers.io) {
            if (!remoteHost.isAllowedLocalNetworkHost() || !runtime.touchSession(sessionId)) return@withContext null
            val state = runtime.state.value
            val recommendedAddress = state.addresses.firstOrNull { it.isRecommended } ?: state.addresses.firstOrNull()
            WebConsoleSessionPayload(
                displayName = "忆刻网页后台",
                port = recommendedAddress?.port ?: 0,
                activeSessionCount = state.activeSessionCount
            )
        }
}
