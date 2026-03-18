package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.mapper.decodeTags
import com.kariscode.yike.data.mapper.toDomain
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
    private val reviewRecordDao: ReviewRecordDao,
    private val dispatchers: AppDispatchers
) : StudyInsightsRepository {
    /**
     * 搜索结果先在数据库做文本和层级过滤，再在仓储层补齐熟练度筛选，
     * 这样既能保持查询简单，也能复用同一套熟练度规则。
     */
    override suspend fun searchQuestionContexts(filters: QuestionQueryFilters): List<QuestionContext> =
        dispatchers.onIo {
            questionDao.listQuestionContexts(
                keyword = filters.keyword.trim().ifBlank { null },
                tagKeyword = filters.tag?.trim()?.ifBlank { null },
                status = filters.status?.storageValue,
                deckId = filters.deckId,
                cardId = filters.cardId,
                maxDueAt = null
            ).map { row -> row.toDomain() }
                .filterByMastery(filters)
        }

    /**
     * 今日预览只取已到期的问题，是为了让用户看到的总量与真正进入复习队列时保持一致。
     */
    override suspend fun listDueQuestionContexts(nowEpochMillis: Long): List<QuestionContext> =
        dispatchers.onIo {
            questionDao.listQuestionContexts(
                keyword = null,
                tagKeyword = null,
                status = QuestionStatus.ACTIVE.storageValue,
                deckId = null,
                cardId = null,
                maxDueAt = nowEpochMillis
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
}
