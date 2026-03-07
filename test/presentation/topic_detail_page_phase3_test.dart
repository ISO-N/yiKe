// 文件用途：TopicDetailPage Phase 3 Widget 测试，覆盖主题进度展示、添加关联、移除关联与异常状态。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/pages/topics/topic_detail_page.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  Future<void> pumpPage(
    WidgetTester tester, {
    required int topicId,
  }) async {
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(home: TopicDetailPage(topicId: topicId)),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  }

  Future<void> pumpUntil(
    WidgetTester tester,
    bool Function() predicate, {
    int maxAttempts = 60,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      await tester.pump(const Duration(milliseconds: 100));
      if (predicate()) return;
    }
    fail('等待主题详情稳定超时');
  }

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  group('TopicDetailPage Phase3', () {
    testWidgets('会展示主题进度并支持添加与移除关联内容', (tester) async {
      final seedContainer = ProviderContainer(
        overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
      );
      addTearDown(seedContainer.dispose);

      final useCase = seedContainer.read(manageTopicUseCaseProvider);
      final topic = await useCase.create(
        const TopicParams(name: 'Phase3 主题', description: '用于详情页测试'),
      );
      final itemA = await seedContainer.read(createLearningItemUseCaseProvider).execute(
        CreateLearningItemParams(
          title: '已完成内容',
          description: '第一个内容',
          reviewIntervals: <ReviewIntervalConfigEntity>[
            ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
          ],
        ),
      );
      final itemB = await seedContainer.read(createLearningItemUseCaseProvider).execute(
        CreateLearningItemParams(
          title: '待复习内容',
          description: '第二个内容',
          reviewIntervals: <ReviewIntervalConfigEntity>[
            ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
          ],
        ),
      );
      final itemC = await seedContainer.read(createLearningItemUseCaseProvider).execute(
        CreateLearningItemParams(
          title: '待添加内容',
          description: '第三个内容',
          reviewIntervals: <ReviewIntervalConfigEntity>[
            ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
          ],
        ),
      );

      await useCase.addItemToTopic(topic.id!, itemA.item.id!);
      await useCase.addItemToTopic(topic.id!, itemB.item.id!);

      final taskRepo = seedContainer.read(reviewTaskRepositoryProvider);
      final plan = await taskRepo.getReviewPlan(itemA.item.id!);
      await taskRepo.completeTask(plan.first.taskId);

      await pumpPage(tester, topicId: topic.id!);
      await pumpUntil(
        tester,
        () => find.text('Phase3 主题').evaluate().isNotEmpty,
      );

      expect(find.text('Phase3 主题'), findsWidgets);
      expect(find.text('用于详情页测试'), findsOneWidget);
      expect(find.textContaining('2 条内容   1/2 完成'), findsOneWidget);
      expect(find.text('已完成内容'), findsOneWidget);
      expect(find.text('待复习内容'), findsOneWidget);

      await tester.tap(find.text('+ 添加关联内容'));
      await tester.pumpAndSettle();
      expect(find.text('添加关联内容'), findsOneWidget);
      await tester.enterText(find.widgetWithText(TextField, '搜索标题'), '待添加');
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(CheckboxListTile, '待添加内容'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('添加'));
      await tester.pumpAndSettle();
      await pumpUntil(
        tester,
        () => find.text('待添加内容').evaluate().isNotEmpty,
      );
      expect(find.textContaining('3 条内容'), findsOneWidget);

      await tester.drag(find.text('待复习内容'), const Offset(-500, 0));
      await tester.pumpAndSettle();
      expect(find.text('移除关联'), findsOneWidget);
      await tester.tap(find.text('移除'));
      await tester.pumpAndSettle();
      await pumpUntil(
        tester,
        () => find.text('待复习内容').evaluate().isEmpty,
      );
      expect(find.text('待复习内容'), findsNothing);

      final reloadedTopic = await container.read(manageTopicUseCaseProvider).getById(
            topic.id!,
          );
      expect(reloadedTopic?.itemIds, containsAll(<int>[itemA.item.id!, itemC.item.id!]));
      expect(reloadedTopic?.itemIds, isNot(contains(itemB.item.id!)));
    });

    testWidgets('主题不存在时展示错误卡片', (tester) async {
      await pumpPage(tester, topicId: 999999);
      await pumpUntil(
        tester,
        () => find.textContaining('加载失败').evaluate().isNotEmpty,
      );

      expect(find.textContaining('加载失败：主题不存在'), findsOneWidget);
    });
  });
}
