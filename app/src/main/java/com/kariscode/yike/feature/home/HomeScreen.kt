package com.kariscode.yike.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeHeaderBlock
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryScaffold
import com.kariscode.yike.ui.component.YikeProgressBar
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 首页作为一级入口，需要同时承接复习主路径和内容管理入口，
 * 因此这里直接复用统一导航壳，而不是继续使用独立 Scaffold。
 */
@Composable
fun HomeScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HomeViewModel>(
        factory = HomeViewModel.factory(
            questionRepository = container.questionRepository,
            deckRepository = container.deckRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikePrimaryScaffold(
        currentDestination = YikePrimaryDestination.HOME,
        title = buildHomeTitle(uiState),
        subtitle = buildHomeSubtitle(uiState)
    ) { padding ->
        HomeContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            navigator = navigator,
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 首页主体抽成独立展示层，是为了让 UI 测试能直接覆盖加载、空、错和成功态，而不依赖真实仓储。
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    navigator: YikeNavigator,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalYikeSpacing.current
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        when {
            uiState.isLoading -> {
                YikeStateBanner(
                    title = "正在整理今天的复习内容",
                    description = "稍等一下，我们会把待复习概览和最近卡组一起准备好。"
                )
            }

            uiState.errorMessage != null -> {
                YikeStateBanner(
                    title = ErrorMessages.HOME_LOAD_FAILED,
                    description = uiState.errorMessage ?: "请稍后重试，或先进入内容管理继续整理卡组。"
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        YikePrimaryButton(
                            text = "重试",
                            onClick = onRetry,
                            modifier = Modifier.weight(1f)
                        )
                        YikeSecondaryButton(
                            text = "进入卡组",
                            onClick = navigator::openDeckList,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            else -> {
                HomeHeroSection(
                    contentMode = uiState.contentMode,
                    dueCards = uiState.summary.dueCardCount,
                    dueQuestions = uiState.summary.dueQuestionCount,
                    navigator = navigator
                )
                HomeRhythmSection(
                    contentMode = uiState.contentMode,
                    dueQuestions = uiState.summary.dueQuestionCount,
                    totalRecentDecks = uiState.recentDecks.size,
                    navigator = navigator
                )
                RecentDeckSection(
                    recentDecks = uiState.recentDecks,
                    navigator = navigator
                )
            }
        }

        YikeSecondaryButton(
            text = "进入设置",
            onClick = navigator::openSettings,
            modifier = Modifier.fillMaxWidth()
        )
        HomeDebugEntry(navigator = navigator)
    }
}

/**
 * Hero 区把待复习数量和主入口组合展示，是为了让首页在第一屏就明确“先复习还是先维护内容”。
 */
@Composable
private fun HomeHeroSection(
    contentMode: HomeContentMode,
    dueCards: Int,
    dueQuestions: Int,
    navigator: YikeNavigator
) {
    val spacing = LocalYikeSpacing.current
    YikeHeroCard(
        eyebrow = "今日复习",
        title = when (contentMode) {
            HomeContentMode.REVIEW_READY -> "$dueQuestions 个问题待复习"
            HomeContentMode.REVIEW_CLEARED -> "今天的复习已经清空"
            HomeContentMode.CONTENT_EMPTY -> "先创建第一组学习内容"
        },
        description = when (contentMode) {
            HomeContentMode.REVIEW_READY -> "从最早到期的卡片开始，优先把今天的复习闭环做完。"
            HomeContentMode.REVIEW_CLEARED -> "今天已经没有到期题目，可以回看今日预览，或继续补充卡组内容。"
            HomeContentMode.CONTENT_EMPTY -> "目前还没有可进入复习流的内容，先创建卡组和卡片再开始积累。"
        }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = dueCards.toString(),
                label = "待复习卡片",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = dueQuestions.toString(),
                label = "待复习问题",
                modifier = Modifier.weight(1f)
            )
        }
        when (contentMode) {
            HomeContentMode.REVIEW_READY -> {
                YikePrimaryButton(
                    text = "开始今日复习",
                    onClick = navigator::openReviewQueue,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HomeContentMode.REVIEW_CLEARED -> {
                YikePrimaryButton(
                    text = "开始自由练习",
                    onClick = { navigator.openPracticeSetup(PracticeSessionArgs()) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HomeContentMode.CONTENT_EMPTY -> {
                YikePrimaryButton(
                    text = "创建内容",
                    onClick = navigator::openDeckList,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 节奏区不再只看题量，而是跟随首页模式切换语气，
 * 这样“今天完成了”和“还没开始积累内容”会呈现出完全不同的下一步引导。
 */
@Composable
private fun HomeRhythmSection(
    contentMode: HomeContentMode,
    dueQuestions: Int,
    totalRecentDecks: Int,
    navigator: YikeNavigator
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "今日节奏",
        description = when (contentMode) {
            HomeContentMode.REVIEW_READY -> "当前还有 $dueQuestions 题待处理，建议先完成复习再继续编辑内容。"
            HomeContentMode.REVIEW_CLEARED -> "今天的待复习已经处理完，现在适合复盘预览或继续整理内容。"
            HomeContentMode.CONTENT_EMPTY -> "还没有形成可复习内容，先创建卡组和卡片，之后首页才会出现复习入口。"
        },
        trailing = {
            YikeBadge(
                text = when {
                    totalRecentDecks > 0 -> "$totalRecentDecks 个最近卡组"
                    contentMode == HomeContentMode.CONTENT_EMPTY -> "等待第一个卡组"
                    else -> "暂无最近卡组"
                }
            )
        }
    ) {
        YikeProgressBar(
            progress = when (contentMode) {
                HomeContentMode.REVIEW_READY -> 0f
                HomeContentMode.REVIEW_CLEARED -> 1f
                HomeContentMode.CONTENT_EMPTY -> 0f
            },
            description = when (contentMode) {
                HomeContentMode.REVIEW_READY -> "今日节奏进度 0%，还有 $dueQuestions 题待处理"
                HomeContentMode.REVIEW_CLEARED -> "今日节奏进度 100%，今天的待复习已经处理完成"
                HomeContentMode.CONTENT_EMPTY -> "今日节奏进度 0%，当前还没有可复习内容"
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (contentMode == HomeContentMode.REVIEW_READY) {
                    YikeSecondaryButton(
                        text = "自由练习",
                        onClick = { navigator.openPracticeSetup(PracticeSessionArgs()) },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    YikeSecondaryButton(
                        text = "今日预览",
                        onClick = navigator::openTodayPreview,
                        modifier = Modifier.weight(1f)
                    )
                }
                YikeSecondaryButton(
                    text = "问题检索",
                    onClick = { navigator.openQuestionSearch() },
                    modifier = Modifier.weight(1f)
                )
            }
            YikeSecondaryButton(
                text = "复习统计",
                onClick = navigator::openAnalytics,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 最近卡组区使用真实聚合数据，是为了让首页不只是“告诉用户有多少题”，还能给出继续入口。
 */
@Composable
private fun RecentDeckSection(
    recentDecks: List<DeckSummary>,
    navigator: YikeNavigator
) {
    val spacing = LocalYikeSpacing.current
    if (recentDecks.isEmpty()) {
        YikeStateBanner(
            title = "最近卡组",
            description = "还没有可继续维护的卡组，先创建第一个卡组开始积累内容。"
        ) {
            YikePrimaryButton(
                text = "创建内容",
                onClick = navigator::openDeckList,
                modifier = Modifier.fillMaxWidth()
            )
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        YikeHeaderBlock(
            eyebrow = "最近卡组",
            title = "最近卡组",
            subtitle = "优先进入最近在维护或今天有到期内容的卡组。"
        )
        recentDecks.forEach { item ->
            YikeListItemCard(
                title = item.deck.name,
                summary = "${item.cardCount} 张卡片 · ${item.questionCount} 个问题",
                supporting = if (item.dueQuestionCount > 0) {
                    "今天还有 ${item.dueQuestionCount} 题到期，适合继续巩固。"
                } else {
                    "今天暂无到期题目，但可以继续补充和整理内容。"
                },
                badge = {
                    YikeBadge(
                        text = if (item.dueQuestionCount > 0) "${item.dueQuestionCount} 题到期" else "已清空"
                    )
                }
            ) {
                YikeSecondaryButton(
                    text = "查看全部",
                    onClick = navigator::openDeckList,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 调试入口只在 Debug 构建可见，是为了让正常用户不被打扰，同时让开发时能快速进入诊断页面。
 */
@Composable
private fun HomeDebugEntry(
    navigator: YikeNavigator
) {
    if (!BuildConfig.DEBUG) {
        return
    }
    YikeStateBanner(
        title = "调试工具",
        description = "开发调试入口仅在 Debug 构建开放，用于快速验证数据与状态。"
    ) {
        YikeSecondaryButton(
            text = "打开调试页",
            onClick = navigator::openDebug,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 首页主标题使用真实待复习数量驱动，是为了让一级导航壳也能传达当前最重要的信息。
 */
private fun buildHomeTitle(uiState: HomeUiState): String {
    val dueQuestions = uiState.summary.dueQuestionCount
    return when (uiState.contentMode) {
        HomeContentMode.REVIEW_READY -> "今天继续巩固 $dueQuestions 个问题"
        HomeContentMode.REVIEW_CLEARED -> "今天的复习已经完成"
        HomeContentMode.CONTENT_EMPTY -> "今天先建立你的第一组内容"
    }
}

/**
 * 首页副标题在加载、错误和成功状态之间切换，是为了让页面语境始终和真实状态一致。
 */
private fun buildHomeSubtitle(uiState: HomeUiState): String = when {
    uiState.isLoading -> "正在汇总待复习内容和最近卡组。"
    uiState.errorMessage != null -> "暂时没能拿到完整数据，但你仍可以继续进入内容管理。"
    uiState.contentMode == HomeContentMode.REVIEW_READY -> "优先从最早到期的卡片开始，减少今天的拖延成本。"
    uiState.contentMode == HomeContentMode.REVIEW_CLEARED -> "待复习入口已经清空，现在适合补充新内容或回看今日预览。"
    else -> "先创建卡组和卡片，首页才会开始积累真实复习节奏。"
}
