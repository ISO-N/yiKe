/// 文件用途：任务中心状态管理（任务时间线 + 游标分页 + 状态筛选）。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../core/utils/date_utils.dart';
import '../../domain/entities/review_task.dart';
import '../../domain/entities/task_timeline.dart';
import 'calendar_provider.dart';
import 'home_time_filter_provider.dart';
import 'home_tasks_provider.dart';
import 'statistics_provider.dart';
import 'task_filter_provider.dart';

/// 任务中心时间线行模型（Provider 侧预处理结果）。
///
/// 说明：
/// - 用于将“分组/排序”从 UI build 热路径移出（spec-performance-optimization.md / Phase 1）
/// - 该模型仅服务 Presentation 层，避免引入 Domain 层依赖
sealed class TaskHubTimelineRow {
  const TaskHubTimelineRow();
}

/// 日期分组标题行。
class TaskHubTimelineHeaderRow extends TaskHubTimelineRow {
  const TaskHubTimelineHeaderRow({required this.day});

  /// 分组日期（当天 00:00）。
  final DateTime day;
}

/// 任务卡片行（轻量结构，避免在 Widget build 阶段频繁拆解实体）。
class TaskHubTimelineTaskRow extends TaskHubTimelineRow {
  const TaskHubTimelineTaskRow({
    required this.taskId,
    required this.learningItemId,
    required this.title,
    required this.description,
    required this.legacyNote,
    required this.subtaskCount,
    required this.tags,
    required this.reviewRound,
    required this.scheduledDate,
    required this.status,
    required this.completedAt,
    required this.skippedAt,
    required this.occurredAt,
    required this.isLastInGroup,
  });

  final int taskId;
  final int learningItemId;
  final String title;
  final String? description;
  final String? legacyNote;
  final int subtaskCount;
  final List<String> tags;
  final int reviewRound;
  final DateTime scheduledDate;
  final ReviewTaskStatus status;
  final DateTime? completedAt;
  final DateTime? skippedAt;
  final DateTime occurredAt;

  /// 是否为该日期分组的最后一条任务（用于插入更大的组间距）。
  final bool isLastInGroup;

  TaskHubTimelineTaskRow copyWith({bool? isLastInGroup}) {
    return TaskHubTimelineTaskRow(
      taskId: taskId,
      learningItemId: learningItemId,
      title: title,
      description: description,
      legacyNote: legacyNote,
      subtaskCount: subtaskCount,
      tags: tags,
      reviewRound: reviewRound,
      scheduledDate: scheduledDate,
      status: status,
      completedAt: completedAt,
      skippedAt: skippedAt,
      occurredAt: occurredAt,
      isLastInGroup: isLastInGroup ?? this.isLastInGroup,
    );
  }

  /// 从时间线实体构建行模型。
  factory TaskHubTimelineTaskRow.fromEntity(
    ReviewTaskTimelineItemEntity item, {
    required bool isLastInGroup,
  }) {
    return TaskHubTimelineTaskRow(
      taskId: item.task.taskId,
      learningItemId: item.task.learningItemId,
      title: item.task.title,
      description: item.task.description,
      legacyNote: item.task.note,
      subtaskCount: item.task.subtaskCount,
      tags: item.task.tags,
      reviewRound: item.task.reviewRound,
      scheduledDate: item.task.scheduledDate,
      status: item.task.status,
      completedAt: item.task.completedAt,
      skippedAt: item.task.skippedAt,
      occurredAt: item.occurredAt,
      isLastInGroup: isLastInGroup,
    );
  }
}

/// 任务中心页面状态。
class TaskHubState {
  /// 构造函数。
  const TaskHubState({
    required this.isLoading,
    required this.isLoadingMore,
    required this.filter,
    required this.timeFilter,
    required this.counts,
    required this.items,
    required this.timelineRows,
    required this.nextCursor,
    required this.expandedTaskIds,
    this.errorMessage,
  });

  /// 首次/刷新加载中。
  final bool isLoading;

  /// 追加分页加载中。
  final bool isLoadingMore;

  /// 当前筛选（单选）。
  final ReviewTaskFilter filter;

  /// 当前时间筛选（仅用于首页 all-tab）。
  final HomeTimeFilter timeFilter;

  /// 全量任务状态计数（用于筛选栏展示）。
  final TaskStatusCounts counts;

  /// 已加载的时间线条目（按发生时间倒序）。
  final List<ReviewTaskTimelineItemEntity> items;

  /// 时间线行模型（Provider 侧预处理结果）。
  ///
  /// 说明：仅当 items 变化（refresh/loadMore）时更新；展开态变化不会触发重建。
  final List<TaskHubTimelineRow> timelineRows;

  /// 下一页游标；为空表示没有更多数据。
  final TaskTimelineCursorEntity? nextCursor;

  /// 当前展开的任务集合（点击卡片展开操作区）。
  final Set<int> expandedTaskIds;

  /// 错误信息（用于 UI 展示）。
  final String? errorMessage;

  factory TaskHubState.initial() => const TaskHubState(
    isLoading: true,
    isLoadingMore: false,
    filter: ReviewTaskFilter.all,
    timeFilter: HomeTimeFilter.all,
    counts: TaskStatusCounts(all: 0, pending: 0, done: 0, skipped: 0),
    items: [],
    timelineRows: [],
    nextCursor: null,
    expandedTaskIds: {},
    errorMessage: null,
  );

  TaskHubState copyWith({
    bool? isLoading,
    bool? isLoadingMore,
    ReviewTaskFilter? filter,
    HomeTimeFilter? timeFilter,
    TaskStatusCounts? counts,
    List<ReviewTaskTimelineItemEntity>? items,
    List<TaskHubTimelineRow>? timelineRows,
    TaskTimelineCursorEntity? nextCursor,
    bool clearCursor = false,
    Set<int>? expandedTaskIds,
    String? errorMessage,
  }) {
    return TaskHubState(
      isLoading: isLoading ?? this.isLoading,
      isLoadingMore: isLoadingMore ?? this.isLoadingMore,
      filter: filter ?? this.filter,
      timeFilter: timeFilter ?? this.timeFilter,
      counts: counts ?? this.counts,
      items: items ?? this.items,
      timelineRows: timelineRows ?? this.timelineRows,
      nextCursor: clearCursor ? null : (nextCursor ?? this.nextCursor),
      expandedTaskIds: expandedTaskIds ?? this.expandedTaskIds,
      errorMessage: errorMessage,
    );
  }
}

/// 任务中心 Notifier。
class TaskHubNotifier extends StateNotifier<TaskHubState> {
  /// 构造函数。
  TaskHubNotifier(this._ref) : super(TaskHubState.initial()) {
    loadInitial();
  }

  final Ref _ref;

  /// 首次加载：同时拉取计数与首屏时间线。
  Future<void> loadInitial() async {
    await Future.wait([_loadCounts(), refresh()]);
  }

  /// 切换筛选并刷新列表。
  Future<void> setFilter(ReviewTaskFilter next) async {
    if (next == state.filter) return;
    state = state.copyWith(filter: next, expandedTaskIds: <int>{});
    await refresh();
  }

  /// 切换时间筛选并刷新列表。
  Future<void> setTimeFilter(HomeTimeFilter next) async {
    if (next == state.timeFilter) return;
    state = state.copyWith(timeFilter: next, expandedTaskIds: <int>{});
    await refresh();
  }

  /// 下拉刷新：重置游标并重新加载首屏。
  Future<void> refresh() async {
    state = state.copyWith(
      isLoading: true,
      isLoadingMore: false,
      items: const [],
      timelineRows: const [],
      clearCursor: true,
      errorMessage: null,
      expandedTaskIds: <int>{},
    );
    try {
      final useCase = _ref.read(getTasksByTimeUseCaseProvider);
      final status = _mapFilterToStatus(state.filter);
      final (scheduledDateBefore, scheduledDateOnOrAfter) =
          _resolveTimeFilterRange(state.timeFilter);
      final page = await useCase.execute(
        status: status,
        scheduledDateBefore: scheduledDateBefore,
        scheduledDateOnOrAfter: scheduledDateOnOrAfter,
        cursor: null,
        limit: 20,
      );
      final rows = _buildTimelineRows(page.items);
      state = state.copyWith(
        isLoading: false,
        items: page.items,
        timelineRows: rows,
        nextCursor: page.nextCursor,
      );
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 追加分页：根据游标加载下一页。
  Future<void> loadMore() async {
    final cursor = state.nextCursor;
    if (state.isLoading || state.isLoadingMore || cursor == null) return;

    state = state.copyWith(isLoadingMore: true, errorMessage: null);
    try {
      final useCase = _ref.read(getTasksByTimeUseCaseProvider);
      final status = _mapFilterToStatus(state.filter);
      final (scheduledDateBefore, scheduledDateOnOrAfter) =
          _resolveTimeFilterRange(state.timeFilter);
      final page = await useCase.execute(
        status: status,
        scheduledDateBefore: scheduledDateBefore,
        scheduledDateOnOrAfter: scheduledDateOnOrAfter,
        cursor: cursor,
        limit: 20,
      );

      final mergedItems = [...state.items, ...page.items];
      final rows = _buildTimelineRows(mergedItems);
      state = state.copyWith(
        isLoadingMore: false,
        items: mergedItems,
        timelineRows: rows,
        nextCursor: page.nextCursor,
      );
    } catch (e) {
      state = state.copyWith(isLoadingMore: false, errorMessage: e.toString());
    }
  }

  /// 展开/收起任务卡片。
  void toggleExpanded(int taskId) {
    final next = Set<int>.from(state.expandedTaskIds);
    if (next.contains(taskId)) {
      next.remove(taskId);
    } else {
      next.add(taskId);
    }
    state = state.copyWith(expandedTaskIds: next);
  }

  /// 完成任务并刷新列表与相关页面数据。
  Future<void> completeTask(int taskId) async {
    await _ref.read(completeReviewTaskUseCaseProvider).execute(taskId);
    _invalidateRelatedPages();
    await Future.wait([_loadCounts(), refresh()]);
  }

  /// 跳过任务并刷新列表与相关页面数据。
  Future<void> skipTask(int taskId) async {
    await _ref.read(skipReviewTaskUseCaseProvider).execute(taskId);
    _invalidateRelatedPages();
    await Future.wait([_loadCounts(), refresh()]);
  }

  /// 撤销任务状态并刷新列表与相关页面数据。
  Future<void> undoTaskStatus(int taskId) async {
    await _ref.read(undoTaskStatusUseCaseProvider).execute(taskId);
    _invalidateRelatedPages();
    await Future.wait([_loadCounts(), refresh()]);
  }

  Future<void> _loadCounts() async {
    try {
      final useCase = _ref.read(getTasksByTimeUseCaseProvider);
      final (all, pending, done, skipped) = await useCase.getStatusCounts();
      state = state.copyWith(
        counts: TaskStatusCounts(
          all: all,
          pending: pending,
          done: done,
          skipped: skipped,
        ),
      );
    } catch (_) {
      // 计数失败不阻断主流程：列表仍可展示。
    }
  }

  ReviewTaskStatus? _mapFilterToStatus(ReviewTaskFilter filter) {
    return switch (filter) {
      ReviewTaskFilter.all => null,
      ReviewTaskFilter.pending => ReviewTaskStatus.pending,
      ReviewTaskFilter.done => ReviewTaskStatus.done,
      ReviewTaskFilter.skipped => ReviewTaskStatus.skipped,
    };
  }

  void _invalidateRelatedPages() {
    // 完成/跳过/撤销会影响首页、日历与统计口径，因此需要主动刷新相关页面状态。
    _ref.invalidate(homeTasksProvider);
    _ref.invalidate(calendarProvider);
    _ref.invalidate(statisticsProvider);
  }

  /// 将 UI 时间筛选映射为查询边界（本地自然日）。
  ///
  /// 返回值：
  /// - `scheduledDateBefore`：用于 `<` 条件
  /// - `scheduledDateOnOrAfter`：用于 `>=` 条件
  (DateTime? scheduledDateBefore, DateTime? scheduledDateOnOrAfter)
  _resolveTimeFilterRange(HomeTimeFilter filter) {
    final todayStart = YikeDateUtils.atStartOfDay(DateTime.now());
    final tomorrowStart = todayStart.add(const Duration(days: 1));
    return switch (filter) {
      HomeTimeFilter.all => (null, null),
      HomeTimeFilter.beforeToday => (todayStart, null),
      HomeTimeFilter.afterToday => (null, tomorrowStart),
    };
  }

  /// 构建时间线行模型（按日期分组，扁平化为 header/task 行序列）。
  ///
  /// 关键逻辑：
  /// - 仅在 items 发生变化时执行（refresh/loadMore）
  /// - 展开态（expandedTaskIds）变化不触发行模型重建，避免频繁分组带来的 jank
  List<TaskHubTimelineRow> _buildTimelineRows(
    List<ReviewTaskTimelineItemEntity> items,
  ) {
    if (items.isEmpty) return const <TaskHubTimelineRow>[];

    final rows = <TaskHubTimelineRow>[];
    DateTime? currentDay;
    int? lastTaskRowIndex;

    for (final item in items) {
      final day = YikeDateUtils.atStartOfDay(item.occurredAt);

      if (currentDay == null || !YikeDateUtils.isSameDay(currentDay, day)) {
        // 当进入新的日期分组时，回填上一组最后一条任务的 isLastInGroup 标记。
        if (lastTaskRowIndex != null) {
          final last = rows[lastTaskRowIndex];
          if (last is TaskHubTimelineTaskRow) {
            rows[lastTaskRowIndex] = last.copyWith(isLastInGroup: true);
          }
        }

        currentDay = day;
        rows.add(TaskHubTimelineHeaderRow(day: day));
      }

      rows.add(TaskHubTimelineTaskRow.fromEntity(item, isLastInGroup: false));
      lastTaskRowIndex = rows.length - 1;
    }

    // 最后一组收尾。
    if (lastTaskRowIndex != null) {
      final last = rows[lastTaskRowIndex];
      if (last is TaskHubTimelineTaskRow) {
        rows[lastTaskRowIndex] = last.copyWith(isLastInGroup: true);
      }
    }

    return rows;
  }
}

/// 任务中心 Provider。
final taskHubProvider = StateNotifierProvider<TaskHubNotifier, TaskHubState>((
  ref,
) {
  return TaskHubNotifier(ref);
});
