package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 问题搜索抽成用例，是为了让“筛选条件如何映射到真实题库查询”集中在领域层，而不是散落在页面状态管理中。
 */
class SearchQuestionsUseCase(
    private val studyInsightsRepository: StudyInsightsRepository
) {
    /**
     * 搜索页只需要拿到带上下文的问题结果，因此这里直接返回搜索命中项，避免 ViewModel 继续拼装仓储调用。
     */
    suspend operator fun invoke(
        filters: QuestionQueryFilters
    ): List<QuestionContext> = studyInsightsRepository.searchQuestionContexts(filters = filters)
}
