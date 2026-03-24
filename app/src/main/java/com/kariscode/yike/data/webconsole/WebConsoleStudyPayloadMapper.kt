package com.kariscode.yike.data.webconsole

import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.QuestionContext
import kotlin.random.Random

/**
 * 学习 payload 映射集中在单点，是为了让正式复习和自由练习在会话拆分后仍共享稳定 DTO 契约。
 */
internal class WebConsoleStudyPayloadMapper {
    /**
     * 学习工作区摘要集中拼装，是为了让壳层统一表达今日规模和可恢复会话。
     */
    fun toStudyWorkspacePayload(
        dueCardCount: Int,
        dueQuestionCount: Int,
        activeSession: WebConsoleStudySession?
    ): WebConsoleStudyWorkspacePayload = WebConsoleStudyWorkspacePayload(
        dueCardCount = dueCardCount,
        dueQuestionCount = dueQuestionCount,
        activeSession = activeSession?.let(::toStudySessionSummaryPayload)
    )

    /**
     * 会话摘要集中拼装，是为了让学习工作区和全局上下文栏共享同一恢复文案。
     */
    fun toStudySessionSummaryPayload(session: WebConsoleStudySession): WebConsoleStudySessionSummaryPayload = when (session) {
        is WebConsoleReviewStudySession -> {
            val currentCard = session.currentCardOrNull()
            when {
                session.isCompleted() -> WebConsoleStudySessionSummaryPayload(
                    type = WebConsoleStudySessionTypes.REVIEW,
                    title = "今日复习已处理完成",
                    detail = "本轮共完成 ${session.cards.size} 张待复习卡片",
                    actionLabel = "查看结果"
                )

                session.isCurrentCardCompleted() -> WebConsoleStudySessionSummaryPayload(
                    type = WebConsoleStudySessionTypes.REVIEW,
                    title = "当前卡片已完成",
                    detail = "${currentCard?.cardTitle ?: "当前卡片"} 已处理完毕，可继续下一张",
                    actionLabel = "继续复习"
                )

                else -> WebConsoleStudySessionSummaryPayload(
                    type = WebConsoleStudySessionTypes.REVIEW,
                    title = "今日复习进行中",
                    detail = "${currentCard?.cardTitle ?: "当前卡片"} · 第 ${session.currentQuestionIndex + 1} / ${currentCard?.questions?.size ?: 0} 题",
                    actionLabel = "恢复复习"
                )
            }
        }

        is WebConsolePracticeStudySession -> WebConsoleStudySessionSummaryPayload(
            type = WebConsoleStudySessionTypes.PRACTICE,
            title = "自由练习进行中",
            detail = "第 ${session.currentIndex + 1} / ${session.questions.size} 题 · ${session.orderMode.label}",
            actionLabel = "恢复练习"
        )
    }

    /**
     * 完整学习会话 payload 统一从映射器输出，是为了让运行时快照与前端工作区解耦。
     */
    fun toStudySessionPayload(session: WebConsoleStudySession): WebConsoleStudySessionPayload = when (session) {
        is WebConsoleReviewStudySession -> {
            val currentCard = session.currentCardOrNull()
            val currentQuestion = session.currentQuestionOrNull()
            val totalQuestionCount = currentCard?.questions?.size ?: 0
            val completedQuestionCount = when {
                session.isCompleted() -> session.cards.lastOrNull()?.questions?.size ?: 0
                else -> session.currentQuestionIndex.coerceAtMost(totalQuestionCount)
            }
            WebConsoleStudySessionPayload(
                type = WebConsoleStudySessionTypes.REVIEW,
                title = "今日复习",
                summary = when {
                    session.isCompleted() -> "本轮待复习内容已全部处理完成"
                    session.isCurrentCardCompleted() -> "${currentCard?.cardTitle ?: "当前卡片"} 已完成，准备继续下一张"
                    else -> "${currentCard?.cardTitle ?: "当前卡片"} · 第 ${session.currentQuestionIndex + 1} / $totalQuestionCount 题"
                },
                review = WebConsoleReviewStudyPayload(
                    deckName = currentCard?.deckName,
                    cardTitle = currentCard?.cardTitle,
                    cardProgressText = when {
                        session.isCompleted() -> "全部完成"
                        else -> "第 ${session.currentCardIndex + 1} / ${session.cards.size} 张卡"
                    },
                    questionProgressText = when {
                        session.isCompleted() -> "本轮复习完成"
                        session.isCurrentCardCompleted() -> "本卡已完成"
                        else -> "第 ${session.currentQuestionIndex + 1} / $totalQuestionCount 题"
                    },
                    completedQuestionCount = completedQuestionCount,
                    totalQuestionCount = totalQuestionCount,
                    answerVisible = session.answerVisible,
                    currentQuestion = currentQuestion?.let(::toStudyQuestionPayload),
                    isCardCompleted = session.isCurrentCardCompleted(),
                    isSessionCompleted = session.isCompleted(),
                    nextCardTitle = session.cards.getOrNull(session.currentCardIndex + 1)?.cardTitle
                )
            )
        }

        is WebConsolePracticeStudySession -> WebConsoleStudySessionPayload(
            type = WebConsoleStudySessionTypes.PRACTICE,
            title = "自由练习",
            summary = "第 ${session.currentIndex + 1} / ${session.questions.size} 题 · ${session.orderMode.label}",
            practice = WebConsolePracticeStudyPayload(
                orderMode = session.orderMode.storageValue,
                orderModeLabel = session.orderMode.label,
                progressText = "第 ${session.currentIndex + 1} / ${session.questions.size} 题",
                answerVisible = session.answerVisible,
                currentQuestion = session.currentQuestionOrNull()?.let(::toPracticeQuestionPayload),
                canGoPrevious = session.currentIndex > 0,
                canGoNext = session.currentIndex < session.questions.lastIndex,
                sessionSeed = session.sessionSeed
            )
        )
    }

    /**
     * due 题目按卡片稳定分组，是为了继续承接 phase2 已验证的“按卡片逐题推进”语义。
     */
    fun toReviewCardSnapshots(contexts: List<QuestionContext>): List<WebConsoleReviewCardSnapshot> = buildList {
        val grouped = linkedMapOf<String, MutableList<QuestionContext>>()
        for (context in contexts) {
            grouped.getOrPut(context.question.cardId) { mutableListOf() }.add(context)
        }
        grouped.values.forEach { items ->
            val first = items.first()
            add(
                WebConsoleReviewCardSnapshot(
                    deckName = first.deckName,
                    cardId = first.question.cardId,
                    cardTitle = first.cardTitle,
                    questions = items.map { questionContext ->
                        WebConsoleReviewQuestionSnapshot(
                            questionId = questionContext.question.id,
                            prompt = questionContext.question.prompt,
                            answerText = questionContext.question.answer.ifBlank { "无答案" },
                            stageIndex = questionContext.question.stageIndex
                        )
                    }
                )
            )
        }
    }

    /**
     * 练习题序在服务端一次性固化，是为了让随机模式和刷新恢复共享同一浏览路径。
     */
    fun toPracticeQuestionSnapshots(
        contexts: List<QuestionContext>,
        orderMode: PracticeOrderMode,
        nowEpochMillis: Long
    ): WebConsoleOrderedPracticeQuestions {
        val questions = contexts.map { context ->
            WebConsolePracticeQuestionSnapshot(
                questionId = context.question.id,
                deckName = context.deckName,
                cardTitle = context.cardTitle,
                prompt = context.question.prompt,
                answerText = context.question.answer.ifBlank { "无答案" }
            )
        }
        if (orderMode != PracticeOrderMode.RANDOM) {
            return WebConsoleOrderedPracticeQuestions(items = questions, seed = null)
        }
        val seed = nowEpochMillis xor questions.joinToString(separator = "|") { question ->
            question.questionId
        }.hashCode().toLong()
        return WebConsoleOrderedPracticeQuestions(
            items = questions.shuffled(Random(seed)),
            seed = seed
        )
    }

    private fun toStudyQuestionPayload(snapshot: WebConsoleReviewQuestionSnapshot): WebConsoleStudyQuestionPayload =
        WebConsoleStudyQuestionPayload(
            questionId = snapshot.questionId,
            prompt = snapshot.prompt,
            answerText = snapshot.answerText,
            stageIndex = snapshot.stageIndex
        )

    private fun toPracticeQuestionPayload(snapshot: WebConsolePracticeQuestionSnapshot): WebConsolePracticeQuestionPayload =
        WebConsolePracticeQuestionPayload(
            questionId = snapshot.questionId,
            deckName = snapshot.deckName,
            cardTitle = snapshot.cardTitle,
            prompt = snapshot.prompt,
            answerText = snapshot.answerText
        )
}

/**
 * 练习题目与随机种子成对返回，是为了让会话服务在保存顺序时不必拆成多份临时状态。
 */
internal data class WebConsoleOrderedPracticeQuestions(
    val items: List<WebConsolePracticeQuestionSnapshot>,
    val seed: Long?
)
