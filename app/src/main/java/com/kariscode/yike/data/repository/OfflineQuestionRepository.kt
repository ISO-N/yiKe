package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.TodayReviewSummaryRow
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.RoomMappers
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * QuestionRepository 需要统一 status/due 条件，才能保证编辑、复习与提醒在同一口径下运行；
 * 这也是离线应用里最容易“看起来能用但口径漂移”的风险点之一。
 */
class OfflineQuestionRepository(
    private val questionDao: QuestionDao,
    private val dispatchers: AppDispatchers
) : QuestionRepository {
    /**
     * 观察式查询让编辑页在新增/删除后自然更新，避免草稿状态与数据库脱节。
     */
    override fun observeQuestionsByCard(cardId: String): Flow<List<Question>> =
        questionDao.observeQuestionsByCard(cardId).map { list -> list.map { entity -> RoomMappers.run { entity.toDomain() } } }

    /**
     * 单对象查询用于评分提交事务等需要精确定位的场景。
     */
    override suspend fun findById(questionId: String): Question? = withContext(dispatchers.io) {
        questionDao.findById(questionId)?.let { entity -> RoomMappers.run { entity.toDomain() } }
    }

    /**
     * 一次性读取编辑页所需问题快照时直接走同步查询，可以减少不必要的 Flow 建立与首次收集成本。
     */
    override suspend fun listByCard(cardId: String): List<Question> = withContext(dispatchers.io) {
        questionDao.listByCard(cardId)
            .map { entity -> RoomMappers.run { entity.toDomain() } }
    }

    /**
     * 批量写入可避免逐条保存导致中途失败的半完成状态，符合编辑页“一次保存”的期望。
     */
    override suspend fun upsertAll(questions: List<Question>) = withContext(dispatchers.io) {
        questionDao.upsertAll(questions.map { RoomMappers.run { it.toEntity() } })
        Unit
    }

    /**
     * due 查询必须显式带 active status，才能保证归档问题不进入待复习集合。
     */
    override suspend fun listDueQuestions(nowEpochMillis: Long): List<Question> = withContext(dispatchers.io) {
        questionDao.listDueQuestions(activeStatus = QuestionEntity.STATUS_ACTIVE, nowEpochMillis = nowEpochMillis)
            .map { entity -> RoomMappers.run { entity.toDomain() } }
    }

    /**
     * 统计查询委托给数据库聚合实现，以保证卡片/问题去重与过滤规则一致。
     */
    override suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary = withContext(dispatchers.io) {
        val row: TodayReviewSummaryRow = questionDao.getTodayReviewSummary(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis
        )
        TodayReviewSummary(
            dueCardCount = row.dueCardCount,
            dueQuestionCount = row.dueQuestionCount
        )
    }

    /**
     * 以 ID 直接删除能让上层不依赖 Entity 类型，
     * 也能避免为了同一条删除路径先执行一次多余查询。
     */
    override suspend fun delete(questionId: String) = withContext(dispatchers.io) {
        questionDao.deleteById(questionId)
        Unit
    }
}
