package com.kariscode.yike.data.webconsole

import android.content.Context
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.defaultForFilePath
import java.io.File

private val friendlyRouteMap = mapOf(
    "" to "index.html",
    "login" to "login.html",
    "logout" to "logout.html",
    "overview" to "app.html",
    "study" to "app.html",
    "content" to "app.html",
    "search" to "app.html",
    "analytics" to "app.html",
    "settings" to "app.html",
    "backup" to "app.html"
)

/**
 * 资源加载器保留在数据层单点，是为了让网页后台的友好路由和静态资源仍沿用同一份本地资产根目录。
 */
internal class WebConsoleAssetLoader(
    private val context: Context
) {
    /**
     * 友好路径会回退到正式页面资产，是为了让浏览器直接访问 `/study` 这类路径时不必显式带 `.html`。
     */
    fun load(path: String): WebConsoleAsset? {
        if (path.contains("..")) {
            return null
        }
        val candidateAssetPaths = resolveCandidateAssetPaths(path)
        for (assetPath in candidateAssetPaths) {
            val bytes = loadAssetBytes(assetPath) ?: continue
            return WebConsoleAsset(
                bytes = bytes,
                contentType = assetPath.resolveContentType()
            )
        }
        return null
    }

    /**
     * 同一路径允许尝试友好页、原始文件与 `.html` 回退，是为了兼容正式页面路由和脚本样式资源共存。
     */
    private fun resolveCandidateAssetPaths(path: String): List<String> {
        val normalizedPath = path.trim().removePrefix("/").removeSuffix("/")
        val candidates = buildList {
            val friendlyAsset = friendlyRouteMap[normalizedPath]
            if (friendlyAsset != null) {
                add(friendlyAsset)
            }
            if (normalizedPath.isNotBlank()) {
                add(normalizedPath)
                if (!normalizedPath.contains('.')) {
                    add("$normalizedPath.html")
                }
            } else if (friendlyAsset == null) {
                add("index.html")
            }
        }
        return candidates.distinct()
    }

    /**
     * 读取 assets 失败后回退到源码目录，是为了让 Robolectric 在资产尚未重新合并时也能校验最新网页入口。
     */
    private fun loadAssetBytes(assetPath: String): ByteArray? {
        val packagedAssetBytes = runCatching {
            context.assets.open("webconsole/$assetPath").use { input ->
                input.readBytes()
            }
        }.getOrNull()
        if (packagedAssetBytes != null) {
            return packagedAssetBytes
        }
        val workingDirectory = File(System.getProperty("user.dir") ?: ".")
        val sourceAssetFile = listOf(
            File("app/src/main/assets/webconsole/$assetPath"),
            File("src/main/assets/webconsole/$assetPath"),
            File(workingDirectory, "app/src/main/assets/webconsole/$assetPath"),
            File(workingDirectory, "src/main/assets/webconsole/$assetPath")
        ).firstOrNull { candidate -> candidate.isFile }
        return sourceAssetFile?.readBytes()
    }
}

private fun String.resolveContentType(): ContentType {
    return ContentType.defaultForFilePath(this)
}

/**
 * 资源响应结构保持极薄，是为了让 HTTP 层只关心字节和类型，不耦合路径推导细节。
 */
internal data class WebConsoleAsset(
    val bytes: ByteArray,
    val contentType: ContentType
)
