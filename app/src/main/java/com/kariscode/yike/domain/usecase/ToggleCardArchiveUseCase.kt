package com.kariscode.yike.domain.usecase

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.repository.CardRepository

/**
 * 卡片归档进入用例层后，更新时间规则和归档语义就不必继续散落在页面写操作里。
 */
class ToggleCardArchiveUseCase(
    private val cardRepository: CardRepository,
    private val timeProvider: TimeProvider
) {
    /**
     * 归档动作统一补齐更新时间，是为了让同步、回收站和备份看到一致的最新状态。
     */
    suspend operator fun invoke(cardId: String, archived: Boolean) {
        cardRepository.setArchived(
            cardId = cardId,
            archived = archived,
            updatedAt = timeProvider.nowEpochMillis()
        )
    }
}
