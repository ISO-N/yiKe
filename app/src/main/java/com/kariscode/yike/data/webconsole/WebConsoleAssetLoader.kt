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
        val normalizedAssetPath = path.trim()
            .removePrefix("/")
            .ifBlank { "index.html" }
            .let { assetPath ->
                when {
                    assetPath.contains("..") -> return null
                    else -> "webconsole/$assetPath"
                }
            }
        val bytes = runCatching {
            context.assets.open(normalizedAssetPath).use { input ->
                input.readBytes()
            }
        }.getOrNull() ?: return null
        return WebConsoleAsset(
            bytes = bytes,
            contentType = normalizedAssetPath.resolveContentType()
        )
    }
}

/**
 * 资源类型按扩展名集中判定，是为了让模块化后的脚本、样式和图片目录不必继续在路由层追加硬编码分支。
 */
private fun String.resolveContentType(): ContentType = when {
    endsWith(".css") -> ContentType.Text.CSS
    endsWith(".js") -> ContentType.Application.JavaScript
    endsWith(".json") -> ContentType.Application.Json
    endsWith(".svg") -> ContentType.Image.SVG
    endsWith(".png") -> ContentType.Image.PNG
    endsWith(".jpg") || endsWith(".jpeg") -> ContentType.Image.JPEG
    endsWith(".woff2") -> ContentType.parse("font/woff2")
    endsWith(".woff") -> ContentType.parse("font/woff")
    else -> ContentType.Text.Html
}

/**
 * 资产值对象只保留响应必需字段，是为了让 Ktor 路由可以稳定复用而不泄漏 Android 资源细节。
 */
internal data class WebConsoleAsset(
    val bytes: ByteArray,
    val contentType: ContentType
)
