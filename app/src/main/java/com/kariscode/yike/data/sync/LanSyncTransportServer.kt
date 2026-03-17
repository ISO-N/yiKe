package com.kariscode.yike.data.sync

/**
 * 传输服务端接口把端口生命周期从仓储中解耦出来，便于测试直接替换成假服务。
 */
interface LanSyncTransportServer {
    /**
     * 当前监听端口需要暴露给广播注册逻辑，以保持发现信息准确。
     */
    val port: Int

    /**
     * 会话开始时启动监听，测试可通过假实现记录是否被调用。
     */
    fun start()

    /**
     * 会话结束后必须关闭监听，避免页面退出后仍暴露局域网入口。
     */
    fun stop()
}
