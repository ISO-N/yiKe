package com.kariscode.yike.data.webconsole

import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot

/**
 * 工作区 payload 映射集中在单点，是为了让内容、统计、设置等工作区继续共享同一份 DTO 口径。
 */
internal class WebConsoleWorkspacePayloadMapper {
    /**
     * 卡组摘要映射集中处理，是为了让概览和内容管理继续共享一致的卡组字段表达。
     */
    fun toDeckPayload(summary: DeckSummary): WebConsoleDeckPayload = WebConsoleDeckPayload(
        id = summary.deck.id,
        name = summary.deck.name,
        description = summary.deck.description,
        tags = summary.deck.tags,
        intervalStepCount = summary.deck.intervalStepCount,
        cardCount = summary.cardCount,
        questionCount = summary.questionCount,
        dueQuestionCount = summary.dueQuestionCount,
        archived = summary.deck.archived
    )

    /**
     * 卡片摘要映射集中处理，是为了让卡片 drill-down 和列表摘要围绕同一口径演进。
     */
    fun toCardPayload(summary: CardSummary): WebConsoleCardPayload = WebConsoleCardPayload(
        id = summary.card.id,
        deckId = summary.card.deckId,
        title = summary.card.title,
        description = summary.card.description,
        questionCount = summary.questionCount,
        dueQuestionCount = summary.dueQuestionCount,
        archived = summary.card.archived
    )

    /**
     * 问题映射集中处理，是为了让列表、编辑和搜索结果共享同一问题字段定义。
     */
    fun toQuestionPayload(question: Question): WebConsoleQuestionPayload = WebConsoleQuestionPayload(
        id = question.id,
        cardId = question.cardId,
        prompt = question.prompt,
        answer = question.answer,
        tags = question.tags,
        status = question.status.storageValue,
        stageIndex = question.stageIndex,
        dueAt = question.dueAt,
        lastReviewedAt = question.lastReviewedAt,
        reviewCount = question.reviewCount,
        lapseCount = question.lapseCount
    )

    /**
     * 搜索结果映射集中处理，是为了让搜索工作区和内容工作区始终共享同一上下文结构。
     */
    fun toSearchResultPayload(result: QuestionContext): WebConsoleSearchResultPayload = WebConsoleSearchResultPayload(
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

    /**
     * 统计映射集中处理，是为了让桌面壳层升级时仍只依赖稳定指标结构。
     */
    fun toAnalyticsPayload(snapshot: ReviewAnalyticsSnapshot): WebConsoleAnalyticsPayload = WebConsoleAnalyticsPayload(
        totalReviews = snapshot.totalReviews,
        againCount = snapshot.againCount,
        hardCount = snapshot.hardCount,
        goodCount = snapshot.goodCount,
        easyCount = snapshot.easyCount,
        averageResponseTimeMs = snapshot.averageResponseTimeMs,
        forgettingRate = snapshot.forgettingRate,
        deckBreakdowns = snapshot.deckBreakdowns.map { breakdown ->
            WebConsoleDeckAnalyticsPayload(
                deckId = breakdown.deckId,
                deckName = breakdown.deckName,
                reviewCount = breakdown.reviewCount,
                forgettingRate = breakdown.forgettingRate,
                averageResponseTimeMs = breakdown.averageResponseTimeMs
            )
        }
    )

    /**
     * 设置 payload 在服务端补齐显示文案，是为了让前端无需重复维护主题模式映射。
     */
    fun toSettingsPayload(settings: AppSettings): WebConsoleSettingsPayload = WebConsoleSettingsPayload(
        dailyReminderEnabled = settings.dailyReminderEnabled,
        dailyReminderHour = settings.dailyReminderHour,
        dailyReminderMinute = settings.dailyReminderMinute,
        themeMode = settings.themeMode.name,
        themeModeLabel = settings.themeMode.displayLabel,
        backupLastAt = settings.backupLastAt
    )
}
