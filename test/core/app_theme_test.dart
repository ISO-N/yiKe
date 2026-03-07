// 文件用途：AppTheme 单元测试，覆盖浅色主题种子色与深色 AMOLED 分支。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/theme/app_theme.dart';

void main() {
  group('AppTheme', () {
    test('light 会使用传入种子色生成主题', () {
      final theme = AppTheme.light(seedColor: Colors.green);

      expect(theme.useMaterial3, isTrue);
      expect(theme.colorScheme.brightness, Brightness.light);
      expect(theme.colorScheme.primary, isNotNull);
      expect(theme.navigationBarTheme.labelTextStyle, isNotNull);
      expect(theme.inputDecorationTheme.focusedBorder, isNotNull);
    });

    test('dark 在 amoled 模式下使用纯黑背景并保留深色组件主题', () {
      final theme = AppTheme.dark(seedColor: Colors.orange, amoled: true);

      expect(theme.colorScheme.brightness, Brightness.dark);
      expect(theme.scaffoldBackgroundColor, Colors.black);
      expect(theme.bottomSheetTheme.backgroundColor, isNotNull);
      expect(theme.dialogTheme.backgroundColor, isNotNull);
      expect(theme.navigationBarTheme.iconTheme, isNotNull);
    });

    test('light 在未传种子色时会使用默认品牌色并启用浅色背景体系', () {
      final theme = AppTheme.light();

      expect(theme.colorScheme.brightness, Brightness.light);
      expect(theme.scaffoldBackgroundColor, isNotNull);
      expect(theme.cardTheme.shape, isNotNull);
      expect(theme.snackBarTheme.behavior, SnackBarBehavior.floating);
      expect(theme.dividerTheme.thickness, 1);
    });

    test('dark 在非 amoled 模式下保留暗色 surface 与输入样式', () {
      final theme = AppTheme.dark(seedColor: Colors.teal, amoled: false);

      expect(theme.colorScheme.brightness, Brightness.dark);
      expect(theme.scaffoldBackgroundColor, isNot(Colors.black));
      expect(theme.inputDecorationTheme.fillColor, isNotNull);
      expect(theme.appBarTheme.titleTextStyle?.color, isNotNull);
      expect(theme.bottomSheetTheme.modalBackgroundColor, isNotNull);
    });
  });
}
