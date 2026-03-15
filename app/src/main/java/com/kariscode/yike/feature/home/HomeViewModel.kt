package com.kariscode.yike.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页状态显式区分加载/成功/错误，并补充最近卡组入口，
 * 这样首页既能回答“今天要复习什么”，也能回答“接下来去哪里继续维护内容”。
 */
data class HomeUiState(
    val isLoading: Boolean,
    val summary: TodayReviewSummary?,
    val recentDecks: List<DeckSummary>,
    val errorMessage: String?
)

/**
 * 首页 ViewModel 把复习概览和最近卡组查询收敛在一起，
 * 是为了避免页面自己发起多路查询后再拼装状态，导致错误反馈分叉。
 */
class HomeViewModel(
    private val questionRepository: QuestionRepository,
    private val deckRepository: DeckRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            summary = null,
            recentDecks = emptyList(),
            errorMessage = null
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        /**
         * 首次进入即加载首页所需的全部真实信息，保证用户不用额外操作就能看到主路径入口。
         */
        refresh()
    }

    /**
     * 刷新入口同时重算待复习概览和最近卡组，是为了让错误态重试后立即恢复完整首页。
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val now = timeProvider.nowEpochMillis()
                val summary = questionRepository.getTodayReviewSummary(now)
                val recentDecks = deckRepository.observeActiveDeckSummaries(now).first().take(3)
                summary to recentDecks
            }.onSuccess { (summary, recentDecks) ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = summary,
                        recentDecks = recentDecks,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        summary = null,
                        recentDecks = emptyList(),
                        errorMessage = throwable.message ?: "加载失败"
                    )
                }
            }
        }
    }

    companion object {
        /**
         * 工厂显式注入统计与内容仓储，避免 ViewModel 直接依赖全局容器而失去可测试性。
         */
        fun factory(
            questionRepository: QuestionRepository,
            deckRepository: DeckRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return HomeViewModel(questionRepository, deckRepository, timeProvider) as T
            }
        }
    }
}
