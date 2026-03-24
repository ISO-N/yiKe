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
    private var practiceNavigateRequest: WebConsolePracticeNavigateRequest? = null
    private var activeStudySession: WebConsoleStudySessionPayload? = null
    private var dashboardPayload: WebConsoleDashboardPayload? = null
    private var settingsPayload: WebConsoleSettingsPayload? = null
    private var endedStudySessionId: String? = null

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
     * 学习会话恢复接口在存在活动会话时必须直接返回当前题位，
     * 否则浏览器刷新后就无法稳定回到最近一次有效学习上下文。
     */
    @Test
    fun getStudySession_returnsCurrentStudyPayload() = testApplication {
        activeStudySession = WebConsoleStudySessionPayload(
            type = WebConsoleStudySessionTypes.REVIEW,
            title = "今日复习",
            summary = "基础短语 · 第 1 / 2 题",
            review = WebConsoleReviewStudyPayload(
                deckName = "英语",
                cardTitle = "基础短语",
                cardProgressText = "第 1 / 1 张卡",
                questionProgressText = "第 1 / 2 题",
                completedQuestionCount = 0,
                totalQuestionCount = 2,
                answerVisible = false,
                currentQuestion = WebConsoleStudyQuestionPayload(
                    questionId = "question_1",
                    prompt = "hello",
                    answerText = "你好",
                    stageIndex = 0
                ),
                isCardCompleted = false,
                isSessionCompleted = false,
                nextCardTitle = null
            )
        )
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.get("/api/web-console/v1/study/session") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("今日复习"))
        assertTrue(response.bodyAsText().contains("question_1"))
    }

    /**
     * 非法切题动作必须在路由层返回稳定的 400 错误，
     * 否则浏览器端会退化成模糊失败而无法给出明确恢复动作。
     */
    @Test
    fun navigatePractice_invalidAction_returnsBadRequest() = testApplication {
        activeStudySession = WebConsoleStudySessionPayload(
            type = WebConsoleStudySessionTypes.PRACTICE,
            title = "自由练习",
            summary = "第 1 / 1 题 · 稳定顺序",
            practice = WebConsolePracticeStudyPayload(
                orderMode = "sequential",
                orderModeLabel = "顺序",
                progressText = "第 1 / 1 题",
                answerVisible = false,
                currentQuestion = WebConsolePracticeQuestionPayload(
                    questionId = "question_1",
                    deckName = "英语",
                    cardTitle = "基础短语",
                    prompt = "hello",
                    answerText = "你好"
                ),
                canGoPrevious = false,
                canGoNext = false,
                sessionSeed = null
            )
        )
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.post("/api/web-console/v1/study/practice/navigate") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
            header(HttpHeaders.ContentType, "application/json")
            setBody(
                WebConsoleJson.json.encodeToString(
                    WebConsolePracticeNavigateRequest.serializer(),
                    WebConsolePracticeNavigateRequest(action = "jump")
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("jump", practiceNavigateRequest?.action)
        assertTrue(response.bodyAsText().contains("练习切题动作不合法"))
    }

    /**
     * 无效会话必须在 API 层被拒绝并返回失效提示，
     * 否则浏览器切换工作区时会在“已失效但看起来还在线”的状态里反复撞墙。
     */
    @Test
    fun dashboard_invalidSession_returnsUnauthorized() = testApplication {
        dashboardPayload = WebConsoleDashboardPayload(
            dueCardCount = 1,
            dueQuestionCount = 2,
            recentDecks = emptyList()
        )
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.get("/api/web-console/v1/dashboard") {
            header(HttpHeaders.Cookie, "yike_web_session=expired_session")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertTrue(response.bodyAsText().contains("登录已失效"))
    }

    /**
     * 某个工作区请求失败后，其他工作区接口仍必须可访问，
     * 否则局部接口错误会直接把整个富后台拖成不可恢复的单点失败。
     */
    @Test
    fun badWorkspaceRequest_doesNotBlockOtherWorkspaceApis() = testApplication {
        settingsPayload = WebConsoleSettingsPayload(
            dailyReminderEnabled = false,
            dailyReminderHour = 21,
            dailyReminderMinute = 0,
            themeMode = "LIGHT",
            themeModeLabel = "浅色",
            backupLastAt = null
        )
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val badResponse = client.get("/api/web-console/v1/cards") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }
        val settingsResponse = client.get("/api/web-console/v1/settings") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }

        assertEquals(HttpStatusCode.BadRequest, badResponse.status)
        assertTrue(badResponse.bodyAsText().contains("缺少 deckId"))
        assertEquals(HttpStatusCode.OK, settingsResponse.status)
        assertTrue(settingsResponse.bodyAsText().contains("浅色"))
    }

    /**
     * 同一登录会话需要能连续读取多个工作区接口，
     * 否则前端从概览切到设置时就会丢失“统一壳层下切工作区”的基本前提。
     */
    @Test
    fun sameSession_canReadDashboardAndSettingsAcrossWorkspaces() = testApplication {
        dashboardPayload = WebConsoleDashboardPayload(
            dueCardCount = 2,
            dueQuestionCount = 5,
            recentDecks = emptyList()
        )
        settingsPayload = WebConsoleSettingsPayload(
            dailyReminderEnabled = true,
            dailyReminderHour = 20,
            dailyReminderMinute = 30,
            themeMode = "SYSTEM",
            themeModeLabel = "跟随系统",
            backupLastAt = null
        )
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val dashboardResponse = client.get("/api/web-console/v1/dashboard") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }
        val settingsResponse = client.get("/api/web-console/v1/settings") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
        }

        assertEquals(HttpStatusCode.OK, dashboardResponse.status)
        assertTrue(dashboardResponse.bodyAsText().contains("dueQuestionCount"))
        assertEquals(HttpStatusCode.OK, settingsResponse.status)
        assertTrue(settingsResponse.bodyAsText().contains("跟随系统"))
    }

    /**
     * 结束学习会话接口必须回传稳定结果文案，
     * 否则前端在执行切换确认或主动结束时无法给出明确的中断后果提示。
     */
    @Test
    fun endStudySession_returnsMutationPayload() = testApplication {
        application {
            configureWebConsoleRoutes(
                assetLoader = WebConsoleAssetLoader(RuntimeEnvironment.getApplication()),
                handler = createHandler()
            )
        }

        val response = client.post("/api/web-console/v1/study/session/end") {
            header(HttpHeaders.Cookie, "yike_web_session=session_1")
            header(HttpHeaders.ContentType, "application/json")
            setBody("{}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("当前学习会话已结束"))
        assertEquals("session_1", endedStudySessionId)
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

        override suspend fun getDashboard(): WebConsoleDashboardPayload = dashboardPayload ?: error("unused")

        override suspend fun getStudyWorkspace(sessionId: String): WebConsoleStudyWorkspacePayload = error("unused")

        override suspend fun getStudySession(sessionId: String): WebConsoleStudySessionPayload? = activeStudySession

        override suspend fun startReviewSession(sessionId: String): WebConsoleStudySessionPayload = activeStudySession ?: error("unused")

        override suspend fun revealStudyAnswer(sessionId: String): WebConsoleStudySessionPayload = activeStudySession ?: error("unused")

        override suspend fun submitReviewRating(
            sessionId: String,
            request: WebConsoleReviewRateRequest
        ): WebConsoleStudySessionPayload = activeStudySession ?: error("unused")

        override suspend fun continueReviewSession(sessionId: String): WebConsoleStudySessionPayload = activeStudySession ?: error("unused")

        override suspend fun startPracticeSession(
            sessionId: String,
            request: WebConsolePracticeStartRequest
        ): WebConsoleStudySessionPayload = activeStudySession ?: error("unused")

        override suspend fun navigatePracticeSession(
            sessionId: String,
            request: WebConsolePracticeNavigateRequest
        ): WebConsoleStudySessionPayload {
            practiceNavigateRequest = request
            require(request.action == "previous" || request.action == "next") { "练习切题动作不合法" }
            return activeStudySession ?: error("unused")
        }

        override suspend fun endStudySession(sessionId: String): WebConsoleMutationPayload {
            endedStudySessionId = sessionId
            return WebConsoleMutationPayload(message = "当前学习会话已结束")
        }

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

        override suspend fun getSettings(): WebConsoleSettingsPayload = settingsPayload ?: error("unused")

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
