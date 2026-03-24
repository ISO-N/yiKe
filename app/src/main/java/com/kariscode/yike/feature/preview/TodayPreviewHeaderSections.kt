package com.kariscode.yike.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.format.formatPreviewDay
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 首屏英雄区把任务总量和开始入口放在一起，是为了让预览页继续服务“马上开始”而不是偏离主路径。
 */
@Composable
internal fun TodayPreviewHeroSection(
    uiState: TodayPreviewUiState,
    onStartReview: () -> Unit,
    onOpenAnalytics: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeHeroCard(
        eyebrow = "今日预览",
        title = "${uiState.totalDueQuestions} 个问题，预计 ${uiState.estimatedMinutes} 分钟",
        description = "估时基于最近 7 天的平均响应时间，先给自己一个可接受的心理预期。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = uiState.totalDecks.toString(),
                label = "涉及卡组",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = uiState.totalDueCards.toString(),
                label = "待复习卡片",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = "直接开始",
                onClick = onStartReview,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "查看统计",
                onClick = onOpenAnalytics,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 摘要区只保留最影响进入决策的提示，是为了让用户快速判断今天是否需要先筛困难题。
 */
@Composable
internal fun TodayPreviewSummarySection(
    uiState: TodayPreviewUiState,
    onOpenSearch: () -> Unit
) {
    YikeStateBanner(
        title = "任务摘要",
        description = buildSummaryDescription(uiState),
        trailing = {
            YikeBadge(text = "平均 ${uiState.averageSecondsPerQuestion} 秒/题")
        }
    ) {
        YikeSecondaryButton(
            text = "先筛低熟练度题",
            onClick = onOpenSearch,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * 摘要文案用最早到期和低熟练度数量组织，是为了把“什么时候开始”和“先做哪些题”一次说清楚。
 */
private fun buildSummaryDescription(uiState: TodayPreviewUiState): String {
    val dueText = uiState.earliestDueAt?.let(::formatPreviewDay) ?: "今天"
    return "最早到期日期 $dueText，当前有 ${uiState.lowMasteryCount} 题属于低熟练度，适合先处理最薄弱的内容。"
}
