// 文件用途：Phase 2 任务相关页面测试，覆盖任务中心时间线与日历当日任务列表的真实交互链路。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/date_utils.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/pages/calendar/widgets/day_task_list.dart';
import 'package:yike/presentation/pages/tasks/task_detail_sheet.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_database.dart';

void main() {
  /// 返回本地自然日零点，便于按业务口径构造 scheduledDate。
  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  /// 等待界面或 Provider 完成异步刷新，避免断言中间态。
  Future<void> pumpUntil(
    WidgetTester tester,
    bool Function() predicate, {
    int maxAttempts = 80,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      await tester.pump(const Duration(milliseconds: 50));
      if (predicate()) return;
    }
    fail('等待页面状态稳定超时');
  }

  /// 插入学习内容，供任务中心/日历页走真实数据库查询链路。
  Future<int> insertLearningItem(
    AppDatabase db, {
    required String title,
    String? description,
    String? note,
    List<String> tags = const <String>[],
    required DateTime learningDate,
  }) {
    return db.into(db.learningItems).insert(
      LearningItemsCompanion.insert(
        title: title,
        description: drift.Value(description),
        note: drift.Value(note),
        tags: drift.Value(jsonEncode(tags)),
        learningDate: learningDate,
        createdAt: drift.Value(learningDate),
        updatedAt: drift.Value(learningDate),
      ),
    );
  }

  /// 插入复习任务，并显式维护 occurredAt，贴近生产口径。
  Future<int> insertReviewTask(
    AppDatabase db, {
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

  group('任务中心主链路', () {
    testWidgets(
      'TaskHubPage 支持时间筛选、展开交互、完成撤销与详情跳转',
      (tester) async {
        final today = startOfDay(DateTime.now());
        final yesterday = today.subtract(const Duration(days: 1));
        final tomorrow = today.add(const Duration(days: 1));
        late final AppHarness harness;
        late final int todayItemId;
        late final int todayTaskId;

        harness = await pumpYiKeApp(
          tester,
          initialLocation: '/tasks',
          size: const Size(1280, 900),
          seed: (container) async {
            final db = container.read(appDatabaseProvider);

            todayItemId = await insertLearningItem(
              db,
              title: '今天待复习任务',
              description: '今天的任务描述',
              tags: const <String>['今天'],
              learningDate: today,
            );
            todayTaskId = await insertReviewTask(
              db,
              learningItemId: todayItemId,
              reviewRound: 1,
              scheduledDate: today,
              status: ReviewTaskStatus.pending,
            );

            final yesterdayItemId = await insertLearningItem(
              db,
              title: '昨天已跳过任务',
              note: '旧备注仍可见',
              tags: const <String>['昨天'],
              learningDate: yesterday,
            );
            await insertReviewTask(
              db,
              learningItemId: yesterdayItemId,
              reviewRound: 2,
              scheduledDate: yesterday,
              status: ReviewTaskStatus.skipped,
              skippedAt: yesterday.add(const Duration(hours: 9)),
            );

            final tomorrowItemId = await insertLearningItem(
              db,
              title: '明天待安排任务',
              description: null,
              tags: const <String>['明天'],
              learningDate: tomorrow,
            );
            await insertReviewTask(
              db,
              learningItemId: tomorrowItemId,
              reviewRound: 3,
              scheduledDate: tomorrow,
              status: ReviewTaskStatus.pending,
            );
          },
        );

        await pumpUntil(
          tester,
          () => find.text('今天待复习任务（第1次）').evaluate().isNotEmpty,
        );
        final homeSub = harness.container.listen<HomeTasksState>(
          homeTasksProvider,
          (previous, next) {},
          fireImmediately: true,
        );
        final calendarSub = harness.container.listen<CalendarState>(
          calendarProvider,
          (previous, next) {},
          fireImmediately: true,
        );
        final statisticsSub = harness.container.listen<StatisticsState>(
          statisticsProvider,
          (previous, next) {},
          fireImmediately: true,
        );
        addTearDown(homeSub.close);
        addTearDown(calendarSub.close);
        addTearDown(statisticsSub.close);

        expect(find.text('时间筛选'), findsOneWidget);
        expect(find.text('今天'), findsWidgets);
        expect(find.text('昨天'), findsWidgets);
        expect(find.text('今天待复习任务（第1次）'), findsOneWidget);
        expect(find.text('昨天已跳过任务（第2次）'), findsOneWidget);
        expect(find.text('明天待安排任务（第3次）'), findsOneWidget);

        await tester.tap(find.text('今天前'));
        await tester.pumpAndSettle();
        expect(find.text('昨天已跳过任务（第2次）'), findsOneWidget);
        expect(find.text('今天待复习任务（第1次）'), findsNothing);

        await tester.tap(find.text('全部'));
        await tester.pumpAndSettle();
        expect(find.text('今天待复习任务（第1次）'), findsOneWidget);

        await tester.tap(find.text('今天待复习任务（第1次）'));
        await tester.pumpAndSettle();
        expect(find.text('完成'), findsWidgets);
        expect(find.text('详情'), findsWidgets);

        await tester.tap(find.widgetWithText(FilledButton, '完成').first);
        await tester.pumpAndSettle();
        expect(find.text('已完成'), findsAtLeastNWidgets(1));

        await harness.container.read(taskHubProvider.notifier).undoTaskStatus(
          todayTaskId,
        );
        await tester.pumpAndSettle();

        expect(find.text('今天待复习任务（第1次）'), findsOneWidget);
        expect(find.text('完成'), findsWidgets);

        await goToRoute(tester, harness, '/tasks/detail/$todayItemId?edit=1');
        expect(find.byType(TaskDetailSheet), findsOneWidget);
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.windows,
      }),
    );
  });

  group('日历当日任务列表', () {
    testWidgets(
      'DayTaskListContent 支持状态筛选、完成、跳过与撤销提示',
      (tester) async {
        final today = startOfDay(DateTime.now());
        final db = createInMemoryDatabase();
        addTearDown(() async => db.close());

        final pendingItemId = await insertLearningItem(
          db,
          title: '当天待复习',
          description: '待复习描述',
          tags: const <String>['待复习'],
          learningDate: today,
        );
        final doneItemId = await insertLearningItem(
          db,
          title: '当天已完成',
          tags: const <String>['完成'],
          learningDate: today,
        );
        final skippedItemId = await insertLearningItem(
          db,
          title: '当天已跳过',
          note: '旧备注',
          learningDate: today,
        );

        final pendingTaskId = await insertReviewTask(
          db,
          learningItemId: pendingItemId,
          reviewRound: 1,
          scheduledDate: today,
          status: ReviewTaskStatus.pending,
        );
        await insertReviewTask(
          db,
          learningItemId: doneItemId,
          reviewRound: 2,
          scheduledDate: today,
          status: ReviewTaskStatus.done,
          completedAt: today.add(const Duration(hours: 8)),
        );
        await insertReviewTask(
          db,
          learningItemId: skippedItemId,
          reviewRound: 3,
          scheduledDate: today,
          status: ReviewTaskStatus.skipped,
          skippedAt: today.add(const Duration(hours: 9)),
        );

        final container = ProviderContainer(
          overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
        );
        addTearDown(container.dispose);

        await container.read(calendarProvider.notifier).selectDay(today);
        while (container.read(calendarProvider).isLoadingTasks) {
          await Future<void>.delayed(Duration.zero);
        }

        await tester.pumpWidget(
          UncontrolledProviderScope(
            container: container,
            child: MaterialApp(
              home: Scaffold(body: DayTaskListContent(selectedDay: today)),
            ),
          ),
        );
        await tester.pumpAndSettle();

        expect(
          find.text('当天任务 · ${YikeDateUtils.formatYmd(today)}'),
          findsOneWidget,
        );
        expect(find.text('当天待复习'), findsOneWidget);
        expect(find.text('当天已完成'), findsOneWidget);
        expect(find.text('当天已跳过'), findsOneWidget);

        await tester.tap(find.text('已完成 1'));
        await tester.pumpAndSettle();
        expect(find.text('当天已完成'), findsOneWidget);
        expect(find.text('当天待复习'), findsNothing);

        await tester.tap(find.text('全部 3'));
        await tester.pumpAndSettle();
        expect(find.text('当天待复习'), findsOneWidget);

        await container.read(calendarProvider.notifier).completeTask(pendingTaskId);
        await tester.pumpAndSettle();
        expect(find.text('已完成'), findsAtLeastNWidgets(1));

        await container.read(calendarProvider.notifier).undoTaskStatus(
          pendingTaskId,
        );
        await tester.pumpAndSettle();
        expect(find.text('完成'), findsWidgets);

        await container.read(calendarProvider.notifier).skipTask(pendingTaskId);
        await tester.pumpAndSettle();
        expect(find.text('已跳过'), findsAtLeastNWidgets(1));

        final finalState = container.read(calendarProvider);
        expect(
          finalState.selectedDayTasks
              .firstWhere((task) => task.learningItemId == pendingItemId)
              .status,
          ReviewTaskStatus.skipped,
        );
        expect(container.read(reviewTaskFilterProvider), ReviewTaskFilter.all);
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.windows,
      }),
    );
  });
}
