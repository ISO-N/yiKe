package com.kariscode.yike.data.webconsole

import kotlinx.serialization.Serializable

/**
 * 网页后台序列化配置集中在单点，是为了让路由和测试共用同一份宽容但稳定的 JSON 策略。
 */
internal object WebConsoleJson {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

/**
 * API 处理器抽成接口，是为了让 HTTP 层只负责协议收发，而把业务语义继续留在仓储实现中。
 */
internal interface WebConsoleApiHandler {
    suspend fun login(code: String, remoteHost: String): String?
    suspend fun logout(sessionId: String?)
    suspend fun resolveSession(sessionId: String, remoteHost: String): WebConsoleSessionPayload?
    suspend fun getDashboard(): WebConsoleDashboardPayload
    suspend fun listDecks(): List<WebConsoleDeckPayload>
    suspend fun upsertDeck(request: WebConsoleUpsertDeckRequest): WebConsoleMutationPayload
    suspend fun archiveDeck(deckId: String, archived: Boolean): WebConsoleMutationPayload
    suspend fun listCards(deckId: String): List<WebConsoleCardPayload>
    suspend fun upsertCard(request: WebConsoleUpsertCardRequest): WebConsoleMutationPayload
    suspend fun archiveCard(cardId: String, archived: Boolean): WebConsoleMutationPayload
    suspend fun listQuestions(cardId: String): List<WebConsoleQuestionPayload>
    suspend fun upsertQuestion(request: WebConsoleUpsertQuestionRequest): WebConsoleMutationPayload
    suspend fun deleteQuestion(questionId: String): WebConsoleMutationPayload
    suspend fun search(request: WebConsoleSearchRequest): List<WebConsoleSearchResultPayload>
    suspend fun getAnalytics(): WebConsoleAnalyticsPayload
    suspend fun getSettings(): WebConsoleSettingsPayload
    suspend fun updateSettings(request: WebConsoleUpdateSettingsRequest): WebConsoleMutationPayload
    suspend fun exportBackup(): WebConsoleBackupExportPayload
}

@Serializable
internal data class WebConsoleLoginRequest(
    val code: String
)

@Serializable
internal data class WebConsoleSessionPayload(
    val displayName: String,
    val port: Int,
    val activeSessionCount: Int
)

@Serializable
internal data class WebConsoleDashboardPayload(
    val dueCardCount: Int,
    val dueQuestionCount: Int,
    val recentDecks: List<WebConsoleDeckPayload>
)

@Serializable
internal data class WebConsoleDeckPayload(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String>,
    val intervalStepCount: Int,
    val cardCount: Int,
    val questionCount: Int,
    val dueQuestionCount: Int,
    val archived: Boolean
)

@Serializable
internal data class WebConsoleUpsertDeckRequest(
    val id: String? = null,
    val name: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val intervalStepCount: Int = 4
)

@Serializable
internal data class WebConsoleCardPayload(
    val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val questionCount: Int,
    val dueQuestionCount: Int,
    val archived: Boolean
)

@Serializable
internal data class WebConsoleUpsertCardRequest(
    val id: String? = null,
    val deckId: String,
    val title: String,
    val description: String = ""
)

@Serializable
internal data class WebConsoleQuestionPayload(
    val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tags: List<String>,
    val status: String,
    val stageIndex: Int,
    val dueAt: Long,
    val lastReviewedAt: Long?,
    val reviewCount: Int,
    val lapseCount: Int
)

@Serializable
internal data class WebConsoleUpsertQuestionRequest(
    val id: String? = null,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tags: List<String> = emptyList()
)

@Serializable
internal data class WebConsoleSearchRequest(
    val keyword: String = "",
    val deckId: String? = null,
    val cardId: String? = null,
    val tag: String? = null,
    val status: String? = "active"
)

@Serializable
internal data class WebConsoleSearchResultPayload(
    val questionId: String,
    val cardId: String,
    val deckId: String,
    val deckName: String,
    val cardTitle: String,
    val prompt: String,
    val answer: String,
    val status: String,
    val stageIndex: Int,
    val dueAt: Long,
    val reviewCount: Int,
    val lapseCount: Int,
    val tags: List<String>
)

@Serializable
internal data class WebConsoleAnalyticsPayload(
    val totalReviews: Int,
    val againCount: Int,
    val hardCount: Int,
    val goodCount: Int,
    val easyCount: Int,
    val averageResponseTimeMs: Double?,
    val forgettingRate: Float,
    val deckBreakdowns: List<WebConsoleDeckAnalyticsPayload>
)

@Serializable
internal data class WebConsoleDeckAnalyticsPayload(
    val deckId: String,
    val deckName: String,
    val reviewCount: Int,
    val forgettingRate: Float,
    val averageResponseTimeMs: Double?
)

@Serializable
internal data class WebConsoleSettingsPayload(
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val themeMode: String,
    val themeModeLabel: String,
    val backupLastAt: Long?
)

@Serializable
internal data class WebConsoleUpdateSettingsRequest(
    val dailyReminderEnabled: Boolean,
    val dailyReminderHour: Int,
    val dailyReminderMinute: Int,
    val themeMode: String
)

@Serializable
internal data class WebConsoleBackupExportPayload(
    val fileName: String,
    val content: String
)

@Serializable
internal data class WebConsoleMutationPayload(
    val message: String
)
