// 文件用途：设置子页面 Phase 3 Widget 测试，覆盖目标/番茄钟设置的输入校验、保存与刷新链路。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/presentation/pages/settings/goal_settings_page.dart';
import 'package:yike/presentation/pages/settings/pomodoro_settings_page.dart';

import '../helpers/test_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const secureStorageChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  setUpAll(() {
    // 说明：visibility_detector 默认 500ms 延迟上报会导致测试结束残留 Timer 或 settle 超时。
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  Future<ProviderContainer> pumpPage(
    WidgetTester tester,
    Widget page,
  ) async {
    // 说明：设置仓储会读取 flutter_secure_storage；在 widget_test 环境下统一 mock 为抛异常，
    // 触发 SecureStorageService 的内存兜底路径，避免平台通道卡死。
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secureStorageChannel, (call) async {
      throw PlatformException(code: 'no_plugin');
    });
    addTearDown(() {
      messenger.setMockMethodCallHandler(secureStorageChannel, null);
    });

    final db = createInMemoryDatabase();
    addTearDown(() async => db.close());

    final container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
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

    return container;
  }

  Future<void> pumpUntilVisible(
    WidgetTester tester,
    Finder finder, {
    int maxAttempts = 200,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      await tester.pump(const Duration(milliseconds: 50));
      if (finder.evaluate().isNotEmpty) return;
    }
    fail('等待界面状态稳定超时');
  }

  group('Settings subpages Phase3', () {
    testWidgets('GoalSettingsPage 支持启用目标、输入校验与保存提示', (tester) async {
      await pumpPage(tester, const GoalSettingsPage());

      expect(find.text('学习目标'), findsOneWidget);
      expect(find.text('目标说明'), findsOneWidget);
      await pumpUntilVisible(tester, find.widgetWithText(SwitchListTile, '每日完成目标'));
      await pumpUntilVisible(tester, find.text('设置每日目标值'));

      await tester.tap(find.byTooltip('刷新'));
      await tester.pump(const Duration(milliseconds: 300));

      await tester.tap(find.text('设置每日目标值'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('每日完成目标'), findsWidgets);

      // 输入非法值（越界）会提示并保持对话框不关闭。
      await tester.enterText(find.byType(TextFormField), '0');
      await tester.tap(find.text('确定'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('请输入有效范围内的数字'), findsOneWidget);

      await tester.enterText(find.byType(TextFormField), '5');
      await tester.tap(find.text('确定'));
      await tester.pump(const Duration(milliseconds: 400));
      await pumpUntilVisible(tester, find.text('目标设置已保存'));

      // 关闭每日目标（默认是启用状态）。
      await tester.tap(find.widgetWithText(SwitchListTile, '每日完成目标'));
      await tester.pump(const Duration(milliseconds: 400));
      await pumpUntilVisible(tester, find.text('目标设置已保存'));
      expect(find.text('设置每日目标值'), findsNothing);

      // 重新开启每日目标。
      await tester.tap(find.widgetWithText(SwitchListTile, '每日完成目标'));
      await tester.pump(const Duration(milliseconds: 400));
      await pumpUntilVisible(tester, find.text('设置每日目标值'));
    });

    testWidgets('PomodoroSettingsPage 支持输入校验、保存与刷新', (tester) async {
      await pumpPage(tester, const PomodoroSettingsPage());

      expect(find.text('番茄钟设置'), findsOneWidget);
      expect(find.text('配置说明'), findsOneWidget);

      await tester.tap(find.byTooltip('刷新'));
      await tester.pump(const Duration(milliseconds: 300));

      await tester.tap(find.text('工作时长'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('工作时长'), findsWidgets);

      await tester.enterText(find.byType(TextFormField), '0');
      await tester.tap(find.text('确定'));
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.text('请输入有效范围内的数字'), findsOneWidget);

      await tester.enterText(find.byType(TextFormField), '30');
      await tester.tap(find.text('确定'));
      await tester.pump(const Duration(milliseconds: 400));
      // 保存后列表应刷新为新的分钟数（并弹出 SnackBar）。
      await pumpUntilVisible(tester, find.text('30 分钟'));

      expect(find.text('专注结束后自动开始休息'), findsOneWidget);
      expect(find.text('休息结束后自动开始专注'), findsOneWidget);
    });
  });
}
