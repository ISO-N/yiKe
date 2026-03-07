/// 文件用途：首页任务状态管理（Riverpod StateNotifier）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../domain/entities/review_task.dart';
import '../../infrastructure/widget/widget_service.dart';
import 'calendar_provider.dart';
import 'statistics_provider.dart';

/// 首页任务状态。
class HomeTasksState {
  /// 构造函数。
  const HomeTasksState({
    required this.isLoading,
    required this.todayPending,
    required this.todayCompleted,
    required this.todaySkipped,
    required this.overduePending,
    required this.completedCount,
    required this.totalCount,
    required this.learningItemCount,
    required this.isSelectionMode,
    required this.selectedTaskIds,
    required this.expandedTaskIds,
    required this.topicFilterId,
    required this.lastDoneOrSkippedRoundByLearningItemId,
    required this.nextReviewScheduledDateByLearningItemId,
    required this.nextReviewPreviewDisabledLearningItemIds,
    this.errorMessage,
  });

  final bool isLoading;
  final List<ReviewTaskViewEntity> todayPending;
  final List<ReviewTaskViewEntity> todayCompleted;
  final List<ReviewTaskViewEntity> todaySkipped;
  final List<ReviewTaskViewEntity> overduePending;
  final int completedCount;
  final int totalCount;

  /// 全库学习内容数量（用于空状态分层：冷启动/常规空态）。
  ///
  /// 口径：learning_items.is_deleted=0，不区分 is_mock_data。
  final int learningItemCount;
  final bool isSelectionMode;
  final Set<int> selectedTaskIds;

  /// 首页任务卡片展开状态（用于查看备注/标签详情与撤销按钮）。
  final Set<int> expandedTaskIds;

  /// 主题筛选（可选，F1.6）。
  ///
  /// 说明：当不为空时，仅展示属于该主题的任务。
  final int? topicFilterId;

  /// done/skipped 列表中，每个学习内容的“当前已完成/已跳过的最大轮次”。
  ///
  /// 说明：
  /// - 用于避免同一学习内容在一天内出现多条 done/skipped 时，重复/错误展示“下次复习”
  /// - UI 仅在 task.reviewRound == lastRound 时展示预览行
  final Map<int, int> lastDoneOrSkippedRoundByLearningItemId;

  /// done/skipped 卡片「下次复习」计划日期（key=learningItemId）。
  ///
  /// 说明：
  /// - 该 Map 仅用于展示，不修改 ReviewTaskViewEntity
  /// - value 为 null 表示不存在下一轮任务（视为已完成全部轮次）
  final Map<int, DateTime?> nextReviewScheduledDateByLearningItemId;

  /// done/skipped 卡片“下次复习”禁用集合（下一轮被手动禁用）。
  ///
  /// 说明：命中该集合时应隐藏预览行，避免与配置口径冲突。
  final Set<int> nextReviewPreviewDisabledLearningItemIds;
  final String? errorMessage;

  factory HomeTasksState.initial() => const HomeTasksState(
    isLoading: true,
    todayPending: [],
    todayCompleted: [],
    todaySkipped: [],
    overduePending: [],
    completedCount: 0,
    totalCount: 0,
    learningItemCount: 0,
    isSelectionMode: false,
    selectedTaskIds: {},
    expandedTaskIds: {},
    topicFilterId: null,
    lastDoneOrSkippedRoundByLearningItemId: {},
    nextReviewScheduledDateByLearningItemId: {},
    nextReviewPreviewDisabledLearningItemIds: {},
  );

  HomeTasksState copyWith({
    bool? isLoading,
    List<ReviewTaskViewEntity>? todayPending,
    List<ReviewTaskViewEntity>? todayCompleted,
    List<ReviewTaskViewEntity>? todaySkipped,
    List<ReviewTaskViewEntity>? overduePending,
    int? completedCount,
    int? totalCount,
    int? learningItemCount,
    bool? isSelectionMode,
    Set<int>? selectedTaskIds,
    Set<int>? expandedTaskIds,
    int? topicFilterId,
    Map<int, int>? lastDoneOrSkippedRoundByLearningItemId,
    Map<int, DateTime?>? nextReviewScheduledDateByLearningItemId,
    Set<int>? nextReviewPreviewDisabledLearningItemIds,
    String? errorMessage,
  }) {
    return HomeTasksState(
      isLoading: isLoading ?? this.isLoading,
      todayPending: todayPending ?? this.todayPending,
      todayCompleted: todayCompleted ?? this.todayCompleted,
      todaySkipped: todaySkipped ?? this.todaySkipped,
      overduePending: overduePending ?? this.overduePending,
      completedCount: completedCount ?? this.completedCount,
      totalCount: totalCount ?? this.totalCount,
      learningItemCount: learningItemCount ?? this.learningItemCount,
      isSelectionMode: isSelectionMode ?? this.isSelectionMode,
      selectedTaskIds: selectedTaskIds ?? this.selectedTaskIds,
      expandedTaskIds: expandedTaskIds ?? this.expandedTaskIds,
      topicFilterId: topicFilterId ?? this.topicFilterId,
      lastDoneOrSkippedRoundByLearningItemId:
          lastDoneOrSkippedRoundByLearningItemId ??
          this.lastDoneOrSkippedRoundByLearningItemId,
      nextReviewScheduledDateByLearningItemId:
          nextReviewScheduledDateByLearningItemId ??
          this.nextReviewScheduledDateByLearningItemId,
      nextReviewPreviewDisabledLearningItemIds:
          nextReviewPreviewDisabledLearningItemIds ??
          this.nextReviewPreviewDisabledLearningItemIds,
      errorMessage: errorMessage,
    );
  }
}

/// 首页任务 Notifier。
class HomeTasksNotifier extends StateNotifier<HomeTasksState> {
  /// 构造函数。
  HomeTasksNotifier(this._ref) : super(HomeTasksState.initial());

  final Ref _ref;

  /// 加载首页数据。
  ///
  /// 返回值：Future（无返回值）。
  /// 异常：异常会捕获并写入 [HomeTasksState.errorMessage]。
  Future<void> load() async {
    // 说明：Provider 被 invalidate/dispose 后，异步任务仍可能回调，此处统一用 mounted 防止“已释放后写 state”。
    if (!mounted) return;
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final useCase = _ref.read(getHomeTasksUseCaseProvider);
      final completedUseCase = _ref.read(getTodayCompletedTasksUseCaseProvider);
      final skippedUseCase = _ref.read(getTodaySkippedTasksUseCaseProvider);

      final resultFuture = useCase.execute();
      final completedFuture = completedUseCase.execute();
      final skippedFuture = skippedUseCase.execute();

      final result = await resultFuture;
      if (!mounted) return;
      final todayCompleted = await completedFuture;
      if (!mounted) return;
      final todaySkipped = await skippedFuture;
      if (!mounted) return;

      // v1.1.0：空状态分层所需的“全库学习内容数量”（仅在待复习列表为空时查询）。
      int learningItemCount = state.learningItemCount;

      final topicId = state.topicFilterId;
      var pendingToday = result.todayPending;
      var pendingOverdue = result.overduePending;
      var filteredCompleted = todayCompleted;
      var filteredSkipped = todaySkipped;
      var finalCompletedCount = result.completedCount;
      var finalTotalCount = result.totalCount;
      if (topicId == null) {
        // 无主题筛选：直接使用用例结果。
      } else {
        // v2.1：按主题筛选任务。
        final topicRepo = _ref.read(learningTopicRepositoryProvider);
        final itemIds = (await topicRepo.getItemIdsByTopicId(topicId)).toSet();
        if (!mounted) return;

        pendingToday = result.todayPending
            .where((t) => itemIds.contains(t.learningItemId))
            .toList();
        pendingOverdue = result.overduePending
            .where((t) => itemIds.contains(t.learningItemId))
            .toList();
        filteredCompleted = todayCompleted
            .where((t) => itemIds.contains(t.learningItemId))
            .toList();
        filteredSkipped = todaySkipped
            .where((t) => itemIds.contains(t.learningItemId))
            .toList();

        // 进度统计：重新按主题口径统计今日完成率（done/(done+pending)）。
        final repo = _ref.read(reviewTaskRepositoryProvider);
        final all = await repo.getTasksByDate(DateTime.now());
        if (!mounted) return;
        final filtered = all.where((t) => itemIds.contains(t.learningItemId));
        final completedCount = filtered
            .where((t) => t.status == ReviewTaskStatus.done)
            .length;
        final totalCount = filtered
            .where((t) => t.status != ReviewTaskStatus.skipped)
            .length;

        // 主题筛选模式：覆盖用例统计口径。
        // 说明：保持“完成率口径”为 done/(done+pending)，跳过不计入 total。
        finalCompletedCount = completedCount;
        finalTotalCount = totalCount;
      }

      final pendingEmpty = pendingToday.isEmpty && pendingOverdue.isEmpty;
      if (pendingEmpty) {
        learningItemCount = await _ref
            .read(learningItemDaoProvider)
            .getLearningItemCount();
        if (!mounted) return;
      }

      // v1.1.0：复习间隔预览增强（策略 A：批量查询下一轮计划日期）。
      final doneOrSkipped = <ReviewTaskViewEntity>[
        ...filteredCompleted,
        ...filteredSkipped,
      ];
      final lastRoundByItem = <int, int>{};
      for (final t in doneOrSkipped) {
        final prev = lastRoundByItem[t.learningItemId];
        if (prev == null || t.reviewRound > prev) {
          lastRoundByItem[t.learningItemId] = t.reviewRound;
        }
      }

      final nextPreviewDisabled = <int>{};
      final nextScheduledDate = <int, DateTime?>{};

      if (lastRoundByItem.isNotEmpty) {
        // 口径：下一轮是否启用，按“复习间隔配置”判断（下一轮禁用时 UI 必须隐藏预览行）。
        final settingsRepo = _ref.read(settingsRepositoryProvider);
        final configs = await settingsRepo.getReviewIntervalConfigs();
        if (!mounted) return;
        final maxConfiguredRound = configs.fold<int>(
          0,
          (max, c) => c.round > max ? c.round : max,
        );
        final enabledRounds = configs
            .where((c) => c.enabled)
            .map((c) => c.round)
            .toSet();

        final targets = <int, int>{};
        for (final e in lastRoundByItem.entries) {
          final nextRound = e.value + 1;
          if (nextRound > maxConfiguredRound) {
            // 已超过配置最大轮次：视为已完成全部轮次（直接置空）。
            nextScheduledDate[e.key] = null;
            continue;
          }
          if (!enabledRounds.contains(nextRound)) {
            // 下一轮被手动禁用：按 spec 禁用展示。
            nextPreviewDisabled.add(e.key);
            continue;
          }
          targets[e.key] = nextRound;
        }

        if (targets.isNotEmpty) {
          final dao = _ref.read(reviewTaskDaoProvider);
          final fetched = await dao.getScheduledDatesByLearningItemAndRounds(
            targets,
          );
          if (!mounted) return;
          for (final e in targets.entries) {
            // 若未命中，按 spec 视为“无下一轮任务”（已完成全部轮次）。
            nextScheduledDate[e.key] = fetched[e.key];
          }
        }
      }

      // 统一落盘 state（避免中途 setState 导致 UI 抖动）。
      if (!mounted) return;
      state = state.copyWith(
        isLoading: false,
        todayPending: pendingToday,
        todayCompleted: filteredCompleted,
        todaySkipped: filteredSkipped,
        overduePending: pendingOverdue,
        completedCount: finalCompletedCount,
        totalCount: finalTotalCount,
        learningItemCount: learningItemCount,
        lastDoneOrSkippedRoundByLearningItemId: lastRoundByItem,
        nextReviewScheduledDateByLearningItemId: nextScheduledDate,
        nextReviewPreviewDisabledLearningItemIds: nextPreviewDisabled,
      );

      // 同步桌面小组件数据（v1.0 Android 展示）。
      await _syncWidget();
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 展开/收起任务卡片详情区域。
  void toggleExpanded(int taskId) {
    final next = Set<int>.from(state.expandedTaskIds);
    if (next.contains(taskId)) {
      next.remove(taskId);
    } else {
      next.add(taskId);
    }
    state = state.copyWith(expandedTaskIds: next);
  }

  /// 开关选择模式（用于批量完成/跳过）。
  void toggleSelectionMode() {
    if (state.isSelectionMode) {
      state = state.copyWith(isSelectionMode: false, selectedTaskIds: <int>{});
    } else {
      state = state.copyWith(isSelectionMode: true, selectedTaskIds: <int>{});
    }
  }

  /// 设置主题筛选（null 表示全部）。
  Future<void> setTopicFilter(int? topicId) async {
    state = state.copyWith(topicFilterId: topicId);
    await load();
  }

  /// 切换某任务的选中状态。
  void toggleSelected(int taskId) {
    final next = Set<int>.from(state.selectedTaskIds);
    if (next.contains(taskId)) {
      next.remove(taskId);
    } else {
      next.add(taskId);
    }
    state = state.copyWith(selectedTaskIds: next);
  }

  /// 完成单个任务。
  Future<void> completeTask(int taskId) async {
    final useCase = _ref.read(completeReviewTaskUseCaseProvider);
    await useCase.execute(taskId);
    _invalidateRelatedPages();
    await load();
  }

  /// 完成单个任务（不刷新列表）。
  ///
  /// 说明：
  /// - 用于 Dismissible 左滑操作：先完成数据写入，等待滑出动画结束后再由 UI 触发 load()
  /// - 失败时会抛出异常，由上层统一提示“操作失败，请重试”
  Future<void> completeTaskWithoutReload(int taskId) async {
    final useCase = _ref.read(completeReviewTaskUseCaseProvider);
    await useCase.execute(taskId);
    _invalidateRelatedPages();
  }

  /// 跳过单个任务。
  Future<void> skipTask(int taskId) async {
    final useCase = _ref.read(skipReviewTaskUseCaseProvider);
    await useCase.execute(taskId);
    _invalidateRelatedPages();
    await load();
  }

  /// 跳过单个任务（不刷新列表）。
  ///
  /// 说明：同 [completeTaskWithoutReload]，用于左滑操作的“失败回滚 + 原生滑出”体验。
  Future<void> skipTaskWithoutReload(int taskId) async {
    final useCase = _ref.read(skipReviewTaskUseCaseProvider);
    await useCase.execute(taskId);
    _invalidateRelatedPages();
  }

  /// 批量完成所选任务。
  Future<void> completeSelected() async {
    final ids = state.selectedTaskIds.toList();
    if (ids.isEmpty) return;
    final useCase = _ref.read(completeReviewTaskUseCaseProvider);
    await useCase.executeBatch(ids);
    state = state.copyWith(isSelectionMode: false, selectedTaskIds: <int>{});
    _invalidateRelatedPages();
    await load();
  }

  /// 批量跳过所选任务。
  Future<void> skipSelected() async {
    final ids = state.selectedTaskIds.toList();
    if (ids.isEmpty) return;
    final useCase = _ref.read(skipReviewTaskUseCaseProvider);
    await useCase.executeBatch(ids);
    state = state.copyWith(isSelectionMode: false, selectedTaskIds: <int>{});
    _invalidateRelatedPages();
    await load();
  }

  /// 撤销任务状态（done/skipped → pending）。
  Future<void> undoTaskStatus(int taskId) async {
    final useCase = _ref.read(undoTaskStatusUseCaseProvider);
    await useCase.execute(taskId);
    _invalidateRelatedPages();
    await load();
  }

  void _invalidateRelatedPages() {
    // 撤销/完成/跳过会影响日历圆点与统计口径，因此需要主动刷新相关页面状态。
    _ref.invalidate(calendarProvider);
    _ref.invalidate(statisticsProvider);
  }

  Future<void> _syncWidget() async {
    try {
      final repo = _ref.read(reviewTaskRepositoryProvider);
      final allToday = await repo.getTasksByDate(DateTime.now());
      final tasks = allToday
          .map(
            (t) => WidgetTaskItem(title: t.title, status: t.status.toDbValue()),
          )
          .toList();

      await WidgetService.updateWidgetData(
        totalCount: state.totalCount,
        completedCount: state.completedCount,
        pendingCount: tasks.where((t) => t.status == 'pending').length,
        tasks: tasks,
      );
    } catch (_) {
      // 小组件同步失败不应影响主流程（如桌面无小组件、插件不可用）。
    }
  }
}

/// 首页任务 Provider。
final homeTasksProvider =
    StateNotifierProvider<HomeTasksNotifier, HomeTasksState>((ref) {
      final notifier = HomeTasksNotifier(ref);
      // 首次创建时加载数据。
      notifier.load();
      return notifier;
    });
