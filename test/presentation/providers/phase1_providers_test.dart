// 文件用途：Phase 1 Provider 状态流测试，覆盖首页、日历与任务中心的真实数据库交互路径。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/home_time_filter_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  /// 等待 Provider 内部异步链路稳定，避免直接读取到中间状态。
  Future<void> waitFor(
    bool Function() predicate, {
    int maxAttempts = 50,
  }) async {
    for (var attempt = 0; attempt < maxAttempts; attempt++) {
      if (predicate()) {
        return;
      }
      await Future<void>.delayed(Duration.zero);
    }
    fail('等待 Provider 状态稳定超时');
  }

  /// 插入一条学习内容，供 Provider 走真实 Repository / UseCase 查询链路。
  Future<int> insertLearningItem({
    required String title,
    required DateTime learningDate,
    List<String> tags = const <String>[],
  }) {
    return db.into(db.learningItems).insert(
      LearningItemsCompanion.insert(
        title: title,
        note: const drift.Value.absent(),
        description: const drift.Value.absent(),
        tags: drift.Value(jsonEncode(tags)),
        learningDate: learningDate,
        createdAt: drift.Value(learningDate),
        updatedAt: drift.Value(learningDate),
      ),
    );
  }

  /// 插入一条复习任务，并显式维护 occurredAt，贴近生产库写入口径。
  Future<int> insertReviewTask({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
    required ReviewTaskStatus status,
    DateTime? completedAt,
    DateTime? skippedAt,
  }) {
    final occurredAt = switch (status) {
      ReviewTaskStatus.pending => scheduledDate,
      ReviewTaskStatus.done => completedAt ?? scheduledDate,
      ReviewTaskStatus.skipped => skippedAt ?? scheduledDate,
    };
    return db.into(db.reviewTasks).insert(
      ReviewTasksCompanion.insert(
        learningItemId: learningItemId,
        reviewRound: reviewRound,
        scheduledDate: scheduledDate,
        occurredAt: drift.Value(occurredAt),
        status: drift.Value(status.toDbValue()),
        completedAt: drift.Value(completedAt),
        skippedAt: drift.Value(skippedAt),
        createdAt: drift.Value(scheduledDate),
        updatedAt: drift.Value(occurredAt),
      ),
    );
  }

  /// 插入主题及其关联，用于验证首页主题筛选会重新计算展示列表。
  Future<int> insertTopicWithRelation({
    required String name,
    required int learningItemId,
    required DateTime createdAt,
  }) async {
    final topicId = await db.into(db.learningTopics).insert(
      LearningTopicsCompanion.insert(
        name: name,
        description: const drift.Value.absent(),
        createdAt: drift.Value(createdAt),
        updatedAt: drift.Value(createdAt),
      ),
    );
    await db.into(db.topicItemRelations).insert(
      TopicItemRelationsCompanion.insert(
        topicId: topicId,
        learningItemId: learningItemId,
        createdAt: drift.Value(createdAt),
      ),
    );
    return topicId;
  }

  setUp(() {
    db = createInMemoryDatabase();
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('CalendarProvider', () {
    test('选中日期并完成任务后，会同步刷新当日列表与月份统计', () async {
      final today = startOfDay(DateTime.now());
      final itemId = await insertLearningItem(title: '日历任务', learningDate: today);
      final taskId = await insertReviewTask(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: today,
        status: ReviewTaskStatus.pending,
      );

      final notifier = container.read(calendarProvider.notifier);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);

      final initialState = container.read(calendarProvider);
      expect(initialState.monthStats[today]?.pendingCount, 1);
      expect(initialState.monthStats[today]?.doneCount, 0);

      await notifier.selectDay(today);
      await waitFor(() => !container.read(calendarProvider).isLoadingTasks);

      final selectedState = container.read(calendarProvider);
      expect(selectedState.selectedDay, today);
      expect(selectedState.selectedDayTasks.single.taskId, taskId);
      expect(selectedState.selectedDayTasks.single.status, ReviewTaskStatus.pending);

      await notifier.completeTask(taskId);
      await waitFor(() => !container.read(calendarProvider).isLoadingTasks);

      final completedState = container.read(calendarProvider);
      expect(completedState.monthStats[today]?.pendingCount, 0);
      expect(completedState.monthStats[today]?.doneCount, 1);
      expect(
        completedState.selectedDayTasks.single.status,
        ReviewTaskStatus.done,
      );

      await notifier.undoTaskStatus(taskId);
      await waitFor(() => !container.read(calendarProvider).isLoadingTasks);

      final undoneState = container.read(calendarProvider);
      expect(undoneState.monthStats[today]?.pendingCount, 1);
      expect(undoneState.monthStats[today]?.doneCount, 0);
      expect(undoneState.selectedDayTasks.single.status, ReviewTaskStatus.pending);
    });
  });

  group('HomeTasksProvider', () {
    test('主题筛选与批量完成会联动刷新首页列表和选择态', () async {
      final today = startOfDay(DateTime.now());
      final topicItemId = await insertLearningItem(
        title: '归属主题的任务',
        learningDate: today,
        tags: const <String>['主题'],
      );
      final otherItemId = await insertLearningItem(
        title: '未归属主题的任务',
        learningDate: today,
      );
      final topicTaskId = await insertReviewTask(
        learningItemId: topicItemId,
        reviewRound: 1,
        scheduledDate: today,
        status: ReviewTaskStatus.pending,
      );
      await insertReviewTask(
        learningItemId: otherItemId,
        reviewRound: 1,
        scheduledDate: today,
        status: ReviewTaskStatus.pending,
      );
      final topicId = await insertTopicWithRelation(
        name: '测试主题',
        learningItemId: topicItemId,
        createdAt: today,
      );

      // 保持联动失效的 Provider 处于监听状态，避免 invalidate 后立刻 dispose。
      final calendarSub = container.listen<CalendarState>(
        calendarProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final statisticsSub = container.listen<StatisticsState>(
        statisticsProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      addTearDown(calendarSub.close);
      addTearDown(statisticsSub.close);

      final notifier = container.read(homeTasksProvider.notifier);
      await waitFor(() => !container.read(homeTasksProvider).isLoading);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);
      await waitFor(() => !container.read(statisticsProvider).isLoading);

      final initialState = container.read(homeTasksProvider);
      expect(initialState.todayPending.length, 2);
      expect(initialState.todayCompleted, isEmpty);

      await notifier.setTopicFilter(topicId);
      await waitFor(() {
        final state = container.read(homeTasksProvider);
        return !state.isLoading && state.topicFilterId == topicId;
      });

      final filteredState = container.read(homeTasksProvider);
      expect(filteredState.todayPending.length, 1);
      expect(filteredState.todayPending.single.learningItemId, topicItemId);

      notifier.toggleSelectionMode();
      notifier.toggleSelected(topicTaskId);
      expect(container.read(homeTasksProvider).selectedTaskIds, <int>{topicTaskId});

      await notifier.completeSelected();
      await waitFor(() => !container.read(homeTasksProvider).isLoading);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);
      await waitFor(() => !container.read(statisticsProvider).isLoading);

      final completedState = container.read(homeTasksProvider);
      expect(completedState.isSelectionMode, false);
      expect(completedState.selectedTaskIds, isEmpty);
      expect(completedState.todayPending, isEmpty);
      expect(completedState.todayCompleted.map((task) => task.taskId), contains(topicTaskId));
      expect(completedState.completedCount, 1);
      expect(completedState.totalCount, 1);
    });
  });

  group('TaskHubProvider', () {
    test('状态筛选、时间筛选与任务跳过会刷新时间线和计数', () async {
      final today = startOfDay(DateTime.now());
      final yesterday = today.subtract(const Duration(days: 1));
      final tomorrow = today.add(const Duration(days: 1));
      final doneAt = today.add(const Duration(hours: 8));

      final pendingTodayItemId = await insertLearningItem(
        title: '今天待复习',
        learningDate: today,
      );
      final doneTodayItemId = await insertLearningItem(
        title: '今天已完成',
        learningDate: today,
      );
      final skippedYesterdayItemId = await insertLearningItem(
        title: '昨天已跳过',
        learningDate: yesterday,
      );
      final futurePendingItemId = await insertLearningItem(
        title: '明天待复习',
        learningDate: tomorrow,
      );

      final pendingTaskId = await insertReviewTask(
        learningItemId: pendingTodayItemId,
        reviewRound: 1,
        scheduledDate: today,
        status: ReviewTaskStatus.pending,
      );
      await insertReviewTask(
        learningItemId: doneTodayItemId,
        reviewRound: 1,
        scheduledDate: today,
        status: ReviewTaskStatus.done,
        completedAt: doneAt,
      );
      await insertReviewTask(
        learningItemId: skippedYesterdayItemId,
        reviewRound: 1,
        scheduledDate: yesterday,
        status: ReviewTaskStatus.skipped,
        skippedAt: yesterday.add(const Duration(hours: 9)),
      );
      await insertReviewTask(
        learningItemId: futurePendingItemId,
        reviewRound: 1,
        scheduledDate: tomorrow,
        status: ReviewTaskStatus.pending,
      );

      // 任务中心会主动刷新首页、日历与统计，因此测试中保持这几个 Provider 常驻。
      final homeTasksSub = container.listen<HomeTasksState>(
        homeTasksProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final calendarSub = container.listen<CalendarState>(
        calendarProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final statisticsSub = container.listen<StatisticsState>(
        statisticsProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      addTearDown(homeTasksSub.close);
      addTearDown(calendarSub.close);
      addTearDown(statisticsSub.close);

      final notifier = container.read(taskHubProvider.notifier);
      await waitFor(() => !container.read(taskHubProvider).isLoading);
      await waitFor(() => !container.read(homeTasksProvider).isLoading);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);
      await waitFor(() => !container.read(statisticsProvider).isLoading);

      final initialState = container.read(taskHubProvider);
      expect(initialState.counts.all, 4);
      expect(initialState.counts.pending, 2);
      expect(initialState.counts.done, 1);
      expect(initialState.counts.skipped, 1);

      final initialTaskRows = initialState.timelineRows
          .whereType<TaskHubTimelineTaskRow>()
          .toList();
      expect(initialState.timelineRows.whereType<TaskHubTimelineHeaderRow>().length, 3);
      expect(initialTaskRows.where((row) => row.isLastInGroup).length, 3);

      await notifier.setFilter(ReviewTaskFilter.pending);
      await waitFor(() => !container.read(taskHubProvider).isLoading);

      final pendingState = container.read(taskHubProvider);
      expect(pendingState.filter, ReviewTaskFilter.pending);
      expect(pendingState.items.length, 2);
      expect(
        pendingState.items.every((item) => item.task.status == ReviewTaskStatus.pending),
        isTrue,
      );

      await notifier.setFilter(ReviewTaskFilter.all);
      await notifier.setTimeFilter(HomeTimeFilter.afterToday);
      await waitFor(() => !container.read(taskHubProvider).isLoading);

      final futureOnlyState = container.read(taskHubProvider);
      expect(futureOnlyState.timeFilter, HomeTimeFilter.afterToday);
      expect(futureOnlyState.items.length, 1);
      expect(futureOnlyState.items.single.task.learningItemId, futurePendingItemId);

      await notifier.setTimeFilter(HomeTimeFilter.all);
      await notifier.skipTask(pendingTaskId);
      await waitFor(() => !container.read(taskHubProvider).isLoading);
      await waitFor(() => !container.read(homeTasksProvider).isLoading);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);
      await waitFor(() => !container.read(statisticsProvider).isLoading);

      final skippedState = container.read(taskHubProvider);
      expect(skippedState.counts.pending, 1);
      expect(skippedState.counts.skipped, 2);
      expect(
        skippedState.items.firstWhere((item) => item.task.taskId == pendingTaskId).task.status,
        ReviewTaskStatus.skipped,
      );
    });

    test('今天后 + 全部 在跨页加载时不会重复任务且会正确结束分页', () async {
      final today = startOfDay(DateTime.now());
      final tomorrow = today.add(const Duration(days: 1));

      for (var index = 0; index < 25; index++) {
        final scheduledDate = tomorrow.add(Duration(days: index));
        final learningItemId = await insertLearningItem(
          title: '未来任务 $index',
          learningDate: scheduledDate,
          tags: <String>['未来'],
        );
        await insertReviewTask(
          learningItemId: learningItemId,
          reviewRound: 1,
          scheduledDate: scheduledDate,
          status: ReviewTaskStatus.pending,
        );
      }

      final notifier = container.read(taskHubProvider.notifier);
      await waitFor(() => !container.read(taskHubProvider).isLoading);

      await notifier.setTimeFilter(HomeTimeFilter.afterToday);
      await waitFor(() => !container.read(taskHubProvider).isLoading);

      var state = container.read(taskHubProvider);
      expect(state.items.length, 20);
      expect(state.nextCursor, isNotNull);

      await notifier.loadMore();
      await waitFor(() => !container.read(taskHubProvider).isLoadingMore);

      state = container.read(taskHubProvider);
      final taskIds = state.items.map((item) => item.task.taskId).toList();
      expect(taskIds.length, 25);
      expect(taskIds.toSet().length, 25);
      expect(state.nextCursor, isNull);

      await notifier.loadMore();
      await waitFor(() => !container.read(taskHubProvider).isLoadingMore);

      final finalState = container.read(taskHubProvider);
      expect(finalState.items.map((item) => item.task.taskId).toSet().length, 25);
      expect(finalState.nextCursor, isNull);
    });
  });
}
