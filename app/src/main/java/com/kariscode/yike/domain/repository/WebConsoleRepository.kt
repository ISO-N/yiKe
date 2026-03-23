package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.WebConsoleState
import kotlinx.coroutines.flow.Flow

/**
 * 网页后台仓储对外只暴露服务生命周期与状态观察，
 * 是为了让手机页和前台服务不必感知 Ktor、Cookie 或资产加载等实现细节。
 */
interface WebConsoleRepository {
    /**
     * 通过 Flow 暴露状态可以让手机页与前台通知在访问码轮换或地址变化时自然同步，
     * 避免两个入口分别维护一份过期快照。
     */
    fun observeState(): Flow<WebConsoleState>

    /**
     * 启动网页后台时由仓储统一处理端口、地址与访问码准备，
     * 是为了保持“能否真正对外访问”的判断集中在同一处。
     */
    suspend fun startServer()

    /**
     * 停止服务时统一清空会话和监听端口，
     * 是为了让用户一键回到完全离线状态，而不是只隐藏入口却继续暴露局域网能力。
     */
    suspend fun stopServer()

    /**
     * 访问码刷新单独暴露出来，是为了在不重启服务的情况下快速使旧浏览器会话失效。
     */
    suspend fun refreshAccessCode()
}
