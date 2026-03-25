package com.kariscode.yike.data.webconsole

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.repository.PracticeRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.withContext

/**
 * 学习工作区服务把正式复习和自由练习的浏览器会话收拢在一起，
 * 是为了在 phase2 基线之上继续稳定维护“会话恢复、切换和完成态”这一条主学习路径。
 */
internal class WebConsoleStudyWorkspaceService(
    private val reviewRepository: ReviewRepository,
    private val practiceRepository: PracticeRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val runtime: WebConsoleRuntime,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    private val payloadMapper: WebConsoleStudyPayloadMapper
) {
    /**
     * 工作区概览统一从 due 统计和活动会话快照拼装，是为了让桌面壳层稳定表达“今天要做什么”和“能否恢复”。
     */
    suspend fun getWorkspace(sessionId: String): WebConsoleStudyWorkspacePayload = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val summary = studyInsightsRepository.listDueQuestionContexts(nowEpochMillis = now)
        val cardCount = summary.groupBy { context -> context.question.cardId }.size
        payloadMapper.toStudyWorkspacePayload(
            dueCardCount = cardCount,
            dueQuestionCount = summary.size,
            activeSession = runtime.getStudySession(sessionId)
        )
    }

    /**
     * 学习会话读取只暴露映射后的稳定 payload，是为了让前端刷新恢复时不需要知道运行时快照结构。
     */
    suspend fun getSession(sessionId: String): WebConsoleStudySessionPayload? = withContext(dispatchers.io) {
        runtime.getStudySession(sessionId)?.let(payloadMapper::toStudySessionPayload)
    }

    /**
     * 今日复习优先恢复已有会话，是为了把 phase2 已落地的浏览器恢复体验作为本次 change 的承接基线。
     */
    suspend fun startReviewSession(sessionId: String): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val existing = runtime.getStudySession(sessionId)
        if (existing is WebConsoleReviewStudySession && !existing.isCompleted()) {
            return@withContext payloadMapper.toStudySessionPayload(existing)
        }
        val now = timeProvider.nowEpochMillis()
        val dueContexts = studyInsightsRepository.listDueQuestionContexts(nowEpochMillis = now)
        require(dueContexts.isNotEmpty()) { "今日暂无待复习内容" }
        val session = WebConsoleReviewStudySession(
            cards = payloadMapper.toReviewCardSnapshots(dueContexts),
            currentCardIndex = 0,
            currentQuestionIndex = 0,
            questionPresentedAt = now,
            answerVisible = false,
            updatedAt = now
        )
        runtime.putStudySession(sessionId, session)
        payloadMapper.toStudySessionPayload(session)
    }

    /**
     * 显示答案通过服务端落状态，是为了让刷新恢复和跨工作区返回后仍能准确知道当前题是否已解锁下一步动作。
     */
    suspend fun revealAnswer(sessionId: String): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val updated = when (val session = runtime.getStudySession(sessionId)) {
            is WebConsoleReviewStudySession -> {
                require(!session.isCompleted() && !session.isCurrentCardCompleted()) { "当前复习会话没有可显示答案的问题" }
                require(session.currentQuestionOrNull() != null) { "当前复习会话没有可显示答案的问题" }
                session.copy(answerVisible = true, updatedAt = now)
            }

            is WebConsolePracticeStudySession -> {
                require(session.currentQuestionOrNull() != null) { "当前练习会话没有可显示答案的问题" }
                session.copy(answerVisible = true, updatedAt = now)
            }

            null -> throw IllegalStateException("当前没有可恢复的学习会话")
        }
        runtime.putStudySession(sessionId, updated)
        payloadMapper.toStudySessionPayload(updated)
    }

    /**
     * 浏览器端复习评分继续委托正式复习仓储，是为了保证网页端与手机端共享完全相同的调度与记录语义。
     */
    suspend fun submitReviewRating(
        sessionId: String,
        request: WebConsoleReviewRateRequest
    ): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val session = runtime.getStudySession(sessionId) as? WebConsoleReviewStudySession
            ?: throw IllegalStateException("当前没有进行中的复习会话")
        require(session.answerVisible) { "请先显示答案，再提交评分" }
        require(!session.isCompleted() && !session.isCurrentCardCompleted()) { "当前复习会话没有可评分的问题" }
        val currentQuestion = session.currentQuestionOrNull()
            ?: throw IllegalStateException("当前复习会话没有可评分的问题")
        val now = timeProvider.nowEpochMillis()
        reviewRepository.submitRating(
            questionId = currentQuestion.questionId,
            rating = request.rating.toReviewRating(),
            reviewedAtEpochMillis = now,
            responseTimeMs = session.questionPresentedAt
                ?.let { presentedAt -> (now - presentedAt).coerceAtLeast(0L) }
        )
        val updated = session.copy(
            currentQuestionIndex = session.currentQuestionIndex + 1,
            questionPresentedAt = null,
            answerVisible = false,
            updatedAt = now
        )
        runtime.putStudySession(sessionId, updated)
        payloadMapper.toStudySessionPayload(updated)
    }

    /**
     * 继续下一张卡仍由服务端推进索引，是为了让桌面工作区切换和刷新恢复共享同一份边界判断。
     */
    suspend fun continueReviewSession(sessionId: String): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val session = runtime.getStudySession(sessionId) as? WebConsoleReviewStudySession
            ?: throw IllegalStateException("当前没有进行中的复习会话")
        require(!session.isCompleted()) { "今日复习已经完成" }
        require(session.isCurrentCardCompleted()) { "当前卡片尚未完成，无法继续下一张" }
        val now = timeProvider.nowEpochMillis()
        val nextCardIndex = session.currentCardIndex + 1
        val updated = if (nextCardIndex >= session.cards.size) {
            session.copy(
                currentCardIndex = session.cards.size,
                currentQuestionIndex = 0,
                questionPresentedAt = null,
                answerVisible = false,
                updatedAt = now
            )
        } else {
            session.copy(
                currentCardIndex = nextCardIndex,
                currentQuestionIndex = 0,
                questionPresentedAt = now,
                answerVisible = false,
                updatedAt = now
            )
        }
        runtime.putStudySession(sessionId, updated)
        payloadMapper.toStudySessionPayload(updated)
    }

    /**
     * 自由练习启动时强制要求显式范围，是为了把空范围和无题可练这种失败尽早限制在服务端边界里。
     */
    suspend fun startPracticeSession(
        sessionId: String,
        request: WebConsolePracticeStartRequest
    ): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val args = PracticeSessionArgs(
            deckIds = request.deckIds,
            cardIds = request.cardIds,
            questionIds = request.questionIds,
            orderMode = request.orderMode.toPracticeOrderMode()
        ).normalized()
        require(args.hasScopedSelection()) { "请至少选择一个练习范围" }
        val orderedQuestions = payloadMapper.toPracticeQuestionSnapshots(
            contexts = practiceRepository.listPracticeQuestionContexts(args),
            orderMode = args.orderMode,
            nowEpochMillis = timeProvider.nowEpochMillis()
        )
        require(orderedQuestions.items.isNotEmpty()) { "当前范围内没有可练习的问题，请调整范围后重试" }
        val session = WebConsolePracticeStudySession(
            orderMode = args.orderMode,
            sessionSeed = orderedQuestions.seed,
            questions = orderedQuestions.items,
            currentIndex = 0,
            answerVisible = false,
            updatedAt = timeProvider.nowEpochMillis()
        )
        runtime.putStudySession(sessionId, session)
        payloadMapper.toStudySessionPayload(session)
    }

    /**
     * 练习切题保持纯内存索引移动，是为了继续守住“只读练习不写调度记录”的 phase2 安全边界。
     */
    suspend fun navigatePracticeSession(
        sessionId: String,
        request: WebConsolePracticeNavigateRequest
    ): WebConsoleStudySessionPayload = withContext(dispatchers.io) {
        val session = runtime.getStudySession(sessionId) as? WebConsolePracticeStudySession
            ?: throw IllegalStateException("当前没有进行中的练习会话")
        val targetIndex = when (request.action.trim().lowercase()) {
            "previous" -> (session.currentIndex - 1).coerceAtLeast(0)
            "next" -> (session.currentIndex + 1).coerceAtMost(session.questions.lastIndex)
            else -> throw IllegalArgumentException("练习切题动作不合法")
        }
        val updated = session.copy(
            currentIndex = targetIndex,
            answerVisible = false,
            updatedAt = timeProvider.nowEpochMillis()
        )
        runtime.putStudySession(sessionId, updated)
        payloadMapper.toStudySessionPayload(updated)
    }

    /**
     * 学习会话结束只清理当前登录上下文，是为了让多标签浏览器仍能维持各自清晰的恢复边界。
     */
    suspend fun endSession(sessionId: String): WebConsoleMutationPayload = withContext(dispatchers.io) {
        runtime.clearStudySession(sessionId)
        WebConsoleMutationPayload(message = "学习会话已结束")
    }

    /**
     * 评分字符串集中校验，是为了让非法值始终以可理解的业务错误返回给前端。
     */
    private fun String.toReviewRating(): ReviewRating = ReviewRating.entries.firstOrNull { rating ->
        rating.name.equals(trim(), ignoreCase = true)
    } ?: throw IllegalArgumentException("评分参数不合法")

    /**
     * 顺序模式解析集中处理，是为了让练习请求在协议层就被约束到已知集合。
     */
    private fun String.toPracticeOrderMode(): PracticeOrderMode = PracticeOrderMode.entries.firstOrNull { mode ->
        mode.storageValue == trim().lowercase()
    } ?: throw IllegalArgumentException("练习顺序不合法")
}

