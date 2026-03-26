package com.kariscode.yike.feature.review

import androidx.lifecycle.ViewModel
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 复习队列页面的副作用用于导航路由，
 * 这样 UI 可以只订阅一次性事件而不用把导航状态混入持续状态中。
 */
sealed interface ReviewQueueEffect {
    data class NavigateToCard(val cardId: String) : ReviewQueueEffect
    data object BackToHomeCompleted : ReviewQueueEffect
}

/**
 * 复习队列状态只关心加载与错误提示，具体路由通过 effect 发出，
 * 从而避免“导航后状态仍停留在 loading”这类易错状态机耦合。
 */
data class ReviewQueueUiState(
    val isLoading: Boolean,
    val isAllDone: Boolean,
    val errorMessage: String?
)

/**
 * ReviewQueueViewModel 把“选择下一张待复习卡片”的逻辑集中起来，
 * 目的是让后续复习页只处理本卡内流程，而不必同时承担队列选择。
 */
class ReviewQueueViewModel(
    private val questionRepository: QuestionRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReviewQueueUiState(isLoading = true, isAllDone = false, errorMessage = null))
    val uiState: StateFlow<ReviewQueueUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<ReviewQueueEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<ReviewQueueEffect> = _effects.asSharedFlow()

    init {
        /**
         * 进入队列即计算下一张卡片，保证复习入口路径尽可能短。
         */
        loadNext()
    }

    /**
     * 提供显式重试入口，避免数据库异常时用户只能返回首页再重新进入。
     */
    fun loadNext() {
        _uiState.update { it.copy(isLoading = true, isAllDone = false, errorMessage = null) }
        launchResult(
            action = {
                val now = timeProvider.nowEpochMillis()
                questionRepository.findNextDueCardId(now)
            },
            onSuccess = { nextCardId ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAllDone = nextCardId == null,
                        errorMessage = null
                    )
                }
                if (nextCardId == null) return@launchResult
                else _effects.tryEmit(ReviewQueueEffect.NavigateToCard(nextCardId))
            },
            onFailure = { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.userMessageOr(ErrorMessages.REVIEW_LOAD_FAILED)
                    )
                }
            }
        )
    }

}


