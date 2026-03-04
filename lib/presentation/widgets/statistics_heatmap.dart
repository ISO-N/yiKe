/// 文件用途：统计热力图组件（GitHub 风格）——展示每日完成率分布，支持年份切换。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/date_utils.dart';
import '../../domain/entities/task_day_stats.dart';
import '../providers/ui_preferences_provider.dart';
import '../providers/statistics_heatmap_provider.dart';
import 'glass_card.dart';
import 'skeleton_loader.dart';

/// 统计热力图（按年）。
///
/// 说明：
/// - 7 行（每列 7 天）× 52~53 列（周块）
/// - tooltip：桌面端 hover，移动端长按（Flutter Tooltip 默认行为）
class StatisticsHeatmap extends ConsumerStatefulWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [initialYear] 初始年份（默认当前年）
  const StatisticsHeatmap({super.key, this.initialYear});

  final int? initialYear;

  @override
  ConsumerState<StatisticsHeatmap> createState() => _StatisticsHeatmapState();
}

class _StatisticsHeatmapState extends ConsumerState<StatisticsHeatmap> {
  late int _year;

  @override
  void initState() {
    super.initState();
    _year = widget.initialYear ?? DateTime.now().year;
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(statisticsHeatmapProvider(_year));
    final skeletonStrategy = ref.watch(skeletonStrategyProvider);

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('年度热力图', style: AppTypography.h2(context)),
                ),
                IconButton(
                  tooltip: '上一年',
                  onPressed: () => setState(() => _year -= 1),
                  icon: const Icon(Icons.chevron_left),
                ),
                Text('$_year', style: AppTypography.bodySecondary(context)),
                IconButton(
                  tooltip: '下一年',
                  onPressed: () => setState(() => _year += 1),
                  icon: const Icon(Icons.chevron_right),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.md),
            async.when(
              loading: () => SkeletonLoader(
                isLoading: true,
                strategy: skeletonStrategy,
                skeleton: const SkeletonShimmer(child: _HeatmapSkeleton()),
                child:
                    skeletonStrategy == 'off'
                        ? const Center(
                            child: Padding(
                              padding: EdgeInsets.all(12),
                              child: CircularProgressIndicator(),
                            ),
                          )
                        : const SizedBox.shrink(),
              ),
              error: (e, _) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  '热力图加载失败：$e',
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
              data: (map) => _HeatmapGrid(year: _year, statsByDay: map),
            ),
          ],
        ),
      ),
    );
  }
}

class _HeatmapSkeleton extends StatelessWidget {
  const _HeatmapSkeleton();

  @override
  Widget build(BuildContext context) {
    // 52 周 × 7 天：与真实热力图结构一致，避免布局跳变。
    return SizedBox(
      height: 7 * 12 + 6 * 2,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: List<Widget>.generate(53, (i) {
            return Padding(
              padding: EdgeInsets.only(right: i == 52 ? 0 : 2),
              child: Column(
                children: List<Widget>.generate(7, (j) {
                  return Padding(
                    padding: EdgeInsets.only(bottom: j == 6 ? 0 : 2),
                    child: const SkeletonBox(
                      width: 12,
                      height: 12,
                      radius: 3,
                    ),
                  );
                }),
              ),
            );
          }),
        ),
      ),
    );
  }
}

class _HeatmapGrid extends StatelessWidget {
  const _HeatmapGrid({required this.year, required this.statsByDay});

  final int year;
  final Map<DateTime, TaskDayStats> statsByDay;

  static const double _cell = 12;
  static const double _gap = 2;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = Theme.of(context).colorScheme.primary;

    final colors = _palette(
      seed: primary,
      isDark: isDark,
      emptyColor: Theme.of(context).dividerColor.withValues(
        alpha: isDark ? 0.18 : 0.12,
      ),
    );

    final yearStart = DateTime(year, 1, 1);
    final yearEnd = DateTime(year + 1, 1, 1);

    // 关键逻辑：1 月 1 日顶格显示（不再对齐到周一，也不显示上一年的补齐占位）。
    final start = yearStart;

    final days = yearEnd.difference(start).inDays;
    final weekCount = ((days + 6) / 7).floor();

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: List<Widget>.generate(weekCount, (weekIndex) {
          final weekStart = start.add(Duration(days: weekIndex * 7));
          return Padding(
            padding: EdgeInsets.only(right: weekIndex == weekCount - 1 ? 0 : _gap),
            child: Column(
              children: List<Widget>.generate(7, (dayIndex) {
                final day = weekStart.add(Duration(days: dayIndex));
                final isInYear =
                    !day.isBefore(yearStart) && day.isBefore(yearEnd);

                if (!isInYear) {
                  // 年外补齐格子：占位透明，保持网格对齐。
                  return const SizedBox(width: _cell, height: _cell);
                }

                final key = YikeDateUtils.atStartOfDay(day);
                final stats = statsByDay[key];
                final cellColor = _colorOfDay(colors, stats);

                return Padding(
                  padding: EdgeInsets.only(bottom: dayIndex == 6 ? 0 : _gap),
                  child: Tooltip(
                    message: _tooltipText(day: key, stats: stats),
                    child: Semantics(
                      label: _semanticsLabel(day: key, stats: stats),
                      child: Container(
                        width: _cell,
                        height: _cell,
                        decoration: BoxDecoration(
                          color: cellColor,
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
                    ),
                  ),
                );
              }),
            ),
          );
        }),
      ),
    );
  }

  String _tooltipText({required DateTime day, TaskDayStats? stats}) {
    final s = stats;
    if (s == null || s.totalCount == 0) {
      return '${YikeDateUtils.formatYmd(day)}: 无任务';
    }
    final total = s.doneCount + s.pendingCount;
    if (total <= 0) {
      return '${YikeDateUtils.formatYmd(day)}: 无任务';
    }
    final rate = ((s.doneCount / total) * 100).clamp(0, 100).toStringAsFixed(0);
    return '${YikeDateUtils.formatYmd(day)}: ${s.doneCount}/$total ($rate%)';
  }

  String _semanticsLabel({required DateTime day, TaskDayStats? stats}) {
    final s = stats;
    if (s == null || s.totalCount == 0) {
      return '${YikeDateUtils.formatYmd(day)}，无任务';
    }
    final total = s.doneCount + s.pendingCount;
    if (total <= 0) {
      return '${YikeDateUtils.formatYmd(day)}，无任务';
    }
    final rate = ((s.doneCount / total) * 100).clamp(0, 100).toStringAsFixed(0);
    return '${YikeDateUtils.formatYmd(day)}，完成率 $rate%';
  }

  Color _colorOfDay(List<Color> colors, TaskDayStats? stats) {
    final s = stats;
    if (s == null) return colors[0];
    final done = s.doneCount;
    final total = s.doneCount + s.pendingCount;
    if (total <= 0) return colors[0];

    // 分档规则：total>0 且 done=0 归入 0-25% 档。
    if (done <= 0) return colors[1];

    final p = (done / total) * 100;
    if (p <= 25) return colors[1];
    if (p <= 50) return colors[2];
    if (p <= 75) return colors[3];
    return colors[4];
  }

  List<Color> _palette({
    required Color seed,
    required bool isDark,
    required Color emptyColor,
  }) {
    // 说明：不引入新的颜色算法依赖，使用“主题色 → 逐级混合”的方式生成同色系 4 档。
    final base = seed;
    final bg = isDark ? Colors.black : Colors.white;

    Color mix(double t) => Color.lerp(bg, base, t) ?? base;

    // 0: 无任务
    // 1~4: 0-25 / 26-50 / 51-75 / 76-100
    return [
      emptyColor,
      mix(isDark ? 0.26 : 0.22),
      mix(isDark ? 0.42 : 0.40),
      mix(isDark ? 0.58 : 0.60),
      mix(isDark ? 0.76 : 0.82),
    ];
  }
}
