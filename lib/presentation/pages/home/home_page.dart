/// 文件用途：首页（今日复习任务），展示今日/逾期任务并支持完成、跳过与批量操作。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_settings/app_settings.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_strings.dart';
import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/date_utils.dart';
import '../../../core/utils/responsive_utils.dart';
import '../../../di/providers.dart';
import '../../../domain/entities/app_settings.dart';
import '../../../domain/entities/learning_topic.dart';
import '../../../domain/entities/review_task.dart';
import '../../widgets/glass_card.dart';
import '../../widgets/gradient_background.dart';
import '../../widgets/completion_animation.dart';
import '../../providers/home_tasks_provider.dart';
import '../../providers/home_task_filter_provider.dart';
import '../../providers/home_task_tab_provider.dart';
import '../../providers/notification_permission_provider.dart';
import '../../providers/search_provider.dart';
import '../../providers/settings_provider.dart';
import '../../providers/sync_provider.dart';
import '../../providers/task_hub_provider.dart';
import '../../providers/task_filter_provider.dart';
import '../../providers/ui_preferences_provider.dart';
import '../../widgets/review_progress.dart';
import '../../widgets/search_bar.dart';
import '../../widgets/task_filter_bar.dart';
import '../../widgets/task_context_menu.dart';
import '../tasks/widgets/task_hub_timeline_list.dart';
import 'widgets/home_tab_switcher.dart';

class HomePage extends ConsumerStatefulWidget {
  /// 首页（今日复习）。
  ///
  /// 返回值：页面 Widget。
  /// 异常：无。
  const HomePage({super.key});

  @override
  ConsumerState<HomePage> createState() => _HomePageState();
}

class _HomePageState extends ConsumerState<HomePage> {
  // 防止弹窗在一次会话内重复出现。
  bool _permissionDialogShown = false;

  // tab=all 使用独立滚动控制器，以支持“接近底部自动加载下一页”的游标分页。
  final ScrollController _allTasksScrollController = ScrollController();

  // v1.1.0：按钮/菜单触发完成后，为“缩放+淡出”移除动效保留的临时占位任务。
  //
  // 说明：
  // - 采用“操作先行”：数据库操作与列表刷新已完成后，再插入占位卡片播放动画
  // - 动画结束后由回调移除占位卡片，避免与真实数据产生重复展示
  final Set<int> _removingTaskIds = <int>{};
  final Map<int, _RemovingTaskSnapshot> _removingTaskSnapshots =
      <int, _RemovingTaskSnapshot>{};

  @override
  void initState() {
    super.initState();
    _allTasksScrollController.addListener(_onAllTasksScroll);
  }

  @override
  void dispose() {
    _allTasksScrollController.removeListener(_onAllTasksScroll);
    _allTasksScrollController.dispose();
    super.dispose();
  }

  void _onAllTasksScroll() {
    // 游标分页：接近底部时自动加载下一页（仅 tab=all 时会挂载该 controller）。
    if (!_allTasksScrollController.hasClients) return;
    final position = _allTasksScrollController.position;
    if (position.maxScrollExtent <= 0) return;
    if (position.pixels >= position.maxScrollExtent - 240) {
      ref.read(taskHubProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    final disableAnimations = MediaQuery.of(context).disableAnimations;

    final settingsState = ref.watch(settingsProvider);
    final permissionAsync = ref.watch(notificationPermissionProvider);
    final syncUi = ref.watch(syncControllerProvider);
    final tab = ref.watch(homeTaskTabProvider);
    final blurEnabled = ref.watch(taskListBlurEnabledProvider);

    // 首页默认展示“今日”任务；tab=all 时会额外复用 taskHubProvider 的逻辑展示全量任务。
    final state = ref.watch(homeTasksProvider);
    final notifier = ref.read(homeTasksProvider.notifier);
    final homeFilter = ref.watch(homeTaskFilterProvider);

    final TaskHubState? hubState = tab == HomeTaskTab.all
        ? ref.watch(taskHubProvider)
        : null;
    final TaskHubNotifier? hubNotifier = tab == HomeTaskTab.all
        ? ref.read(taskHubProvider.notifier)
        : null;

    final statusCounts = TaskStatusCounts(
      all:
          state.todayPending.length +
          state.overduePending.length +
          state.todayCompleted.length +
          state.todaySkipped.length,
      pending: state.todayPending.length + state.overduePending.length,
      done: state.todayCompleted.length,
      skipped: state.todaySkipped.length,
    );

    // 首页仅对“待复习”提供批量选择能力，已完成/已跳过列表默认禁止批量操作。
    final effectiveSelectionMode =
        tab == HomeTaskTab.today && homeFilter == ReviewTaskFilter.pending
        ? state.isSelectionMode
        : false;

    void showSnack(String text) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    }

    /// 统一任务操作执行器（v1.1.0：失败提示统一为“操作失败，请重试”）。
    ///
    /// 返回值：true=成功；false=失败（不抛出异常，便于 Dismissible 回滚）。
    Future<bool> runAction(Future<void> Function() action, {String? ok}) async {
      try {
        await action();
        if (ok != null) showSnack(ok);
        return true;
      } catch (_) {
        // 失败策略：不刷新列表，保持当前 UI 状态一致。
        showSnack('操作失败，请重试');
        return false;
      }
    }

    void onRemovingAnimationCompleted(int taskId) {
      if (!mounted) return;
      setState(() {
        _removingTaskIds.remove(taskId);
        _removingTaskSnapshots.remove(taskId);
      });
    }

    _RemovingTaskSnapshot? resolvePendingSnapshot(int taskId) {
      final overdueIndex = state.overduePending.indexWhere(
        (t) => t.taskId == taskId,
      );
      if (overdueIndex >= 0) {
        return _RemovingTaskSnapshot(
          task: state.overduePending[overdueIndex],
          section: _RemovingTaskSection.overdue,
          index: overdueIndex,
        );
      }

      final todayIndex = state.todayPending.indexWhere(
        (t) => t.taskId == taskId,
      );
      if (todayIndex >= 0) {
        return _RemovingTaskSnapshot(
          task: state.todayPending[todayIndex],
          section: _RemovingTaskSection.today,
          index: todayIndex,
        );
      }
      return null;
    }

    Future<bool> completeFromButtonOrMenu(int taskId) async {
      // 先捕获“完成前”的卡片快照，用于刷新后插入占位卡片播放移除动效。
      final snapshot = disableAnimations
          ? null
          : resolvePendingSnapshot(taskId);

      final ok = await runAction(
        () => notifier.completeTask(taskId),
        ok: '已完成',
      );
      if (!ok) return false;

      // 动画禁用/非待复习列表触发：不插入占位卡片。
      if (disableAnimations || snapshot == null) return true;

      if (!mounted) return true;
      setState(() {
        _removingTaskIds.add(taskId);
        _removingTaskSnapshots[taskId] = snapshot;
      });
      return true;
    }

    Future<bool> completeFromSwipe(int taskId) {
      // 左滑：保持原生滑出效果，不叠加缩放淡出动画。
      return runAction(
        () => notifier.completeTaskWithoutReload(taskId),
        ok: '已完成',
      );
    }

    Future<bool> skipTask(int taskId) {
      return runAction(() => notifier.skipTask(taskId), ok: '已跳过');
    }

    Future<bool> skipFromSwipe(int taskId) {
      return runAction(() => notifier.skipTaskWithoutReload(taskId), ok: '已跳过');
    }

    List<ReviewTaskViewEntity> withRemovingGhosts(
      List<ReviewTaskViewEntity> tasks,
      _RemovingTaskSection section,
    ) {
      if (_removingTaskSnapshots.isEmpty || _removingTaskIds.isEmpty) {
        return tasks;
      }

      final list = List<ReviewTaskViewEntity>.from(tasks);
      final ghosts =
          _removingTaskSnapshots.entries
              .where(
                (e) =>
                    _removingTaskIds.contains(e.key) &&
                    e.value.section == section,
              )
              .map((e) => e.value)
              .toList()
            ..sort((a, b) => a.index.compareTo(b.index));

      for (final g in ghosts) {
        // 防止极端情况（刷新异常）导致重复插入。
        if (list.any((t) => t.taskId == g.task.taskId)) continue;
        final insertIndex = g.index.clamp(0, list.length);
        list.insert(insertIndex, g.task);
      }
      return list;
    }

    // v1.1.0：在列表刷新后插入“占位卡片”播放移除动效，同时避免 done/skipped 列表重复出现。
    final overduePendingUi = withRemovingGhosts(
      state.overduePending,
      _RemovingTaskSection.overdue,
    );
    final todayPendingUi = withRemovingGhosts(
      state.todayPending,
      _RemovingTaskSection.today,
    );
    final todayCompletedUi = state.todayCompleted
        .where((t) => !_removingTaskIds.contains(t.taskId))
        .toList();
    final todaySkippedUi = state.todaySkipped
        .where((t) => !_removingTaskIds.contains(t.taskId))
        .toList();

    Future<void> confirmUndo(int taskId) async {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text('撤销任务状态？'),
            content: const Text('该任务将恢复为待复习状态，是否确认撤销？'),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: const Text('确认撤销'),
              ),
            ],
          );
        },
      );

      if (confirmed != true) return;
      await runAction(() => notifier.undoTaskStatus(taskId), ok: '已撤销');
    }

    final searchQuery = ref.watch(learningSearchQueryProvider);
    final searchQueryNotifier = ref.read(learningSearchQueryProvider.notifier);
    final searchKeyword = searchQuery.trim();
    final searchAsync = searchKeyword.isEmpty
        ? null
        : ref.watch(learningSearchResultsProvider);

    final permission = permissionAsync.valueOrNull;
    final shouldPromptPermission =
        !settingsState.isLoading &&
        settingsState.settings.notificationsEnabled &&
        !settingsState.settings.notificationPermissionGuideDismissed &&
        permission == NotificationPermissionState.disabled;

    if (shouldPromptPermission && !_permissionDialogShown) {
      _permissionDialogShown = true;
      WidgetsBinding.instance.addPostFrameCallback((_) async {
        if (!mounted) return;
        await _showNotificationPermissionDialog(
          context: context,
          ref: ref,
          settings: settingsState.settings,
        );
      });
    }

    Future<void> refresh() {
      // 按当前 tab 选择刷新目标：
      // - today：刷新首页（今日/逾期）列表
      // - all：刷新任务中心时间线
      if (tab == HomeTaskTab.all) {
        final n = hubNotifier;
        return n == null ? Future.value() : n.refresh();
      }
      return notifier.load();
    }

    void changeTab(HomeTaskTab next) {
      // 使用本地状态管理 Tab，避免路由重建触发全量页面刷新。
      ref.read(homeTaskTabProvider.notifier).state = next;
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(AppStrings.todayReview),
        actions: [
          IconButton(
            tooltip: _syncTooltip(syncUi.state),
            onPressed: () => context.push('/settings/sync'),
            icon: Icon(
              _syncIcon(syncUi.state),
              color: _syncColor(context, syncUi.state),
            ),
          ),
          if (tab == HomeTaskTab.today)
            IconButton(
              tooltip: state.topicFilterId == null
                  ? '筛选：全部'
                  : '筛选：主题 #${state.topicFilterId}',
              onPressed: state.isLoading ? null : () => _showTopicFilterSheet(),
              icon: Icon(
                Icons.filter_list,
                color: state.topicFilterId == null
                    ? null
                    : Theme.of(context).colorScheme.primary,
              ),
            ),
          IconButton(
            tooltip: '刷新',
            onPressed:
                (tab == HomeTaskTab.all
                        ? (hubState?.isLoading ?? false)
                        : state.isLoading) ==
                    true
                ? null
                : () => refresh(),
            icon: const Icon(Icons.refresh),
          ),
          if (tab == HomeTaskTab.today)
            TextButton(
              onPressed:
                  state.isLoading || homeFilter != ReviewTaskFilter.pending
                  ? null
                  : () => notifier.toggleSelectionMode(),
              child: Text(effectiveSelectionMode ? '完成' : '批量'),
            ),
        ],
      ),
      body: GradientBackground(
        child: SafeArea(
          child: RefreshIndicator(
            onRefresh: refresh,
            // 性能优化（Phase 1）：tab=all 使用 CustomScrollView + SliverList 虚拟化时间线，避免一次性构建全部卡片。
            child: (tab == HomeTaskTab.all && hubState != null && hubNotifier != null)
                ? CustomScrollView(
                    controller: _allTasksScrollController,
                    slivers: [
                      SliverPadding(
                        padding: const EdgeInsets.fromLTRB(
                          AppSpacing.lg,
                          AppSpacing.lg,
                          AppSpacing.lg,
                          0,
                        ),
                        sliver: SliverToBoxAdapter(
                          child: LearningSearchBar(
                            query: searchQuery,
                            enabled: !(hubState.isLoading),
                            onChanged: (v) => searchQueryNotifier.state = v,
                            onClear: () => searchQueryNotifier.state = '',
                          ),
                        ),
                      ),
                      if (searchKeyword.isNotEmpty)
                        SliverPadding(
                          padding: const EdgeInsets.fromLTRB(
                            AppSpacing.lg,
                            AppSpacing.md,
                            AppSpacing.lg,
                            0,
                          ),
                          sliver: SliverToBoxAdapter(
                            child: _SearchResultsCard(
                              keyword: searchKeyword,
                              results: searchAsync,
                              onTapItem: (id) => context.push('/items/$id'),
                            ),
                          ),
                        ),
                      const SliverToBoxAdapter(
                        child: SizedBox(height: AppSpacing.lg),
                      ),
                      SliverPadding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.lg,
                        ),
                        sliver: SliverToBoxAdapter(
                          child: HomeTabSwitcher(
                            tab: tab,
                            onChanged: changeTab,
                          ),
                        ),
                      ),
                      const SliverToBoxAdapter(
                        child: SizedBox(height: AppSpacing.lg),
                      ),
                      SliverPadding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.lg,
                        ),
                        sliver: SliverToBoxAdapter(
                          child: TaskFilterBar(
                            filter: hubState.filter,
                            counts: hubState.counts,
                            onChanged: (next) => hubNotifier.setFilter(next),
                          ),
                        ),
                      ),
                      const SliverToBoxAdapter(
                        child: SizedBox(height: AppSpacing.lg),
                      ),
                      SliverPadding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: AppSpacing.lg,
                        ),
                        sliver: TaskHubTimelineSliver(
                          state: hubState,
                          notifier: hubNotifier,
                          blurEnabled: blurEnabled,
                          emptyState: state.isLoading
                              ? null
                              : _EmptyState(
                                  learningItemCount: state.learningItemCount,
                                ),
                        ),
                      ),
                      const SliverToBoxAdapter(child: SizedBox(height: 96)),
                    ],
                  )
                : ListView(
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    children: [
                      LearningSearchBar(
                        query: searchQuery,
                        enabled: tab == HomeTaskTab.all
                            ? !(hubState?.isLoading ?? false)
                            : !state.isLoading,
                        onChanged: (v) => searchQueryNotifier.state = v,
                        onClear: () => searchQueryNotifier.state = '',
                      ),
                      if (searchKeyword.isNotEmpty) ...[
                        const SizedBox(height: AppSpacing.md),
                        _SearchResultsCard(
                          keyword: searchKeyword,
                          results: searchAsync,
                          onTapItem: (id) => context.push('/items/$id'),
                        ),
                      ],
                      const SizedBox(height: AppSpacing.lg),
                      HomeTabSwitcher(tab: tab, onChanged: changeTab),
                      const SizedBox(height: AppSpacing.lg),
                      if (tab == HomeTaskTab.all &&
                          hubState != null &&
                          hubNotifier != null) ...[
                        TaskFilterBar(
                          filter: hubState.filter,
                          counts: hubState.counts,
                          onChanged: (next) => hubNotifier.setFilter(next),
                        ),
                        const SizedBox(height: AppSpacing.lg),
                        TaskHubTimelineSliver(
                          state: hubState,
                          notifier: hubNotifier,
                          blurEnabled: blurEnabled,
                          emptyState: state.isLoading
                              ? null
                              : _EmptyState(
                                  learningItemCount: state.learningItemCount,
                                ),
                        ),
                        const SizedBox(height: 96),
                      ] else ...[
                  const _DateHeader(),
                  const SizedBox(height: AppSpacing.lg),
                  const ReviewProgressWidget(),
                  const SizedBox(height: AppSpacing.lg),
                  TaskFilterBar(
                    filter: homeFilter,
                    counts: statusCounts,
                    onChanged: (next) {
                      ref.read(homeTaskFilterProvider.notifier).state = next;
                    },
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  if (state.errorMessage != null) ...[
                    GlassCard(
                      child: Padding(
                        padding: const EdgeInsets.all(AppSpacing.lg),
                        child: Text(
                          '加载失败：${state.errorMessage}',
                          style: const TextStyle(color: AppColors.error),
                        ),
                      ),
                    ),
                    const SizedBox(height: AppSpacing.lg),
                  ],
                  if (state.isLoading) ...[
                    const Center(
                      child: Padding(
                        padding: EdgeInsets.all(24),
                        child: CircularProgressIndicator(),
                      ),
                    ),
                  ] else ...[
                    if ((homeFilter == ReviewTaskFilter.pending ||
                            homeFilter == ReviewTaskFilter.all) &&
                        state.todayPending.length +
                                state.overduePending.length >
                            20) ...[
                      GlassCard(
                        child: Padding(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          child: Row(
                            children: [
                              const Icon(
                                Icons.warning_amber_rounded,
                                color: AppColors.warning,
                              ),
                              const SizedBox(width: AppSpacing.sm),
                              Expanded(
                                child: Text(
                                  '今日任务较多，建议优先完成逾期任务。',
                                  style: AppTypography.bodySecondary(context),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: AppSpacing.lg),
                    ],
                    if (homeFilter == ReviewTaskFilter.pending) ...[
                      if (overduePendingUi.isNotEmpty) ...[
                        _SectionHeader(
                          title: '逾期任务',
                          subtitle: '优先处理红色逾期任务，避免堆积',
                          color: AppColors.warning,
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        _TaskGrid(
                          tasks: overduePendingUi,
                          isOverdue: true,
                          selectionMode: effectiveSelectionMode,
                          selectedTaskIds: state.selectedTaskIds,
                          expandedTaskIds: state.expandedTaskIds,
                          lastDoneOrSkippedRoundByLearningItemId:
                              state.lastDoneOrSkippedRoundByLearningItemId,
                          nextReviewScheduledDateByLearningItemId:
                              state.nextReviewScheduledDateByLearningItemId,
                          nextReviewPreviewDisabledLearningItemIds:
                              state.nextReviewPreviewDisabledLearningItemIds,
                          removingTaskIds: _removingTaskIds,
                          disableAnimations: disableAnimations,
                          onRemovingAnimationCompleted:
                              onRemovingAnimationCompleted,
                          onSwipeDismissed: () => notifier.load(),
                          onToggleSelected: notifier.toggleSelected,
                          onToggleExpanded: notifier.toggleExpanded,
                          onComplete: completeFromButtonOrMenu,
                          onSwipeComplete: completeFromSwipe,
                          onSkip: skipTask,
                          onSwipeSkip: skipFromSwipe,
                        ),
                        const SizedBox(height: AppSpacing.lg),
                      ],
                      _SectionHeader(
                        title: '今日待复习',
                        subtitle: todayPendingUi.isEmpty ? '今天没有待复习任务' : null,
                        color: primary,
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      if (todayPendingUi.isEmpty && overduePendingUi.isEmpty)
                        _EmptyState(learningItemCount: state.learningItemCount)
                      else if (todayPendingUi.isEmpty)
                        const _EmptySectionHint(
                          text: AppStrings.emptyTodayTasks,
                        )
                      else
                        _TaskGrid(
                          tasks: todayPendingUi,
                          isOverdue: false,
                          selectionMode: effectiveSelectionMode,
                          selectedTaskIds: state.selectedTaskIds,
                          expandedTaskIds: state.expandedTaskIds,
                          lastDoneOrSkippedRoundByLearningItemId:
                              state.lastDoneOrSkippedRoundByLearningItemId,
                          nextReviewScheduledDateByLearningItemId:
                              state.nextReviewScheduledDateByLearningItemId,
                          nextReviewPreviewDisabledLearningItemIds:
                              state.nextReviewPreviewDisabledLearningItemIds,
                          removingTaskIds: _removingTaskIds,
                          disableAnimations: disableAnimations,
                          onRemovingAnimationCompleted:
                              onRemovingAnimationCompleted,
                          onSwipeDismissed: () => notifier.load(),
                          onToggleSelected: notifier.toggleSelected,
                          onToggleExpanded: notifier.toggleExpanded,
                          onComplete: completeFromButtonOrMenu,
                          onSwipeComplete: completeFromSwipe,
                          onSkip: skipTask,
                          onSwipeSkip: skipFromSwipe,
                        ),
                    ] else if (homeFilter == ReviewTaskFilter.done) ...[
                      _SectionHeader(
                        title: '今日已完成',
                        subtitle: todayCompletedUi.isEmpty ? '今天还没有完成任务' : null,
                        color: AppColors.success,
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      if (todayCompletedUi.isEmpty)
                        const _EmptySectionHint(text: '暂无今日已完成任务')
                      else
                        _TaskGrid(
                          tasks: todayCompletedUi,
                          isOverdue: false,
                          selectionMode: false,
                          selectedTaskIds: const {},
                          expandedTaskIds: state.expandedTaskIds,
                          lastDoneOrSkippedRoundByLearningItemId:
                              state.lastDoneOrSkippedRoundByLearningItemId,
                          nextReviewScheduledDateByLearningItemId:
                              state.nextReviewScheduledDateByLearningItemId,
                          nextReviewPreviewDisabledLearningItemIds:
                              state.nextReviewPreviewDisabledLearningItemIds,
                          removingTaskIds: const {},
                          disableAnimations: disableAnimations,
                          onRemovingAnimationCompleted: (_) {},
                          onSwipeDismissed: () {},
                          onToggleSelected: (_) {},
                          onToggleExpanded: notifier.toggleExpanded,
                          onComplete: (_) async => true,
                          onSwipeComplete: (_) async => true,
                          onSkip: (_) async => true,
                          onSwipeSkip: (_) async => true,
                          onUndo: confirmUndo,
                        ),
                    ] else if (homeFilter == ReviewTaskFilter.skipped) ...[
                      _SectionHeader(
                        title: '今日已跳过',
                        subtitle: todaySkippedUi.isEmpty ? '今天还没有跳过任务' : null,
                        color: AppColors.warning,
                      ),
                      const SizedBox(height: AppSpacing.sm),
                      if (todaySkippedUi.isEmpty)
                        const _EmptySectionHint(text: '暂无今日已跳过任务')
                      else
                        _TaskGrid(
                          tasks: todaySkippedUi,
                          isOverdue: false,
                          selectionMode: false,
                          selectedTaskIds: const {},
                          expandedTaskIds: state.expandedTaskIds,
                          lastDoneOrSkippedRoundByLearningItemId:
                              state.lastDoneOrSkippedRoundByLearningItemId,
                          nextReviewScheduledDateByLearningItemId:
                              state.nextReviewScheduledDateByLearningItemId,
                          nextReviewPreviewDisabledLearningItemIds:
                              state.nextReviewPreviewDisabledLearningItemIds,
                          removingTaskIds: const {},
                          disableAnimations: disableAnimations,
                          onRemovingAnimationCompleted: (_) {},
                          onSwipeDismissed: () {},
                          onToggleSelected: (_) {},
                          onToggleExpanded: notifier.toggleExpanded,
                          onComplete: (_) async => true,
                          onSwipeComplete: (_) async => true,
                          onSkip: (_) async => true,
                          onSwipeSkip: (_) async => true,
                          onUndo: confirmUndo,
                        ),
                    ] else ...[
                      if (statusCounts.all == 0) ...[
                        _EmptyState(learningItemCount: state.learningItemCount),
                      ] else ...[
                        if (overduePendingUi.isNotEmpty) ...[
                          _SectionHeader(
                            title: '逾期任务',
                            subtitle: '优先处理红色逾期任务，避免堆积',
                            color: AppColors.warning,
                          ),
                          const SizedBox(height: AppSpacing.sm),
                          _TaskGrid(
                            tasks: overduePendingUi,
                            isOverdue: true,
                            selectionMode: false,
                            selectedTaskIds: const {},
                            expandedTaskIds: state.expandedTaskIds,
                            lastDoneOrSkippedRoundByLearningItemId:
                                state.lastDoneOrSkippedRoundByLearningItemId,
                            nextReviewScheduledDateByLearningItemId:
                                state.nextReviewScheduledDateByLearningItemId,
                            nextReviewPreviewDisabledLearningItemIds:
                                state.nextReviewPreviewDisabledLearningItemIds,
                            removingTaskIds: _removingTaskIds,
                            disableAnimations: disableAnimations,
                            onRemovingAnimationCompleted:
                                onRemovingAnimationCompleted,
                            onSwipeDismissed: () => notifier.load(),
                            onToggleSelected: (_) {},
                            onToggleExpanded: notifier.toggleExpanded,
                            onComplete: completeFromButtonOrMenu,
                            onSwipeComplete: completeFromSwipe,
                            onSkip: skipTask,
                            onSwipeSkip: skipFromSwipe,
                          ),
                          const SizedBox(height: AppSpacing.lg),
                        ],
                        _SectionHeader(
                          title: '今日待复习',
                          subtitle: todayPendingUi.isEmpty ? '今天没有待复习任务' : null,
                          color: primary,
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        if (todayPendingUi.isEmpty)
                          const _EmptySectionHint(
                            text: AppStrings.emptyTodayTasks,
                          )
                        else
                          _TaskGrid(
                            tasks: todayPendingUi,
                            isOverdue: false,
                            selectionMode: false,
                            selectedTaskIds: const {},
                            expandedTaskIds: state.expandedTaskIds,
                            lastDoneOrSkippedRoundByLearningItemId:
                                state.lastDoneOrSkippedRoundByLearningItemId,
                            nextReviewScheduledDateByLearningItemId:
                                state.nextReviewScheduledDateByLearningItemId,
                            nextReviewPreviewDisabledLearningItemIds:
                                state.nextReviewPreviewDisabledLearningItemIds,
                            removingTaskIds: _removingTaskIds,
                            disableAnimations: disableAnimations,
                            onRemovingAnimationCompleted:
                                onRemovingAnimationCompleted,
                            onSwipeDismissed: () => notifier.load(),
                            onToggleSelected: (_) {},
                            onToggleExpanded: notifier.toggleExpanded,
                            onComplete: completeFromButtonOrMenu,
                            onSwipeComplete: completeFromSwipe,
                            onSkip: skipTask,
                            onSwipeSkip: skipFromSwipe,
                          ),
                        const SizedBox(height: AppSpacing.lg),
                        _SectionHeader(
                          title: '今日已完成',
                          subtitle: todayCompletedUi.isEmpty
                              ? '今天还没有完成任务'
                              : null,
                          color: AppColors.success,
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        if (todayCompletedUi.isEmpty)
                          const _EmptySectionHint(text: '暂无今日已完成任务')
                        else
                          _TaskGrid(
                            tasks: todayCompletedUi,
                            isOverdue: false,
                            selectionMode: false,
                            selectedTaskIds: const {},
                            expandedTaskIds: state.expandedTaskIds,
                            lastDoneOrSkippedRoundByLearningItemId:
                                state.lastDoneOrSkippedRoundByLearningItemId,
                            nextReviewScheduledDateByLearningItemId:
                                state.nextReviewScheduledDateByLearningItemId,
                            nextReviewPreviewDisabledLearningItemIds:
                                state.nextReviewPreviewDisabledLearningItemIds,
                            removingTaskIds: const {},
                            disableAnimations: disableAnimations,
                            onRemovingAnimationCompleted: (_) {},
                            onSwipeDismissed: () {},
                            onToggleSelected: (_) {},
                            onToggleExpanded: notifier.toggleExpanded,
                            onComplete: (_) async => true,
                            onSwipeComplete: (_) async => true,
                            onSkip: (_) async => true,
                            onSwipeSkip: (_) async => true,
                            onUndo: confirmUndo,
                          ),
                        const SizedBox(height: AppSpacing.lg),
                        _SectionHeader(
                          title: '今日已跳过',
                          subtitle: todaySkippedUi.isEmpty ? '今天还没有跳过任务' : null,
                          color: AppColors.warning,
                        ),
                        const SizedBox(height: AppSpacing.sm),
                        if (todaySkippedUi.isEmpty)
                          const _EmptySectionHint(text: '暂无今日已跳过任务')
                        else
                          _TaskGrid(
                            tasks: todaySkippedUi,
                            isOverdue: false,
                            selectionMode: false,
                            selectedTaskIds: const {},
                            expandedTaskIds: state.expandedTaskIds,
                            lastDoneOrSkippedRoundByLearningItemId:
                                state.lastDoneOrSkippedRoundByLearningItemId,
                            nextReviewScheduledDateByLearningItemId:
                                state.nextReviewScheduledDateByLearningItemId,
                            nextReviewPreviewDisabledLearningItemIds:
                                state.nextReviewPreviewDisabledLearningItemIds,
                            removingTaskIds: const {},
                            disableAnimations: disableAnimations,
                            onRemovingAnimationCompleted: (_) {},
                            onSwipeDismissed: () {},
                            onToggleSelected: (_) {},
                            onToggleExpanded: notifier.toggleExpanded,
                            onComplete: (_) async => true,
                            onSwipeComplete: (_) async => true,
                            onSkip: (_) async => true,
                            onSwipeSkip: (_) async => true,
                            onUndo: confirmUndo,
                          ),
                      ],
                    ],
                    const SizedBox(height: 96),
                  ],
                ],
              ],
            ),
          ),
        ),
      ),
      bottomNavigationBar: effectiveSelectionMode
          ? _BatchActionBar(
              selectedCount: state.selectedTaskIds.length,
              onCompleteSelected: state.selectedTaskIds.isEmpty
                  ? null
                  : () async {
                      await runAction(notifier.completeSelected, ok: '已完成所选任务');
                    },
              onSkipSelected: state.selectedTaskIds.isEmpty
                  ? null
                  : () async {
                      await runAction(notifier.skipSelected, ok: '已跳过所选任务');
                    },
            )
          : null,
    );
  }

  String _syncTooltip(SyncState state) {
    switch (state) {
      case SyncState.disconnected:
        return '同步：未连接';
      case SyncState.connecting:
        return '同步：连接中…';
      case SyncState.connected:
        return '同步：已连接';
      case SyncState.syncing:
        return '同步：同步中…';
      case SyncState.synced:
        return '同步：同步完成';
      case SyncState.error:
        return '同步：失败（点击查看）';
    }
  }

  IconData _syncIcon(SyncState state) {
    switch (state) {
      case SyncState.disconnected:
        return Icons.cloud_off_outlined;
      case SyncState.connecting:
      case SyncState.syncing:
        return Icons.sync;
      case SyncState.connected:
      case SyncState.synced:
        return Icons.cloud_done_outlined;
      case SyncState.error:
        return Icons.error_outline;
    }
  }

  Color? _syncColor(BuildContext context, SyncState state) {
    switch (state) {
      case SyncState.connected:
      case SyncState.synced:
        return Colors.green;
      case SyncState.connecting:
      case SyncState.syncing:
        return Colors.blue;
      case SyncState.error:
        return Colors.red;
      case SyncState.disconnected:
        return Theme.of(context).colorScheme.onSurface.withAlpha(160);
    }
  }

  Future<void> _showTopicFilterSheet() async {
    final current = ref.read(homeTasksProvider).topicFilterId;
    List<LearningTopicEntity> topics = const [];
    try {
      topics = await ref.read(manageTopicUseCaseProvider).getAll();
    } catch (_) {
      // 主题加载失败时仍允许切回“全部”。
    }
    if (!mounted) return;

    final picked = await showModalBottomSheet<int>(
      context: context,
      showDragHandle: true,
      builder: (context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                title: const Text('全部'),
                trailing: current == null ? const Icon(Icons.check) : null,
                onTap: () => Navigator.of(context).pop(-1),
              ),
              const Divider(height: 1),
              if (topics.isEmpty)
                Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Text(
                    '暂无主题',
                    style: AppTypography.bodySecondary(context),
                  ),
                )
              else
                ...topics.map((t) {
                  return ListTile(
                    title: Text(t.name),
                    subtitle: (t.description ?? '').trim().isEmpty
                        ? null
                        : Text(
                            t.description!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                    trailing: current == t.id ? const Icon(Icons.check) : null,
                    onTap: () => Navigator.of(context).pop(t.id!),
                  );
                }),
            ],
          ),
        );
      },
    );

    if (picked == null) return;
    final next = picked == -1 ? null : picked;
    await ref.read(homeTasksProvider.notifier).setTopicFilter(next);
  }
}

Future<void> _showNotificationPermissionDialog({
  required BuildContext context,
  required WidgetRef ref,
  required AppSettingsEntity settings,
}) async {
  final settingsNotifier = ref.read(settingsProvider.notifier);

  await showDialog<void>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('开启通知，确保及时复习'),
        content: const Text(
          '检测到系统通知权限未开启。\n\n'
          '为了在每日提醒时间收到复习通知（v1.0 允许 ±30 分钟误差），请前往系统设置开启通知权限。',
        ),
        actions: [
          TextButton(
            onPressed: () async {
              await settingsNotifier.save(
                settings.copyWith(notificationPermissionGuideDismissed: true),
              );
              if (context.mounted) Navigator.of(context).pop();
            },
            child: const Text('不再提示'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('稍后'),
          ),
          FilledButton(
            onPressed: () async {
              await AppSettings.openAppSettings(
                type: AppSettingsType.notification,
              );
              ref.invalidate(notificationPermissionProvider);
              if (context.mounted) Navigator.of(context).pop();
            },
            child: const Text('去开启'),
          ),
        ],
      );
    },
  );
}

/// 搜索结果卡片（v3.1 F14.1）。
class _SearchResultsCard extends StatelessWidget {
  const _SearchResultsCard({
    required this.keyword,
    required this.results,
    required this.onTapItem,
  });

  final String keyword;
  final AsyncValue<List<LearningItemSearchResult>>? results;
  final ValueChanged<int> onTapItem;

  @override
  Widget build(BuildContext context) {
    final async = results;
    if (async == null) return const SizedBox.shrink();

    final isDark = Theme.of(context).brightness == Brightness.dark;
    final highlightBg = isDark
        ? AppColors.primaryLight.withValues(alpha: 0.22)
        : AppColors.primary.withValues(alpha: 0.18);

    final highlightStyle = AppTypography.body(
      context,
    ).copyWith(backgroundColor: highlightBg, fontWeight: FontWeight.w800);
    final normalTitle = AppTypography.body(
      context,
    ).copyWith(fontWeight: FontWeight.w700);
    final normalNote = AppTypography.bodySecondary(context);

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: async.when(
          loading: () => const Center(
            child: Padding(
              padding: EdgeInsets.all(12),
              child: CircularProgressIndicator(),
            ),
          ),
          error: (e, _) =>
              Text('搜索失败：$e', style: const TextStyle(color: AppColors.error)),
          data: (list) {
            if (list.isEmpty) {
              return Text(
                '未找到匹配结果',
                style: AppTypography.bodySecondary(context),
              );
            }

            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '搜索结果（最多 50 条） · ${list.length}',
                  style: AppTypography.bodySecondary(context),
                ),
                const SizedBox(height: AppSpacing.sm),
                for (final item in list) ...[
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: RichText(
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      text: buildHighlightedTextSpan(
                        text: item.title,
                        keyword: keyword,
                        normalStyle: normalTitle,
                        highlightStyle: highlightStyle,
                      ),
                    ),
                    subtitle: _subtitleOf(item) == null
                        ? null
                        : RichText(
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            text: buildHighlightedTextSpan(
                              text: _subtitleOf(item)!,
                              keyword: keyword,
                              normalStyle: normalNote,
                              highlightStyle: highlightStyle,
                            ),
                          ),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => onTapItem(item.id),
                  ),
                  const Divider(height: 1),
                ],
              ],
            );
          },
        ),
      ),
    );
  }

  /// 生成搜索结果的副标题（v2.6：description 优先，其次子任务摘要，最后 fallback 到旧 note）。
  String? _subtitleOf(LearningItemSearchResult item) {
    final desc = (item.description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    if (item.subtaskCount > 0) {
      return '${item.subtaskCount} 个子任务';
    }

    final legacy = (item.note ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注：$legacy';

    return null;
  }
}

class _DateHeader extends StatelessWidget {
  const _DateHeader();

  @override
  Widget build(BuildContext context) {
    final secondaryText =
        Theme.of(context).textTheme.bodySmall?.color ?? AppColors.textSecondary;

    final now = DateTime.now();
    final weekday = _weekdayZh(now.weekday);
    final text = '${YikeDateUtils.formatYmd(now)}  $weekday';
    return Row(
      children: [
        Icon(Icons.calendar_today_outlined, size: 18, color: secondaryText),
        const SizedBox(width: AppSpacing.sm),
        Text(
          text,
          style: AppTypography.body(
            context,
          ).copyWith(fontWeight: FontWeight.w500),
        ),
      ],
    );
  }

  String _weekdayZh(int weekday) {
    switch (weekday) {
      case DateTime.monday:
        return '周一';
      case DateTime.tuesday:
        return '周二';
      case DateTime.wednesday:
        return '周三';
      case DateTime.thursday:
        return '周四';
      case DateTime.friday:
        return '周五';
      case DateTime.saturday:
        return '周六';
      case DateTime.sunday:
        return '周日';
      default:
        return '';
    }
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({
    required this.title,
    this.subtitle,
    required this.color,
  });

  final String title;
  final String? subtitle;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        Container(
          width: 8,
          height: 20,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(8),
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Text(title, style: AppTypography.h2(context)),
        if (subtitle != null) ...[
          const SizedBox(width: AppSpacing.sm),
          Expanded(
            child: Text(
              subtitle!,
              style: AppTypography.bodySecondary(context),
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ],
    );
  }
}

class _TaskGrid extends StatelessWidget {
  const _TaskGrid({
    required this.tasks,
    required this.isOverdue,
    required this.selectionMode,
    required this.selectedTaskIds,
    required this.expandedTaskIds,
    required this.lastDoneOrSkippedRoundByLearningItemId,
    required this.nextReviewScheduledDateByLearningItemId,
    required this.nextReviewPreviewDisabledLearningItemIds,
    required this.removingTaskIds,
    required this.disableAnimations,
    required this.onRemovingAnimationCompleted,
    required this.onSwipeDismissed,
    required this.onToggleSelected,
    required this.onToggleExpanded,
    required this.onComplete,
    required this.onSwipeComplete,
    required this.onSkip,
    required this.onSwipeSkip,
    this.onUndo,
  });

  final List<ReviewTaskViewEntity> tasks;
  final bool isOverdue;
  final bool selectionMode;
  final Set<int> selectedTaskIds;
  final Set<int> expandedTaskIds;
  final Map<int, int> lastDoneOrSkippedRoundByLearningItemId;
  final Map<int, DateTime?> nextReviewScheduledDateByLearningItemId;
  final Set<int> nextReviewPreviewDisabledLearningItemIds;
  final Set<int> removingTaskIds;
  final bool disableAnimations;
  final void Function(int taskId) onRemovingAnimationCompleted;
  final VoidCallback onSwipeDismissed;
  final void Function(int taskId) onToggleSelected;
  final void Function(int taskId) onToggleExpanded;
  final Future<bool> Function(int taskId) onComplete;
  final Future<bool> Function(int taskId) onSwipeComplete;
  final Future<bool> Function(int taskId) onSkip;
  final Future<bool> Function(int taskId) onSwipeSkip;
  final Future<void> Function(int taskId)? onUndo;

  @override
  Widget build(BuildContext context) {
    final columnCount = ResponsiveUtils.getColumnCount(context);

    // 移动端/窄屏：保持单列 + 滑动操作。
    if (columnCount <= 1) {
      return Column(
        children: tasks
            .map(
              (t) => Padding(
                padding: const EdgeInsets.only(bottom: AppSpacing.md),
                child: _TaskCard(
                  taskId: t.taskId,
                  learningItemId: t.learningItemId,
                  title: t.title,
                  description: t.description,
                  legacyNote: t.note,
                  subtaskCount: t.subtaskCount,
                  tags: t.tags,
                  reviewRound: t.reviewRound,
                  scheduledDate: t.scheduledDate,
                  status: t.status,
                  completedAt: t.completedAt,
                  skippedAt: t.skippedAt,
                  isOverdue: isOverdue,
                  selectionMode: selectionMode,
                  selected: selectedTaskIds.contains(t.taskId),
                  expanded: expandedTaskIds.contains(t.taskId),
                  isRemoving: removingTaskIds.contains(t.taskId),
                  disableAnimations: disableAnimations,
                  lastDoneOrSkippedRound:
                      lastDoneOrSkippedRoundByLearningItemId[t.learningItemId],
                  nextReviewScheduledDate:
                      nextReviewScheduledDateByLearningItemId[t.learningItemId],
                  nextReviewPreviewDisabled:
                      nextReviewPreviewDisabledLearningItemIds.contains(
                        t.learningItemId,
                      ),
                  onRemovingAnimationCompleted: () =>
                      onRemovingAnimationCompleted(t.taskId),
                  onSwipeDismissed: onSwipeDismissed,
                  onToggleSelected: () => onToggleSelected(t.taskId),
                  onToggleExpanded: () => onToggleExpanded(t.taskId),
                  onComplete: () => onComplete(t.taskId),
                  onSwipeComplete: () => onSwipeComplete(t.taskId),
                  onSkip: () => onSkip(t.taskId),
                  onSwipeSkip: () => onSwipeSkip(t.taskId),
                  onUndo: onUndo == null ? null : () => onUndo!(t.taskId),
                ),
              ),
            )
            .toList(),
      );
    }

    // 桌面/宽屏：多列网格，关闭滑动（桌面端用按钮/快捷键更符合预期）。
    // v2.6：副标题增加"描述/子任务摘要"，桌面端卡片略增高以避免溢出。
    // v2.7：增加高度以容纳展开描述后的"查看完整描述"链接。
    final itemExtent = selectionMode ? 240.0 : 220.0;
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      itemCount: tasks.length,
      gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: columnCount,
        mainAxisSpacing: AppSpacing.md,
        crossAxisSpacing: AppSpacing.md,
        mainAxisExtent: itemExtent,
      ),
      itemBuilder: (context, index) {
        final t = tasks[index];
        return _TaskCard(
          taskId: t.taskId,
          learningItemId: t.learningItemId,
          title: t.title,
          description: t.description,
          legacyNote: t.note,
          subtaskCount: t.subtaskCount,
          tags: t.tags,
          reviewRound: t.reviewRound,
          scheduledDate: t.scheduledDate,
          status: t.status,
          completedAt: t.completedAt,
          skippedAt: t.skippedAt,
          isOverdue: isOverdue,
          selectionMode: selectionMode,
          selected: selectedTaskIds.contains(t.taskId),
          expanded: expandedTaskIds.contains(t.taskId),
          isRemoving: removingTaskIds.contains(t.taskId),
          disableAnimations: disableAnimations,
          lastDoneOrSkippedRound:
              lastDoneOrSkippedRoundByLearningItemId[t.learningItemId],
          nextReviewScheduledDate:
              nextReviewScheduledDateByLearningItemId[t.learningItemId],
          nextReviewPreviewDisabled: nextReviewPreviewDisabledLearningItemIds
              .contains(t.learningItemId),
          onRemovingAnimationCompleted: () =>
              onRemovingAnimationCompleted(t.taskId),
          onSwipeDismissed: onSwipeDismissed,
          enableSwipe: false,
          onToggleSelected: () => onToggleSelected(t.taskId),
          onToggleExpanded: () => onToggleExpanded(t.taskId),
          onComplete: () => onComplete(t.taskId),
          onSwipeComplete: () => onSwipeComplete(t.taskId),
          onSkip: () => onSkip(t.taskId),
          onSwipeSkip: () => onSwipeSkip(t.taskId),
          onUndo: onUndo == null ? null : () => onUndo!(t.taskId),
        );
      },
    );
  }
}

class _TaskCard extends StatelessWidget {
  const _TaskCard({
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
    required this.isOverdue,
    required this.selectionMode,
    required this.selected,
    required this.expanded,
    required this.isRemoving,
    required this.disableAnimations,
    required this.lastDoneOrSkippedRound,
    required this.nextReviewScheduledDate,
    required this.nextReviewPreviewDisabled,
    required this.onRemovingAnimationCompleted,
    required this.onSwipeDismissed,
    this.enableSwipe = true,
    required this.onToggleSelected,
    required this.onToggleExpanded,
    required this.onComplete,
    required this.onSwipeComplete,
    required this.onSkip,
    required this.onSwipeSkip,
    required this.onUndo,
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
  final bool isOverdue;
  final bool selectionMode;
  final bool selected;
  final bool expanded;
  final bool isRemoving;
  final bool disableAnimations;
  final int? lastDoneOrSkippedRound;
  final DateTime? nextReviewScheduledDate;
  final bool nextReviewPreviewDisabled;
  final VoidCallback onRemovingAnimationCompleted;
  final VoidCallback onSwipeDismissed;
  final bool enableSwipe;
  final VoidCallback onToggleSelected;
  final VoidCallback onToggleExpanded;
  final Future<bool> Function() onComplete;
  final Future<bool> Function() onSwipeComplete;
  final Future<bool> Function() onSkip;
  final Future<bool> Function() onSwipeSkip;
  final Future<void> Function()? onUndo;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final normalBorderColor = isDark
        ? AppColors.darkGlassBorder
        : AppColors.glassBorder;
    final secondaryText =
        Theme.of(context).textTheme.bodySmall?.color ?? AppColors.textSecondary;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;

    final borderColor = status == ReviewTaskStatus.pending && isOverdue
        ? AppColors.warning
        : normalBorderColor;
    final statusText = _subtitleText(context);
    final infoText = _infoText();
    final subtitleText = infoText == null
        ? statusText
        : '$statusText · $infoText';
    final detailLabel = _expandedDetailLabel();
    final detailText = _expandedDetailText();

    final showNextReviewPreview =
        !selectionMode &&
        !isRemoving &&
        !nextReviewPreviewDisabled &&
        (status == ReviewTaskStatus.done ||
            status == ReviewTaskStatus.skipped) &&
        lastDoneOrSkippedRound != null &&
        lastDoneOrSkippedRound == reviewRound;

    final nextReviewPreview = showNextReviewPreview
        ? _NextReviewPreview(
            nextScheduledDate: nextReviewScheduledDate,
            nextRound: reviewRound + 1,
          )
        : null;

    final statusTag = switch (status) {
      ReviewTaskStatus.done => _StatusTag(
        label: '已完成',
        color: AppColors.success,
        onTap: onUndo == null
            ? null
            : () {
                onUndo!();
              },
      ),
      ReviewTaskStatus.skipped => _StatusTag(
        label: '已跳过',
        color: AppColors.warning,
        onTap: onUndo == null
            ? null
            : () {
                onUndo!();
              },
      ),
      ReviewTaskStatus.pending => null,
    };

    final card = InkWell(
      onTap: isRemoving
          ? null
          : (selectionMode ? onToggleSelected : onToggleExpanded),
      borderRadius: BorderRadius.circular(16),
      child: GlassCard(
        child: DecoratedBox(
          decoration: BoxDecoration(
            border: Border.all(color: borderColor),
            borderRadius: BorderRadius.circular(16),
          ),
          child: Stack(
            children: [
              Padding(
                padding: const EdgeInsets.all(AppSpacing.lg),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (selectionMode)
                      Padding(
                        padding: const EdgeInsets.only(top: 2),
                        child: Checkbox(
                          value: selected,
                          onChanged: isRemoving
                              ? null
                              : (_) => onToggleSelected(),
                        ),
                      )
                    else
                      Icon(
                        switch (status) {
                          ReviewTaskStatus.pending =>
                            isOverdue
                                ? Icons.error_outline
                                : Icons.circle_outlined,
                          ReviewTaskStatus.done => Icons.check_circle_outline,
                          ReviewTaskStatus.skipped =>
                            Icons.not_interested_outlined,
                        },
                        color: switch (status) {
                          ReviewTaskStatus.pending =>
                            isOverdue ? AppColors.warning : secondaryText,
                          ReviewTaskStatus.done => AppColors.success,
                          ReviewTaskStatus.skipped => secondaryText,
                        },
                        size: 22,
                      ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          // 关键布局：已完成/已跳过的“撤销入口”改为点击状态标签，
                          // 因此将状态标签放入标题行内，避免与标题/按钮产生覆盖（窄屏更明显）。
                          Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Expanded(
                                child: Text(
                                  '$title（第$reviewRound次）',
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: AppTypography.body(
                                    context,
                                  ).copyWith(fontWeight: FontWeight.w700),
                                ),
                              ),
                              if (statusTag != null) ...[
                                const SizedBox(width: AppSpacing.sm),
                                statusTag,
                              ],
                            ],
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          Text(
                            subtitleText,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.bodySecondary(context),
                          ),
                          if (tags.isNotEmpty) ...[
                            const SizedBox(height: AppSpacing.sm),
                            Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              children: tags
                                  .take(3)
                                  .map(
                                    (t) => Container(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 10,
                                        vertical: 4,
                                      ),
                                      decoration: BoxDecoration(
                                        color: primary.withValues(alpha: 0.18),
                                        borderRadius: BorderRadius.circular(
                                          999,
                                        ),
                                        border: Border.all(
                                          color: primary.withValues(
                                            alpha: 0.35,
                                          ),
                                        ),
                                      ),
                                      child: Text(
                                        t,
                                        style: AppTypography.bodySecondary(
                                          context,
                                        ).copyWith(fontSize: 12),
                                      ),
                                    ),
                                  )
                                  .toList(),
                            ),
                          ],
                          AnimatedCrossFade(
                            crossFadeState: expanded
                                ? CrossFadeState.showSecond
                                : CrossFadeState.showFirst,
                            duration: const Duration(milliseconds: 160),
                            firstChild: const SizedBox.shrink(),
                            secondChild: Padding(
                              padding: const EdgeInsets.only(
                                top: AppSpacing.md,
                              ),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  if (detailLabel != null &&
                                      detailText != null) ...[
                                    Text(
                                      detailLabel,
                                      style: AppTypography.h2(
                                        context,
                                      ).copyWith(fontSize: 14),
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      detailText,
                                      maxLines: 5,
                                      overflow: TextOverflow.ellipsis,
                                      style: AppTypography.bodySecondary(
                                        context,
                                      ),
                                    ),
                                    // 当描述文本超过5行时，显示"查看完整描述"链接
                                    if (detailText.split('\n').length > 5) ...[
                                      const SizedBox(height: AppSpacing.xs),
                                      GestureDetector(
                                        onTap: isRemoving
                                            ? null
                                            : () {
                                                // 跳转到任务详情页
                                                context.push(
                                                  '/tasks/detail/$learningItemId',
                                                );
                                              },
                                        child: Text(
                                          '查看完整描述 >>',
                                          style: TextStyle(
                                            color: primary,
                                            fontSize: 12,
                                            fontWeight: FontWeight.w500,
                                          ),
                                        ),
                                      ),
                                    ],
                                    const SizedBox(height: AppSpacing.sm),
                                  ],
                                  Text(
                                    '点击卡片可收起详情',
                                    style: TextStyle(
                                      color: secondaryText,
                                      fontSize: 12,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                          if (nextReviewPreview != null) ...[
                            const SizedBox(height: AppSpacing.sm),
                            nextReviewPreview,
                          ],
                        ],
                      ),
                    ),
                    if (!selectionMode &&
                        status == ReviewTaskStatus.pending) ...[
                      const SizedBox(width: AppSpacing.md),
                      Column(
                        children: [
                          IconButton(
                            tooltip: isOverdue ? '补做' : '完成',
                            onPressed: isRemoving
                                ? null
                                : () async {
                                    await onComplete();
                                  },
                            icon: const Icon(Icons.check_circle_outline),
                            color: AppColors.success,
                          ),
                          IconButton(
                            tooltip: '跳过',
                            onPressed: isRemoving
                                ? null
                                : () async {
                                    await onSkip();
                                  },
                            icon: const Icon(Icons.not_interested_outlined),
                            color: secondaryText,
                          ),
                        ],
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );

    Future<void> openContextMenu(Offset globalPosition) async {
      // v1.1.0：选择模式优先级最高，且移除动画期间禁用菜单，避免误操作。
      if (selectionMode || isRemoving) return;
      final action = await showTaskContextMenu(
        context: context,
        globalPosition: globalPosition,
        status: status,
      );
      if (action == null) return;

      switch (action) {
        case TaskContextMenuAction.complete:
          await onComplete();
        case TaskContextMenuAction.skip:
          await onSkip();
        case TaskContextMenuAction.undo:
          final undo = onUndo;
          if (undo != null) {
            await undo();
          }
        case TaskContextMenuAction.viewDetail:
          if (!context.mounted) return;
          context.push('/tasks/detail/$learningItemId');
      }
    }

    Widget decoratedCard = card;
    if (!selectionMode && !isRemoving) {
      decoratedCard = GestureDetector(
        behavior: HitTestBehavior.translucent,
        onLongPressStart: (d) => openContextMenu(d.globalPosition),
        onSecondaryTapDown: (d) => openContextMenu(d.globalPosition),
        child: decoratedCard,
      );
    }

    if (isRemoving) {
      decoratedCard = IgnorePointer(
        ignoring: true,
        child: CompletionAnimation(
          play: true,
          enabled: !disableAnimations,
          onCompleted: onRemovingAnimationCompleted,
          child: decoratedCard,
        ),
      );
    }

    if (selectionMode ||
        isRemoving ||
        !enableSwipe ||
        status != ReviewTaskStatus.pending) {
      return decoratedCard;
    }

    return Dismissible(
      key: ValueKey('task_$taskId'),
      direction: DismissDirection.horizontal,
      background: _SwipeBackground(
        alignment: Alignment.centerLeft,
        color: AppColors.success,
        icon: Icons.check,
        text: '完成',
      ),
      secondaryBackground: _SwipeBackground(
        alignment: Alignment.centerRight,
        color: AppColors.error,
        icon: Icons.not_interested,
        text: '跳过',
      ),
      confirmDismiss: (direction) async {
        // v1.1.0：操作先行 + 失败回滚（失败时不 dismiss，卡片回弹并提示“操作失败，请重试”）。
        final ok = switch (direction) {
          DismissDirection.startToEnd => await onSwipeComplete(),
          DismissDirection.endToStart => await onSwipeSkip(),
          _ => false,
        };
        if (!ok) return false;

        // 触觉反馈：仅在“确认触发”时触发一次（松手确认时），减少干扰。
        if (!disableAnimations) {
          HapticFeedback.lightImpact();
        }
        return true;
      },
      onDismissed: (_) => onSwipeDismissed(),
      child: decoratedCard,
    );
  }

  String _subtitleText(BuildContext context) {
    switch (status) {
      case ReviewTaskStatus.pending:
        return _dueText();
      case ReviewTaskStatus.done:
        final time = completedAt;
        if (time == null) return '已完成';
        final formatted = TimeOfDay.fromDateTime(time).format(context);
        return '完成于 $formatted';
      case ReviewTaskStatus.skipped:
        final time = skippedAt;
        if (time == null) return '已跳过';
        final formatted = TimeOfDay.fromDateTime(time).format(context);
        return '跳过于 $formatted';
    }
  }

  /// 生成任务信息摘要（v2.6：description 优先，其次子任务数量，最后 fallback 到旧 note）。
  String? _infoText() {
    final desc = (description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    if (subtaskCount > 0) return '$subtaskCount 个子任务';

    final legacy = (legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注：$legacy';

    return null;
  }

  String? _expandedDetailLabel() {
    final desc = (description ?? '').trim();
    if (desc.isNotEmpty) return '描述';

    final legacy = (legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注（待迁移）';

    if (subtaskCount > 0) return '子任务';
    return null;
  }

  String? _expandedDetailText() {
    final desc = (description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    final legacy = (legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return legacy;

    if (subtaskCount > 0) return '共 $subtaskCount 个子任务（详见任务详情）';
    return null;
  }

  String _dueText() {
    final todayStart = DateTime(
      DateTime.now().year,
      DateTime.now().month,
      DateTime.now().day,
    );
    final scheduledStart = DateTime(
      scheduledDate.year,
      scheduledDate.month,
      scheduledDate.day,
    );
    final diffDays = todayStart.difference(scheduledStart).inDays;
    if (diffDays <= 0) return '今日待复习';
    return '逾期 $diffDays 天';
  }
}

class _StatusTag extends StatelessWidget {
  const _StatusTag({required this.label, required this.color, this.onTap});

  final String label;
  final Color color;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final chip = Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withAlpha(26),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withAlpha(120)),
      ),
      child: Text(
        label,
        style: AppTypography.bodySecondary(
          context,
        ).copyWith(color: color, fontWeight: FontWeight.w700, fontSize: 12),
      ),
    );

    if (onTap == null) return chip;

    // 交互说明：点击“已完成/已跳过”状态标签弹出撤销确认框，替代右侧撤销按钮，
    // 以避免窄屏下按钮与标题/状态区的布局冲突。
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: chip,
      ),
    );
  }
}

class _SwipeBackground extends StatelessWidget {
  const _SwipeBackground({
    required this.alignment,
    required this.color,
    required this.icon,
    required this.text,
  });

  final Alignment alignment;
  final Color color;
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Container(
      alignment: alignment,
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
      decoration: BoxDecoration(
        color: color.withAlpha(40),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, color: color),
          const SizedBox(width: AppSpacing.sm),
          Text(
            text,
            style: TextStyle(color: color, fontWeight: FontWeight.w700),
          ),
        ],
      ),
    );
  }
}

class _BatchActionBar extends StatelessWidget {
  const _BatchActionBar({
    required this.selectedCount,
    required this.onCompleteSelected,
    required this.onSkipSelected,
  });

  final int selectedCount;
  final VoidCallback? onCompleteSelected;
  final VoidCallback? onSkipSelected;

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.sm,
          AppSpacing.lg,
          AppSpacing.lg,
        ),
        child: GlassCard(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.md),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    '已选择 $selectedCount 项',
                    style: AppTypography.bodySecondary(context),
                  ),
                ),
                FilledButton(
                  onPressed: onCompleteSelected,
                  style: FilledButton.styleFrom(
                    backgroundColor: AppColors.success,
                  ),
                  child: const Text('完成所选'),
                ),
                const SizedBox(width: AppSpacing.sm),
                OutlinedButton(
                  onPressed: onSkipSelected,
                  child: const Text('跳过所选'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _EmptyState extends StatelessWidget {
  const _EmptyState({required this.learningItemCount});

  final int learningItemCount;

  @override
  Widget build(BuildContext context) {
    final isColdStart = learningItemCount <= 0;
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    final iconColor = isColdStart ? primary : AppColors.success;
    final title = isColdStart ? '还没有任何学习内容' : '今日任务已完成！';

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            Icon(
              isColdStart
                  ? Icons.auto_stories_outlined
                  : Icons.check_circle_outline,
              size: 52,
              color: iconColor,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              title,
              style: AppTypography.bodySecondary(context),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.lg),
            FilledButton(
              onPressed: () => context.push('/input'),
              child: const Text('＋添加学习内容'),
            ),
            const SizedBox(height: AppSpacing.sm),
            TextButton(
              onPressed: () => context.push('/topics'),
              child: const Text('或创建学习专题'),
            ),
          ],
        ),
      ),
    );
  }
}

class _EmptySectionHint extends StatelessWidget {
  const _EmptySectionHint({required this.text});

  final String text;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: AppSpacing.lg),
      child: Text(
        text,
        style: AppTypography.bodySecondary(context),
        textAlign: TextAlign.center,
      ),
    );
  }
}

/// v1.1.0：复习间隔预览展示行（done/skipped 卡片底部）。
class _NextReviewPreview extends StatelessWidget {
  const _NextReviewPreview({
    required this.nextScheduledDate,
    required this.nextRound,
  });

  final DateTime? nextScheduledDate;
  final int nextRound;

  @override
  Widget build(BuildContext context) {
    final secondary = AppTypography.bodySecondary(context);
    final date = nextScheduledDate;
    if (date == null) {
      return Row(
        children: [
          const Icon(
            Icons.celebration_outlined,
            size: 16,
            color: AppColors.success,
          ),
          const SizedBox(width: 6),
          Text('本内容已完成全部轮次', style: secondary),
        ],
      );
    }

    return Row(
      children: [
        const Icon(Icons.schedule_outlined, size: 16),
        const SizedBox(width: 6),
        Text('下次复习：${_relativeText(date)}（第$nextRound轮）', style: secondary),
      ],
    );
  }

  String _relativeText(DateTime date) {
    final todayStart = YikeDateUtils.atStartOfDay(DateTime.now());
    final targetStart = YikeDateUtils.atStartOfDay(date);
    final diffDays = targetStart.difference(todayStart).inDays;
    if (diffDays == 0) return '今天';
    if (diffDays == 1) return '明天';
    if (diffDays > 1) return '$diffDays天后';
    return '已逾期${diffDays.abs()}天';
  }
}

/// v1.1.0：完成动画占位任务所属分区（用于插入到正确列表位置）。
enum _RemovingTaskSection {
  /// 逾期任务分区。
  overdue,

  /// 今日待复习分区。
  today,
}

/// v1.1.0：完成动画占位任务快照。
///
/// 说明：用于在 provider 刷新后，短暂插入“完成前”的卡片数据播放移除动效。
class _RemovingTaskSnapshot {
  const _RemovingTaskSnapshot({
    required this.task,
    required this.section,
    required this.index,
  });

  final ReviewTaskViewEntity task;
  final _RemovingTaskSection section;
  final int index;
}
