/// 文件用途：自定义月历网格组件，统一承载按钮翻页、手势翻页与日期选择。
/// 作者：Codex
/// 创建日期：2026-03-08
library;

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../../domain/entities/task_day_stats.dart';
import '../../../widgets/skeleton_loader.dart';

/// 日历网格组件。
///
/// 说明：
/// - 仅支持“月视图”，避免额外格式切换状态机
/// - 按钮翻页与手势翻页统一走同一套 PageView 逻辑
/// - Provider 只负责数据加载，当前可视月份由组件内部维护
class CalendarGrid extends StatefulWidget {
  /// 构造函数。
  const CalendarGrid({
    super.key,
    required this.initialMonth,
    required this.selectedDay,
    required this.loadedMonth,
    required this.dayStats,
    required this.hasLoadedMonth,
    required this.isLoading,
    required this.skeletonStrategy,
    required this.onMonthChanged,
    required this.onDaySelected,
  });

  /// 初始显示月份（规范到每月 1 号）。
  final DateTime initialMonth;

  /// 当前选中日期。
  final DateTime? selectedDay;

  /// 最近一次成功加载完成的月份。
  final DateTime? loadedMonth;

  /// 月份单日统计（key 为当天 00:00）。
  final Map<DateTime, TaskDayStats> dayStats;

  /// 是否至少成功加载过一次月份数据。
  final bool hasLoadedMonth;

  /// 当前月份数据是否加载中。
  final bool isLoading;

  /// 骨架屏策略。
  final String skeletonStrategy;

  /// 月份变化回调。
  final ValueChanged<DateTime> onMonthChanged;

  /// 日期点击回调。
  final ValueChanged<DateTime> onDaySelected;

  @override
  State<CalendarGrid> createState() => _CalendarGridState();
}

class _CalendarGridState extends State<CalendarGrid> {
  static final DateTime _firstMonth = DateTime(2000, 1, 1);
  static final DateTime _lastMonth = DateTime(2100, 12, 1);
  static const int _rowCount = 6;
  static const int _columnCount = 7;
  static const double _headerHeight = 56;
  static const double _weekdaysHeight = 28;
  static const double _cellHeight = 48;

  late final PageController _pageController;
  late DateTime _visibleMonth;
  late int _currentPage;

  @override
  void initState() {
    super.initState();
    _visibleMonth = _normalizeMonth(widget.initialMonth);
    _currentPage = _monthIndex(_visibleMonth);
    _pageController = PageController(initialPage: _currentPage);
  }

  @override
  void didUpdateWidget(covariant CalendarGrid oldWidget) {
    super.didUpdateWidget(oldWidget);
    final nextMonth = _normalizeMonth(widget.initialMonth);
    if (_isSameMonth(nextMonth, _visibleMonth)) {
      return;
    }

    _visibleMonth = nextMonth;
    _currentPage = _monthIndex(nextMonth);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_pageController.hasClients) return;
      _pageController.jumpToPage(_currentPage);
    });
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final showSkeleton = widget.isLoading && !widget.hasLoadedMonth;
    if (showSkeleton) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 12),
        child: SkeletonLoader(
          isLoading: true,
          strategy: widget.skeletonStrategy,
          skeleton: const SkeletonShimmer(child: _CalendarGridSkeleton()),
          child:
              widget.skeletonStrategy == 'off'
                  ? const Padding(
                      padding: EdgeInsets.symmetric(vertical: 24),
                      child: Center(child: CircularProgressIndicator()),
                    )
                  : const SizedBox.shrink(),
        ),
      );
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final textPrimary = isDark
        ? AppColors.darkTextPrimary
        : AppColors.textPrimary;
    final textSecondary = isDark
        ? AppColors.darkTextSecondary
        : AppColors.textSecondary;

    return SizedBox(
      height: _headerHeight + _weekdaysHeight + (_rowCount * _cellHeight),
      child: Column(
        children: [
          _CalendarHeader(
            month: _visibleMonth,
            canGoPrevious: _currentPage > 0,
            canGoNext: _currentPage < _totalMonthCount - 1,
            textPrimary: textPrimary,
            onPrevious: () => _animateToAdjacentMonth(-1),
            onNext: () => _animateToAdjacentMonth(1),
          ),
          _WeekdayRow(textSecondary: textSecondary),
          Expanded(
            child: Stack(
              children: [
                PageView.builder(
                  controller: _pageController,
                  itemCount: _totalMonthCount,
                  onPageChanged: _handlePageChanged,
                  itemBuilder: (context, index) {
                    final month = _monthForIndex(index);
                    final stats =
                        _isSameMonth(widget.loadedMonth, month)
                            ? widget.dayStats
                            : const <DateTime, TaskDayStats>{};
                    return _CalendarMonthPage(
                      month: month,
                      selectedDay: widget.selectedDay,
                      dayStats: stats,
                      onDaySelected: widget.onDaySelected,
                    );
                  },
                ),
                if (widget.isLoading)
                  const Positioned(
                    top: 8,
                    right: 8,
                    child: _LoadingBadge(),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// 处理 PageView 月份变化。
  void _handlePageChanged(int page) {
    final month = _monthForIndex(page);
    setState(() {
      _currentPage = page;
      _visibleMonth = month;
    });
    widget.onMonthChanged(month);
  }

  /// 切换到相邻月份。
  void _animateToAdjacentMonth(int delta) {
    final targetPage = _currentPage + delta;
    if (targetPage < 0 || targetPage >= _totalMonthCount) {
      return;
    }
    _pageController.animateToPage(
      targetPage,
      duration: const Duration(milliseconds: 220),
      curve: Curves.easeOutCubic,
    );
  }

  /// 将日期规范到每月 1 号。
  DateTime _normalizeMonth(DateTime month) {
    return DateTime(month.year, month.month, 1);
  }

  /// 判断两个日期是否位于同一个自然月。
  bool _isSameMonth(DateTime? left, DateTime? right) {
    if (left == null || right == null) return false;
    return left.year == right.year && left.month == right.month;
  }

  /// 将月份映射到 PageView 索引。
  int _monthIndex(DateTime month) {
    return (month.year - _firstMonth.year) * 12 + month.month - _firstMonth.month;
  }

  /// 根据 PageView 索引计算月份。
  DateTime _monthForIndex(int index) {
    return DateTime(_firstMonth.year, _firstMonth.month + index, 1);
  }

  /// 总月份数（含首尾月）。
  int get _totalMonthCount {
    return (_lastMonth.year - _firstMonth.year) * 12 +
        _lastMonth.month -
        _firstMonth.month +
        1;
  }
}

/// 月历头部（标题 + 左右翻页按钮）。
class _CalendarHeader extends StatelessWidget {
  const _CalendarHeader({
    required this.month,
    required this.canGoPrevious,
    required this.canGoNext,
    required this.textPrimary,
    required this.onPrevious,
    required this.onNext,
  });

  final DateTime month;
  final bool canGoPrevious;
  final bool canGoNext;
  final Color textPrimary;
  final VoidCallback onPrevious;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    final title = DateFormat('yyyy年M月').format(month);
    return SizedBox(
      height: _CalendarGridState._headerHeight,
      child: Row(
        children: [
          IconButton(
            tooltip: '上个月',
            onPressed: canGoPrevious ? onPrevious : null,
            icon: Icon(Icons.chevron_left, color: textPrimary),
          ),
          Expanded(
            child: Center(
              child: Text(
                title,
                style: TextStyle(
                  color: textPrimary,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          IconButton(
            tooltip: '下个月',
            onPressed: canGoNext ? onNext : null,
            icon: Icon(Icons.chevron_right, color: textPrimary),
          ),
        ],
      ),
    );
  }
}

/// 星期标题行（周一至周日）。
class _WeekdayRow extends StatelessWidget {
  const _WeekdayRow({required this.textSecondary});

  final Color textSecondary;

  @override
  Widget build(BuildContext context) {
    const labels = <String>['一', '二', '三', '四', '五', '六', '日'];
    return SizedBox(
      height: _CalendarGridState._weekdaysHeight,
      child: Row(
        children: [
          for (final label in labels)
            Expanded(
              child: Center(
                child: Text(
                  label,
                  style: TextStyle(color: textSecondary, fontSize: 12),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// 单个月份页面。
class _CalendarMonthPage extends StatelessWidget {
  const _CalendarMonthPage({
    required this.month,
    required this.selectedDay,
    required this.dayStats,
    required this.onDaySelected,
  });

  final DateTime month;
  final DateTime? selectedDay;
  final Map<DateTime, TaskDayStats> dayStats;
  final ValueChanged<DateTime> onDaySelected;

  @override
  Widget build(BuildContext context) {
    final cells = _buildMonthCells(month);
    return GridView.builder(
      physics: const NeverScrollableScrollPhysics(),
      padding: EdgeInsets.zero,
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: _CalendarGridState._columnCount,
        mainAxisExtent: _CalendarGridState._cellHeight,
      ),
      itemCount: cells.length,
      itemBuilder: (context, index) {
        final day = cells[index];
        return _CalendarDayCell(
          day: day,
          selectedDay: selectedDay,
          dayStats: day == null ? null : dayStats[YikeDateUtils.atStartOfDay(day)],
          onTap: day == null ? null : () => onDaySelected(day),
        );
      },
    );
  }

  /// 构建月份 6x7 日期格子。
  ///
  /// 说明：
  /// - 采用固定 42 格，确保不同月份切换时高度完全稳定
  /// - 月外日期以空白格展示，避免误触和视觉干扰
  List<DateTime?> _buildMonthCells(DateTime targetMonth) {
    final firstDay = DateTime(targetMonth.year, targetMonth.month, 1);
    final leadingEmpty = firstDay.weekday - DateTime.monday;
    final daysInMonth = DateTime(targetMonth.year, targetMonth.month + 1, 0).day;
    final result = List<DateTime?>.filled(
      _CalendarGridState._rowCount * _CalendarGridState._columnCount,
      null,
    );

    for (var day = 1; day <= daysInMonth; day++) {
      final index = leadingEmpty + day - 1;
      result[index] = DateTime(targetMonth.year, targetMonth.month, day);
    }
    return result;
  }
}

/// 单日日期格子。
class _CalendarDayCell extends StatelessWidget {
  const _CalendarDayCell({
    required this.day,
    required this.selectedDay,
    required this.dayStats,
    required this.onTap,
  });

  final DateTime? day;
  final DateTime? selectedDay;
  final TaskDayStats? dayStats;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    if (day == null) {
      return const SizedBox.expand();
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    final textPrimary = isDark
        ? AppColors.darkTextPrimary
        : AppColors.textPrimary;
    final isSelected =
        selectedDay != null && YikeDateUtils.isSameDay(day!, selectedDay!);
    final isToday = YikeDateUtils.isSameDay(day!, DateTime.now());
    final markerColor = _markerColor(
      stats: dayStats,
      dayStart: YikeDateUtils.atStartOfDay(day!),
      todayStart: YikeDateUtils.atStartOfDay(DateTime.now()),
      primary: primary,
    );

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 2),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: DecoratedBox(
            decoration: BoxDecoration(
              color:
                  isSelected
                      ? primary
                      : isToday
                      ? primary.withValues(alpha: isDark ? 0.22 : 0.16)
                      : Colors.transparent,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    '${day!.day}',
                    style: TextStyle(
                      color: isSelected ? Colors.white : textPrimary,
                      fontWeight: isSelected || isToday
                          ? FontWeight.w700
                          : FontWeight.w500,
                    ),
                  ),
                  const SizedBox(height: 4),
                  SizedBox(
                    width: 6,
                    height: 6,
                    child:
                        markerColor == null
                            ? const SizedBox.shrink()
                            : DecoratedBox(
                                decoration: BoxDecoration(
                                  color: markerColor,
                                  shape: BoxShape.circle,
                                ),
                              ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  /// 根据 PRD 规则计算当日圆点颜色。
  ///
  /// 优先级：逾期 > 待复习 > 已处理（完成/跳过）。
  Color? _markerColor({
    required TaskDayStats? stats,
    required DateTime dayStart,
    required DateTime todayStart,
    required Color primary,
  }) {
    if (stats == null || stats.totalCount == 0) return null;

    final isPastDay = dayStart.isBefore(todayStart);
    final hasOverdue = stats.pendingCount > 0 && isPastDay;
    if (hasOverdue) return AppColors.warning;

    if (stats.pendingCount > 0) return primary;

    return AppColors.success;
  }
}

/// 加载中的右上角轻量指示器。
class _LoadingBadge extends StatelessWidget {
  const _LoadingBadge();

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface.withValues(alpha: 0.92),
        borderRadius: BorderRadius.circular(999),
      ),
      child: const Padding(
        padding: EdgeInsets.all(8),
        child: SizedBox(
          width: 18,
          height: 18,
          child: CircularProgressIndicator(strokeWidth: 2),
        ),
      ),
    );
  }
}

class _CalendarGridSkeleton extends StatelessWidget {
  const _CalendarGridSkeleton();

  @override
  Widget build(BuildContext context) {
    // 6 行 x 7 列：模拟固定高度的月历视图。
    const cell = 34.0;
    const gap = 8.0;
    return Column(
      children: [
        Row(
          children: const [
            SkeletonBox(width: 28, height: 28, radius: 8),
            Spacer(),
            SkeletonBox(width: 120, height: 16),
            Spacer(),
            SkeletonBox(width: 28, height: 28, radius: 8),
          ],
        ),
        const SizedBox(height: 12),
        Row(
          children: const [
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
            SizedBox(width: gap),
            Expanded(child: SkeletonBox(width: cell, height: 12, radius: 6)),
          ],
        ),
        const SizedBox(height: 12),
        for (var r = 0; r < 6; r++) ...[
          Row(
            children: [
              for (var c = 0; c < 7; c++) ...[
                const Expanded(
                  child: SkeletonBox(width: cell, height: cell, radius: 10),
                ),
                if (c != 6) const SizedBox(width: gap),
              ],
            ],
          ),
          if (r != 5) const SizedBox(height: gap),
        ],
      ],
    );
  }
}
