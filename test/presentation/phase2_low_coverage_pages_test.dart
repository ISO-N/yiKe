// 文件用途：Phase 2 低覆盖页面补测，覆盖帮助页、模板页、学习内容详情页与模拟数据生成页。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_template_usecase.dart';
import 'package:yike/presentation/pages/debug/mock_data_generator_page.dart';
import 'package:yike/presentation/pages/help/help_page.dart';
import 'package:yike/presentation/pages/input/template_edit_page.dart';
import 'package:yike/presentation/pages/input/template_sort_page.dart';
import 'package:yike/presentation/pages/input/templates_page.dart';
import 'package:yike/presentation/pages/learning_item/learning_item_detail_page.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/templates_provider.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  /// 等待界面完成异步加载，避免直接断言 FutureBuilder / Provider 中间态。
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

  /// 用统一容器启动页面，保持真实 DB/Provider 装配。
  Future<void> pumpPage(
    WidgetTester tester,
    Widget page, {
    List<Override> overrides = const <Override>[],
  }) async {
    container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        ...overrides,
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(home: page),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  }

  /// 创建一条包含子任务的学习内容，便于详情页走真实查询链路。
  Future<int> createLearningItemForDetail() async {
    final result = await container.read(createLearningItemUseCaseProvider).execute(
      CreateLearningItemParams(
        title: '搜索跳转的学习内容',
        description: '结构化描述',
        subtasks: const <String>['第一条子任务', '第二条子任务'],
        tags: const <String>['详情', '测试'],
        learningDate: DateTime(2026, 3, 6),
        reviewIntervals: <ReviewIntervalConfigEntity>[
          ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ],
      ),
    );
    return result.item.id!;
  }

  /// 保持会被 mock_data_provider 主动 invalidate 的页面 Provider 常驻。
  void keepMockDependenciesAlive() {
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
  }

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  group('Phase2 low coverage pages', () {
    testWidgets('HelpPage 会加载目录与 Markdown 正文', (tester) async {
      await pumpPage(tester, const HelpPage());
      await pumpUntil(tester, () => find.text('目录').evaluate().isNotEmpty);

      expect(find.text('忆刻学习指南'), findsWidgets);
      expect(find.text('科学学习方法，让记忆更牢固'), findsWidgets);

      await tester.tap(find.text('目录'));
      await tester.pumpAndSettle();
      expect(find.text('点击跳转到对应章节'), findsOneWidget);
      expect(find.text('原理一：艾宾浩斯遗忘曲线'), findsWidgets);
    });

    testWidgets('TemplatesPage 支持查看模板并进入新建、排序与删除流程', (tester) async {
      await pumpPage(tester, const TemplatesPage());
      final notifier = container.read(templatesProvider.notifier);

      await notifier.create(
        const TemplateParams(
          name: '模板一',
          titlePattern: '模板标题 {date}',
          notePattern: '模板备注',
          tags: <String>['模板', '测试'],
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('模板管理'), findsOneWidget);
      expect(find.text('模板一'), findsOneWidget);
      expect(find.text('模板标题 {date}'), findsOneWidget);

      await tester.tap(find.byTooltip('新建'));
      await tester.pumpAndSettle();
      expect(find.byType(TemplateEditPage), findsOneWidget);

      Navigator.of(tester.element(find.byType(TemplateEditPage))).pop();
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('排序'));
      await tester.pumpAndSettle();
      expect(find.byType(TemplateSortPage), findsOneWidget);

      Navigator.of(tester.element(find.byType(TemplateSortPage))).pop();
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('删除'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('删除').last);
      await tester.pumpAndSettle();

      expect(find.text('模板一'), findsNothing);
      expect(find.textContaining('还没有模板'), findsOneWidget);
    });

    testWidgets('LearningItemDetailPage 会展示描述、子任务、标签与元信息', (tester) async {
      await pumpPage(tester, const SizedBox.shrink());
      final itemId = await createLearningItemForDetail();

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(home: LearningItemDetailPage(itemId: itemId)),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      await pumpUntil(
        tester,
        () => find.text('搜索跳转的学习内容').evaluate().isNotEmpty,
      );

      expect(find.text('学习内容详情'), findsOneWidget);
      expect(find.text('标题'), findsOneWidget);
      expect(find.text('结构化描述'), findsOneWidget);
      expect(find.text('子任务'), findsOneWidget);
      expect(find.text('第一条子任务'), findsOneWidget);
      expect(find.text('第二条子任务'), findsOneWidget);
      expect(find.text('信息'), findsOneWidget);
      expect(find.text('标签'), findsOneWidget);
      expect(find.text('详情'), findsOneWidget);
      expect(find.text('测试'), findsOneWidget);
    });

    testWidgets('MockDataGeneratorPage 支持修改配置、生成与清理模拟数据', (tester) async {
      await pumpPage(tester, const MockDataGeneratorPage());
      keepMockDependenciesAlive();

      expect(find.text('模拟数据生成器'), findsOneWidget);
      expect(find.text('配置'), findsOneWidget);
      expect(find.text('操作'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, '学习内容数量（1-100）'),
        '2',
      );
      await tester.enterText(
        find.widgetWithText(TextField, '复习任务数量（1-500）'),
        '2',
      );
      await tester.tap(find.text('随机'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('自定义').last);
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '自定义模板前缀'),
        '联调',
      );

      await tester.tap(find.text('生成数据'));
      await tester.pumpAndSettle();
      expect(find.textContaining('已生成：学习内容'), findsOneWidget);

      await tester.ensureVisible(find.text('清理模拟数据（按 Mock 标记）'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('清理模拟数据（按 Mock 标记）'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确认'));
      await tester.pumpAndSettle();
      expect(find.textContaining('已清理：学习内容'), findsOneWidget);
    });
  });
}
