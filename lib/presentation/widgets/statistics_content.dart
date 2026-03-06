/// 文件用途：统计详情内容（从 StatisticsPage 抽离，供 Sheet 与页面复用）。
/// 作者：Codex
/// 创建日期：2026-03-01
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/responsive_utils.dart';
import '../providers/statistics_insights_provider.dart';
import '../providers/statistics_provider.dart';
import '../../domain/entities/statistics_insights.dart';
import '../providers/ui_preferences_provider.dart';
import 'error_card.dart';
import 'glass_card.dart';
import 'goal_progress_card.dart';
import 'semantic_panels.dart';
import 'skeleton_loader.dart';
import 'statistics_heatmap.dart';
import 'statistics_trend_chart.dart';
import 'yike_refresh_indicator.dart';

/// 统计详情内容（可复用）。
///
/// 说明：
/// - 该组件只负责展示统计内容，不包含 AppBar。
/// - 上层可通过 [onRefresh] 提供下拉刷新能力。
class StatisticsContent extends StatelessWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [state] 统计状态。
  /// - [onRefresh] 下拉刷新回调；为空则不启用 RefreshIndicator。
  /// - [padding] 列表内边距。
  /// 返回值：Widget。
  /// 异常：无。
  const StatisticsContent({
    super.key,
    required this.state,
    this.onRefresh,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final StatisticsState state;
  final Future<void> Function()? onRefresh;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final isDesktop = ResponsiveUtils.isDesktop(context);
    final list = ListView(
      padding: padding,
      children: [
        _StatisticsHero(state: state),
        const SizedBox(height: AppSpacing.lg),
        if (isDesktop)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Expanded(child: GoalProgressCard()),
              const SizedBox(width: AppSpacing.lg),
              Expanded(
                child: Column(
                  children: [
                    _StreakCard(days: state.consecutiveCompletedDays),
                    const SizedBox(height: AppSpacing.lg),
                    Row(
                      children: [
                        Expanded(
                          child: _CompletionCard(
                            title: '本周',
                            completed: state.weekCompleted,
                            total: state.weekTotal,
                            rate: state.weekCompletionRate,
                          ),
                        ),
                        const SizedBox(width: AppSpacing.lg),
                        Expanded(
                          child: _CompletionCard(
                            title: '本月',
                            completed: state.monthCompleted,
                            total: state.monthTotal,
                            rate: state.monthCompletionRate,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          )
        else ...[
          // 统计增强（P0）：目标进度（展示在统计详情顶部）。
          const GoalProgressCard(),
          const SizedBox(height: AppSpacing.lg),
          _StreakCard(days: state.consecutiveCompletedDays),
          const SizedBox(height: AppSpacing.lg),
          Row(
            children: [
              Expanded(
                child: _CompletionCard(
                  title: '本周',
                  completed: state.weekCompleted,
                  total: state.weekTotal,
                  rate: state.weekCompletionRate,
                ),
              ),
              const SizedBox(width: AppSpacing.lg),
              Expanded(
                child: _CompletionCard(
                  title: '本月',
                  completed: state.monthCompleted,
                  total: state.monthTotal,
                  rate: state.monthCompletionRate,
                ),
              ),
            ],
          ),
        ],
        const SizedBox(height: AppSpacing.lg),
        _InsightsSection(isDesktop: isDesktop),
        const SizedBox(height: AppSpacing.lg),
        if (isDesktop)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const Expanded(child: StatisticsHeatmap()),
              const SizedBox(width: AppSpacing.lg),
              Expanded(
                child: _TagPieChart(distribution: state.tagDistribution),
              ),
            ],
          )
        else ...[
          // 统计增强（P0）：年度热力图。
          StatisticsHeatmap(),
          const SizedBox(height: AppSpacing.lg),
          _TagPieChart(distribution: state.tagDistribution),
        ],
        if (state.errorMessage != null) ...[
          const SizedBox(height: AppSpacing.lg),
          ErrorCard(message: state.errorMessage!),
        ],
        if (state.isLoading) ...[
          const SizedBox(height: 24),
          const Center(child: CircularProgressIndicator()),
        ],
        const SizedBox(height: 96),
      ],
    );

    if (onRefresh == null) return list;
    return Consumer(
      builder: (context, ref, _) {
        final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);
        return YiKeRefreshIndicator(
          hapticEnabledByUser: hapticEnabled,
          onRefresh: onRefresh!,
          child: list,
        );
      },
    );
  }
}

class _StatisticsHero extends StatelessWidget {
  const _StatisticsHero({required this.state});

  final StatisticsState state;

  @override
  Widget build(BuildContext context) {
    final weekPercent =
        (state.weekCompletionRate.isNaN ? 0 : state.weekCompletionRate * 100)
            .clamp(0, 100);
    final monthPercent =
        (state.monthCompletionRate.isNaN ? 0 : state.monthCompletionRate * 100)
            .clamp(0, 100);

    return HeroCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('学习统计', style: AppTypography.h2(context)),
          const SizedBox(height: AppSpacing.sm),
          Text(
            '本周完成率 ${weekPercent.toStringAsFixed(0)}%',
            style: AppTypography.display(context),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            state.consecutiveCompletedDays <= 0
                ? '先完成今天的复习，开始形成稳定节奏。'
                : '已连续打卡 ${state.consecutiveCompletedDays} 天，本月完成率 ${monthPercent.toStringAsFixed(0)}%。',
            style: AppTypography.bodySecondary(context),
          ),
          const SizedBox(height: AppSpacing.lg),
          SummaryStrip(
            child: Row(
              children: [
                Expanded(
                  child: _HeroSummaryMetric(
                    label: '本周完成',
                    value: '${state.weekCompleted}/${state.weekTotal}',
                  ),
                ),
                Expanded(
                  child: _HeroSummaryMetric(
                    label: '本月完成',
                    value: '${state.monthCompleted}/${state.monthTotal}',
                  ),
                ),
                Expanded(
                  child: _HeroSummaryMetric(
                    label: '连续打卡',
                    value: '${state.consecutiveCompletedDays} 天',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeroSummaryMetric extends StatelessWidget {
  const _HeroSummaryMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value, style: AppTypography.title(context)),
        const SizedBox(height: 2),
        Text(label, style: AppTypography.meta(context)),
      ],
    );
  }
}

class _InsightsSection extends ConsumerWidget {
  const _InsightsSection({required this.isDesktop});

  final bool isDesktop;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(statisticsInsightsProvider);
    final skeletonStrategy = ref.watch(skeletonStrategyProvider);
    return async.when(
      loading: () => SkeletonLoader(
        isLoading: true,
        strategy: skeletonStrategy,
        skeleton: const SkeletonShimmer(child: _StatisticsInsightsSkeleton()),
        child: skeletonStrategy == 'off'
            ? const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Center(child: CircularProgressIndicator()),
              )
            : const SizedBox.shrink(),
      ),
      error: (e, _) => ErrorCard(message: '趋势数据加载失败：$e'),
      data: (insights) => SectionCard(
        title: '最近趋势',
        subtitle: isDesktop ? '先看趋势，再对比本周与上周的变化。' : '趋势图与对比分析放在同一模块，方便快速理解变化。',
        child: Column(
          children: [
            StatisticsTrendChart(insights: insights),
            const SizedBox(height: AppSpacing.lg),
            _WeekCompareCard(compare: insights.weekCompare),
          ],
        ),
      ),
    );
  }
}

class _StreakCard extends StatelessWidget {
  const _StreakCard({required this.days});

  final int days;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: [
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: AppColors.cta.withAlpha(24),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(color: AppColors.cta.withAlpha(80)),
              ),
              child: const Icon(
                Icons.local_fire_department,
                color: AppColors.cta,
              ),
            ),
            const SizedBox(width: AppSpacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('连续打卡', style: AppTypography.h2(context)),
                  const SizedBox(height: 4),
                  Text(
                    days == 0 ? '还没有形成连续打卡' : '已连续打卡 $days 天',
                    style: AppTypography.bodySecondary(context),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _CompletionCard extends StatelessWidget {
  const _CompletionCard({
    required this.title,
    required this.completed,
    required this.total,
    required this.rate,
  });

  final String title;
  final int completed;
  final int total;
  final double rate;

  @override
  Widget build(BuildContext context) {
    final percent = (rate.isNaN ? 0 : rate * 100).clamp(0, 100);
    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: AppTypography.h2(context)),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '$completed / $total',
              style: AppTypography.bodySecondary(context),
            ),
            const SizedBox(height: AppSpacing.sm),
            ClipRRect(
              borderRadius: BorderRadius.circular(999),
              child: LinearProgressIndicator(
                value: total == 0 ? 0 : completed / total,
                minHeight: 10,
                backgroundColor: Theme.of(context).dividerColor.withAlpha(60),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              '${percent.toStringAsFixed(0)}%',
              style: AppTypography.bodySecondary(context),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatisticsInsightsSkeleton extends StatelessWidget {
  const _StatisticsInsightsSkeleton();

  @override
  Widget build(BuildContext context) {
    return Column(
      children: const [
        GlassCard(
          child: Padding(
            padding: EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SkeletonBox(width: 120, height: 18),
                SizedBox(height: AppSpacing.md),
                SkeletonBox(width: double.infinity, height: 220, radius: 14),
              ],
            ),
          ),
        ),
        SizedBox(height: AppSpacing.lg),
        GlassCard(
          child: Padding(
            padding: EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SkeletonBox(width: 120, height: 18),
                SizedBox(height: AppSpacing.md),
                SkeletonBox(width: 240, height: 12),
                SizedBox(height: 10),
                SkeletonBox(width: 180, height: 22),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _WeekCompareCard extends StatelessWidget {
  const _WeekCompareCard({required this.compare});

  final WeekCompareEntity compare;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final upColor = AppColors.success;
    final downColor = AppColors.error;

    final hasCompare = compare.hasCompareData;
    final diff = compare.diffCompleted;
    final diffRate = compare.diffRatePercent;

    final isUp = diff > 0 || (diff == 0 && diffRate > 0);
    final isDown = diff < 0 || (diff == 0 && diffRate < 0);
    final color = isUp
        ? upColor
        : (isDown ? downColor : AppColors.textSecondary);

    final arrow = isUp
        ? Icons.trending_up
        : (isDown ? Icons.trending_down : Icons.trending_flat);

    final subtitle = compare.isInProgress
        ? '进行中 ${compare.daysInPeriod}/7 天（对比上周同期）'
        : '已结束（对比上周）';

    String headline;
    if (!hasCompare) {
      headline = '无对比数据';
    } else {
      final sign = diff > 0 ? '+' : '';
      headline =
          '$sign$diff  ·  ${diffRate >= 0 ? '+' : ''}${diffRate.toStringAsFixed(0)}%';
    }

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text('本周对比', style: AppTypography.h2(context))),
                Icon(arrow, color: color),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(subtitle, style: AppTypography.bodySecondary(context)),
            const SizedBox(height: AppSpacing.md),
            Text(
              headline,
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: hasCompare
                    ? color
                    : (isDark
                          ? AppColors.darkTextSecondary
                          : AppColors.textSecondary),
              ),
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '本周：${compare.thisCompleted}/${compare.thisTotal}（${compare.thisRatePercent.toStringAsFixed(0)}%）\n'
              '上周：${compare.lastCompleted}/${compare.lastTotal}（${compare.lastRatePercent.toStringAsFixed(0)}%）',
              style: AppTypography.bodySecondary(context),
            ),
          ],
        ),
      ),
    );
  }
}

class _TagPieChart extends StatefulWidget {
  const _TagPieChart({required this.distribution});

  final Map<String, int> distribution;

  @override
  State<_TagPieChart> createState() => _TagPieChartState();
}

class _TagPieChartState extends State<_TagPieChart> {
  int? _touchedIndex;

  @override
  Widget build(BuildContext context) {
    final entries = widget.distribution.entries
        .where((e) => e.value > 0)
        .toList();

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('标签分布', style: AppTypography.h2(context)),
            const SizedBox(height: AppSpacing.sm),
            Text(
              entries.isEmpty ? '暂无标签数据' : '点击饼图可查看具体占比',
              style: AppTypography.bodySecondary(context),
            ),
            const SizedBox(height: AppSpacing.lg),
            if (entries.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Center(
                  child: Text(
                    '暂无可展示的数据',
                    style: AppTypography.bodySecondary(context),
                  ),
                ),
              )
            else
              _Pie(
                entries: entries,
                touchedIndex: _touchedIndex,
                onTouch: (i) {
                  setState(() => _touchedIndex = i);
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _Pie extends StatelessWidget {
  const _Pie({
    required this.entries,
    required this.touchedIndex,
    required this.onTouch,
  });

  final List<MapEntry<String, int>> entries;
  final int? touchedIndex;
  final ValueChanged<int?> onTouch;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final tooltipBackground = isDark ? const Color(0xFF1E293B) : Colors.white;
    final tooltipTextColor = isDark
        ? AppColors.darkTextPrimary
        : AppColors.textPrimary;

    final total = entries.fold<int>(0, (sum, e) => sum + e.value);
    final palette = _palette(isDark: isDark);
    final sections = <PieChartSectionData>[];

    for (var i = 0; i < entries.length; i++) {
      final color = palette[i % palette.length];
      final v = entries[i].value.toDouble();
      final isTouched = touchedIndex == i;
      final percent = total == 0 ? 0.0 : (entries[i].value / total) * 100;
      sections.add(
        PieChartSectionData(
          value: v,
          color: color,
          radius: isTouched ? 22 : 18,
          showTitle: false,
          badgeWidget: isTouched
              ? _PieTooltip(
                  text:
                      '${entries[i].key} · ${entries[i].value}（${percent.toStringAsFixed(1)}%）',
                  backgroundColor: tooltipBackground,
                  textColor: tooltipTextColor,
                )
              : null,
          badgePositionPercentageOffset: 1.18,
        ),
      );
    }

    return Column(
      children: [
        SizedBox(
          height: 200,
          child: PieChart(
            PieChartData(
              sectionsSpace: 2,
              centerSpaceRadius: 38,
              startDegreeOffset: -90,
              sections: sections,
              pieTouchData: PieTouchData(
                touchCallback: (event, response) {
                  if (!event.isInterestedForInteractions) {
                    onTouch(null);
                    return;
                  }
                  final index =
                      response?.touchedSection?.touchedSectionIndex ?? -1;
                  onTouch(index < 0 ? null : index);
                },
              ),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        for (var i = 0; i < entries.length; i++) ...[
          _LegendRow(
            color: palette[i % palette.length],
            title: entries[i].key,
            value: entries[i].value,
            percent: total == 0 ? 0 : (entries[i].value / total) * 100,
          ),
          const SizedBox(height: 8),
        ],
      ],
    );
  }

  List<Color> _palette({required bool isDark}) {
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    return [
      primary,
      AppColors.success,
      AppColors.warning,
      AppColors.cta,
      const Color(0xFF6366F1), // Indigo
      const Color(0xFF0EA5E9), // Sky
      const Color(0xFFA855F7), // Purple
      const Color(0xFFEC4899), // Pink
    ];
  }
}

class _PieTooltip extends StatelessWidget {
  const _PieTooltip({
    required this.text,
    required this.backgroundColor,
    required this.textColor,
  });

  final String text;
  final Color backgroundColor;
  final Color textColor;

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: const BoxConstraints(maxWidth: 220),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: backgroundColor,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: Theme.of(context).dividerColor),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.08),
            blurRadius: 12,
            offset: const Offset(0, 6),
          ),
        ],
      ),
      child: Text(
        text,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
        style: TextStyle(
          color: textColor,
          fontSize: 12,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

class _LegendRow extends StatelessWidget {
  const _LegendRow({
    required this.color,
    required this.title,
    required this.value,
    required this.percent,
  });

  final Color color;
  final String title;
  final int value;
  final double percent;

  @override
  Widget build(BuildContext context) {
    final p = percent.isNaN ? 0 : percent;
    return Row(
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Text(
            title,
            style: AppTypography.bodySecondary(context),
            overflow: TextOverflow.ellipsis,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Text(
          '$value · ${p.toStringAsFixed(1)}%',
          style: AppTypography.bodySecondary(context),
        ),
      ],
    );
  }
}
