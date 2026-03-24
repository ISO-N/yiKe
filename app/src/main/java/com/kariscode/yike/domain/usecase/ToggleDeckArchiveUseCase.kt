package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.repository.DeckRepository

/**
 * 卡组归档进入用例层后，更新时间规则就不会在多个列表页里各自复制。
 */
class ToggleDeckArchiveUseCase(
    private val deckRepository: DeckRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 归档动作统一补齐更新时间，是为了让回收站、同步和备份都看到一致的最近变更时间。
     */
    suspend operator fun invoke(deckId: String, archived: Boolean) {
        deckRepository.setArchived(
            deckId = deckId,
            archived = archived,
            updatedAt = timeProvider.nowEpochMillis()
        )
    }
}
