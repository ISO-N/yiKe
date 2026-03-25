package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.repository.DeckRepository

/**
 * 删除卡组进入用例层后，页面就不需要直接依赖仓储删除细节，
 * 也更容易把“会级联清理下层卡片与题目”固定成统一的高风险入口。
 */
class DeleteDeckUseCase(
    private val deckRepository: DeckRepository
) {
    /**
     * 删除动作保持最小参数，是为了让调用点只表达“确认删除这个卡组”，
     * 不再额外携带容易漂移的界面上下文。
     */
    suspend operator fun invoke(deckId: String) {
        deckRepository.delete(deckId)
    }
}
