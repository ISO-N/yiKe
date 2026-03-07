// 文件用途：Phase 2 首页高价值回归测试，覆盖今日视图、批量操作与全部任务视图。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/presentation/pages/home/widgets/home_tab_switcher.dart';
import 'package:yike/presentation/providers/home_task_filter_provider.dart';
import 'package:yike/presentation/providers/home_task_tab_provider.dart';
import 'package:yike/presentation/providers/home_time_filter_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/search_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  Future<int> createTodayPending(
    AppHarness harness, {
    required String title,
    String? description,
  }) async {
    return TestDataFactory.createLearningItemWithPlan(
      harness.container,
      title: title,
      description: description,
      learningDate: DateTime.now().subtract(const Duration(days: 1)),
    );
  }

  Future<void> seedHomeScenarios(AppHarness harness) async {
    final reviewRepo = harness.container.read(reviewTaskRepositoryProvider);

    await TestDataFactory.createLearningItemWithPlan(
      harness.container,
      title: '逾期任务 A',
      description: '需要优先处理',
      learningDate: DateTime.now().subtract(const Duration(days: 5)),
    );

    final longDescription = <String>[
      '第一行说明',
      '第二行说明',
      '第三行说明',
      '第四行说明',
      '第五行说明',
      '第六行说明',
    ].join('\n');
    await createTodayPending(
      harness,
      title: '长描述任务',
      description: longDescription,
    );

    final doneItemId = await createTodayPending(
      harness,
      title: '已完成任务 A',
      description: '完成后可从首页撤销',
    );
    final donePlan = await reviewRepo.getReviewPlan(doneItemId);
    await reviewRepo.completeTask(donePlan.first.taskId);

    final skippedItemId = await createTodayPending(
      harness,
      title: '已跳过任务 A',
      description: '跳过后也会在首页展示',
    );
    final skippedPlan = await reviewRepo.getReviewPlan(skippedItemId);
    await reviewRepo.skipTask(skippedPlan.first.taskId);
  }

  testWidgets('首页今日视图支持搜索、详情跳转与已完成状态撤销', (tester) async {
    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home?tab=today&focus=overdue',
      size: const Size(1440, 960),
    );
    await seedHomeScenarios(harness);
    await harness.container.read(homeTasksProvider.notifier).load();
    await tester.pumpAndSettle();

    final homeState = harness.container.read(homeTasksProvider);
    expect(homeState.overduePending, isNotEmpty);
    expect(
      homeState.todayCompleted.any((task) => task.title == '已完成任务 A'),
      isTrue,
    );
    expect(
      homeState.todaySkipped.any((task) => task.title == '已跳过任务 A'),
      isTrue,
    );

    expect(find.text('今日复习总览'), findsOneWidget);
    expect(find.text('逾期任务'), findsWidgets);
    expect(find.text('今日待复习'), findsWidgets);

    harness.container.read(homeTaskFilterProvider.notifier).state =
        ReviewTaskFilter.all;
    await tester.pumpAndSettle();
    expect(find.text('今日已完成'), findsWidgets);
    expect(find.text('今日已跳过'), findsWidgets);

    await tester.enterText(find.widgetWithText(TextField, '搜索学习内容...'), '长描述');
    await tester.pumpAndSettle();
    expect(find.textContaining('搜索结果（最多 50 条）'), findsOneWidget);
    expect(harness.container.read(learningSearchQueryProvider), '长描述');
  });

  testWidgets('首页支持批量完成今日待复习任务', (tester) async {
    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: const Size(390, 844),
      seed: (container) async {
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: '批量任务一',
          description: '第一条',
          learningDate: DateTime.now().subtract(const Duration(days: 1)),
        );
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: '批量任务二',
          description: '第二条',
          learningDate: DateTime.now().subtract(const Duration(days: 1)),
        );
      },
    );
    await harness.container.read(homeTasksProvider.notifier).load();
    await tester.pumpAndSettle();

    final taskIds = harness.container
        .read(homeTasksProvider)
        .todayPending
        .map((task) => task.taskId)
        .take(2)
        .toList();
    expect(taskIds, hasLength(2));

    final notifier = harness.container.read(homeTasksProvider.notifier);
    notifier.toggleSelectionMode();
    notifier.toggleSelected(taskIds[0]);
    notifier.toggleSelected(taskIds[1]);
    await tester.pumpAndSettle();
    expect(find.text('已选择 2 项'), findsOneWidget);
    expect(find.text('完成所选'), findsOneWidget);

    await tester.tap(find.text('完成所选'));
    await tester.pumpAndSettle();

    final homeState = harness.container.read(homeTasksProvider);
    expect(
      homeState.todayCompleted.map((task) => task.title),
      containsAll(<String>['批量任务一', '批量任务二']),
    );
    expect(find.text('已完成所选任务'), findsOneWidget);
  });

  testWidgets('首页全部视图支持时间筛选切换', (tester) async {
    final today = startOfDay(DateTime.now());

    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home?tab=all',
      size: const Size(1280, 900),
      seed: (container) async {
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: '历史任务',
          description: '应该出现在今天前筛选',
          learningDate: today.subtract(const Duration(days: 5)),
        );
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: '未来任务',
          description: '应该出现在今天后筛选',
          learningDate: today,
        );
      },
    );
    await harness.container.read(taskHubProvider.notifier).refresh();
    await tester.pumpAndSettle();
    expect(harness.container.read(taskHubProvider).items, isNotEmpty);
    harness.container.read(homeTaskTabProvider.notifier).state =
        HomeTaskTab.all;
    await tester.pumpAndSettle();

    expect(find.text('时间筛选'), findsOneWidget);

    await tester.tap(find.text('今天前'));
    await tester.pumpAndSettle();
    expect(
      harness.container.read(homeTimeFilterProvider),
      HomeTimeFilter.beforeToday,
    );

    await tester.tap(find.text('今天后'));
    await tester.pumpAndSettle();
    expect(
      harness.container.read(homeTimeFilterProvider),
      HomeTimeFilter.afterToday,
    );

    await tester.enterText(find.widgetWithText(TextField, '搜索学习内容...'), '未来');
    await tester.pumpAndSettle();
    expect(find.textContaining('搜索结果（最多 50 条）'), findsOneWidget);
    expect(harness.container.read(learningSearchQueryProvider), '未来');
  });

}
