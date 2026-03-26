package com.kariscode.yike.feature.practice

import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.practice.PracticeSelectionProjection as DomainSelectionProjection
import com.kariscode.yike.domain.practice.applyToggle as applyToggleDomain
import com.kariscode.yike.domain.practice.buildCardOptions as buildCardOptionsDomain
import com.kariscode.yike.domain.practice.buildDeckOptions as buildDeckOptionsDomain
import com.kariscode.yike.domain.practice.buildPracticeSelectionProjection as buildSelectionProjectionDomain
import com.kariscode.yike.domain.practice.normalizeQuestionSelection as normalizeQuestionSelectionDomain

/**
 * feature 层只保留 UI 模型映射，是为了把 deck -> card -> question 的范围裁剪与归一化规则收口到 domain，
 * 同时避免 UI 预览文案（例如答案截断）反向污染领域层。
 */

/**
 * 练习范围投影把 deck/card/question 三层候选与归一化后的选择结果打包给 ViewModel，
 * 但真正的业务裁剪逻辑委托给 domain 侧的纯函数实现。
 */
internal data class PracticeSelectionProjection(
    val deckOptions: List<PracticeDeckOptionUiModel>,
    val cardOptions: List<PracticeCardOptionUiModel>,
    val questionOptions: List<PracticeQuestionOptionUiModel>,
    val selectedDeckIds: Set<String>,
    val selectedCardIds: Set<String>,
    val selectedQuestionIds: Set<String>?,
    val effectiveQuestionCount: Int
)

/**
 * deck 选项的构建保留在 feature 层的唯一目的，是把 domain 输出映射到 UI 模型，
 * 让组合组件只消费展示友好的字段。
 */
fun buildDeckOptions(
    allQuestionContexts: List<QuestionContext>,
    selectedDeckIds: Set<String>
): List<PracticeDeckOptionUiModel> = buildDeckOptionsDomain(
    allQuestionContexts = allQuestionContexts,
    selectedDeckIds = selectedDeckIds
).map { option ->
    PracticeDeckOptionUiModel(
        deckId = option.deckId,
        deckName = option.deckName,
        cardCount = option.cardCount,
        questionCount = option.questionCount,
        isSelected = option.isSelected
    )
}

/**
 * card 选项同样只做模型转换，是为了让候选口径继续由 domain 层负责。
 */
fun buildCardOptions(
    questionContexts: List<QuestionContext>,
    selectedCardIds: Set<String>
): List<PracticeCardOptionUiModel> = buildCardOptionsDomain(
    questionContexts = questionContexts,
    selectedCardIds = selectedCardIds
).map { option ->
    PracticeCardOptionUiModel(
        cardId = option.cardId,
        deckId = option.deckId,
        deckName = option.deckName,
        cardTitle = option.cardTitle,
        questionCount = option.questionCount,
        isSelected = option.isSelected
    )
}

/**
 * 题目手选若恰好覆盖当前全集则回退成 null，是为了让状态明确区分“全选当前范围”和“显式裁剪过”。
 */
fun Set<String>.normalizeQuestionSelection(
    availableQuestionIds: Set<String>
): Set<String>? = normalizeQuestionSelectionDomain(this, availableQuestionIds)

/**
 * 多选集合统一用可逆 toggle，是为了让 deck/card/question 三层都共享同一套点击语义。
 */
fun MutableSet<String>.applyToggle(id: String): Set<String> = applyToggleDomain(this, id)

/**
 * practice 设置页的范围投影依赖 domain 纯函数，然后把输出映射到 UI 需要的预览字段。
 */
internal fun buildPracticeSelectionProjection(
    allQuestionContexts: List<QuestionContext>,
    selectedDeckIds: Set<String>,
    selectedCardIds: Set<String>,
    selectedQuestionIds: Set<String>?
): PracticeSelectionProjection {
    val projection: DomainSelectionProjection = buildSelectionProjectionDomain(
        allQuestionContexts = allQuestionContexts,
        selectedDeckIds = selectedDeckIds,
        selectedCardIds = selectedCardIds,
        selectedQuestionIds = selectedQuestionIds
    )
    return PracticeSelectionProjection(
        deckOptions = projection.deckOptions.map { option ->
            PracticeDeckOptionUiModel(
                deckId = option.deckId,
                deckName = option.deckName,
                cardCount = option.cardCount,
                questionCount = option.questionCount,
                isSelected = option.isSelected
            )
        },
        cardOptions = projection.cardOptions.map { option ->
            PracticeCardOptionUiModel(
                cardId = option.cardId,
                deckId = option.deckId,
                deckName = option.deckName,
                cardTitle = option.cardTitle,
                questionCount = option.questionCount,
                isSelected = option.isSelected
            )
        },
        questionOptions = projection.questionOptions.map { option ->
            PracticeQuestionOptionUiModel(
                questionId = option.questionId,
                cardId = option.cardId,
                deckName = option.deckName,
                cardTitle = option.cardTitle,
                prompt = option.prompt,
                answerPreview = option.answer.ifBlank { "无答案" }.take(48),
                isSelected = option.isSelected
            )
        },
        selectedDeckIds = projection.selectedDeckIds,
        selectedCardIds = projection.selectedCardIds,
        selectedQuestionIds = projection.selectedQuestionIds,
        effectiveQuestionCount = projection.effectiveQuestionCount
    )
}

