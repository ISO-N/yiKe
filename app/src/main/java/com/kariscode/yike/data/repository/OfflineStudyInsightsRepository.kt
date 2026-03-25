package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.QuestionContextRow
import com.kariscode.yike.data.local.db.dao.QuestionSearchTokenDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.mapper.decodeTags
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.data.search.QuestionSearchTokenizer
import com.kariscode.yike.domain.model.DeckReviewAnalyticsSnapshot
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionMasteryCalculator
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot
import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 学习洞察仓储把搜索、预览和统计查询集中实现，
 * 是为了让 P0 新页面依赖同一套数据口径，而不是各自直接操作 DAO。
 */
class OfflineStudyInsightsRepository(
    private val questionDao: QuestionDao,
    private val questionSearchTokenDao: QuestionSearchTokenDao,
    private val reviewRecordDao: ReviewRecordDao,
    private val dispatchers: AppDispatchers
) : StudyInsightsRepository {
    /**
     * 搜索结果先在数据库做文本和层级过滤，再在仓储层补齐熟练度筛选，
     * 这样既能保持查询简单，也能复用同一套熟练度规则。
     */
    override suspend fun searchQuestionContexts(filters: QuestionQueryFilters): List<QuestionContext> =
        dispatchers.onIo {
            val keyword = filters.keyword.trim().ifBlank { null }
            val keywordTokens = QuestionSearchTokenizer.tokenize(keyword.orEmpty())
            val candidateQuestionIds = resolveCandidateQuestionIds(
                keyword = keyword,
                keywordTokens = keywordTokens
            )
            if (keyword != null && keywordTokens.isNotEmpty() && candidateQuestionIds.isEmpty()) {
                return@onIo emptyList()
            }
            loadQuestionContextRows(
                keyword = keyword,
                tagKeyword = filters.tag?.trim()?.ifBlank { null },
                status = filters.status?.storageValue,
                deckId = filters.deckId,
                cardId = filters.cardId,
                maxDueAt = null,
                questionIds = candidateQuestionIds,
                includeAllQuestionIds = keyword == null || keywordTokens.isEmpty()
            ).map { row -> row.toDomain() }
                .filterByMastery(filters)
        }

    /**
     * 今日预览只取已到期的问题，是为了让用户看到的总量与真正进入复习队列时保持一致。
     */
    override suspend fun listDueQuestionContexts(nowEpochMillis: Long): List<QuestionContext> =
        dispatchers.onIo {
            loadQuestionContextRows(
                keyword = null,
                tagKeyword = null,
                status = QuestionStatus.ACTIVE.storageValue,
                deckId = null,
                cardId = null,
                maxDueAt = nowEpochMillis,
                questionIds = listOf(NO_MATCH_QUESTION_ID),
                includeAllQuestionIds = true
            ).map { row -> row.toDomain() }
        }

    /**
     * 标签建议在仓储层完成解析与去重，是为了让不同页面共享同一套“常用标签”排序方式。
     */
    override suspend fun listAvailableTags(limit: Int): List<String> = dispatchers.onIo {
        questionDao.listTagsJson(activeStatus = QuestionStatus.ACTIVE.storageValue)
            .flatMap(::decodeTags)
            .map(String::trim)
            .filter(String::isNotBlank)
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key }
    }

    /**
     * 统计摘要在仓储层转换成领域模型后，页面就不需要再理解 SQL 聚合字段的细节语义。
     */
    override suspend fun getReviewAnalytics(startEpochMillis: Long?): ReviewAnalyticsSnapshot =
        dispatchers.onIo {
            val summary = reviewRecordDao.getReviewAnalytics(startEpochMillis = startEpochMillis)
            val deckBreakdowns = reviewRecordDao.listDeckReviewAnalytics(startEpochMillis = startEpochMillis)
                .map { row ->
                    DeckReviewAnalyticsSnapshot(
                        deckId = row.deckId,
                        deckName = row.deckName,
                        reviewCount = row.reviewCount,
                        forgettingRate = row.reviewCount.safeRatio(row.againCount),
                        averageResponseTimeMs = row.averageResponseTimeMs
                    )
                }
            ReviewAnalyticsSnapshot(
                totalReviews = summary.totalReviews,
                againCount = summary.againCount,
                hardCount = summary.hardCount,
                goodCount = summary.goodCount,
                easyCount = summary.easyCount,
                averageResponseTimeMs = summary.averageResponseTimeMs,
                forgettingRate = summary.totalReviews.safeRatio(summary.againCount),
                deckBreakdowns = deckBreakdowns
            )
        }

    /**
     * 连续学习天数依赖原始时间戳按本地日期计算，因此直接透传可保留时区判断自由度。
     */
    override suspend fun listReviewTimestamps(startEpochMillis: Long?): List<Long> =
        dispatchers.onIo {
            reviewRecordDao.listReviewTimestamps(startEpochMillis = startEpochMillis)
        }
    /**
     * 熟练度筛选保持在仓储层收口，是为了让卡片页摘要和搜索页结果始终看到同一批题目。
     */
    private fun List<QuestionContext>.filterByMastery(
        filters: QuestionQueryFilters
    ): List<QuestionContext> = filters.masteryLevel?.let { expectedLevel ->
        filter { context ->
            QuestionMasteryCalculator.snapshot(context.question).level == expectedLevel
        }
    } ?: this

    /**
     * 比例换算收口为辅助函数，是为了避免 AGAIN 比例和遗忘率在多个调用点重复处理除零分支。
     */
    private fun Int.safeRatio(numerator: Int): Float {
        if (this <= 0) return 0f
        return numerator.toFloat() / this.toFloat()
    }

    /**
     * 关键词候选集只在可分词时走索引表，是为了兼顾单字符搜索兼容性和多字符搜索性能收益。
     */
    private suspend fun resolveCandidateQuestionIds(
        keyword: String?,
        keywordTokens: List<String>
    ): List<String> {
        if (keyword == null || keywordTokens.isEmpty()) {
            return emptyList()
        }
        return questionSearchTokenDao.listByTokens(tokens = keywordTokens)
            .groupBy { token -> token.questionId }
            .filterValues { matches -> matches.map { token -> token.token }.distinct().size >= keywordTokens.size }
            .keys
            .toList()
    }

    /**
     * 题目候选过多时按块查询并在仓储层统一排序，是为了既避开 SQLite 变量上限，也保持页面结果顺序稳定。
     */
    private suspend fun loadQuestionContextRows(
        keyword: String?,
        tagKeyword: String?,
        status: String?,
        deckId: String?,
        cardId: String?,
        maxDueAt: Long?,
        questionIds: List<String>,
        includeAllQuestionIds: Boolean
    ): List<QuestionContextRow> {
        val effectiveKeyword = keyword.takeIf { includeAllQuestionIds }
        if (includeAllQuestionIds) {
            return questionDao.listQuestionContexts(
                keyword = effectiveKeyword,
                tagKeyword = tagKeyword,
                status = status,
                deckId = deckId,
                cardId = cardId,
                maxDueAt = maxDueAt,
                includeAllQuestionIds = true,
                questionIds = listOf(NO_MATCH_QUESTION_ID)
            )
        }
        return questionIds.chunked(MAX_QUESTION_IDS_PER_QUERY)
            .flatMap { chunk ->
                questionDao.listQuestionContexts(
                    keyword = null,
                    tagKeyword = tagKeyword,
                    status = status,
                    deckId = deckId,
                    cardId = cardId,
                    maxDueAt = maxDueAt,
                    includeAllQuestionIds = false,
                    questionIds = chunk
                )
            }
            .sortedWith(questionContextRowOrder())
    }

    /**
     * 分块查询后的合并结果需要重放数据库的排序规则，才能保证搜索页和今日预览的展示顺序不漂移。
     */
    private fun questionContextRowOrder(): Comparator<QuestionContextRow> =
        compareBy<QuestionContextRow> { row -> row.dueAt }
            .thenByDescending { row -> row.updatedAt }
            .thenBy { row -> row.createdAt }

    private companion object {
        const val MAX_QUESTION_IDS_PER_QUERY: Int = 900
        const val NO_MATCH_QUESTION_ID: String = "__unused__"
    }
}
