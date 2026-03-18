package com.kariscode.yike.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.core.message.ErrorMessages
import com.kariscode.yike.domain.model.DeckSummary
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
                val summary = uiState.summary
                val dueQuestions = summary?.dueQuestionCount ?: 0
                val dueCards = summary?.dueCardCount ?: 0
                HomeHeroSection(
                    dueCards = dueCards,
                    dueQuestions = dueQuestions,
                    navigator = navigator
                )
                HomeRhythmSection(
                    dueQuestions = dueQuestions,
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
    dueCards: Int,
    dueQuestions: Int,
    navigator: YikeNavigator
) {
    val spacing = LocalYikeSpacing.current
    val hasDueItems = dueQuestions > 0
    val primaryActionText = if (hasDueItems) "开始复习" else "今日预览"
    val primaryAction: () -> Unit = if (hasDueItems) navigator::openReviewQueue else navigator::openTodayPreview
    YikeHeroCard(
        eyebrow = "Today Review",
        title = if (hasDueItems) "$dueQuestions 个问题待复习" else "今日暂无待复习",
        description = if (hasDueItems) {
            "从最早到期的卡片开始，优先把今天的复习闭环做完。"
        } else {
            "今天的复习已经清空，适合去补充新内容或整理已有卡组。"
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
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = primaryActionText,
                onClick = primaryAction,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "今日预览",
                onClick = navigator::openTodayPreview,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 节奏区只基于真实待复习数量给出当前状态说明，
 * 这样即使暂时没有更细粒度统计，也能保持原型要求的状态层级。
 */
@Composable
private fun HomeRhythmSection(
    dueQuestions: Int,
    totalRecentDecks: Int,
    navigator: YikeNavigator
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "今日节奏",
        description = if (dueQuestions <= 0) {
            "今天的待复习已经处理完，可以转去维护卡组和卡片内容。"
        } else {
            "当前还有 $dueQuestions 题待处理，建议先完成复习再继续编辑内容。"
        },
        trailing = {
            YikeBadge(
                text = if (totalRecentDecks > 0) "$totalRecentDecks 个最近卡组" else "暂无最近卡组"
            )
        }
    ) {
        YikeProgressBar(progress = if (dueQuestions <= 0) 1f else 0f)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                YikeSecondaryButton(
                    text = "复习统计",
                    onClick = navigator::openAnalytics,
                    modifier = Modifier.weight(1f)
                )
                YikeSecondaryButton(
                    text = "问题检索",
                    onClick = { navigator.openQuestionSearch() },
                    modifier = Modifier.weight(1f)
                )
            }
            YikeSecondaryButton(
                text = "浏览卡组",
                onClick = navigator::openDeckList,
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
            eyebrow = "Recent Decks",
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
    val dueQuestions = uiState.summary?.dueQuestionCount ?: 0
    return if (dueQuestions > 0) {
        "今天继续巩固 $dueQuestions 个问题"
    } else {
        "今天先整理你的学习内容"
    }
}

/**
 * 首页副标题在加载、错误和成功状态之间切换，是为了让页面语境始终和真实状态一致。
 */
private fun buildHomeSubtitle(uiState: HomeUiState): String = when {
    uiState.isLoading -> "正在汇总待复习内容和最近卡组。"
    uiState.errorMessage != null -> "暂时没能拿到完整数据，但你仍可以继续进入内容管理。"
    (uiState.summary?.dueQuestionCount ?: 0) > 0 -> "优先从最早到期的卡片开始，减少今天的拖延成本。"
    else -> "复习入口已经清空，现在最值得做的是补齐新的卡组与卡片。"
}
