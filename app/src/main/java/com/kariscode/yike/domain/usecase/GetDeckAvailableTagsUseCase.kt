package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.repository.StudyInsightsRepository

/**
 * 卡组标签候选沿用题目层的既有标签，是为了让内容管理页和搜索页继续围绕同一套词汇体系工作。
 */
class GetDeckAvailableTagsUseCase(
    private val studyInsightsRepository: StudyInsightsRepository
) {
    /**
     * 标签候选在领域层集中读取后，ViewModel 就不需要再知道标签来自题目统计这一实现细节。
     */
    suspend operator fun invoke(limit: Int = 12): List<String> =
        studyInsightsRepository.listAvailableTags(limit = limit)
}
