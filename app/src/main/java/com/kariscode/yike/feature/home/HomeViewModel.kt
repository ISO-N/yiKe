package com.kariscode.yike.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.kariscode.yike.core.ui.message.ErrorMessages
import com.kariscode.yike.core.ui.message.userMessageOr
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.launchResult
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.usecase.GetHomeOverviewUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 首页内容模式需要直接表达“今天该复习 / 今天已清空 / 还没有内容”，
 * 否则 UI 很容易把“无任务”和“无数据”混成同一种空态。
 */
enum class HomeContentMode {
    REVIEW_READY,
    REVIEW_CLEARED,
    CONTENT_EMPTY
}

/**
 * 首页状态显式区分加载/成功/错误，并补充最近卡组入口，
 * 这样首页既能回答“今天要复习什么”，也能回答“接下来去哪里继续维护内容”。
 */
data class HomeUiState(
    val isLoading: Boolean,
    val summary: TodayReviewSummary,
    val recentDecks: List<DeckSummary>,
    val contentMode: HomeContentMode,
    val errorMessage: String?
)

/**
 * 首页 ViewModel 把复习概览和最近卡组查询收敛在一起，
 * 是为了避免页面自己发起多路查询后再拼装状态，导致错误反馈分叉。
 */
class HomeViewModel(
    private val getHomeOverviewUseCase: GetHomeOverviewUseCase,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        HomeUiState(
            isLoading = true,
            summary = TodayReviewSummary(
                dueCardCount = 0,
                dueQuestionCount = 0
            ),
            recentDecks = emptyList(),
            contentMode = HomeContentMode.CONTENT_EMPTY,
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
        launchResult(
            action = {
                getHomeOverviewUseCase(
                    nowEpochMillis = timeProvider.nowEpochMillis(),
                    recentDeckLimit = 3
                )
            },
            onSuccess = { overview ->
                _uiState.update {
                    HomeStateFactory.success(
                        state = it,
                        summary = overview.summary,
                        recentDecks = overview.recentDecks
                    )
                }
            },
            onFailure = { throwable ->
                _uiState.update {
                    HomeStateFactory.loadFailed(
                        state = it,
                        errorMessage = throwable.userMessageOr(ErrorMessages.HOME_LOAD_FAILED)
                    )
                }
            }
        )
    }

    companion object {
        /**
         * 工厂显式注入统计与内容仓储，避免 ViewModel 直接依赖全局容器而失去可测试性。
         */
        fun factory(
            questionRepository: QuestionRepository,
            deckRepository: DeckRepository,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = typedViewModelFactory {
            HomeViewModel(
                getHomeOverviewUseCase = GetHomeOverviewUseCase(
                    questionRepository = questionRepository,
                    deckRepository = deckRepository
                ),
                timeProvider = timeProvider
            )
        }
    }
}

