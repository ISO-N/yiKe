package com.kariscode.yike.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.component.YikeRatingPalette
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * Hero 区把连续学习天数和时间范围切换并列展示，是为了让用户先确认“近期状态”再看细分指标。
 */
@Composable
internal fun AnalyticsHeroSection(
    uiState: AnalyticsUiState,
    onRangeSelected: (AnalyticsRange) -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeHeroCard(
        eyebrow = "复习统计",
        title = if (uiState.streakDays > 0) "学习状态保持了 ${uiState.streakDays} 天连贯" else "先建立连续复习节奏",
        description = "把最影响节奏的 4 个指标放在首屏，是为了先判断当前学习是否健康。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AnalyticsRange.entries.forEach { range ->
                FilterChip(
                    selected = uiState.selectedRange == range,
                    onClick = { onRangeSelected(range) },
                    label = { Text(range.label) }
                )
            }
        }
    }
}

/**
 * 指标区使用统一统计卡片，是为了让评分量、遗忘率和平均耗时在同一视觉层级下比较。
 */
@Composable
internal fun AnalyticsMetricSection(
    uiState: AnalyticsUiState
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = "${uiState.streakDays} 天",
                label = "连续学习",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = "${uiState.averageResponseSeconds} 秒",
                label = "平均响应时间",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            YikeMetricCard(
                value = "${uiState.forgettingRatePercent}%",
                label = "遗忘率",
                modifier = Modifier.weight(1f)
            )
            YikeMetricCard(
                value = uiState.totalReviews.toString(),
                label = "完成题量",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 评分分布单独成卡，是为了让用户快速看到自己更常卡在“不会”还是“有点难”的哪一档。
 */
@Composable
internal fun AnalyticsDistributionSection(
    uiState: AnalyticsUiState
) {
    val spacing = LocalYikeSpacing.current
    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "评分分布", style = MaterialTheme.typography.titleLarge)
            YikeBadge(text = "基于 ${uiState.totalReviews} 次评分")
        }
        uiState.distributions.forEach { item ->
            AnalyticsDistributionRow(item = item, barColor = distributionColor(item.label))
        }
        if (uiState.totalReviews == 0) {
            Text(
                text = "当前时间范围内还没有复习记录，开始一轮复习后这里会出现真实分布。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 卡组拆分区只保留少量重点卡组，是为了让统计结论更聚焦，而不是把页面塞成完整排行榜。
 */
@Composable
internal fun AnalyticsDeckSection(
    deckBreakdowns: List<AnalyticsDeckUiModel>
) {
    val spacing = LocalYikeSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        deckBreakdowns.forEach { deck ->
            YikeSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(text = deck.deckName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "遗忘率 ${deck.forgettingRatePercent}% · 平均响应 ${deck.averageResponseSeconds} 秒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    YikeBadge(text = "${deck.reviewCount} 次评分")
                }
            }
        }
    }
}

/**
 * 结论区把统计转换成下一步动作，是为了让页面在看完数字后仍能把用户带回复习主路径。
 */
@Composable
internal fun AnalyticsConclusionSection(
    conclusion: String?,
    onOpenPreview: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val spacing = LocalYikeSpacing.current
    YikeStateBanner(
        title = "统计结论",
        description = conclusion ?: "当前还没有足够的复习记录，先完成今天的任务再回来观察趋势。"
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            YikePrimaryButton(
                text = "查看今日预览",
                onClick = onOpenPreview,
                modifier = Modifier.weight(1f)
            )
            YikeSecondaryButton(
                text = "检索困难题",
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 分布行把文字和比例条放在一行，是为了在手机上保留足够可读性又不占太多高度。
 */
@Composable
private fun AnalyticsDistributionRow(
    item: AnalyticsDistributionUiModel,
    barColor: Color
) {
    val spacing = LocalYikeSpacing.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = item.label, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.width(spacing.sm))
        Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.ratio.coerceIn(0f, 1f))
                        .height(10.dp)
                        .background(barColor, RoundedCornerShape(999.dp))
                )
            }
        }
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            text = "${(item.ratio * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * 评分颜色沿用复习按钮色阶，是为了让 AGAIN/HARD/GOOD/EASY 在统计页也保持一致记忆。
 */
private fun distributionColor(label: String): Color = when (label) {
    "AGAIN" -> YikeRatingPalette.criticalContainer
    "HARD" -> YikeRatingPalette.warningContainer
    "GOOD" -> YikeRatingPalette.successContainer
    else -> YikeRatingPalette.bestContainer
}
