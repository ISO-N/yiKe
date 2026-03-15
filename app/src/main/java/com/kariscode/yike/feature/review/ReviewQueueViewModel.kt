package com.kariscode.yike.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val _uiState = MutableStateFlow(ReviewQueueUiState(isLoading = true, errorMessage = null))
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
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val now = timeProvider.nowEpochMillis()
                val dueQuestions = questionRepository.listDueQuestions(now)
                selectNextCardId(dueQuestions)
            }.onSuccess { nextCardId ->
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
                if (nextCardId == null) _effects.tryEmit(ReviewQueueEffect.BackToHomeCompleted)
                else _effects.tryEmit(ReviewQueueEffect.NavigateToCard(nextCardId))
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "加载失败") }
            }
        }
    }

    /**
     * 选择策略首版以“最早 dueAt 的卡片优先”为准，
     * 这样能让用户优先处理已经超期最久的内容，且实现简单可解释。
     */
    private fun selectNextCardId(dueQuestions: List<Question>): String? {
        if (dueQuestions.isEmpty()) return null
        return dueQuestions
            .groupBy { it.cardId }
            .minBy { (_, qs) -> qs.minOf { it.dueAt } }
            .key
    }

    companion object {
        /**
         * 工厂注入依赖，避免 ViewModel 直接访问全局单例以保持可测试性。
         */
        fun factory(
            questionRepository: QuestionRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ReviewQueueViewModel(questionRepository, timeProvider) as T
            }
        }
    }
}

