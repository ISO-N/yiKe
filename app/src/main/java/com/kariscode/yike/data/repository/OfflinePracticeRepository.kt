package com.kariscode.yike.data.repository

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.repository.PracticeRepository

/**
 * OfflinePracticeRepository 把练习模式的读取单独落到只读实现，
 * 是为了在仓储边界上明确区分“主动练习取数”和“正式复习提交评分”两条链路。
 */
class OfflinePracticeRepository(
    private val questionDao: QuestionDao,
    private val dispatchers: AppDispatchers
) : PracticeRepository {
    /**
     * 查询先在仓储层规范化范围参数，再下推到 DAO，
     * 这样空集合就不会被误解释为“查不到任何内容”而是“不过滤该层级”。
     */
    override suspend fun listPracticeQuestionContexts(args: PracticeSessionArgs): List<QuestionContext> =
        dispatchers.onIo {
            val normalized = args.normalized()
            questionDao.listPracticeQuestionContexts(
                activeStatus = QuestionEntity.STATUS_ACTIVE,
                includeAllDecks = normalized.deckIds.isEmpty(),
                deckIds = normalized.deckIds.ifEmpty { listOf("") },
                includeAllCards = normalized.cardIds.isEmpty(),
                cardIds = normalized.cardIds.ifEmpty { listOf("") },
                includeAllQuestions = normalized.questionIds.isEmpty(),
                questionIds = normalized.questionIds.ifEmpty { listOf("") }
            ).map { row -> row.toDomain() }
        }
}

