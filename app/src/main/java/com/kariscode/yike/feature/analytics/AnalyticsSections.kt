package com.kariscode.yike.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kariscode.yike.domain.model.StreakAchievement
import com.kariscode.yike.domain.model.StreakAchievementUnlock
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeHeroCard
import com.kariscode.yike.ui.component.YikeMetricCard
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeSurfaceCard
import com.kariscode.yike.ui.component.YikeRatingPalette
import com.kariscode.yike.ui.theme.YikeThemeTokens
import com.kariscode.yike.ui.theme.YikeSemanticColors
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
 * 热力图采用 52 周 x 7 天网格展示活跃度，是为了让用户从“是否连续”之外再看到“是否稳定”，
 * 并在不看具体数字的情况下快速识别低谷与高峰。
 */
@Composable
internal fun AnalyticsHeatmapSection(
    heatmapCells: List<AnalyticsHeatmapCellUiModel>
) {
    val spacing = LocalYikeSpacing.current
    val semanticColors = YikeThemeTokens.semanticColors
    val weeks = heatmapCells.chunked(7)
    val cellSize = 10.dp
    val cellGap = 3.dp
    val heatmapScrollState = rememberScrollState()
    var hasAlignedToLatest by remember(weeks.size) { mutableStateOf(false) }

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "近 52 周活跃度", style = MaterialTheme.typography.titleLarge)
            YikeBadge(text = "52 周")
        }

        if (heatmapCells.isEmpty()) {
            Text(
                text = "当前还没有复习记录，开始复习后这里会出现按天聚合的热力图。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@YikeSurfaceCard
        }

        LaunchedEffect(weeks.size, heatmapScrollState.maxValue) {
            if (!hasAlignedToLatest && heatmapScrollState.maxValue > 0) {
                heatmapScrollState.scrollTo(heatmapScrollState.maxValue)
                hasAlignedToLatest = true
            }
        }

        Row(
            modifier = Modifier
                .horizontalScroll(heatmapScrollState)
                .padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(cellGap)
        ) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                    week.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color = heatmapColor(cell.level, semanticColors))
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "少",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color = heatmapColor(level, semanticColors))
                )
            }
            Text(
                text = "多",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 颜色映射沿用 issue 建议的 5 档区间，是为了让用户在不同设备和主题下都能形成稳定直觉。
 */
@Composable
private fun heatmapColor(level: Int, semanticColors: YikeSemanticColors): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> semanticColors.successContainer.copy(alpha = 0.3f)
    2 -> semanticColors.successContainer.copy(alpha = 0.6f)
    3 -> semanticColors.successContainer
    else -> MaterialTheme.colorScheme.primary
}

/**
 * 成就次级区承接首页以外的成就展示，是为了让首页 Hero 继续保持“最高徽章 + streak”的聚焦叙事，
 * 同时仍给用户一个可回顾全部进度的稳定入口。
 */
@Composable
internal fun AnalyticsAchievementSection(
    streakAchievementUnlocks: List<StreakAchievementUnlock>
) {
    val achievements = streakAchievementUnlocks
        .mapNotNull { unlock -> StreakAchievement.fromId(unlock.achievementId) }
        .distinctBy { it.id }
        .sortedByDescending { it.requiredDays }

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "成就进度", style = MaterialTheme.typography.titleLarge)
            YikeBadge(text = "${achievements.size}/${StreakAchievement.entries.size}")
        }

        if (achievements.isEmpty()) {
            Text(
                text = "连续学习 3 天即可解锁第一枚徽章，进度会跟随备份与同步一起保存。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(LocalYikeSpacing.current.sm)) {
                achievements.forEach { achievement ->
                    YikeBadge(text = achievement.title)
                }
            }
            Text(
                text = "首页只展示最高徽章，其余解锁徽章统一收纳在这里，避免主视觉变成信息墙。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        AnalyticsRatingDonutChart(
            distributions = uiState.distributions,
            totalReviews = uiState.totalReviews
        )

        Column(
            modifier = Modifier.padding(top = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            uiState.distributions.forEach { item ->
                AnalyticsDistributionLegendRow(
                    item = item,
                    color = distributionColor(item.label)
                )
            }
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
 * 遗忘曲线用 stage 作为横轴、AGAIN 比例作为纵轴，是为了把“难点集中在哪个阶段”可视化，
 * 从而帮助用户判断是否需要拆卡、降难度或放慢节奏。
 */
@Composable
internal fun AnalyticsForgettingCurveSection(
    items: List<AnalyticsStageAgainUiModel>
) {
    val spacing = LocalYikeSpacing.current
    val lineColor = distributionColor("AGAIN")
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "遗忘曲线", style = MaterialTheme.typography.titleLarge)
            YikeBadge(text = "按 stage 统计 AGAIN 比例")
        }

        if (items.all { it.reviewCount <= 0 }) {
            Text(
                modifier = Modifier.padding(top = spacing.sm),
                text = "当前时间范围内复习记录不足，先完成一轮复习后这里会出现按阶段聚合的 AGAIN 曲线。",
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor
            )
            return@YikeSurfaceCard
        }

        val sortedItems = items.sortedBy(AnalyticsStageAgainUiModel::stageIndex)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            AnalyticsChartYAxis(
                labels = listOf("100%", "50%", "0%"),
                modifier = Modifier
                    .width(40.dp)
                    .height(168.dp)
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                ) {
                    val paddingTop = 12.dp.toPx()
                    val paddingRight = 10.dp.toPx()
                    val paddingBottom = 14.dp.toPx()
                    val plotWidth = size.width - paddingRight
                    val plotHeight = size.height - paddingTop - paddingBottom
                    if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

                    val originX = 0f
                    val originY = paddingTop + plotHeight

                    drawLine(
                        color = axisColor,
                        start = Offset(originX, paddingTop),
                        end = Offset(originX, originY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = axisColor,
                        start = Offset(originX, originY),
                        end = Offset(plotWidth, originY),
                        strokeWidth = 2.dp.toPx()
                    )

                    listOf(0f, 0.5f, 1f).forEach { ratio ->
                        val y = paddingTop + plotHeight * (1f - ratio)
                        drawLine(
                            color = gridColor,
                            start = Offset(originX, y),
                            end = Offset(plotWidth, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val pointCount = sortedItems.size.coerceAtLeast(1)
                    val stepX = if (pointCount <= 1) 0f else plotWidth / (pointCount - 1).toFloat()
                    val points = sortedItems.mapIndexed { index, item ->
                        val x = if (pointCount <= 1) plotWidth / 2f else index * stepX
                        val y = paddingTop + plotHeight * (1f - item.againRatio.coerceIn(0f, 1f))
                        Offset(x, y)
                    }

                    points.forEach { point ->
                        drawLine(
                            color = axisColor,
                            start = Offset(point.x, originY),
                            end = Offset(point.x, originY + 4.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    for (index in 0 until points.lastIndex) {
                        drawLine(
                            color = lineColor,
                            start = points[index],
                            end = points[index + 1],
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    points.forEach { point ->
                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = point
                        )
                    }
                }

                AnalyticsChartBottomLabels(
                    labels = sortedItems.map { item -> "S${item.stageIndex}" }
                )
            }
        }

        Text(
            modifier = Modifier.padding(top = spacing.sm),
            text = "提示：曲线越高代表该阶段更容易点 AGAIN，可以考虑拆分卡片或降低单次信息密度。",
            style = MaterialTheme.typography.bodySmall,
            color = labelColor
        )
    }
}

/**
 * 未来到期预测用 7 天柱状图展示，是为了把未来压力提前暴露给用户，
 * 避免某一天突然集中爆发导致复习中断。
 */
@Composable
internal fun AnalyticsDueForecastSection(
    items: List<AnalyticsDueForecastUiModel>
) {
    val spacing = LocalYikeSpacing.current
    val barColor = YikeThemeTokens.semanticColors.successContainer
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    YikeSurfaceCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "未来 7 天到期预测", style = MaterialTheme.typography.titleLarge)
            YikeBadge(text = "按 dueAt 统计")
        }

        if (items.isEmpty() || items.all { it.dueCount <= 0 }) {
            Text(
                modifier = Modifier.padding(top = spacing.sm),
                text = "未来 7 天内没有预计到期的问题，保持节奏就好。",
                style = MaterialTheme.typography.bodyMedium,
                color = labelColor
            )
            return@YikeSurfaceCard
        }

        val max = items.maxOfOrNull(AnalyticsDueForecastUiModel::dueCount)?.coerceAtLeast(1) ?: 1
        val axisColor = MaterialTheme.colorScheme.outlineVariant
        val gridColor = MaterialTheme.colorScheme.surfaceVariant

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = spacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            AnalyticsChartYAxis(
                labels = listOf(max.toString(), (max / 2).toString(), "0"),
                modifier = Modifier
                    .width(40.dp)
                    .height(148.dp)
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                ) {
                    val paddingTop = 8.dp.toPx()
                    val paddingBottom = 16.dp.toPx()
                    val plotWidth = size.width
                    val plotHeight = size.height - paddingTop - paddingBottom
                    if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

                    val originX = 0f
                    val originY = paddingTop + plotHeight
                    val middleY = paddingTop + plotHeight * 0.5f

                    drawLine(
                        color = axisColor,
                        start = Offset(originX, paddingTop),
                        end = Offset(originX, originY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(originX, middleY),
                        end = Offset(plotWidth, middleY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = axisColor,
                        start = Offset(originX, originY),
                        end = Offset(plotWidth, originY),
                        strokeWidth = 2.dp.toPx()
                    )

                    val barCount = items.size.coerceAtLeast(1)
                    val slotWidth = plotWidth / barCount.toFloat()
                    val gap = 6.dp.toPx().coerceAtMost(slotWidth * 0.28f)
                    val barWidth = (slotWidth - gap).coerceAtLeast(8.dp.toPx())

                    items.forEachIndexed { index, item ->
                        val centerX = slotWidth * index + slotWidth / 2f
                        val x = centerX - barWidth / 2f
                        val heightRatio = item.dueCount.toFloat() / max.toFloat()
                        val barHeight = plotHeight * heightRatio.coerceIn(0f, 1f)
                        val top = paddingTop + (plotHeight - barHeight)
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, top),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                        drawLine(
                            color = axisColor,
                            start = Offset(centerX, originY),
                            end = Offset(centerX, originY + 4.dp.toPx()),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                AnalyticsChartBottomLabels(labels = items.map(AnalyticsDueForecastUiModel::label))
            }
        }

        Text(
            modifier = Modifier.padding(top = spacing.sm),
            text = "如果某天柱子明显更高，可以提前安排一轮复习或拆分卡片，避免压力堆积。",
            style = MaterialTheme.typography.bodySmall,
            color = labelColor
        )
    }
}

/**
 * 图表纵轴标签独立到布局层，是为了避免把文字也塞进 Canvas 后因为内边距不足被裁掉，
 * 同时让不同图表共享同一套稳定的左侧读数区域。
 */
@Composable
private fun AnalyticsChartYAxis(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 横轴标签统一用等宽槽位排布，是为了让日期和 stage 标签与柱子/折线点保持一一对齐，
 * 避免数据一多就出现“标签挤在一起但图形不对应”的阅读落差。
 */
@Composable
private fun AnalyticsChartBottomLabels(
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 评分分布使用 Canvas 绘制环形图，是为了让四档评分在同一视觉尺度下对比，
 * 并满足 issue 对“环形图而不是比例条”的验收要求。
 */
@Composable
private fun AnalyticsRatingDonutChart(
    distributions: List<AnalyticsDistributionUiModel>,
    totalReviews: Int,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    val strokeWidth = 18.dp
    val chartSize = 156.dp
    val total = totalReviews.coerceAtLeast(1)
    val backgroundRingColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.size(chartSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Butt)
                val inset = stroke.width / 2f
                val arcSize = androidx.compose.ui.geometry.Size(
                    width = size.width - stroke.width,
                    height = size.height - stroke.width
                )
                val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

                // 背景环让“无数据”或小比例时仍然有稳定结构，不会显得空白或跳变。
                drawArc(
                    color = backgroundRingColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )

                var startAngle = -90f
                distributions
                    .filter { item -> item.count > 0 }
                    .forEach { item ->
                        val sweepAngle = item.count.toFloat() / total.toFloat() * 360f
                        drawArc(
                            color = distributionColor(item.label),
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                        startAngle += sweepAngle
                    }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = totalReviews.toString(),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "次评分",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Legend 行提供颜色与数字对照，是为了让环形图在不标注角度的情况下仍能被精确读出。
 */
@Composable
private fun AnalyticsDistributionLegendRow(
    item: AnalyticsDistributionUiModel,
    color: Color,
    modifier: Modifier = Modifier
) {
    val spacing = LocalYikeSpacing.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = item.label, style = MaterialTheme.typography.labelLarge)
        }
        Text(
            text = "${item.count} · ${(item.ratio * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
 * 评分颜色沿用复习按钮色阶，是为了让 AGAIN/HARD/GOOD/EASY 在统计页也保持一致记忆。
 */
private fun distributionColor(label: String): Color = when (label) {
    "AGAIN" -> YikeRatingPalette.criticalContainer
    "HARD" -> YikeRatingPalette.warningContainer
    "GOOD" -> YikeRatingPalette.successContainer
    else -> YikeRatingPalette.bestContainer
}
