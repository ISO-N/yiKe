// 文件用途：Phase 2 任务详情页高价值测试，覆盖编辑、计划操作与空状态。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/pages/tasks/task_detail_sheet.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/task_detail_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  Future<void> pumpDetailSheet(
    WidgetTester tester,
    TaskDetailSheet page,
  ) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    tester.view.physicalSize = const Size(1280, 900);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(home: page),
      ),
    );
    await tester.pumpAndSettle();
  }

  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  Future<void> waitUntil(
    bool Function() predicate, {
    int maxAttempts = 80,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      if (predicate()) return;
      await Future<void>.delayed(Duration.zero);
    }
    fail('等待状态稳定超时');
  }

  void keepDetailDependenciesAlive() {
    final homeSub = container.listen<HomeTasksState>(
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
    final taskHubSub = container.listen<TaskHubState>(
      taskHubProvider,
      (previous, next) {},
      fireImmediately: true,
    );
    addTearDown(homeSub.close);
    addTearDown(calendarSub.close);
    addTearDown(statisticsSub.close);
    addTearDown(taskHubSub.close);
  }

  Future<int> createLearningItemWithPlan({
    required String title,
    String? description,
    List<String> subtasks = const <String>[],
    List<String> tags = const <String>[],
  }) async {
    final result = await container
        .read(createLearningItemUseCaseProvider)
        .execute(
          CreateLearningItemParams(
            title: title,
            description: description,
            subtasks: subtasks,
            tags: tags,
            learningDate: startOfDay(DateTime.now()),
            reviewIntervals: <ReviewIntervalConfigEntity>[
              ReviewIntervalConfigEntity(
                round: 1,
                intervalDays: 1,
                enabled: true,
              ),
              ReviewIntervalConfigEntity(
                round: 2,
                intervalDays: 3,
                enabled: true,
              ),
              ReviewIntervalConfigEntity(
                round: 3,
                intervalDays: 7,
                enabled: true,
              ),
            ],
          ),
        );
    return result.item.id!;
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

  testWidgets('任务详情支持编辑基本信息、描述、子任务与轮次操作', (tester) async {
    keepDetailDependenciesAlive();
    final itemId = await createLearningItemWithPlan(
      title: '任务详情原始标题',
      description: '原始描述',
      subtasks: const <String>['原始子任务'],
      tags: const <String>['旧标签'],
    );
    final topic = await container
        .read(manageTopicUseCaseProvider)
        .create(const TopicParams(name: '详情主题', description: '用于任务详情测试'));
    await container
        .read(manageTopicUseCaseProvider)
        .addItemToTopic(topic.id!, itemId);

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: itemId, openEditOnLoad: true),
    );

    expect(find.text('编辑基本信息'), findsOneWidget);
    await tester.enterText(
      find.widgetWithText(TextField, '任务名（必填）'),
      '任务详情更新标题',
    );
    await tester.enterText(
      find.widgetWithText(TextField, '标签（选填，用逗号分隔）'),
      '标签甲, 标签乙',
    );
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    expect(find.text('任务详情更新标题'), findsWidgets);
    expect(find.text('标签甲'), findsOneWidget);
    expect(find.text('标签乙'), findsOneWidget);
    expect(find.text('详情主题'), findsOneWidget);

    await tester.tap(find.text('编辑描述'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, '更新后的描述内容');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();
    expect(find.text('更新后的描述内容'), findsOneWidget);

    await tester.tap(find.text('新增'));
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, '新增子任务');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();
    expect(find.text('新增子任务'), findsOneWidget);

    await tester.tap(find.byTooltip('编辑').last);
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(TextField).last, '新增子任务-更新');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();
    expect(find.text('新增子任务-更新'), findsOneWidget);

    await tester.tap(find.byTooltip('删除').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('删除'));
    await tester.pumpAndSettle();
    expect(find.text('新增子任务-更新'), findsNothing);

    await tester.tap(find.text('查看计划'));
    await tester.pumpAndSettle();
    expect(find.text('完整复习计划'), findsOneWidget);
    expect(find.textContaining('第1轮'), findsWidgets);
    Navigator.of(tester.element(find.text('完整复习计划'))).pop();
    await tester.pumpAndSettle();

    await tester.tap(find.text('增加轮次'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确认增加'));
    await tester.pumpAndSettle();
    await waitUntil(
      () => container.read(taskDetailProvider(itemId)).plan.length == 4,
    );
    expect(container.read(taskDetailProvider(itemId)).plan.length, 4);

    await tester.tap(find.text('减少轮次'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确认减少'));
    await tester.pumpAndSettle();
    await waitUntil(
      () => container.read(taskDetailProvider(itemId)).plan.length == 3,
    );
    expect(container.read(taskDetailProvider(itemId)).plan.length, 3);

    await tester.tap(find.text('停用'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('确认停用'));
    await tester.pumpAndSettle();
    expect(find.textContaining('已停用'), findsWidgets);

    final state = container.read(taskDetailProvider(itemId));
    expect(state.item?.isDeleted, isTrue);
  });

  testWidgets('任务详情在内容不存在时展示空状态', (tester) async {
    await pumpDetailSheet(
      tester,
      const TaskDetailSheet(learningItemId: 999999),
    );

    await waitUntil(
      () => !container.read(taskDetailProvider(999999)).isLoading,
    );
    await tester.pumpAndSettle();

    expect(find.text('学习内容不存在或已被移除'), findsOneWidget);
    expect(find.text('返回'), findsOneWidget);
  });

  testWidgets('任务详情支持撤销已完成任务并识别已完成轮次的减少提示', (tester) async {
    keepDetailDependenciesAlive();
    final itemId = await createLearningItemWithPlan(
      title: '撤销任务状态',
      description: '用于撤销测试',
      subtasks: const <String>['子任务甲'],
    );

    final reviewRepo = container.read(reviewTaskRepositoryProvider);
    final initialPlan = await reviewRepo.getReviewPlan(itemId);
    await reviewRepo.completeTask(initialPlan.first.taskId);
    await reviewRepo.completeTask(initialPlan.last.taskId);

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: itemId),
    );

    await tester.tap(find.widgetWithText(OutlinedButton, '撤销').first);
    await tester.pumpAndSettle();
    expect(find.text('撤销任务状态？'), findsOneWidget);
    await tester.tap(find.text('确认撤销'));
    await tester.pumpAndSettle();
    await waitUntil(
      () => container
          .read(taskDetailProvider(itemId))
          .plan
          .where((task) => task.status == ReviewTaskStatus.done)
          .length ==
          1,
    );

    await tester.tap(find.text('减少轮次'));
    await tester.pumpAndSettle();
    expect(find.textContaining('已完成'), findsWidgets);
    await tester.tap(find.text('取消'));
    await tester.pumpAndSettle();
  });

  testWidgets('任务详情会展示轮次上下限文案', (tester) async {
    final singleRoundId = await container
        .read(createLearningItemUseCaseProvider)
        .execute(
          CreateLearningItemParams(
            title: '单轮任务',
            learningDate: startOfDay(DateTime.now()),
            reviewIntervals: <ReviewIntervalConfigEntity>[
              ReviewIntervalConfigEntity(
                round: 1,
                intervalDays: 1,
                enabled: true,
              ),
            ],
          ),
        )
        .then((result) => result.item.id!);

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: singleRoundId),
    );
    expect(find.text('已达下限'), findsOneWidget);

    final maxRoundResult = await container
        .read(createLearningItemUseCaseProvider)
        .execute(
          CreateLearningItemParams(
            title: '满轮任务',
            learningDate: startOfDay(DateTime.now()),
            reviewIntervals: List<ReviewIntervalConfigEntity>.generate(
              10,
              (index) => ReviewIntervalConfigEntity(
                round: index + 1,
                intervalDays: index + 1,
                enabled: true,
              ),
            ),
          ),
        );

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: maxRoundResult.item.id!),
    );
    expect(find.text('已达上限'), findsOneWidget);
  });

  testWidgets('任务详情在停用后进入只读展示并显示空标签信息', (tester) async {
    final itemId = await createLearningItemWithPlan(
      title: '只读详情',
    );
    await container.read(learningItemRepositoryProvider).deactivate(itemId);

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: itemId),
    );

    expect(find.textContaining('已停用'), findsOneWidget);
    expect(find.text('未关联'), findsOneWidget);
    expect(find.text('标签：未设置'), findsOneWidget);

    final editButton = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, '编辑').first,
    );
    final editDescriptionButton = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, '编辑描述'),
    );
    final adjustPlanButton = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, '调整计划'),
    );
    final deactivateButton = tester.widget<OutlinedButton>(
      find.widgetWithText(OutlinedButton, '停用'),
    );

    expect(editButton.onPressed, isNull);
    expect(editDescriptionButton.onPressed, isNull);
    expect(adjustPlanButton.onPressed, isNull);
    expect(deactivateButton.onPressed, isNull);
  });

  testWidgets('任务详情编辑基本信息时支持新建主题并回填关联', (tester) async {
    keepDetailDependenciesAlive();
    final itemId = await createLearningItemWithPlan(
      title: '主题新建测试',
      tags: const <String>['旧标签'],
    );

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: itemId),
    );

    await tester.tap(find.widgetWithText(OutlinedButton, '编辑').first);
    await tester.pumpAndSettle();
    expect(find.text('编辑基本信息'), findsOneWidget);

    await tester.tap(find.widgetWithText(ListTile, '主题'));
    await tester.pumpAndSettle();
    expect(find.text('选择主题（可多选）'), findsOneWidget);
    expect(find.text('暂无主题'), findsOneWidget);

    await tester.tap(find.widgetWithText(OutlinedButton, '新建主题'));
    await tester.pumpAndSettle();
    expect(find.text('新建主题'), findsWidgets);
    await tester.tap(find.text('创建'));
    await tester.pumpAndSettle();
    expect(find.text('主题名称不能为空'), findsOneWidget);

    await tester.tap(find.widgetWithText(OutlinedButton, '新建主题'));
    await tester.pumpAndSettle();
    await tester.enterText(
      find.widgetWithText(TextField, '主题名称（必填）'),
      '新增主题',
    );
    await tester.enterText(
      find.widgetWithText(TextField, '主题描述（选填）'),
      '详情页新建的主题',
    );
    await tester.tap(find.text('创建'));
    await tester.pumpAndSettle();

    expect(find.text('新增主题'), findsOneWidget);
    await tester.tap(find.text('确定'));
    await tester.pumpAndSettle();

    expect(find.text('新增主题'), findsOneWidget);
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();

    expect(find.text('新增主题'), findsWidgets);
  });

  testWidgets('任务详情编辑基本信息时会校验空标题', (tester) async {
    keepDetailDependenciesAlive();
    final itemId = await createLearningItemWithPlan(
      title: '标题校验测试',
    );

    await pumpDetailSheet(
      tester,
      TaskDetailSheet(learningItemId: itemId),
    );

    await tester.tap(find.widgetWithText(OutlinedButton, '编辑').first);
    await tester.pumpAndSettle();

    await tester.enterText(
      find.widgetWithText(TextField, '任务名（必填）'),
      '',
    );
    await tester.tap(find.widgetWithText(FilledButton, '保存'));
    await tester.pumpAndSettle();
    expect(find.text('请输入任务名'), findsOneWidget);
  });
}
