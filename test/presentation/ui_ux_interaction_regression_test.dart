// 文件用途：UI/UX 改造后的交互一致性回归测试（减少动效、焦点与桌面提示）。
// 文件用途：UI/UX 改造后的交互一致性回归测试（减少动效、焦点与桌面提示）。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:yike/presentation/widgets/search_bar.dart';
import 'package:yike/presentation/widgets/shortcut_hint.dart';

import '../helpers/app_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('YiKeApp 在系统要求减少动效时关闭主题切换动画', (tester) async {
    tester.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    addTearDown(tester.platformDispatcher.clearAccessibilityFeaturesTestValue);

    await pumpYiKeApp(tester);

    final app = tester.widget<MaterialApp>(find.byType(MaterialApp));
    expect(app.themeAnimationDuration, Duration.zero);
  });

  testWidgets('搜索栏点击后能够保持输入焦点', (tester) async {
    var query = '';

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LearningSearchBar(
            query: query,
            onChanged: (value) => query = value,
            onClear: () => query = '',
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pump();

    expect(tester.testTextInput.hasAnyClients, isTrue);
    expect(FocusManager.instance.primaryFocus, isNotNull);
  });

  testWidgets('桌面端快捷键提示按钮保留 Tooltip 与悬停提示', (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.windows;
    try {
      await tester.pumpWidget(
        MaterialApp(
          home: ShortcutHintScope(
            shouldShowHints: true,
            child: Scaffold(
              body: Center(
                child: ShortcutHintIconButton(
                  hint: 'Ctrl+R',
                  tooltip: '刷新',
                  icon: const Icon(Icons.refresh),
                  onPressed: () {},
                ),
              ),
            ),
          ),
        ),
      );

      expect(find.byTooltip('刷新（Ctrl+R）'), findsOneWidget);

      final mouse = await tester.createGesture(kind: PointerDeviceKind.mouse);
      await mouse.addPointer();
      await mouse.moveTo(tester.getCenter(find.byIcon(Icons.refresh)));
      await tester.pump();
      await mouse.removePointer();

      expect(find.text('Ctrl+R'), findsOneWidget);
    } finally {
      debugDefaultTargetPlatformOverride = null;
    }
  });
}
