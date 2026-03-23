package com.kariscode.yike.data.webconsole

import android.content.Context
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.model.WebConsoleState
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.repository.WebConsoleRepository
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 网页后台仓储把服务生命周期、登录和网页 API 统一编排，
 * 是为了让手机页、前台服务和 Ktor 路由共享同一套业务规则与状态来源。
 */
internal class WebConsoleRepositoryImpl(
    context: Context,
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val backupService: BackupService,
    private val reminderScheduler: ReminderScheduler,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers
) : WebConsoleRepository, WebConsoleApiHandler {
    private val runtime = WebConsoleRuntime(timeProvider = timeProvider)
    private val addressProvider = WebConsoleAddressProvider()
    private val httpServer = WebConsoleHttpServer(
        portAllocator = WebConsolePortAllocator(),
        assetLoader = WebConsoleAssetLoader(context.applicationContext),
        handler = this
    )

    /**
     * 外部统一观察运行时状态，是为了让服务、页面和通知都围绕同一份可变状态协作。
     */
    override fun observeState(): Flow<WebConsoleState> = runtime.state

    /**
     * 启动时先准备访问码与地址，再对外开放端口，是为了减少用户看到“服务已启动但地址为空”的半完成状态。
     */
    override suspend fun startServer() = withContext(dispatchers.io) {
        if (runtime.state.value.isRunning) return@withContext
        runtime.markStarting()
        runCatching {
            httpServer.start()
            val addresses = addressProvider.getAccessibleAddresses(httpServer.port)
            require(addresses.isNotEmpty()) { "未检测到可用于局域网访问的地址，请确认 Wi‑Fi 或热点已开启" }
            runtime.activate(port = httpServer.port, addresses = addresses)
        }.onFailure { throwable ->
            httpServer.stop()
            runtime.markFailure(throwable.message ?: "网页后台启动失败")
            throw throwable
        }
    }

    /**
     * 停止时连同内存会话一起清空，是为了让服务关闭后旧浏览器立即失去访问能力。
     */
    override suspend fun stopServer() = withContext(dispatchers.io) {
        httpServer.stop()
        runtime.deactivate()
    }

    /**
     * 刷新访问码后不重启端口监听，可以减少用户在同一会话内临时重新授权的等待成本。
     */
    override suspend fun refreshAccessCode() = withContext(dispatchers.io) {
        runtime.rotateAccessCode()
    }

    /**
     * 登录继续要求匹配当前访问码和本地网络来源，是为了把“显式拿到手机上的码”作为唯一放行条件。
     */
    override suspend fun login(code: String, remoteHost: String): String? = withContext(dispatchers.io) {
        if (!remoteHost.isAllowedLocalNetworkHost()) return@withContext null
        if (!runtime.matchesAccessCode(code.trim())) return@withContext null
        runtime.createSession()
    }

    /**
     * 退出登录时只移除当前会话，是为了避免同一浏览器多个标签页被无差别踢下线。
     */
    override suspend fun logout(sessionId: String?) = withContext(dispatchers.io) {
        runtime.removeSession(sessionId)
    }

    /**
     * 会话解析顺带做本地网络校验和续期，是为了把 Cookie 是否仍有效的判断收敛到同一入口。
     */
    override suspend fun resolveSession(sessionId: String, remoteHost: String): WebConsoleSessionPayload? =
        withContext(dispatchers.io) {
            if (!remoteHost.isAllowedLocalNetworkHost() || !runtime.touchSession(sessionId)) return@withContext null
            val state = runtime.state.value
            val recommendedAddress = state.addresses.firstOrNull { it.isRecommended } ?: state.addresses.firstOrNull()
            WebConsoleSessionPayload(
                displayName = "忆刻网页后台",
                port = recommendedAddress?.port ?: 0,
                activeSessionCount = state.activeSessionCount
            )
        }

    /**
     * 概览接口复用现有首页与卡组摘要口径，是为了保证网页端看到的任务规模和手机端一致。
     */
    override suspend fun getDashboard(): WebConsoleDashboardPayload = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val summary = questionRepository.getTodayReviewSummary(nowEpochMillis = now)
        val recentDecks = deckRepository.listRecentActiveDeckSummaries(nowEpochMillis = now, limit = 5).map { it.toDeckPayload() }
        WebConsoleDashboardPayload(
            dueCardCount = summary.dueCardCount,
            dueQuestionCount = summary.dueQuestionCount,
            recentDecks = recentDecks
        )
    }

    /**
     * 卡组列表使用摘要查询，是为了让网页端直接拿到题量和到期量，而不是自己拼 N+1 请求。
     */
    override suspend fun listDecks(): List<WebConsoleDeckPayload> = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        deckRepository.observeActiveDeckSummaries(now).first().map { it.toDeckPayload() }
    }

    /**
     * 卡组保存沿用现有实体 ID 与时间戳策略，是为了让网页端创建的内容继续符合手机端的读取假设。
     */
    override suspend fun upsertDeck(request: WebConsoleUpsertDeckRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedName = request.name.trim()
        require(trimmedName.isNotBlank()) { "卡组名称不能为空" }
        val now = timeProvider.nowEpochMillis()
        val existing = if (request.id != null) deckRepository.findById(request.id) else null
        deckRepository.upsert(
            Deck(
                id = existing?.id ?: EntityIds.newDeckId(),
                name = trimmedName,
                description = request.description,
                tags = request.tags.map(String::trim).filter(String::isNotBlank).distinct(),
                intervalStepCount = request.intervalStepCount,
                archived = existing?.archived ?: false,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        WebConsoleMutationPayload(message = "卡组已保存")
    }

    /**
     * 卡组先走归档而不是物理删除，是为了继续延续当前产品对内容管理的保守安全边界。
     */
    override suspend fun archiveDeck(deckId: String, archived: Boolean): WebConsoleMutationPayload = withContext(dispatchers.io) {
        deckRepository.setArchived(deckId = deckId, archived = archived, updatedAt = timeProvider.nowEpochMillis())
        WebConsoleMutationPayload(message = if (archived) "卡组已归档" else "卡组已恢复")
    }

    /**
     * 卡片列表同样复用摘要流，是为了让网页端能直接看到每张卡的题量和待复习量。
     */
    override suspend fun listCards(deckId: String): List<WebConsoleCardPayload> = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        cardRepository.observeActiveCardSummaries(deckId, now).first().map { summary ->
            WebConsoleCardPayload(
                id = summary.card.id,
                deckId = summary.card.deckId,
                title = summary.card.title,
                description = summary.card.description,
                questionCount = summary.questionCount,
                dueQuestionCount = summary.dueQuestionCount,
                archived = summary.card.archived
            )
        }
    }

    /**
     * 卡片保存复用手机端当前默认字段，是为了让网页端创建的卡片可以无缝进入现有编辑与复习流程。
     */
    override suspend fun upsertCard(request: WebConsoleUpsertCardRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedTitle = request.title.trim()
        require(trimmedTitle.isNotBlank()) { "卡片标题不能为空" }
        val now = timeProvider.nowEpochMillis()
        val existing = if (request.id != null) cardRepository.findById(request.id) else null
        cardRepository.upsert(
            Card(
                id = existing?.id ?: EntityIds.newCardId(),
                deckId = existing?.deckId ?: request.deckId,
                title = trimmedTitle,
                description = request.description,
                archived = existing?.archived ?: false,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
        )
        WebConsoleMutationPayload(message = "卡片已保存")
    }

    /**
     * 卡片归档继续沿用现有列表过滤语义，是为了让网页端不会绕开手机端既有的风险缓冲设计。
     */
    override suspend fun archiveCard(cardId: String, archived: Boolean): WebConsoleMutationPayload = withContext(dispatchers.io) {
        cardRepository.setArchived(cardId = cardId, archived = archived, updatedAt = timeProvider.nowEpochMillis())
        WebConsoleMutationPayload(message = if (archived) "卡片已归档" else "卡片已恢复")
    }

    /**
     * 问题列表直接走单卡快照查询，是为了保持网页端编辑口径与现有问题编辑页一致。
     */
    override suspend fun listQuestions(cardId: String): List<WebConsoleQuestionPayload> = withContext(dispatchers.io) {
        questionRepository.listByCard(cardId).map { question -> question.toQuestionPayload() }
    }

    /**
     * 问题保存复用初始 due 计算和编辑态保留策略，是为了让网页端新增或修改后仍符合既有调度规则。
     */
    override suspend fun upsertQuestion(request: WebConsoleUpsertQuestionRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedPrompt = request.prompt.trim()
        require(trimmedPrompt.isNotBlank()) { "问题题面不能为空" }
        val now = timeProvider.nowEpochMillis()
        val initialDueAt = InitialDueAtCalculator.compute(nowEpochMillis = now)
        val existing = if (request.id != null) questionRepository.findById(request.id) else null
        val question = if (existing != null) {
            existing.copy(
                prompt = trimmedPrompt,
                answer = request.answer,
                tags = request.tags.map(String::trim).filter(String::isNotBlank).distinct(),
                updatedAt = now
            )
        } else {
            Question(
                id = EntityIds.newQuestionId(),
                cardId = request.cardId,
                prompt = trimmedPrompt,
                answer = request.answer,
                tags = request.tags.map(String::trim).filter(String::isNotBlank).distinct(),
                status = QuestionStatus.ACTIVE,
                stageIndex = 0,
                dueAt = initialDueAt,
                lastReviewedAt = null,
                reviewCount = 0,
                lapseCount = 0,
                createdAt = now,
                updatedAt = now
            )
        }
        questionRepository.upsertAll(listOf(question))
        WebConsoleMutationPayload(message = "问题已保存")
    }

    /**
     * 问题删除保持显式单项入口，是为了让危险动作在网页端也必须绑定到具体对象而不是模糊批量操作。
     */
    override suspend fun deleteQuestion(questionId: String): WebConsoleMutationPayload = withContext(dispatchers.io) {
        questionRepository.delete(questionId)
        WebConsoleMutationPayload(message = "问题已删除")
    }

    /**
     * 搜索接口直接复用题库查询仓储，是为了保证网页端和手机端的筛选结果保持同一口径。
     */
    override suspend fun search(request: WebConsoleSearchRequest): List<WebConsoleSearchResultPayload> = withContext(dispatchers.io) {
        studyInsightsRepository.searchQuestionContexts(
            QuestionQueryFilters(
                keyword = request.keyword,
                tag = request.tag,
                status = request.status?.let(QuestionStatus::fromStorageValue),
                deckId = request.deckId,
                cardId = request.cardId,
                masteryLevel = null
            )
        ).map { result ->
            WebConsoleSearchResultPayload(
                questionId = result.question.id,
                cardId = result.question.cardId,
                deckId = result.deckId,
                deckName = result.deckName,
                cardTitle = result.cardTitle,
                prompt = result.question.prompt,
                answer = result.question.answer,
                status = result.question.status.storageValue,
                stageIndex = result.question.stageIndex,
                dueAt = result.question.dueAt,
                reviewCount = result.question.reviewCount,
                lapseCount = result.question.lapseCount,
                tags = result.question.tags
            )
        }
    }

    /**
     * 统计接口直接回转为网页 DTO，是为了让桌面端在不理解手机端 ViewModel 逻辑的情况下也能稳定渲染。
     */
    override suspend fun getAnalytics(): WebConsoleAnalyticsPayload = withContext(dispatchers.io) {
        val snapshot = studyInsightsRepository.getReviewAnalytics(startEpochMillis = null)
        WebConsoleAnalyticsPayload(
            totalReviews = snapshot.totalReviews,
            againCount = snapshot.againCount,
            hardCount = snapshot.hardCount,
            goodCount = snapshot.goodCount,
            easyCount = snapshot.easyCount,
            averageResponseTimeMs = snapshot.averageResponseTimeMs,
            forgettingRate = snapshot.forgettingRate,
            deckBreakdowns = snapshot.deckBreakdowns.map {
                WebConsoleDeckAnalyticsPayload(
                    deckId = it.deckId,
                    deckName = it.deckName,
                    reviewCount = it.reviewCount,
                    forgettingRate = it.forgettingRate,
                    averageResponseTimeMs = it.averageResponseTimeMs
                )
            }
        )
    }

    /**
     * 设置读取继续走仓储快照，是为了让网页端展示出的提醒和主题配置与手机端保持同一来源。
     */
    override suspend fun getSettings(): WebConsoleSettingsPayload = withContext(dispatchers.io) {
        appSettingsRepository.getSettings().toSettingsPayload()
    }

    /**
     * 设置更新复用现有提醒重建路径，是为了避免网页端写入后漏掉后台提醒任务同步。
     */
    override suspend fun updateSettings(request: WebConsoleUpdateSettingsRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val current = appSettingsRepository.getSettings()
        val themeMode = ThemeMode.entries.firstOrNull { it.name == request.themeMode } ?: current.themeMode
        val updated = current.copy(
            dailyReminderEnabled = request.dailyReminderEnabled,
            dailyReminderHour = request.dailyReminderHour,
            dailyReminderMinute = request.dailyReminderMinute,
            themeMode = themeMode
        )
        appSettingsRepository.setSettings(updated)
        reminderScheduler.syncReminder(updated)
        WebConsoleMutationPayload(message = "设置已保存")
    }

    /**
     * 备份导出直接复用既有 JSON 生成链路，是为了避免网页端再维护一套独立的备份格式。
     */
    override suspend fun exportBackup(): WebConsoleBackupExportPayload = withContext(dispatchers.io) {
        WebConsoleBackupExportPayload(
            fileName = backupService.createSuggestedFileName(),
            content = backupService.exportToJsonString()
        )
    }

    /**
     * 卡组摘要到网页 DTO 的映射集中在单点，是为了避免概览页和卡组页各自拼装不同字段组合。
     */
    private fun com.kariscode.yike.domain.model.DeckSummary.toDeckPayload(): WebConsoleDeckPayload = WebConsoleDeckPayload(
        id = deck.id,
        name = deck.name,
        description = deck.description,
        tags = deck.tags,
        intervalStepCount = deck.intervalStepCount,
        cardCount = cardCount,
        questionCount = questionCount,
        dueQuestionCount = dueQuestionCount,
        archived = deck.archived
    )

    /**
     * 问题映射集中后，网页列表和编辑面板都能围绕同一份字段定义工作。
     */
    private fun Question.toQuestionPayload(): WebConsoleQuestionPayload = WebConsoleQuestionPayload(
        id = id,
        cardId = cardId,
        prompt = prompt,
        answer = answer,
        tags = tags,
        status = status.storageValue,
        stageIndex = stageIndex,
        dueAt = dueAt,
        lastReviewedAt = lastReviewedAt,
        reviewCount = reviewCount,
        lapseCount = lapseCount
    )

    /**
     * 设置回转为网页 DTO 时同时给出显示文案，是为了让前端不必重复维护主题模式映射关系。
     */
    private fun AppSettings.toSettingsPayload(): WebConsoleSettingsPayload = WebConsoleSettingsPayload(
        dailyReminderEnabled = dailyReminderEnabled,
        dailyReminderHour = dailyReminderHour,
        dailyReminderMinute = dailyReminderMinute,
        themeMode = themeMode.name,
        themeModeLabel = themeMode.displayLabel,
        backupLastAt = backupLastAt
    )
}
