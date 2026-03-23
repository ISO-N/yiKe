package com.kariscode.yike.domain.model

/**
 * 网页后台地址单独建模，是为了让手机页和通知都围绕同一份可访问地址展示，
 * 避免端口、主机和完整 URL 在多个层次分别拼接后逐渐漂移。
 */
data class WebConsoleAddress(
    val label: String,
    val host: String,
    val port: Int,
    val url: String,
    val isRecommended: Boolean
)
