package com.kariscode.yike.domain.usecase

import com.kariscode.yike.domain.repository.CardRepository

/**
 * 删除卡片进入用例层后，页面不需要再直接依赖仓储删除语义，
 * 也更容易把“删除会级联清理下层内容”固定成统一入口。
 */
class DeleteCardUseCase(
    private val cardRepository: CardRepository
) {
    /**
     * 删除动作保持最小参数，是为了让高风险操作在调用点尽量不再携带无关上下文。
     */
    suspend operator fun invoke(cardId: String) {
        cardRepository.delete(cardId)
    }
}
