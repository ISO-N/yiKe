package com.kariscode.yike.data.webconsole

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.id.EntityIds
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 内容工作区服务把概览、内容管理、搜索和统计等读写编排集中在一处，
 * 是为了让富后台后续扩展对象上下文时仍围绕稳定的数据协作者演进。
 */
internal class WebConsoleContentWorkspaceService(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val studyInsightsRepository: StudyInsightsRepository,
    private val timeProvider: TimeProvider,
    private val dispatchers: AppDispatchers,
    private val payloadMapper: WebConsoleWorkspacePayloadMapper
) {
    /**
     * 概览页复用首页摘要口径，是为了让网页后台与手机端对“今天有多少内容待处理”保持一致理解。
     */
    suspend fun getDashboard(): WebConsoleDashboardPayload = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        val summary = questionRepository.getTodayReviewSummary(nowEpochMillis = now)
        val recentDecks = deckRepository.listRecentActiveDeckSummaries(
            nowEpochMillis = now,
            limit = 5
        ).map(payloadMapper::toDeckPayload)
        WebConsoleDashboardPayload(
            dueCardCount = summary.dueCardCount,
            dueQuestionCount = summary.dueQuestionCount,
            recentDecks = recentDecks
        )
    }

    /**
     * 卡组列表继续直接输出摘要，是为了让 drill-down 工作区升级时无需再补 N+1 统计请求。
     */
    suspend fun listDecks(): List<WebConsoleDeckPayload> = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        deckRepository.observeActiveDeckSummaries(now).first().map(payloadMapper::toDeckPayload)
    }

    /**
     * 卡组保存沿用既有实体时间戳和归档语义，是为了让网页端编辑仍与手机端共享同一内容生命周期规则。
     */
    suspend fun upsertDeck(request: WebConsoleUpsertDeckRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedName = request.name.trim()
        require(trimmedName.isNotBlank()) { "卡组名称不能为空" }
        val now = timeProvider.nowEpochMillis()
        val existing = if (request.id != null) {
            deckRepository.findById(request.id)
        } else {
            null
        }
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
     * 卡组保留归档入口而非物理删除，是为了延续当前产品对学习内容的保守安全边界。
     */
    suspend fun archiveDeck(deckId: String, archived: Boolean): WebConsoleMutationPayload = withContext(dispatchers.io) {
        deckRepository.setArchived(deckId = deckId, archived = archived, updatedAt = timeProvider.nowEpochMillis())
        WebConsoleMutationPayload(message = if (archived) "卡组已归档" else "卡组已恢复")
    }

    /**
     * 卡片列表继续从摘要流读取，是为了让当前卡片的题量和待复习量在同一请求里返回给桌面工作区。
     */
    suspend fun listCards(deckId: String): List<WebConsoleCardPayload> = withContext(dispatchers.io) {
        val now = timeProvider.nowEpochMillis()
        cardRepository.observeActiveCardSummaries(deckId, now).first().map(payloadMapper::toCardPayload)
    }

    /**
     * 卡片保存继续复用现有字段默认值，是为了避免网页端创建内容后与手机端排序和归档假设出现偏差。
     */
    suspend fun upsertCard(request: WebConsoleUpsertCardRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedTitle = request.title.trim()
        require(trimmedTitle.isNotBlank()) { "卡片标题不能为空" }
        val now = timeProvider.nowEpochMillis()
        val existing = if (request.id != null) {
            cardRepository.findById(request.id)
        } else {
            null
        }
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
     * 卡片归档继续沿用摘要过滤语义，是为了让网页与手机两端对“隐藏但可恢复”的理解保持一致。
     */
    suspend fun archiveCard(cardId: String, archived: Boolean): WebConsoleMutationPayload = withContext(dispatchers.io) {
        cardRepository.setArchived(cardId = cardId, archived = archived, updatedAt = timeProvider.nowEpochMillis())
        WebConsoleMutationPayload(message = if (archived) "卡片已归档" else "卡片已恢复")
    }

    /**
     * 问题列表直接输出当前卡片下的问题快照，是为了让就地编辑面板总能围绕明确卡片上下文工作。
     */
    suspend fun listQuestions(cardId: String): List<WebConsoleQuestionPayload> = withContext(dispatchers.io) {
        questionRepository.listByCard(cardId).map(payloadMapper::toQuestionPayload)
    }

    /**
     * 问题保存继续复用初始 due 计算，是为了让浏览器端新增题目后仍能进入现有复习调度语义。
     */
    suspend fun upsertQuestion(request: WebConsoleUpsertQuestionRequest): WebConsoleMutationPayload = withContext(dispatchers.io) {
        val trimmedPrompt = request.prompt.trim()
        require(trimmedPrompt.isNotBlank()) { "问题题面不能为空" }
        val now = timeProvider.nowEpochMillis()
        val initialDueAt = InitialDueAtCalculator.compute(nowEpochMillis = now)
        val existing = if (request.id != null) {
            questionRepository.findById(request.id)
        } else {
            null
        }
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
     * 问题删除保持单项入口，是为了让高风险操作始终绑定在明确对象上，而不是隐式批量执行。
     */
    suspend fun deleteQuestion(questionId: String): WebConsoleMutationPayload = withContext(dispatchers.io) {
        questionRepository.delete(questionId)
        WebConsoleMutationPayload(message = "问题已删除")
    }

    /**
     * 搜索接口继续复用题库查询仓储，是为了让网页与手机端对过滤条件和结果范围共用同一口径。
     */
    suspend fun search(request: WebConsoleSearchRequest): List<WebConsoleSearchResultPayload> = withContext(dispatchers.io) {
        studyInsightsRepository.searchQuestionContexts(
            QuestionQueryFilters(
                keyword = request.keyword,
                tag = request.tag,
                status = request.status?.let(QuestionStatus::fromStorageValue),
                deckId = request.deckId,
                cardId = request.cardId,
                masteryLevel = null
            )
        ).map(payloadMapper::toSearchResultPayload)
    }

    /**
     * 统计数据集中从洞察仓储回转，是为了让统计工作区在壳层升级后仍只依赖稳定 payload。
     */
    suspend fun getAnalytics(): WebConsoleAnalyticsPayload = withContext(dispatchers.io) {
        payloadMapper.toAnalyticsPayload(
            studyInsightsRepository.getReviewAnalytics(startEpochMillis = null)
        )
    }
}
