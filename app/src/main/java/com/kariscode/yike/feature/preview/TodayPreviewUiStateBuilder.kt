package com.kariscode.yike.feature.preview

import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionMasteryLevel
import com.kariscode.yike.domain.model.QuestionMasterySnapshot
import kotlin.math.ceil

internal const val DEFAULT_RESPONSE_TIME_MS: Double = 15_000.0
internal const val MIN_RESPONSE_TIME_MS: Double = 10_000.0

/**
 * 预处理后的题目上下文缓存熟练度结果，是为了让分组、计数和预览项共用同一份计算，
 * 避免同一轮刷新里对同一个问题重复做多次 snapshot。
 */
private data class ResolvedDueQuestion(
    val context: QuestionContext,
    val mastery: QuestionMasterySnapshot,
    val isLowMastery: Boolean
)

/**
 * 汇总统计先在 builder 内部形成快照，是为了让总题数、卡片数与最早到期时间共享同一轮遍历结果，
 * 避免随着字段增加把顶层统计拆成越来越多分散扫描。
 */
private data class TodayPreviewBuildSummary(
    val totalDueQuestions: Int,
    val totalDueCards: Int,
    val lowMasteryCount: Int,
    val earliestDueAt: Long?
)

/**
 * 卡组分组在排序前额外保留首个到期时间，是为了避免排序阶段再把卡片和题目重新摊平一遍。
 */
private data class DeckGroupBuildResult(
    val uiModel: TodayPreviewDeckUiModel,
    val earliestDueAt: Long?
)

/**
 * 今日预览状态组装器保持纯输入输出，是为了让任务规模、分组与估时逻辑脱离 ViewModel 生命周期独立演进。
 */
internal object TodayPreviewUiStateBuilder {
    /**
     * 预览页的汇总值与分组都从同一轮 due 列表构建，是为了保证页面上的所有数字口径一致。
     */
    fun build(
        dueQuestions: List<QuestionContext>,
        averageResponseTimeMs: Double?
    ): TodayPreviewUiState {
        val resolvedResponseTimeMs = averageResponseTimeMs
            ?.coerceAtLeast(MIN_RESPONSE_TIME_MS)
            ?: DEFAULT_RESPONSE_TIME_MS
        val resolvedQuestions = dueQuestions.map(::resolveDueQuestion)
        val summary = summarizeResolvedQuestions(resolvedQuestions)
        val deckGroups = resolvedQuestions
            .groupBy { it.context.deckId }
            .values
            .map { deckQuestions -> buildDeckGroup(deckQuestions, resolvedResponseTimeMs) }
            .sortedWith(
                compareByDescending<DeckGroupBuildResult> { it.uiModel.dueQuestionCount }
                    .thenBy(DeckGroupBuildResult::earliestDueAt)
            )
            .map(DeckGroupBuildResult::uiModel)
        return TodayPreviewUiState(
            isLoading = false,
            totalDueQuestions = summary.totalDueQuestions,
            totalDueCards = summary.totalDueCards,
            totalDecks = deckGroups.size,
            estimatedMinutes = estimateMinutes(summary.totalDueQuestions, resolvedResponseTimeMs),
            averageSecondsPerQuestion = ceil(resolvedResponseTimeMs / 1000.0).toInt(),
            lowMasteryCount = summary.lowMasteryCount,
            earliestDueAt = summary.earliestDueAt,
            deckGroups = deckGroups,
            errorMessage = null
        )
    }

    /**
     * 卡组分组内部继续按卡片组织，是为了贴合“先决定学哪科，再决定做哪张卡”的使用顺序。
     */
    private fun buildDeckGroup(
        questions: List<ResolvedDueQuestion>,
        averageResponseTimeMs: Double
    ): DeckGroupBuildResult {
        val firstQuestion = questions.first()
        val deckLowMasteryCount = questions.count(ResolvedDueQuestion::isLowMastery)
        var earliestDueAt: Long? = null
        val cards = questions.groupBy { it.context.question.cardId }
            .map { (cardId, cardQuestions) ->
                val firstCardQuestion = cardQuestions.first()
                val sortedCardQuestions = cardQuestions
                    .sortedBy { it.context.question.dueAt }
                val cardEarliestDueAt = sortedCardQuestions.firstOrNull()?.context?.question?.dueAt
                if (cardEarliestDueAt != null && (earliestDueAt == null || cardEarliestDueAt < earliestDueAt)) {
                    earliestDueAt = cardEarliestDueAt
                }
                val previewQuestions = sortedCardQuestions
                    .take(3)
                    .map { question ->
                        TodayPreviewQuestionUiModel(
                            questionId = question.context.question.id,
                            prompt = question.context.question.prompt,
                            dueAt = question.context.question.dueAt,
                            mastery = question.mastery
                        )
                    }
                TodayPreviewCardUiModel(
                    cardId = cardId,
                    cardTitle = firstCardQuestion.context.cardTitle,
                    dueQuestionCount = cardQuestions.size,
                    estimatedMinutes = estimateMinutes(cardQuestions.size, averageResponseTimeMs),
                    lowMasteryCount = cardQuestions.count(ResolvedDueQuestion::isLowMastery),
                    questions = previewQuestions
                )
            }
            .sortedByDescending(TodayPreviewCardUiModel::dueQuestionCount)
        return DeckGroupBuildResult(
            uiModel = TodayPreviewDeckUiModel(
                deckId = firstQuestion.context.deckId,
                deckName = firstQuestion.context.deckName,
                dueQuestionCount = questions.size,
                estimatedMinutes = estimateMinutes(questions.size, averageResponseTimeMs),
                lowMasteryCount = deckLowMasteryCount,
                cards = cards
            ),
            earliestDueAt = earliestDueAt
        )
    }

    /**
     * 估时采用向上取整分钟，是为了让用户拿到更保守的预期，减少“实际比预览更久”的挫败感。
     */
    private fun estimateMinutes(questionCount: Int, averageResponseTimeMs: Double): Int {
        if (questionCount <= 0) return 0
        return maxOf(1, ceil(questionCount * averageResponseTimeMs / 60_000.0).toInt())
    }

    /**
     * 同一题目的熟练度与低熟练度标签在进入分组前先算好，是为了避免统计与预览项重复调用 snapshot。
     */
    private fun resolveDueQuestion(context: QuestionContext): ResolvedDueQuestion {
        val mastery = QuestionMasteryCalculator.snapshot(context.question)
        return ResolvedDueQuestion(
            context = context,
            mastery = mastery,
            isLowMastery = mastery.level == QuestionMasteryLevel.NEW || mastery.level == QuestionMasteryLevel.LEARNING
        )
    }

    /**
     * 顶层汇总单独收口，是为了让“总卡片数/低熟练度数/最早到期”这组统计随字段增长时仍维持单次扫描。
     */
    private fun summarizeResolvedQuestions(
        questions: List<ResolvedDueQuestion>
    ): TodayPreviewBuildSummary {
        val cardIds = LinkedHashSet<String>(questions.size)
        var lowMasteryCount = 0
        var earliestDueAt: Long? = null
        questions.forEach { question ->
            cardIds += question.context.question.cardId
            if (question.isLowMastery) {
                lowMasteryCount += 1
            }
            val dueAt = question.context.question.dueAt
            if (earliestDueAt == null || dueAt < earliestDueAt) {
                earliestDueAt = dueAt
            }
        }
        return TodayPreviewBuildSummary(
            totalDueQuestions = questions.size,
            totalDueCards = cardIds.size,
            lowMasteryCount = lowMasteryCount,
            earliestDueAt = earliestDueAt
        )
    }
}
