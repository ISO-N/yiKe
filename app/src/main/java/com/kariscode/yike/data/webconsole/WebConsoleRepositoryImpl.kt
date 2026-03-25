package com.kariscode.yike.data.webconsole

import android.content.Context
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.domain.model.WebConsoleState
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.PracticeRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.repository.WebConsoleRepository
import kotlinx.coroutines.flow.Flow

/**
 * 网页后台仓储收敛为组合外观，是为了继续满足应用层只依赖一个入口，
 * 同时把富后台的会话、工作区和维护逻辑拆给稳定协作者长期演进。
 */
internal class WebConsoleRepositoryImpl(
    context: Context,
    deckRepository: DeckRepository,
    cardRepository: CardRepository,
    questionRepository: QuestionRepository,
    reviewRepository: ReviewRepository,
    practiceRepository: PracticeRepository,
    studyInsightsRepository: StudyInsightsRepository,
    appSettingsRepository: AppSettingsRepository,
    backupService: BackupService,
    reminderScheduler: ReminderScheduler,
    timeProvider: TimeProvider,
    dispatchers: AppDispatchers
) : WebConsoleRepository, WebConsoleApiHandler {
    private val workspacePayloadMapper = WebConsoleWorkspacePayloadMapper()
    private val studyPayloadMapper = WebConsoleStudyPayloadMapper()
    private val sessionCoordinator = WebConsoleSessionCoordinator(
        context = context,
        timeProvider = timeProvider,
        dispatchers = dispatchers,
        handler = this
    )
    private val contentWorkspaceService = WebConsoleContentWorkspaceService(
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        questionRepository = questionRepository,
        studyInsightsRepository = studyInsightsRepository,
        timeProvider = timeProvider,
        dispatchers = dispatchers,
        payloadMapper = workspacePayloadMapper
    )
    private val studyWorkspaceService = WebConsoleStudyWorkspaceService(
        reviewRepository = reviewRepository,
        practiceRepository = practiceRepository,
        studyInsightsRepository = studyInsightsRepository,
        runtime = sessionCoordinator.runtime,
        timeProvider = timeProvider,
        dispatchers = dispatchers,
        payloadMapper = studyPayloadMapper
    )
    private val maintenanceWorkspaceService = WebConsoleMaintenanceWorkspaceService(
        appSettingsRepository = appSettingsRepository,
        backupService = backupService,
        reminderScheduler = reminderScheduler,
        dispatchers = dispatchers,
        payloadMapper = workspacePayloadMapper
    )

    /**
     * 运行时状态继续由统一入口暴露，是为了让前台服务和手机页无需感知内部协作者拆分。
     */
    override fun observeState(): Flow<WebConsoleState> = sessionCoordinator.observeState()

    /**
     * 服务启动委托给会话协作者，是为了让仓储外观不再直接承担端口和访问码生命周期管理。
     */
    override suspend fun startServer() = sessionCoordinator.startServer()

    /**
     * 服务停止仍由统一入口发起，是为了保持应用层对网页后台生命周期的调用方式不变。
     */
    override suspend fun stopServer() = sessionCoordinator.stopServer()

    /**
     * 访问码刷新继续挂在仓储外观下，是为了让调用方无需知道运行时和会话协作者的内部边界。
     */
    override suspend fun refreshAccessCode() = sessionCoordinator.refreshAccessCode()

    /**
     * 登录委托给会话协作者，是为了让局域网来源和访问码校验继续收敛在同一处。
     */
    override suspend fun login(code: String, remoteHost: String): String? = sessionCoordinator.login(code, remoteHost)

    /**
     * 退出登录继续只清理当前会话，是为了保持现有多标签浏览器行为不回退。
     */
    override suspend fun logout(sessionId: String?) = sessionCoordinator.logout(sessionId)

    /**
     * 会话解析保留在仓储接口中，是为了让 HTTP 层仍只依赖单一处理器协议。
     */
    override suspend fun resolveSession(sessionId: String, remoteHost: String): WebConsoleSessionPayload? =
        sessionCoordinator.resolveSession(sessionId, remoteHost)

    /**
     * 概览接口委托给内容工作区服务，是为了把对象统计和列表摘要从总控类中释放出来。
     */
    override suspend fun getDashboard(): WebConsoleDashboardPayload = contentWorkspaceService.getDashboard()

    /**
     * 学习工作区概览委托给学习服务，是为了让当前 change 的学习上下文演进围绕单独协作者继续扩展。
     */
    override suspend fun getStudyWorkspace(sessionId: String): WebConsoleStudyWorkspacePayload =
        studyWorkspaceService.getWorkspace(sessionId)

    /**
     * 学习会话读取继续只返回当前浏览器上下文，是为了保持 phase2 已验证的刷新恢复边界。
     */
    override suspend fun getStudySession(sessionId: String): WebConsoleStudySessionPayload? =
        studyWorkspaceService.getSession(sessionId)

    /**
     * 正式复习入口委托学习服务，是为了让后续桌面工作区升级时继续复用同一会话编排器。
     */
    override suspend fun startReviewSession(sessionId: String): WebConsoleStudySessionPayload =
        studyWorkspaceService.startReviewSession(sessionId)

    /**
     * 答案显隐继续由学习服务落状态，是为了让前端壳层升级时无需知道运行时快照细节。
     */
    override suspend fun revealStudyAnswer(sessionId: String): WebConsoleStudySessionPayload =
        studyWorkspaceService.revealAnswer(sessionId)

    /**
     * 复习评分委托学习服务，是为了继续复用与手机端一致的正式复习语义。
     */
    override suspend fun submitReviewRating(
        sessionId: String,
        request: WebConsoleReviewRateRequest
    ): WebConsoleStudySessionPayload = studyWorkspaceService.submitReviewRating(sessionId, request)

    /**
     * 继续下一张卡委托学习服务，是为了把学习完成态和恢复边界继续放在单一协作者维护。
     */
    override suspend fun continueReviewSession(sessionId: String): WebConsoleStudySessionPayload =
        studyWorkspaceService.continueReviewSession(sessionId)

    /**
     * 自由练习入口委托学习服务，是为了让不同来源上下文的会话启动逻辑逐步收敛。
     */
    override suspend fun startPracticeSession(
        sessionId: String,
        request: WebConsolePracticeStartRequest
    ): WebConsoleStudySessionPayload = studyWorkspaceService.startPracticeSession(sessionId, request)

    /**
     * 练习切题继续由学习服务控制，是为了守住“只读练习不改调度”的产品边界。
     */
    override suspend fun navigatePracticeSession(
        sessionId: String,
        request: WebConsolePracticeNavigateRequest
    ): WebConsoleStudySessionPayload = studyWorkspaceService.navigatePracticeSession(sessionId, request)

    /**
     * 学习会话结束委托学习服务，是为了保持结束动作与恢复语义始终在同一条工作区边界里。
     */
    override suspend fun endStudySession(sessionId: String): WebConsoleMutationPayload =
        studyWorkspaceService.endSession(sessionId)

    /**
     * 卡组列表委托内容工作区服务，是为了让 drill-down 上下文的对象读取从总控类解耦。
     */
    override suspend fun listDecks(): List<WebConsoleDeckPayload> = contentWorkspaceService.listDecks()

    /**
     * 卡组保存委托内容工作区服务，是为了让内容边界在后续支持就地编辑时保持稳定。
     */
    override suspend fun upsertDeck(request: WebConsoleUpsertDeckRequest): WebConsoleMutationPayload =
        contentWorkspaceService.upsertDeck(request)

    /**
     * 卡组归档继续沿用内容工作区服务，是为了让对象风险操作保留在同一边界下。
     */
    override suspend fun archiveDeck(deckId: String, archived: Boolean): WebConsoleMutationPayload =
        contentWorkspaceService.archiveDeck(deckId, archived)

    /**
     * 卡片列表继续走内容工作区服务，是为了让卡组和卡片 drill-down 读取口径保持一致。
     */
    override suspend fun listCards(deckId: String): List<WebConsoleCardPayload> =
        contentWorkspaceService.listCards(deckId)

    /**
     * 卡片保存委托内容工作区服务，是为了让卡片上下文与未来的就地编辑逻辑共享同一服务边界。
     */
    override suspend fun upsertCard(request: WebConsoleUpsertCardRequest): WebConsoleMutationPayload =
        contentWorkspaceService.upsertCard(request)

    /**
     * 卡片归档继续留在内容工作区服务，是为了保证列表和编辑面板对归档状态理解一致。
     */
    override suspend fun archiveCard(cardId: String, archived: Boolean): WebConsoleMutationPayload =
        contentWorkspaceService.archiveCard(cardId, archived)

    /**
     * 问题列表委托内容工作区服务，是为了让问题编辑与搜索共享稳定的对象读取路径。
     */
    override suspend fun listQuestions(cardId: String): List<WebConsoleQuestionPayload> =
        contentWorkspaceService.listQuestions(cardId)

    /**
     * 问题保存委托内容工作区服务，是为了让浏览器端题目编辑继续复用既有调度初始化语义。
     */
    override suspend fun upsertQuestion(request: WebConsoleUpsertQuestionRequest): WebConsoleMutationPayload =
        contentWorkspaceService.upsertQuestion(request)

    /**
     * 问题删除继续由内容工作区服务承接，是为了让高风险对象操作维持清晰的服务边界。
     */
    override suspend fun deleteQuestion(questionId: String): WebConsoleMutationPayload =
        contentWorkspaceService.deleteQuestion(questionId)

    /**
     * 搜索委托内容工作区服务，是为了让内容上下文和搜索结果继续共用同一查询口径。
     */
    override suspend fun search(request: WebConsoleSearchRequest): List<WebConsoleSearchResultPayload> =
        contentWorkspaceService.search(request)

    /**
     * 统计接口委托内容工作区服务，是为了让富后台壳层后续接统计卡片时无需感知底层仓储。
     */
    override suspend fun getAnalytics(): WebConsoleAnalyticsPayload = contentWorkspaceService.getAnalytics()

    /**
     * 设置读取委托维护工作区服务，是为了把配置与备份等高风险动作从内容边界中分离出来。
     */
    override suspend fun getSettings(): WebConsoleSettingsPayload = maintenanceWorkspaceService.getSettings()

    /**
     * 设置更新委托维护工作区服务，是为了继续复用提醒重建路径且不让仓储外观膨胀。
     */
    override suspend fun updateSettings(request: WebConsoleUpdateSettingsRequest): WebConsoleMutationPayload =
        maintenanceWorkspaceService.updateSettings(request)

    /**
     * 备份导出委托维护工作区服务，是为了让下载协议和恢复语义都聚焦在同一维护边界内。
     */
    override suspend fun exportBackup(): WebConsoleBackupExportPayload = maintenanceWorkspaceService.exportBackup()

    /**
     * 备份恢复委托维护工作区服务，是为了让高风险数据操作保持统一反馈和同步语义。
     */
    override suspend fun restoreBackup(request: WebConsoleBackupRestoreRequest): WebConsoleMutationPayload =
        maintenanceWorkspaceService.restoreBackup(request)
}

