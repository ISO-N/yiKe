package com.kariscode.yike.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.QuestionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 首页状态显式区分加载/成功/错误，是为了让用户在本地存储异常或首次启动时仍能得到可理解反馈，
 * 并避免 UI 通过 null/0 的隐式约定猜测状态导致文案漂移。
 */
data class HomeUiState(
    val isLoading: Boolean,
    val summary: TodayReviewSummary?,
    val errorMessage: String?
)

/**
 * 首页 ViewModel 把“统计查询”从 UI 层剥离出来，原因是：
 * - 统计口径需要与提醒 Worker 复用
 * - 错误处理需要统一而不是散落在 Composable
 */
class HomeViewModel(
    private val questionRepository: QuestionRepository,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            summary = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        /**
         * 首次进入即加载统计，保证首页默认可用且不依赖用户手动刷新。
         */
        refresh()
    }

    /**
     * 刷新入口用于错误态重试与用户手动触发，避免页面只能靠重启应用恢复。
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching {
                val now = timeProvider.nowEpochMillis()
                questionRepository.getTodayReviewSummary(now)
            }.onSuccess { summary ->
                _uiState.update { it.copy(isLoading = false, summary = summary, errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update { it.copy(isLoading = false, errorMessage = throwable.message ?: "加载失败") }
            }
        }
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
                return HomeViewModel(questionRepository, timeProvider) as T
            }
        }
    }
}

