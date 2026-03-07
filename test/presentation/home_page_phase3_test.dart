// 文件用途：HomePage Phase 3 Widget 测试，覆盖概览 CTA、高负载提示、批量跳过与通知权限引导持久化。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/providers/notification_permission_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:visibility_detector/visibility_detector.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    // 说明：visibility_detector 默认 500ms 延迟上报会导致测试结束残留 Timer。
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  /// 返回本地自然日零点，便于按业务口径构造 scheduledDate。
  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  /// 插入学习内容，供首页走真实数据库查询链路。
  Future<int> insertLearningItem(
    AppDatabase db, {
    required String title,
    String? description,
    List<String> tags = const <String>[],
    required DateTime learningDate,
  }) {
    return db.into(db.learningItems).insert(
      LearningItemsCompanion.insert(
        title: title,
        description: drift.Value(description),
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

  group('HomePage Phase3', () {
    testWidgets('首页概览支持快速录入跳转', (tester) async {
      await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 844),
      );

      expect(find.text('今日复习总览'), findsOneWidget);
      expect(find.text('查看全部任务'), findsOneWidget);
      expect(find.text('快速录入'), findsOneWidget);

      await tester.tap(find.text('快速录入'));
      await tester.pumpAndSettle();

      expect(find.text('今天学了什么？'), findsOneWidget);
    });

    testWidgets('高负载任务场景下支持批量跳过', (tester) async {
      final harness = await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 960),
        seed: (container) async {
          for (var i = 0; i < 21; i++) {
            await TestDataFactory.createLearningItemWithPlan(
              container,
              title: '高负载任务 ${i + 1}',
              learningDate: DateTime.now().subtract(const Duration(days: 1)),
            );
          }
        },
      );
      await harness.container.read(homeTasksProvider.notifier).load();
      await tester.pumpAndSettle();

      final pendingCount =
          harness.container.read(homeTasksProvider).todayPending.length +
          harness.container.read(homeTasksProvider).overduePending.length;
      expect(pendingCount, greaterThan(20));
      expect(find.text('批量'), findsOneWidget);

      final taskIds = harness.container
          .read(homeTasksProvider)
          .todayPending
          .map((task) => task.taskId)
          .take(2)
          .toList();
      expect(taskIds, hasLength(2));

      await tester.tap(find.text('批量'));
      await tester.pumpAndSettle();

      final notifier = harness.container.read(homeTasksProvider.notifier);
      notifier.toggleSelected(taskIds[0]);
      notifier.toggleSelected(taskIds[1]);
      await tester.pumpAndSettle();

      expect(find.text('已选择 2 项'), findsOneWidget);
      final skipSelectedButton = find.widgetWithText(OutlinedButton, '跳过所选');
      final button = tester.widget<OutlinedButton>(skipSelectedButton);
      button.onPressed!.call();
      await tester.pumpAndSettle();

      expect(find.text('已跳过所选任务'), findsOneWidget);
      expect(
        harness.container.read(homeTasksProvider).todaySkipped.length,
        greaterThanOrEqualTo(2),
      );
    });

    testWidgets('通知权限未开启时会弹出引导并支持稍后关闭', (tester) async {
      await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 844),
        overrides: <Override>[
          notificationPermissionProvider.overrideWith(
            (ref) async => NotificationPermissionState.disabled,
          ),
        ],
      );

      expect(find.text('开启通知，确保及时复习'), findsOneWidget);
      expect(find.text('不再提示'), findsOneWidget);
      expect(find.text('稍后'), findsOneWidget);
      expect(find.text('去开启'), findsOneWidget);

      await tester.tap(find.widgetWithText(TextButton, '稍后'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('开启通知，确保及时复习'), findsNothing);
    });

    testWidgets(
      '首页支持展示逾期/已完成/已跳过分区，并可从状态标签撤销任务',
      (tester) async {
        final today = startOfDay(DateTime.now());
        final yesterday = today.subtract(const Duration(days: 1));

        late final int doneTaskId;
        late final int skippedTaskId;

        final harness = await pumpYiKeApp(
          tester,
          // 说明：显式带上 tab/focus 参数，覆盖深链解析逻辑。
          initialLocation: '/home?tab=today&focus=overdue',
          size: const Size(390, 960),
          seed: (container) async {
            final db = container.read(appDatabaseProvider);

            final overdueItemId = await insertLearningItem(
              db,
              title: '逾期待复习',
              description: '逾期任务描述',
              tags: const <String>['逾期'],
              learningDate: yesterday,
            );
            await insertReviewTask(
              db,
              learningItemId: overdueItemId,
              reviewRound: 1,
              scheduledDate: yesterday,
              status: ReviewTaskStatus.pending,
            );

            final todayItemId = await insertLearningItem(
              db,
              title: '今日待复习',
              description: '今日任务描述',
              tags: const <String>['今日'],
              learningDate: today,
            );
            await insertReviewTask(
              db,
              learningItemId: todayItemId,
              reviewRound: 2,
              scheduledDate: today,
              status: ReviewTaskStatus.pending,
            );

            final doneItemId = await insertLearningItem(
              db,
              title: '今日已完成',
              tags: const <String>['完成'],
              learningDate: today,
            );
            doneTaskId = await insertReviewTask(
              db,
              learningItemId: doneItemId,
              reviewRound: 1,
              scheduledDate: today,
              status: ReviewTaskStatus.done,
              completedAt: today.add(const Duration(hours: 8)),
            );

            final skippedItemId = await insertLearningItem(
              db,
              title: '今日已跳过',
              tags: const <String>['跳过'],
              learningDate: today,
            );
            skippedTaskId = await insertReviewTask(
              db,
              learningItemId: skippedItemId,
              reviewRound: 1,
              scheduledDate: today,
              status: ReviewTaskStatus.skipped,
              skippedAt: today.add(const Duration(hours: 9)),
            );
          },
        );

        await harness.container.read(homeTasksProvider.notifier).load();
        await tester.pumpAndSettle();

        expect(find.text('逾期任务'), findsOneWidget);
        expect(find.text('今日待复习'), findsOneWidget);

        // 说明：首页默认筛选为“待复习”，因此要先切换到 done/skipped 才会渲染对应卡片。
        expect(find.text('已完成 1'), findsOneWidget);
        expect(find.text('已跳过 1'), findsOneWidget);

        await tester.tap(find.text('已完成 1'));
        await tester.pumpAndSettle();
        expect(find.textContaining('今日已完成（第1次）'), findsOneWidget);

        // 点击“已完成”状态标签触发撤销弹窗（覆盖“点击状态标签撤销”的交互）。
        final doneCard = find.ancestor(
          of: find.textContaining('今日已完成（第1次）'),
          matching: find.byType(InkWell),
        );
        await tester.tap(
          find.descendant(of: doneCard.first, matching: find.text('已完成')),
        );
        await tester.pumpAndSettle();
        expect(find.text('撤销任务状态？'), findsOneWidget);
        await tester.tap(find.text('确认撤销'));
        await tester.pumpAndSettle();
        expect(find.text('已撤销'), findsOneWidget);

        // 额外验证：撤销后 Provider 状态会反映为 pending。
        final doneAfterUndo = harness.container
            .read(homeTasksProvider)
            .todayPending
            .any((task) => task.taskId == doneTaskId);
        expect(doneAfterUndo, isTrue);

        // 点击“已跳过”状态标签触发撤销。
        await tester.ensureVisible(find.text('已跳过 1'));
        await tester.tap(find.text('已跳过 1'));
        await tester.pumpAndSettle();
        expect(find.textContaining('今日已跳过（第1次）'), findsOneWidget);

        final skippedCard = find.ancestor(
          of: find.textContaining('今日已跳过（第1次）'),
          matching: find.byType(InkWell),
        );
        await tester.tap(
          find.descendant(of: skippedCard.first, matching: find.text('已跳过')),
        );
        await tester.pumpAndSettle();
        expect(find.text('撤销任务状态？'), findsOneWidget);
        await tester.tap(find.text('确认撤销'));
        await tester.pumpAndSettle();

        final skippedAfterUndo = harness.container
            .read(homeTasksProvider)
            .todayPending
            .any((task) => task.taskId == skippedTaskId);
        expect(skippedAfterUndo, isTrue);
      },
    );

    testWidgets(
      '长按任务卡片可打开上下文菜单并进入编辑详情路由',
      (tester) async {
        final today = startOfDay(DateTime.now());

        await pumpYiKeApp(
          tester,
          initialLocation: '/home',
          size: const Size(1280, 900),
          seed: (container) async {
            final db = container.read(appDatabaseProvider);
            final itemId = await insertLearningItem(
              db,
              title: '长按打开菜单的任务',
              learningDate: today,
            );
            await insertReviewTask(
              db,
              learningItemId: itemId,
              reviewRound: 1,
              scheduledDate: today,
              status: ReviewTaskStatus.pending,
            );
          },
        );
        await tester.pumpAndSettle();

        await tester.longPress(find.text('长按打开菜单的任务（第1次）'));
        await tester.pumpAndSettle();

        expect(find.text('完成'), findsOneWidget);
        expect(find.text('跳过'), findsOneWidget);
        expect(find.text('编辑'), findsOneWidget);

        await tester.tap(find.text('编辑'));
        await tester.pumpAndSettle();

        // 说明：桌面端详情页会以 dialog/Sheet 形式呈现（仍然是详情页内容）。
        expect(find.text('编辑基本信息'), findsOneWidget);
        expect(find.text('主题'), findsWidgets);
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.windows,
      }),
    );
  });
}
