package com.kariscode.yike.data.webconsole

import java.net.ServerSocket

/**
 * 网页后台端口单独扫描固定区间，是为了让排障和用户提示都围绕稳定端口段工作，
 * 而不是每次随机落在难以预判的位置。
 */
internal class WebConsolePortAllocator(
    private val startPort: Int = PORT_RANGE_START,
    private val endPort: Int = PORT_RANGE_END
) {
    /**
     * 在专用端口段里顺序寻找可用端口，可以把冲突行为收敛为稳定且可解释的回退策略。
     */
    fun findAvailablePort(): Int {
        for (port in startPort..endPort) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        error("未找到可用的网页后台端口")
    }

    /**
     * 启动服务前先做一次显式占用检测，是为了在真正绑定 Ktor 之前尽早发现本机端口冲突。
     */
    private fun isPortAvailable(port: Int): Boolean = runCatching {
        ServerSocket(port).use { socket ->
            socket.reuseAddress = true
        }
    }.isSuccess

    companion object {
        const val PORT_RANGE_START: Int = 9440
        const val PORT_RANGE_END: Int = 9459
    }
}
