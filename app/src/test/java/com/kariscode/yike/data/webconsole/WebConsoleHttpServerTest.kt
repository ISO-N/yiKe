package com.kariscode.yike.data.webconsole

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 网页后台 HTTP 测试锁住导出响应头这类浏览器契约，
 * 是为了避免控制台明明还能返回 JSON，却悄悄丢掉下载文件名这类用户直接可感知的体验细节。
 */
@RunWith(RobolectricTestRunner::class)
class WebConsoleHttpServerTest {
    private var restoredBackupRequest: WebConsoleBackupRestoreRequest? = null

    /**
     * 备份导出必须携带服务端建议文件名，
     * 否则浏览器只能退回时间戳临时名，用户后续整理和识别备份文件会明显变难。
     */
    @Test
    fun exportBackup_setsAttachmentFileNameHeader() = testApplication {
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.get("/api/web-console/v1/backup/export") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            """attachment; filename="yike-backup-test.json"""",
            response.headers[HttpHeaders.ContentDisposition]
        )
        assertEquals("""{"version":1}""", response.bodyAsText())
    }

    /**
     * 备份恢复应把网页上传的 JSON 原样交给业务处理器，
     * 这样浏览器端的文件读取和手机端的本地导入才能真正共用同一恢复语义。
     */
    @Test
    fun restoreBackup_passesUploadedPayloadToHandler() = testApplication {
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.post("/api/web-console/v1/backup/restore") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                WebConsoleJson.json.encodeToString(
                    WebConsoleBackupRestoreRequest.serializer(),
                    WebConsoleBackupRestoreRequest(
                        fileName = "restore.json",
                        content = """{"app":{"backupVersion":1}}"""
                    )
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("备份已恢复"))
        assertEquals("restore.json", restoredBackupRequest?.fileName)
        assertEquals("""{"app":{"backupVersion":1}}""", restoredBackupRequest?.content)
    }

    /**
     * 测试处理器集中在单点构造，是为了让路由测试只描述当前关心的契约，而不用在每个用例里重复铺开整套桩实现。
     */
    private fun createHandler(): WebConsoleApiHandler = object : WebConsoleApiHandler {
        override suspend fun login(code: String, remoteHost: String): String? = null

        override suspend fun logout(sessionId: String?) = Unit

        override suspend fun resolveSession(
            sessionId: String,
            remoteHost: String
        ): WebConsoleSessionPayload? = if (sessionId == "session_1") {
            WebConsoleSessionPayload(
                displayName = "忆刻网页后台",
                port = 9440,
                activeSessionCount = 1
            )
        } else {
            null
        }

        override suspend fun getDashboard(): WebConsoleDashboardPayload = error("unused")

        override suspend fun listDecks(): List<WebConsoleDeckPayload> = error("unused")

        override suspend fun upsertDeck(request: WebConsoleUpsertDeckRequest): WebConsoleMutationPayload = error("unused")

        override suspend fun archiveDeck(deckId: String, archived: Boolean): WebConsoleMutationPayload = error("unused")

        override suspend fun listCards(deckId: String): List<WebConsoleCardPayload> = error("unused")

        override suspend fun upsertCard(request: WebConsoleUpsertCardRequest): WebConsoleMutationPayload = error("unused")

        override suspend fun archiveCard(cardId: String, archived: Boolean): WebConsoleMutationPayload = error("unused")

        override suspend fun listQuestions(cardId: String): List<WebConsoleQuestionPayload> = error("unused")

        override suspend fun upsertQuestion(request: WebConsoleUpsertQuestionRequest): WebConsoleMutationPayload = error("unused")

        override suspend fun deleteQuestion(questionId: String): WebConsoleMutationPayload = error("unused")

        override suspend fun search(request: WebConsoleSearchRequest): List<WebConsoleSearchResultPayload> = error("unused")

        override suspend fun getAnalytics(): WebConsoleAnalyticsPayload = error("unused")

        override suspend fun getSettings(): WebConsoleSettingsPayload = error("unused")

        override suspend fun updateSettings(request: WebConsoleUpdateSettingsRequest): WebConsoleMutationPayload = error("unused")

        override suspend fun exportBackup(): WebConsoleBackupExportPayload = WebConsoleBackupExportPayload(
            fileName = "yike-backup-test.json",
            content = """{"version":1}"""
        )

        override suspend fun restoreBackup(request: WebConsoleBackupRestoreRequest): WebConsoleMutationPayload {
            restoredBackupRequest = request
            return WebConsoleMutationPayload(message = "备份已恢复")
        }
    }
}
