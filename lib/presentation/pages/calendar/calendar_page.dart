/// 文件用途：日历视图页面（Tab）——月历 + 当日任务详情 + 次级统计入口。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-03-06（新增月度摘要与桌面端常驻详情布局）
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_strings.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/date_utils.dart';
import '../../../core/utils/responsive_utils.dart';
import '../../../domain/entities/task_day_stats.dart';
import '../../providers/calendar_provider.dart';
import '../../providers/ui_preferences_provider.dart';
import '../../widgets/error_card.dart';
import '../../widgets/glass_card.dart';
import '../../widgets/gradient_background.dart';
import '../../widgets/semantic_panels.dart';
import 'widgets/calendar_grid.dart';
import 'widgets/compact_stats_bar.dart';
import 'widgets/day_task_list.dart';

/// 日历视图页面（Tab）。
class CalendarPage extends ConsumerStatefulWidget {
  /// 构造函数。
  ///
  /// 返回值：页面 Widget。
  /// 异常：无。
  const CalendarPage({super.key});

  @override
  ConsumerState<CalendarPage> createState() => _CalendarPageState();
}

class _CalendarPageState extends ConsumerState<CalendarPage> {
  // 当日任务 Sheet 打开状态：避免重复叠加弹出。
  bool _isDaySheetOpen = false;
  late DateTime _visibleMonth;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _visibleMonth = DateTime(now.year, now.month, 1);
  }

  /// 处理月历翻页。
  ///
  /// 说明：
  /// - 页面本地只维护当前可视月份，避免 Provider 反向控制月历翻页
  /// - 月份数据加载仍交给 Provider，保证业务状态来源单一
  void _handleMonthChanged(DateTime targetMonth) {
    setState(() {
      _visibleMonth = DateTime(targetMonth.year, targetMonth.month, 1);
    });
    ref.read(calendarProvider.notifier).loadMonth(
      _visibleMonth.year,
      _visibleMonth.month,
    );
  }

  Future<void> _openDayTaskListSheet(DateTime day) async {
    // 若当日任务 Sheet 已打开，先关闭再重新打开（允许用户切换日期）。
    if (_isDaySheetOpen) {
      Navigator.of(context).pop();
      await Future<void>.delayed(Duration.zero);
      if (!mounted) return;
    }

    setState(() => _isDaySheetOpen = true);
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (_) =>
          DayTaskListSheet(selectedDay: YikeDateUtils.atStartOfDay(day)),
    ).whenComplete(() {
      if (!mounted) return;
      setState(() => _isDaySheetOpen = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    // 使用 select 精准获取页面所需的字段，避免非当前页面所需数据引发整页重建。
    final state = ref.watch(
      calendarProvider.select(
        (s) => (
          selectedDay: s.selectedDay,
          monthStats: s.monthStats,
          loadedMonth: s.loadedMonth,
          selectedDayTasks: s.selectedDayTasks,
          hasLoadedMonth: s.hasLoadedMonth,
          isLoadingMonth: s.isLoadingMonth,
          isLoadingTasks: s.isLoadingTasks,
          errorMessage: s.errorMessage,
        ),
      ),
    );
    final notifier = ref.read(calendarProvider.notifier);
    final skeletonStrategy = ref.watch(skeletonStrategyProvider);
    final isDesktop = ResponsiveUtils.isDesktop(context);

    final overview = _MonthOverviewStrip(monthStats: state.monthStats);
    final calendarPanel = GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('复习计划', style: AppTypography.h2(context)),
            const SizedBox(height: AppSpacing.sm),
            Text(
              isDesktop ? '左侧查看月历，右侧常驻展示选中日期详情。' : '点击日期可查看当日任务列表',
              style: AppTypography.bodySecondary(context),
            ),
            const SizedBox(height: AppSpacing.lg),
            CompactStatsBar(
              // 统计已改为次级页面入口：从日历页进入后可返回当前计划视图。
              onTap: () => context.push('/statistics'),
            ),
            const SizedBox(height: AppSpacing.lg),
            CalendarGrid(
              initialMonth: _visibleMonth,
              selectedDay: state.selectedDay,
              loadedMonth: state.loadedMonth,
              dayStats: state.monthStats,
              hasLoadedMonth: state.hasLoadedMonth,
              isLoading: state.isLoadingMonth,
              skeletonStrategy: skeletonStrategy,
              onMonthChanged: _handleMonthChanged,
              onDaySelected: (day) async {
                await notifier.selectDay(day);
                if (!context.mounted || isDesktop) return;
                await _openDayTaskListSheet(day);
              },
            ),
          ],
        ),
      ),
    );

    return Scaffold(
      appBar: AppBar(
        title: Text(isDesktop ? AppStrings.plan : AppStrings.calendar),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: state.isLoadingMonth
                ? null
                : () => notifier.loadMonth(
                    _visibleMonth.year,
                    _visibleMonth.month,
                  ),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: GradientBackground(
        child: SafeArea(
          child: isDesktop
              ? Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        flex: 5,
                        child: ListView(
                          key: const PageStorageKey('calendar_scroll_desktop'),
                          children: [
                            overview,
                            const SizedBox(height: AppSpacing.lg),
                            calendarPanel,
                            const SizedBox(height: AppSpacing.lg),
                            const _LegendCard(),
                            if (state.errorMessage != null) ...[
                              const SizedBox(height: AppSpacing.lg),
                              ErrorCard(message: state.errorMessage!),
                            ],
                            const SizedBox(height: 24),
                          ],
                        ),
                      ),
                      const SizedBox(width: AppSpacing.lg),
                      Expanded(
                        flex: 4,
                        child: _SelectedDayPanel(
                          selectedDay: state.selectedDay,
                          selectedTaskCount: state.selectedDayTasks.length,
                          isLoadingTasks: state.isLoadingTasks,
                        ),
                      ),
                    ],
                  ),
                )
              : ListView(
                  key: const PageStorageKey('calendar_scroll'),
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  children: [
                    overview,
                    const SizedBox(height: AppSpacing.lg),
                    calendarPanel,
                    const SizedBox(height: AppSpacing.lg),
                    const _LegendCard(),
                    if (state.errorMessage != null) ...[
                      const SizedBox(height: AppSpacing.lg),
                      ErrorCard(message: state.errorMessage!),
                    ],
                    const SizedBox(height: 96),
                  ],
                ),
        ),
      ),
    );
  }
}

class _MonthOverviewStrip extends StatelessWidget {
  const _MonthOverviewStrip({required this.monthStats});

  final Map<DateTime, TaskDayStats> monthStats;

  @override
  Widget build(BuildContext context) {
    final activeDays = monthStats.values.where((s) => s.totalCount > 0).length;
    final pendingCount = monthStats.values.fold<int>(
      0,
      (sum, stats) => sum + stats.pendingCount,
    );
    final doneCount = monthStats.values.fold<int>(
      0,
      (sum, stats) => sum + stats.doneCount,
    );
    final totalCount = monthStats.values.fold<int>(
      0,
      (sum, stats) => sum + stats.totalCount,
    );
    final rate = totalCount == 0 ? 0 : (doneCount / totalCount * 100).round();

    return SummaryStrip(
      child: Row(
        children: [
          Expanded(
            child: _OverviewMetric(label: '本月完成率', value: '$rate%'),
          ),
          Expanded(
            child: _OverviewMetric(label: '有任务天数', value: '$activeDays'),
          ),
          Expanded(
            child: _OverviewMetric(label: '待处理项', value: '$pendingCount'),
          ),
        ],
      ),
    );
  }
}

class _OverviewMetric extends StatelessWidget {
  const _OverviewMetric({required this.label, required this.value});

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

class _SelectedDayPanel extends StatelessWidget {
  const _SelectedDayPanel({
    required this.selectedDay,
    required this.selectedTaskCount,
    required this.isLoadingTasks,
  });

  final DateTime? selectedDay;
  final int selectedTaskCount;
  final bool isLoadingTasks;

  @override
  Widget build(BuildContext context) {
    final day = selectedDay;
    if (day == null) {
      return SectionCard(
        title: '当天安排',
        subtitle: '选中左侧日期后，在这里查看任务详情。',
        child: const _EmptyDetailPlaceholder(),
      );
    }

    final subtitle = isLoadingTasks
        ? '正在加载 ${YikeDateUtils.formatYmd(day)} 的任务详情'
        : '${YikeDateUtils.formatYmd(day)} · 共 $selectedTaskCount 项';

    return SectionCard(
      title: '当天安排',
      subtitle: subtitle,
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.72,
        child: DayTaskListContent(selectedDay: day, showInteractionHint: false),
      ),
    );
  }
}

class _EmptyDetailPlaceholder extends StatelessWidget {
  const _EmptyDetailPlaceholder();

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: 240,
      child: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.calendar_today_outlined,
              size: 40,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: AppSpacing.md),
            Text('选择一天开始查看计划详情', style: AppTypography.body(context)),
          ],
        ),
      ),
    );
  }
}

class _LegendCard extends StatelessWidget {
  const _LegendCard();

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('状态说明', style: AppTypography.h2(context)),
            const SizedBox(height: AppSpacing.sm),
            Wrap(
              spacing: 12,
              runSpacing: 8,
              children: [
                const _LegendItem(color: AppColors.warning, text: '有逾期任务'),
                _LegendItem(color: primary, text: '有待复习任务'),
                const _LegendItem(color: AppColors.success, text: '已处理（完成/跳过）'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _LegendItem extends StatelessWidget {
  const _LegendItem({required this.color, required this.text});

  final Color color;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 6),
        Text(text, style: AppTypography.bodySecondary(context)),
      ],
    );
  }
}
