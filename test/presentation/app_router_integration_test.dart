// 文件用途：生产路由集成测试，直接验证 app_router 的关键入口、深链兼容与桌面/移动端弹层分支。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/pages/tasks/task_detail_sheet.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  testWidgets('生产路由支持 /calendar?openStats=1 重定向到统计页', (tester) async {
    await pumpYiKeApp(tester, initialLocation: '/calendar?openStats=1');

    expect(find.text('学习统计'), findsOneWidget);
    expect(find.text('统计'), findsAtLeastNWidgets(1));
  });

  testWidgets('移动端任务详情路由使用底部 Sheet 形态展示', (tester) async {
    late final int itemId;
    final harness = await pumpYiKeApp(
      tester,
      size: const Size(390, 844),
      seed: (container) async {
        itemId = await TestDataFactory.createLearningItemWithPlan(container);
      },
    );
    await goToRoute(tester, harness, '/tasks/detail/$itemId');

    expect(find.byType(TaskDetailSheet), findsOneWidget);
    expect(
      find.ancestor(
        of: find.byType(TaskDetailSheet),
        matching: find.byType(FractionallySizedBox),
      ),
      findsAtLeastNWidgets(1),
    );
  });

  testWidgets('桌面端任务详情路由使用对话框形态展示', (tester) async {
    late final int itemId;
    final harness = await pumpYiKeApp(
      tester,
      size: const Size(1280, 900),
      seed: (container) async {
        itemId = await TestDataFactory.createLearningItemWithPlan(container);
      },
    );
    await goToRoute(tester, harness, '/tasks/detail/$itemId');

    expect(find.byType(TaskDetailSheet), findsOneWidget);
    expect(
      find.ancestor(
        of: find.byType(TaskDetailSheet),
        matching: find.byType(ConstrainedBox),
      ),
      findsAtLeastNWidgets(1),
    );
  });
}
