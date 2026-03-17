package com.kariscode.yike.feature.analytics

import com.kariscode.yike.core.time.TimeConstants
import com.kariscode.yike.domain.model.DeckReviewAnalyticsSnapshot
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot
import com.kariscode.yike.testsupport.FakeStudyInsightsRepository
import com.kariscode.yike.testsupport.FixedTimeProvider
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AnalyticsViewModel 测试用于守住时间范围切换、连续学习和结论生成，
 * 避免统计页继续退化成“只有数字没有语义”的展示壳层。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    /**
     * 首次刷新必须根据时间戳算出连续学习天数，并把最高遗忘率卡组提炼成结论。
     */
    @Test
    fun init_buildsStreakAndConclusion() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val now = 1_700_000_000_000L
            val repository = FakeStudyInsightsRepository().apply {
                analytics = ReviewAnalyticsSnapshot(
                    totalReviews = 10,
                    againCount = 3,
                    hardCount = 2,
                    goodCount = 4,
                    easyCount = 1,
                    averageResponseTimeMs = 1_800.0,
                    forgettingRate = 0.3f,
                    deckBreakdowns = listOf(
                        DeckReviewAnalyticsSnapshot(
                            deckId = "deck_math",
                            deckName = "数学",
                            reviewCount = 6,
                            forgettingRate = 0.5f,
                            averageResponseTimeMs = 2_000.0
                        ),
                        DeckReviewAnalyticsSnapshot(
                            deckId = "deck_os",
                            deckName = "操作系统",
                            reviewCount = 4,
                            forgettingRate = 0.25f,
                            averageResponseTimeMs = 1_200.0
                        )
                    )
                )
                reviewTimestamps = listOf(
                    now,
                    now - TimeConstants.DAY_MILLIS,
                    now - 2L * TimeConstants.DAY_MILLIS
                )
            }

            val viewModel = AnalyticsViewModel(
                studyInsightsRepository = repository,
                timeProvider = FixedTimeProvider(nowEpochMillis = now),
                zoneId = ZoneId.of("UTC")
            )
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.streakDays)
            assertEquals(10, viewModel.uiState.value.totalReviews)
            assertEquals(1, viewModel.uiState.value.averageResponseSeconds)
            assertTrue(viewModel.uiState.value.conclusion.orEmpty().contains("数学"))
            assertEquals(listOf("AGAIN", "HARD", "GOOD", "EASY"), viewModel.uiState.value.distributions.map { it.label })
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 连续学习统计只关心“某天是否学过”，
     * 因此重复时间戳和乱序输入都不应抬高或打断 streak。
     */
    @Test
    fun init_streakIgnoresDuplicateTimestampsAndOrdering() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val now = 1_700_000_000_000L
            val repository = FakeStudyInsightsRepository().apply {
                reviewTimestamps = listOf(
                    now - TimeConstants.DAY_MILLIS,
                    now,
                    now,
                    now - 2L * TimeConstants.DAY_MILLIS,
                    now - TimeConstants.DAY_MILLIS
                )
            }

            val viewModel = AnalyticsViewModel(
                studyInsightsRepository = repository,
                timeProvider = FixedTimeProvider(nowEpochMillis = now),
                zoneId = ZoneId.of("UTC")
            )
            advanceUntilIdle()

            assertEquals(3, viewModel.uiState.value.streakDays)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 切换统计范围必须按对应时间窗口重新请求仓储，
     * 否则用户看到的标签和数据口径会错位。
     */
    @Test
    fun onRangeSelected_requestsRangeSpecificStartEpoch() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val now = 1_700_000_000_000L
            val repository = FakeStudyInsightsRepository()
            val viewModel = AnalyticsViewModel(
                studyInsightsRepository = repository,
                timeProvider = FixedTimeProvider(nowEpochMillis = now),
                zoneId = ZoneId.of("UTC")
            )
            advanceUntilIdle()

            viewModel.onRangeSelected(AnalyticsRange.LAST_30_DAYS)
            advanceUntilIdle()

            assertEquals(now - 30L * TimeConstants.DAY_MILLIS, repository.analyticsRequests.last())
            assertEquals(AnalyticsRange.LAST_30_DAYS, viewModel.uiState.value.selectedRange)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 仓储失败时必须让页面退出 loading 并回写错误信息，
     * 否则统计页会在异常时看起来像永久空白。
     */
    @Test
    fun refresh_failureShowsErrorMessage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeStudyInsightsRepository().apply {
                analyticsError = IllegalStateException("统计异常")
            }

            val viewModel = AnalyticsViewModel(
                studyInsightsRepository = repository,
                timeProvider = FixedTimeProvider(nowEpochMillis = 1_700_000_000_000L),
                zoneId = ZoneId.of("UTC")
            )
            advanceUntilIdle()

            assertEquals("统计异常", viewModel.uiState.value.errorMessage)
            assertEquals(false, viewModel.uiState.value.isLoading)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
