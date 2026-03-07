// 文件用途：Phase 2 纯 Widget/桌面热点测试，覆盖目标卡片、骨架屏、完成动画、标题栏与快捷键。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/repositories/settings_repository.dart';
import 'package:yike/domain/repositories/ui_preferences_repository.dart';
import 'package:yike/presentation/providers/goal_provider.dart';
import 'package:yike/presentation/widgets/completion_animation.dart';
import 'package:yike/presentation/widgets/desktop_shortcuts.dart';
import 'package:yike/presentation/widgets/desktop_title_bar.dart';
import 'package:yike/presentation/widgets/goal_progress_card.dart';
import 'package:yike/presentation/widgets/review_preview_panel.dart';
import 'package:yike/presentation/widgets/skeleton_loader.dart';
import 'package:yike/presentation/widgets/shortcut_actions_scope.dart';

class _FakeUiPreferencesRepository implements UiPreferencesRepository {
  @override
  Future<bool> getHapticFeedbackEnabled() async => false;

  @override
  Future<String> getSkeletonStrategy() async => 'auto';

  @override
  Future<bool> getTaskListBlurEnabled() async => true;

  @override
  Future<bool> getUndoSnackbarEnabled() async => true;

  @override
  Future<void> setHapticFeedbackEnabled(bool enabled) async {}

  @override
  Future<void> setSkeletonStrategy(String strategy) async {}

  @override
  Future<void> setTaskListBlurEnabled(bool enabled) async {}

  @override
  Future<void> setUndoSnackbarEnabled(bool enabled) async {}
}

/// 测试用设置仓储，覆盖复习间隔面板的加载与保存路径。
class _FakeSettingsRepository implements SettingsRepository {
  _FakeSettingsRepository(this._configs);

  List<ReviewIntervalConfigEntity> _configs;
  final List<List<ReviewIntervalConfigEntity>> savedSnapshots =
      <List<ReviewIntervalConfigEntity>>[];

  @override
  Future<AppSettingsEntity> getSettings() async => AppSettingsEntity.defaults;

  @override
  Future<List<ReviewIntervalConfigEntity>> getReviewIntervalConfigs() async {
    return List<ReviewIntervalConfigEntity>.from(_configs);
  }

  @override
  Future<void> saveReviewIntervalConfigs(
    List<ReviewIntervalConfigEntity> configs,
  ) async {
    _configs = List<ReviewIntervalConfigEntity>.from(configs);
    savedSnapshots.add(List<ReviewIntervalConfigEntity>.from(configs));
  }

  @override
  Future<void> saveSettings(AppSettingsEntity settings) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpScopedWidget(
    WidgetTester tester,
    Widget child, {
    List<Override> overrides = const <Override>[],
  }) async {
    tester.view.physicalSize = const Size(1440, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: overrides,
        child: MaterialApp(home: Scaffold(body: child)),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets('GoalProgressCard 支持 loading 展示', (tester) async {
    await pumpScopedWidget(
      tester,
      const GoalProgressCard(),
      overrides: <Override>[
        goalProgressProvider.overrideWithValue(const AsyncValue.loading()),
      ],
    );
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
  });

  testWidgets('GoalProgressCard 支持空状态展示', (tester) async {
    await pumpScopedWidget(
      tester,
      const GoalProgressCard(),
      overrides: <Override>[
        goalProgressProvider.overrideWithValue(
          const AsyncValue.data(<GoalProgressItem>[]),
        ),
      ],
    );
    expect(find.text('未启用目标'), findsOneWidget);
    expect(find.text('去设置开启'), findsOneWidget);
  });

  testWidgets('GoalProgressCard 支持目标列表与达成提示', (tester) async {
    await pumpScopedWidget(
      tester,
      const GoalProgressCard(),
      overrides: <Override>[
        uiPreferencesRepositoryProvider.overrideWithValue(
          _FakeUiPreferencesRepository(),
        ),
        goalProgressProvider.overrideWithValue(
          const AsyncValue.data(<GoalProgressItem>[
            GoalProgressItem(
              id: 'goal_daily',
              title: '每日完成',
              subtitle: '每天完成 2 个任务',
              currentText: '已完成 2',
              targetText: '目标 2',
              progress: 1,
              achieved: true,
            ),
            GoalProgressItem(
              id: 'goal_rate',
              title: '本周完成率',
              subtitle: '本周完成率达到 80%',
              currentText: '当前 60%',
              targetText: '目标 80%',
              progress: 0.75,
              achieved: false,
            ),
          ]),
        ),
      ],
    );
    await tester.pump();
    expect(find.text('每日完成'), findsOneWidget);
    expect(find.text('本周完成率'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 50));
    expect(find.textContaining('目标达成：每日完成'), findsOneWidget);
  });

  testWidgets('SkeletonLoader 与 CompletionAnimation 覆盖主要分支', (tester) async {
    await pumpScopedWidget(
      tester,
      SkeletonLoader(
        isLoading: true,
        strategy: 'auto',
        skeleton: const Text('骨架'),
        child: const Text('内容'),
      ),
    );
    expect(find.text('内容'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 250));
    expect(find.text('骨架'), findsOneWidget);

    await pumpScopedWidget(
      tester,
      SkeletonLoader(
        isLoading: true,
        strategy: 'on',
        skeleton: const Text('立即骨架'),
        child: const Text('内容'),
      ),
    );
    expect(find.text('立即骨架'), findsOneWidget);

    await pumpScopedWidget(
      tester,
      SkeletonLoader(
        isLoading: true,
        strategy: 'off',
        skeleton: const Text('骨架'),
        child: const Text('内容'),
      ),
    );
    expect(find.text('内容'), findsOneWidget);

    var completed = 0;
    await pumpScopedWidget(
      tester,
      CompletionAnimation(
        play: true,
        enabled: false,
        onCompleted: () => completed++,
        child: const Text('任务卡片'),
      ),
    );
    await tester.pump();
    expect(completed, 1);

    completed = 0;
    await pumpScopedWidget(
      tester,
      CompletionAnimation(
        play: true,
        enabled: true,
        onCompleted: () => completed++,
        child: const Text('任务卡片'),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    await tester.pump();
    expect(find.text('任务卡片'), findsOneWidget);
  });

  testWidgets('ReviewPreviewPanel 支持减少轮次、恢复默认与保存', (tester) async {
    final repository = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
      ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
      ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
      ReviewIntervalConfigEntity(round: 3, intervalDays: 7, enabled: true),
    ]);
    final unsavedChanges = <bool>[];

    await pumpScopedWidget(
      tester,
      ReviewPreviewPanel(
        learningDate: DateTime(2026, 3, 6),
        onUnsavedChangesChanged: unsavedChanges.add,
      ),
      overrides: <Override>[
        settingsRepositoryProvider.overrideWithValue(repository),
      ],
    );

    await tester.tap(find.text('复习计划预览'));
    await tester.pumpAndSettle();
    expect(find.text('减少轮次'), findsOneWidget);

    await tester.tap(find.text('减少轮次'));
    await tester.pumpAndSettle();
    expect(find.text('有未保存更改'), findsOneWidget);

    await tester.tap(find.text('恢复默认'));
    await tester.pumpAndSettle();
    expect(find.textContaining('第10次复习'), findsOneWidget);

    await tester.tap(find.text('确认保存'));
    await tester.pumpAndSettle();

    expect(repository.savedSnapshots, isNotEmpty);
    expect(repository.savedSnapshots.last, hasLength(10));
    expect(unsavedChanges, containsAllInOrder(<bool>[true, false]));
    expect(find.text('复习配置已保存'), findsOneWidget);
  });

  testWidgets('ReviewPreviewPanel 会阻止关闭最后一轮启用状态', (tester) async {
    final repository = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
      ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
      ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: false),
      ReviewIntervalConfigEntity(round: 3, intervalDays: 7, enabled: false),
    ]);

    await pumpScopedWidget(
      tester,
      ReviewPreviewPanel(learningDate: DateTime(2026, 3, 6)),
      overrides: <Override>[
        settingsRepositoryProvider.overrideWithValue(repository),
      ],
    );

    await tester.tap(find.text('复习计划预览'));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Switch).first);
    await tester.pumpAndSettle();

    expect(find.text('至少保留一轮复习'), findsWidgets);
    expect(repository.savedSnapshots, isEmpty);
  });

  testWidgets('DesktopTitleBar 支持拖动、最大化切换与关闭', (tester) async {
    final binding = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    final windowCalls = <String>[];
    var maximized = false;

    binding.setMockMethodCallHandler(
      const MethodChannel('window_manager'),
      (call) async {
        windowCalls.add(call.method);
        switch (call.method) {
          case 'isFullScreen':
            return false;
          case 'isMaximized':
            return maximized;
          case 'maximize':
            maximized = true;
            return null;
          case 'unmaximize':
            maximized = false;
            return null;
          default:
            return null;
        }
      },
    );
    addTearDown(
      () => binding.setMockMethodCallHandler(
        const MethodChannel('window_manager'),
        null,
      ),
    );

    await pumpScopedWidget(
      tester,
      const DesktopTitleBar(
        title: '忆刻桌面端',
        actions: <Widget>[Text('附加操作')],
      ),
    );

    expect(find.text('附加操作'), findsOneWidget);
    expect(find.byTooltip('最大化/还原'), findsOneWidget);
    expect(find.byTooltip('关闭'), findsOneWidget);

    await tester.drag(find.byType(DesktopTitleBar), const Offset(30, 0));
    await tester.pump();
    expect(windowCalls, contains('startDragging'));

    await tester.tap(find.byTooltip('最大化/还原'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('最大化/还原'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('关闭'));
    await tester.pumpAndSettle();

    expect(windowCalls, containsAll(<String>['maximize', 'unmaximize', 'close']));
  });

  testWidgets('DesktopShortcuts 支持新建、帮助、设置、搜索与保存快捷键', (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    var focusTriggered = false;
    var saveTriggered = false;
    final router = GoRouter(
      routes: <RouteBase>[
        GoRoute(
          path: '/',
          builder: (context, state) {
            return ShortcutActionsScope(
              onFocusSearch: () => focusTriggered = true,
              onSave: () => saveTriggered = true,
              child: const DesktopShortcuts(
                child: Scaffold(body: Text('主页')),
              ),
            );
          },
        ),
        GoRoute(
          path: '/input',
          builder: (context, state) =>
              const DesktopShortcuts(child: Scaffold(body: Text('录入页'))),
        ),
        GoRoute(
          path: '/help',
          builder: (context, state) =>
              const DesktopShortcuts(child: Scaffold(body: Text('帮助页'))),
        ),
        GoRoute(
          path: '/settings',
          builder: (context, state) =>
              const DesktopShortcuts(child: Scaffold(body: Text('设置页'))),
        ),
      ],
    );

    try {
      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.keyF);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.keyF);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.keyS);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.keyS);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pumpAndSettle();

      expect(focusTriggered, isTrue);
      expect(saveTriggered, isTrue);

      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.keyN);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.keyN);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pumpAndSettle();
      expect(find.text('录入页'), findsOneWidget);

      router.go('/');
      await tester.pumpAndSettle();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.keyH);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.keyH);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pumpAndSettle();
      expect(find.text('帮助页'), findsOneWidget);

      router.go('/');
      await tester.pumpAndSettle();
      await tester.sendKeyDownEvent(LogicalKeyboardKey.controlLeft);
      await tester.sendKeyDownEvent(LogicalKeyboardKey.comma);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.comma);
      await tester.sendKeyUpEvent(LogicalKeyboardKey.controlLeft);
      await tester.pumpAndSettle();
      expect(find.text('设置页'), findsOneWidget);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}
