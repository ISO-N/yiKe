package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.domain.coroutine.parallel3
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionEditorDraftLoadResult
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.QuestionRepository

/**
 * 编辑页正式内容加载单独收口成用例，是为了让“正式卡片 + 正式问题 + 本地草稿”始终以同一轮快照返回。
 */
class LoadQuestionEditorContentUseCase(
    private val cardRepository: CardRepository,
    private val questionRepository: QuestionRepository,
    private val questionEditorDraftRepository: QuestionEditorDraftRepository
) {
    /**
     * 编辑页进入时需要同步决策展示正式内容还是提示恢复草稿，因此这里直接返回三部分数据的统一快照。
     */
    suspend operator fun invoke(
        cardId: String
    ): QuestionEditorContentSnapshot {
        val (card, questions, draftResult) = parallel3(
            first = { cardRepository.findById(cardId) },
            second = { questionRepository.listByCard(cardId) },
            third = { questionEditorDraftRepository.loadDraft(cardId) }
        )
        return QuestionEditorContentSnapshot(
            card = card,
            questions = questions,
            draftResult = draftResult
        )
    }
}

/**
 * 编辑页加载快照显式聚合正式内容与草稿信息，是为了让调用方只处理恢复策略，而不重复关心查询来源。
 */
data class QuestionEditorContentSnapshot(
    val card: Card?,
    val questions: List<Question>,
    val draftResult: QuestionEditorDraftLoadResult
)

