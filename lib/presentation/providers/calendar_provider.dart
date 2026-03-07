/// 文件用途：日历视图状态管理（F6，Riverpod StateNotifier）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/utils/date_utils.dart';
import '../../di/providers.dart';
import '../../domain/entities/review_task.dart';
import '../../domain/entities/task_day_stats.dart';

/// 日历页面状态。
class CalendarState {
  /// 构造函数。
  const CalendarState({
    required this.focusedMonth,
    required this.selectedDay,
    required this.monthStats,
    required this.selectedDayTasks,
    required this.isLoadingMonth,
    required this.isLoadingTasks,
    this.errorMessage,
  });

  /// 当前聚焦月份（用于 TableCalendar 翻页与标题展示）。
  final DateTime focusedMonth;

  /// 当前选中日期（可为空）。
  final DateTime? selectedDay;

  /// 月份单日统计（key 为当天 00:00:00）。
  final Map<DateTime, TaskDayStats> monthStats;

  /// 选中日期的任务列表（含学习内容）。
  final List<ReviewTaskViewEntity> selectedDayTasks;

  /// 月份数据加载中。
  final bool isLoadingMonth;

  /// 选中日期任务加载中。
  final bool isLoadingTasks;

  /// 错误信息（用于 UI 展示）。
  final String? errorMessage;

  factory CalendarState.initial() {
    final now = DateTime.now();
    final focused = DateTime(now.year, now.month, 1);
    return CalendarState(
      focusedMonth: focused,
      selectedDay: null,
      monthStats: const {},
      selectedDayTasks: const [],
      isLoadingMonth: true,
      isLoadingTasks: false,
      errorMessage: null,
    );
  }

  CalendarState copyWith({
    DateTime? focusedMonth,
    DateTime? selectedDay,
    bool clearSelectedDay = false,
    Map<DateTime, TaskDayStats>? monthStats,
    List<ReviewTaskViewEntity>? selectedDayTasks,
    bool? isLoadingMonth,
    bool? isLoadingTasks,
    String? errorMessage,
  }) {
    return CalendarState(
      focusedMonth: focusedMonth ?? this.focusedMonth,
      selectedDay: clearSelectedDay ? null : (selectedDay ?? this.selectedDay),
      monthStats: monthStats ?? this.monthStats,
      selectedDayTasks: selectedDayTasks ?? this.selectedDayTasks,
      isLoadingMonth: isLoadingMonth ?? this.isLoadingMonth,
      isLoadingTasks: isLoadingTasks ?? this.isLoadingTasks,
      errorMessage: errorMessage,
    );
  }
}

/// 日历页面 Notifier。
class CalendarNotifier extends StateNotifier<CalendarState> {
  /// 构造函数。
  CalendarNotifier(this._ref) : super(CalendarState.initial()) {
    // 首次创建时加载当月数据。
    loadMonth(state.focusedMonth.year, state.focusedMonth.month);
  }

  final Ref _ref;

  /// 加载月份数据（用于日历圆点标记）。
  ///
  /// 异常：异常会捕获并写入 [CalendarState.errorMessage]。
  Future<void> loadMonth(int year, int month) async {
    final focused = DateTime(year, month, 1);
    state = state.copyWith(
      focusedMonth: focused,
      isLoadingMonth: true,
      errorMessage: null,
    );
    try {
      final useCase = _ref.read(getCalendarTasksUseCaseProvider);
      final result = await useCase.execute(year: year, month: month);
      // Provider 可能在 await 期间被 invalidate；此时直接退出，避免 dispose 后回写状态。
      if (!mounted) return;
      state = state.copyWith(
        monthStats: result.dayStats,
        isLoadingMonth: false,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoadingMonth: false, errorMessage: e.toString());
    }
  }

  /// 选中某天并加载当日任务列表。
  Future<void> selectDay(DateTime day) async {
    final selected = YikeDateUtils.atStartOfDay(day);
    state = state.copyWith(
      selectedDay: selected,
      isLoadingTasks: true,
      errorMessage: null,
    );
    try {
      final useCase = _ref.read(getCalendarTasksUseCaseProvider);
      final tasks = await useCase.getTasksByDate(selected);
      if (!mounted) return;
      state = state.copyWith(selectedDayTasks: tasks, isLoadingTasks: false);
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoadingTasks: false, errorMessage: e.toString());
    }
  }

  /// 完成某条任务，并刷新当日列表与月份统计。
  Future<void> completeTask(int taskId) async {
    try {
      await _ref.read(completeReviewTaskUseCaseProvider).execute(taskId);
      if (!mounted) return;
      await _refreshAfterStatusChanged();
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(errorMessage: e.toString());
    }
  }

  /// 跳过某条任务，并刷新当日列表与月份统计。
  Future<void> skipTask(int taskId) async {
    try {
      await _ref.read(skipReviewTaskUseCaseProvider).execute(taskId);
      if (!mounted) return;
      await _refreshAfterStatusChanged();
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(errorMessage: e.toString());
    }
  }

  /// 撤销任务状态（done/skipped → pending），并刷新当日列表与月份统计。
  Future<void> undoTaskStatus(int taskId) async {
    try {
      await _ref.read(undoTaskStatusUseCaseProvider).execute(taskId);
      if (!mounted) return;
      await _refreshAfterStatusChanged();
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(errorMessage: e.toString());
    }
  }

  Future<void> _refreshAfterStatusChanged() async {
    final selected = state.selectedDay;
    await loadMonth(state.focusedMonth.year, state.focusedMonth.month);
    if (selected != null) {
      await selectDay(selected);
    }
  }
}

/// 日历页面 Provider。
final calendarProvider = StateNotifierProvider<CalendarNotifier, CalendarState>(
  (ref) {
    return CalendarNotifier(ref);
  },
);
