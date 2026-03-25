package com.kariscode.yike.data.webconsole

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.domain.model.WebConsoleAddress
import com.kariscode.yike.domain.model.WebConsoleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * 网页后台运行时把访问码、活跃会话和服务状态集中到一处，
 * 是为了让前台服务、Ktor 路由和手机页都围绕同一份可变状态协作。
 */
internal class WebConsoleRuntime(
    private val timeProvider: TimeProvider
) {
    private val sessionExpirations = linkedMapOf<String, Long>()
    private val studySessions = linkedMapOf<String, WebConsoleStudySession>()

    val state = MutableStateFlow(
        WebConsoleState(
            isRunning = false,
            isStarting = false,
            port = null,
            addresses = emptyList(),
            accessCode = null,
            accessCodeIssuedAt = null,
            activeSessionCount = 0,
            lastStartedAt = null,
            lastError = null
        )
    )

    /**
     * 启动前先切到“准备中”，是为了让手机页和通知在端口扫描期间能明确表达当前不是卡死而是在启动。
     */
    @Synchronized
    fun markStarting() {
        state.update { current ->
            current.copy(
                isStarting = true,
                lastError = null
            )
        }
    }

    /**
     * 服务真正可访问后一次性写入端口、地址和访问码，是为了让外部观察者只看到完整就绪态而不是半成品状态。
     */
    @Synchronized
    fun activate(port: Int, addresses: List<WebConsoleAddress>) {
        val issuedAt = timeProvider.nowEpochMillis()
        val accessCode = WebConsoleAccessCodeGenerator.generate()
        sessionExpirations.clear()
        studySessions.clear()
        state.value = WebConsoleState(
            isRunning = true,
            isStarting = false,
            port = port,
            addresses = addresses,
            accessCode = accessCode,
            accessCodeIssuedAt = issuedAt,
            activeSessionCount = 0,
            lastStartedAt = issuedAt,
            lastError = null
        )
    }

    /**
     * 停止服务时连同访问码和历史会话一起清空，是为了让下一次启动回到全新的可信边界。
     */
    @Synchronized
    fun deactivate() {
        sessionExpirations.clear()
        studySessions.clear()
        state.update { current ->
            current.copy(
                isRunning = false,
                isStarting = false,
                port = null,
                addresses = emptyList(),
                accessCode = null,
                accessCodeIssuedAt = null,
                activeSessionCount = 0
            )
        }
    }

    /**
     * 访问码轮换时同步让旧会话失效，是为了避免“码已刷新但旧浏览器仍可继续操作”的安全落差。
     */
    @Synchronized
    fun rotateAccessCode() {
        if (!state.value.isRunning) {
            return
        }
        sessionExpirations.clear()
        studySessions.clear()
        state.update { current ->
            current.copy(
                accessCode = WebConsoleAccessCodeGenerator.generate(),
                accessCodeIssuedAt = timeProvider.nowEpochMillis(),
                activeSessionCount = 0
            )
        }
    }

    /**
     * 启动失败时保留错误信息而不伪造运行态，是为了让手机页能给出清晰反馈并允许用户重试。
     */
    @Synchronized
    fun markFailure(message: String) {
        sessionExpirations.clear()
        studySessions.clear()
        state.update { current ->
            current.copy(
                isRunning = false,
                isStarting = false,
                port = null,
                addresses = emptyList(),
                accessCode = null,
                accessCodeIssuedAt = null,
                activeSessionCount = 0,
                lastError = message
            )
        }
    }

    /**
     * 登录成功后签发短期会话 ID，可以避免在后续每个请求里重复回传访问码。
     */
    @Synchronized
    fun createSession(): String {
        pruneExpiredSessions()
        val sessionId = UUID.randomUUID().toString()
        sessionExpirations[sessionId] = timeProvider.nowEpochMillis() + SESSION_TTL_MILLIS
        refreshSessionCount()
        return sessionId
    }

    /**
     * 会话校验时顺便续期，是为了让用户在持续操作时不会因为固定过期点被频繁踢回登录页。
     */
    @Synchronized
    fun touchSession(sessionId: String): Boolean {
        pruneExpiredSessions()
        val expiresAt = sessionExpirations[sessionId] ?: return false
        sessionExpirations[sessionId] = maxOf(expiresAt, timeProvider.nowEpochMillis()) + SESSION_TTL_MILLIS
        refreshSessionCount()
        return true
    }

    /**
     * 主动退出只移除当前会话，是为了避免同一台电脑多个标签页被无差别清空。
     */
    @Synchronized
    fun removeSession(sessionId: String?) {
        if (sessionId == null) {
            return
        }
        sessionExpirations.remove(sessionId)
        studySessions.remove(sessionId)
        refreshSessionCount()
    }

    /**
     * 浏览器学习会话挂在登录会话下，是为了让刷新恢复与访问码失效统一共享同一条生命周期边界。
     */
    @Synchronized
    fun getStudySession(sessionId: String): WebConsoleStudySession? {
        pruneExpiredSessions()
        return studySessions[sessionId]
    }

    /**
     * 学习会话每次变更都整份覆盖写回，是为了让运行时只维护已经规范化完成的稳定快照。
     */
    @Synchronized
    fun putStudySession(sessionId: String, session: WebConsoleStudySession) {
        pruneExpiredSessions()
        studySessions[sessionId] = session
    }

    /**
     * 学习会话显式结束后立即移除，是为了让浏览器返回工作区时不会误恢复到已放弃的旧进度。
     */
    @Synchronized
    fun clearStudySession(sessionId: String) {
        pruneExpiredSessions()
        studySessions.remove(sessionId)
    }

    /**
     * 访问码校验集中在运行时，是为了让路由层只表达“要不要放行”，不重复感知当前有效访问码。
     */
    @Synchronized
    fun matchesAccessCode(code: String): Boolean = state.value.isRunning && state.value.accessCode == code

    /**
     * 过期会话统一在运行时清理后再回写状态，是为了保证通知和手机页展示的在线会话数始终可信。
     */
    @Synchronized
    private fun pruneExpiredSessions() {
        val now = timeProvider.nowEpochMillis()
        val iterator = sessionExpirations.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value < now) {
                studySessions.remove(entry.key)
                iterator.remove()
            }
        }
        refreshSessionCount()
    }

    /**
     * 活跃会话数量回写到状态流，是为了让手机页能及时判断是否适合刷新访问码或直接停止服务。
     */
    @Synchronized
    private fun refreshSessionCount() {
        state.update { current ->
            current.copy(activeSessionCount = sessionExpirations.size)
        }
    }

    companion object {
        const val SESSION_TTL_MILLIS: Long = 30 * 60 * 1000L
    }
}

