package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.TodayReviewSummaryRow
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.data.mapper.toEntity
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow

/**
 * QuestionRepository 需要统一 status/due 条件，才能保证编辑、复习与提醒在同一口径下运行；
 * 这也是离线应用里最容易“看起来能用但口径漂移”的风险点之一。
 */
class OfflineQuestionRepository(
    private val questionDao: QuestionDao,
    private val dispatchers: AppDispatchers,
    private val timeProvider: TimeProvider,
    private val syncChangeRecorder: LanSyncChangeRecorder
) : QuestionRepository {
    /**
     * 观察式查询让编辑页在新增/删除后自然更新，避免草稿状态与数据库脱节。
     */
    override fun observeQuestionsByCard(cardId: String): Flow<List<Question>> =
        questionDao.observeQuestionsByCard(cardId).mapEach { entity ->
            entity.toDomain()
        }

    /**
     * 单对象查询用于评分提交事务等需要精确定位的场景。
     */
    override suspend fun findById(questionId: String): Question? = dispatchers.onIo {
        questionDao.findById(questionId).mapNullable { entity ->
            entity.toDomain()
        }
    }

    /**
     * 一次性读取编辑页所需问题快照时直接走同步查询，可以减少不必要的 Flow 建立与首次收集成本。
     */
    override suspend fun listByCard(cardId: String): List<Question> = dispatchers.onIo {
        questionDao.listByCard(cardId)
            .map { entity -> entity.toDomain() }
    }

    /**
     * 批量写入可避免逐条保存导致中途失败的半完成状态，符合编辑页“一次保存”的期望。
     */
    override suspend fun upsertAll(questions: List<Question>) = dispatchers.onIo {
        questionDao.upsertAll(questions.map { question -> question.toEntity() })
        questions.forEach { question ->
            syncChangeRecorder.recordQuestionUpsert(question)
        }
        Unit
    }

    /**
     * due 查询必须显式带 active status，才能保证归档问题不进入待复习集合。
     */
    override suspend fun listDueQuestions(nowEpochMillis: Long): List<Question> = dispatchers.onIo {
        questionDao.listDueQuestions(activeStatus = QuestionEntity.STATUS_ACTIVE, nowEpochMillis = nowEpochMillis)
            .map { entity -> entity.toDomain() }
    }

    /**
     * 队列页只要下一张卡片 id，因此复用数据库聚合结果能减少无意义的问题对象构建与集合分组。
     */
    override suspend fun findNextDueCardId(nowEpochMillis: Long): String? = dispatchers.onIo {
        questionDao.findNextDueCardId(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = nowEpochMillis
        )
    }

    /**
     * 统计查询委托给数据库聚合实现，以保证卡片/问题去重与过滤规则一致。
     */
    override suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary = dispatchers.onIo {
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
    override suspend fun delete(questionId: String) = dispatchers.onIo {
        val current = questionDao.findById(questionId)?.let { entity ->
            entity.toDomain()
        }
        questionDao.deleteById(questionId)
        syncChangeRecorder.recordDelete(
            entityType = SyncEntityType.QUESTION,
            entityId = questionId,
            summary = current?.prompt?.take(48) ?: questionId,
            modifiedAt = current?.updatedAt ?: timeProvider.nowEpochMillis()
        )
        Unit
    }

    /**
     * 批量删除在仓储层收口后，编辑页保存就不必手写循环删除模板，且数据库往返次数更可控。
     */
    override suspend fun deleteAll(questionIds: Collection<String>) = dispatchers.onIo {
        if (questionIds.isEmpty()) return@onIo
        val currentQuestions = questionDao.listByIds(questionIds.toList()).map { entity ->
            entity.toDomain()
        }
        questionDao.deleteByIds(questionIds)
        currentQuestions.forEach { question ->
            syncChangeRecorder.recordDelete(
                entityType = SyncEntityType.QUESTION,
                entityId = question.id,
                summary = question.prompt.take(48),
                modifiedAt = question.updatedAt
            )
        }
        Unit
    }
}
