// 文件用途：基础 Widget 覆盖率补充测试，覆盖任务上下文菜单与同步启动器的关键分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/widgets/sync_bootstrap.dart';
import 'package:yike/presentation/widgets/task_context_menu.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Presentation widgets', () {
    testWidgets('SyncBootstrap 在测试环境下会直接渲染 child', (tester) async {
      await tester.pumpWidget(
        const ProviderScope(
          child: MaterialApp(
            home: SyncBootstrap(child: Text('bootstrap-child')),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('bootstrap-child'), findsOneWidget);
    });

    testWidgets('showTaskContextMenu 会按任务状态生成不同菜单项', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(home: Scaffold(body: SizedBox(width: 400, height: 300))),
      );
      await tester.pumpAndSettle();

      final context = tester.element(find.byType(Scaffold));

      // pending：包含 完成/跳过/编辑/查看详情。
      final pendingFuture = showTaskContextMenu(
        context: context,
        globalPosition: const Offset(20, 20),
        status: ReviewTaskStatus.pending,
      );
      await tester.pumpAndSettle();
      expect(find.text('完成'), findsOneWidget);
      expect(find.text('跳过'), findsOneWidget);
      expect(find.text('编辑'), findsOneWidget);
      expect(find.text('查看详情'), findsOneWidget);

      await tester.tap(find.text('查看详情'));
      await tester.pumpAndSettle();
      expect(await pendingFuture, TaskContextMenuAction.viewDetail);

      // done/skipped：包含 撤销/编辑/查看详情。
      final doneFuture = showTaskContextMenu(
        context: context,
        globalPosition: const Offset(40, 40),
        status: ReviewTaskStatus.done,
      );
      await tester.pumpAndSettle();
      expect(find.text('撤销'), findsOneWidget);
      expect(find.text('编辑'), findsOneWidget);
      expect(find.text('查看详情'), findsOneWidget);

      await tester.tap(find.text('撤销'));
      await tester.pumpAndSettle();
      expect(await doneFuture, TaskContextMenuAction.undo);
    });
  });
}

