// 文件用途：Phase 2 应用级主链路集成测试，覆盖创建内容、任务状态流转与粘贴导入。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  /// 使用 push 打开弹层/独立页面，确保页面内部 `pop` 能正常返回。
  Future<void> pushRoute(
    WidgetTester tester,
    AppHarness harness,
    String location,
  ) async {
    harness.router.push(location);
    await tester.pumpAndSettle();
  }

  testWidgets('4.1 创建学习内容后会生成任务并在首页与日历展示', (tester) async {
    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: const Size(390, 844),
    );

    const title = 'Phase2 创建链路';
    await TestDataFactory.createLearningItemWithPlan(
      harness.container,
      title: title,
      description: '验证创建后任务展示与跨页面可见性',
      // 将学习日期设为昨天，确保首轮任务出现在今天首页。
      learningDate: DateTime.now().subtract(const Duration(days: 1)),
    );
    await harness.container.read(homeTasksProvider.notifier).load();
    await tester.pumpAndSettle();

    final homeState = harness.container.read(homeTasksProvider);
    expect(
      homeState.todayPending.any((task) => task.title == title),
      isTrue,
    );

    await harness.container.read(calendarProvider.notifier).selectDay(DateTime.now());
    await tester.pumpAndSettle();
    expect(
      harness.container.read(calendarProvider).selectedDayTasks.any(
        (task) => task.title == title,
      ),
      isTrue,
    );
  });

  testWidgets('4.2 完成任务后首页、日历与统计会同步刷新', (tester) async {
    const title = 'Phase2 完成任务';

    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: const Size(390, 844),
      seed: (container) async {
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: title,
          description: '完成后应刷新统计与日历',
          learningDate: DateTime.now().subtract(const Duration(days: 1)),
        );
      },
    );

    final taskId = harness.container
        .read(homeTasksProvider)
        .todayPending
        .firstWhere((task) => task.title == title)
        .taskId;

    await harness.container.read(homeTasksProvider.notifier).completeTask(taskId);
    await tester.pumpAndSettle();

    final homeState = harness.container.read(homeTasksProvider);
    expect(
      homeState.todayCompleted.any((task) => task.title == title),
      isTrue,
    );
    expect(
      homeState.todayPending.any((task) => task.title == title),
      isFalse,
    );

    await harness.container.read(calendarProvider.notifier).selectDay(DateTime.now());
    await tester.pumpAndSettle();
    final calendarTask = harness.container
        .read(calendarProvider)
        .selectedDayTasks
        .firstWhere((task) => task.title == title);
    expect(calendarTask.status, ReviewTaskStatus.done);

    final statisticsState = harness.container.read(statisticsProvider);
    expect(statisticsState.weekCompleted, 1);
    expect(statisticsState.weekTotal, greaterThanOrEqualTo(1));
  });

  testWidgets('4.2 跳过并撤销任务后首页与日历状态保持一致', (tester) async {
    const title = 'Phase2 跳过撤销';

    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: const Size(390, 844),
      seed: (container) async {
        await TestDataFactory.createLearningItemWithPlan(
          container,
          title: title,
          description: '验证跳过与撤销后的跨页面一致性',
          learningDate: DateTime.now().subtract(const Duration(days: 1)),
        );
      },
    );

    final taskId = harness.container
        .read(homeTasksProvider)
        .todayPending
        .firstWhere((task) => task.title == title)
        .taskId;

    await harness.container.read(homeTasksProvider.notifier).skipTask(taskId);
    await tester.pumpAndSettle();
    expect(
      harness.container.read(homeTasksProvider).todaySkipped.any(
        (task) => task.title == title,
      ),
      isTrue,
    );

    await harness.container.read(calendarProvider.notifier).selectDay(DateTime.now());
    await tester.pumpAndSettle();
    expect(
      harness.container.read(calendarProvider).selectedDayTasks.any(
        (task) =>
            task.title == title && task.status == ReviewTaskStatus.skipped,
      ),
      isTrue,
    );

    await harness.container.read(homeTasksProvider.notifier).undoTaskStatus(taskId);
    await tester.pumpAndSettle();

    final homeState = harness.container.read(homeTasksProvider);
    expect(
      homeState.todayPending.any((task) => task.title == title),
      isTrue,
    );
    expect(
      homeState.todaySkipped.any((task) => task.title == title),
      isFalse,
    );

    await harness.container.read(calendarProvider.notifier).selectDay(DateTime.now());
    await tester.pumpAndSettle();
    expect(
      harness.container.read(calendarProvider).selectedDayTasks.any(
        (task) =>
            task.title == title && task.status == ReviewTaskStatus.pending,
      ),
      isTrue,
    );
  });

  testWidgets('4.3 粘贴导入预览后可回填录入页并保存成功', (tester) async {
    final harness = await pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: const Size(390, 844),
    );

    await pushRoute(tester, harness, '/input');
    await tester.tap(find.text('批量导入'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('粘贴导入'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '# 导入条目一\n描述一\n- 子任务甲\n\n# 导入条目二\n描述二\n- 子任务乙\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    expect(find.text('导入条目一'), findsOneWidget);
    expect(find.text('导入条目二'), findsOneWidget);

    await tester.tap(find.text('导入'));
    await tester.pumpAndSettle();

    // InputPage 默认自带一条空白草稿；导入返回后删除该空白项，确保只保存导入结果。
    await tester.tap(find.byTooltip('删除条目').first);
    await tester.pumpAndSettle();

    await tester.tap(find.text('保存'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 800));

    expect(find.textContaining('保存成功：2 条'), findsOneWidget);

    final homeState = harness.container.read(homeTasksProvider);
    final allItems = await harness.container.read(learningItemRepositoryProvider).getAll();
    final allTasks = await harness.container.read(reviewTaskRepositoryProvider).getAllTasks();
    expect(
      allItems.map((item) => item.title),
      containsAll(<String>['导入条目一', '导入条目二']),
    );
    expect(
      allTasks.length,
      20,
    );
    expect(homeState.errorMessage, isNull);
  });
}
