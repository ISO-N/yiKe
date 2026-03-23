package com.kariscode.yike.domain.model

/**
 * 网页后台状态把服务生命周期、访问码和可访问地址收口到单一模型，
 * 是为了让手机页、前台通知和后续网页自检接口共享同一份事实来源。
 */
data class WebConsoleState(
    val isRunning: Boolean,
    val isStarting: Boolean,
    val port: Int?,
    val addresses: List<WebConsoleAddress>,
    val accessCode: String?,
    val accessCodeIssuedAt: Long?,
    val activeSessionCount: Int,
    val lastStartedAt: Long?,
    val lastError: String?
)
