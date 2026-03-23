package com.kariscode.yike.data.webconsole

import android.content.Context
import io.ktor.http.ContentType

/**
 * 静态资源加载独立出来，是为了让路由层只关心“返回哪个资源”，
 * 不需要直接碰 Android assets API 和内容类型判断细节。
 */
internal class WebConsoleAssetLoader(
    private val context: Context
) {
    /**
     * 缺省路径回退到首页，是为了让用户只输入 IP:端口 就能直接进入网页后台入口。
     */
    fun load(path: String): WebConsoleAsset? {
        val normalizedPath = when (path.trim().removePrefix("/")) {
            "", "index.html" -> "webconsole/index.html"
            "app.css" -> "webconsole/app.css"
            "app.js" -> "webconsole/app.js"
            else -> null
        } ?: return null
        val bytes = runCatching {
            context.assets.open(normalizedPath).use { input ->
                input.readBytes()
            }
        }.getOrNull() ?: return null
        return WebConsoleAsset(
            bytes = bytes,
            contentType = when {
                normalizedPath.endsWith(".css") -> ContentType.Text.CSS
                normalizedPath.endsWith(".js") -> ContentType.Application.JavaScript
                else -> ContentType.Text.Html
            }
        )
    }
}

/**
 * 资产值对象只保留响应必需字段，是为了让 Ktor 路由可以稳定复用而不泄漏 Android 资源细节。
 */
internal data class WebConsoleAsset(
    val bytes: ByteArray,
    val contentType: ContentType
)
