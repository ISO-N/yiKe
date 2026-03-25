package com.kariscode.yike.navigation

import android.net.Uri

/**
 * 将 Shortcuts/Widget 等系统级入口统一映射到固定 Uri，是为了让入口协议不依赖 extras，
 * 并在静态 shortcuts（不支持 extras）与 RemoteViews PendingIntent 之间共享同一套解析逻辑。
 */
object YikeAppLinks {
    private const val SCHEME = "yike"
    private const val HOST_SHORTCUT = "shortcut"

    private const val PATH_REVIEW = "/review"
    private const val PATH_NEW_CARD = "/new-card"

    /**
     * 快速复习入口固定为 Uri，是为了让任何系统入口都能用同一串字符串触发复习队列导航。
     */
    fun shortcutReviewUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST_SHORTCUT)
        .path(PATH_REVIEW.removePrefix("/"))
        .build()

    /**
     * 新建卡片入口固定为 Uri，是为了让后续把“选择最近卡组 + 自动打开新建弹窗”能力
     * 统一封装到启动解析处，而不需要 Shortcuts 本身携带额外上下文。
     */
    fun shortcutNewCardUri(): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(HOST_SHORTCUT)
        .path(PATH_NEW_CARD.removePrefix("/"))
        .build()

    /**
     * 使用显式判定而不是基于 path 前缀，是为了避免未来扩展更多入口时出现误匹配。
     */
    fun isShortcutReview(uri: Uri): Boolean =
        uri.scheme == SCHEME &&
            uri.authority == HOST_SHORTCUT &&
            uri.path == PATH_REVIEW

    /**
     * 新建卡片入口的判定单独抽出，是为了让解析侧可以延迟引入“最近卡组策略”，
     * 而 Shortcuts/Widget 仍保持纯静态协议。
     */
    fun isShortcutNewCard(uri: Uri): Boolean =
        uri.scheme == SCHEME &&
            uri.authority == HOST_SHORTCUT &&
            uri.path == PATH_NEW_CARD
}

