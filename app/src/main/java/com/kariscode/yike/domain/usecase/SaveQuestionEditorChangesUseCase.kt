package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.id.EntityIds
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.scheduler.InitialDueAtCalculator

/**
 * 编辑页正式保存链路抽成用例，是为了让“更新卡片、落问题、删除旧题、清理草稿”始终按同一业务意图执行。
 */
class SaveQuestionEditorChangesUseCase(
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val questionEditorDraftRepository: QuestionEditorDraftRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 正式保存阶段统一接收编辑快照，是为了让 ViewModel 只负责校验与状态推进，而不再承担领域对象重建细节。
     */
    suspend operator fun invoke(
        request: QuestionEditorSaveRequest
    ) {
        val now = timeProvider.nowEpochMillis()
        val initialDueAt = InitialDueAtCalculator.compute(
            nowEpochMillis = now
        )
        val updatedCard = request.card.copy(
            title = request.title.trim(),
            description = request.description,
            updatedAt = now
        )
        cardRepository.upsert(updatedCard)
        questionRepository.upsertAll(
            request.questions.map { draft ->
                draft.toQuestion(
                    cardId = request.cardId,
                    nowEpochMillis = now,
                    initialDueAt = initialDueAt,
                    original = request.originalQuestionsById[draft.id]
                )
            }
        )
        questionRepository.deleteAll(request.deletedQuestionIds)
        questionEditorDraftRepository.deleteDraft(request.cardId)
    }

    /**
     * 草稿转正式问题的规则收在用例层，是为了把新增题目默认字段与旧题更新策略固定在一个可复用入口。
     */
    private fun QuestionEditorQuestionDraftValue.toQuestion(
        cardId: String,
        nowEpochMillis: Long,
        initialDueAt: Long,
        original: Question?
    ): Question {
        val trimmedPrompt = prompt.trim()
        if (!isNew && original != null) {
            return original.copy(
                prompt = trimmedPrompt,
                answer = answer,
                updatedAt = nowEpochMillis
            )
        }
        return Question(
            id = if (isNew) EntityIds.newQuestionId() else id,
            cardId = cardId,
            prompt = trimmedPrompt,
            answer = answer,
            tags = emptyList(),
            status = QuestionStatus.ACTIVE,
            stageIndex = 0,
            dueAt = initialDueAt,
            lastReviewedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            createdAt = original?.createdAt ?: nowEpochMillis,
            updatedAt = nowEpochMillis
        )
    }
}

/**
 * 编辑页草稿值只保留正式保存需要的字段，是为了避免用例层依赖 UI 特有的校验提示等展示状态。
 */
data class QuestionEditorQuestionDraftValue(
    val id: String,
    val prompt: String,
    val answer: String,
    val isNew: Boolean
)

/**
 * 正式保存请求显式建模，是为了让编辑页保存边界清晰可测，而不是依赖多个松散参数维持约定。
 */
data class QuestionEditorSaveRequest(
    val cardId: String,
    val card: Card,
    val title: String,
    val description: String,
    val questions: List<QuestionEditorQuestionDraftValue>,
    val originalQuestionsById: Map<String, Question>,
    val deletedQuestionIds: Set<String>
)

