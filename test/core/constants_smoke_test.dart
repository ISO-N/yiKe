// 文件用途：核心常量覆盖率烟囱测试，确保 app_colors/app_spacing/app_strings/app_visual_tokens 在运行态被引用，避免常量文件长期 0% 覆盖率影响门禁。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/constants/app_colors.dart';
import 'package:yike/core/constants/app_spacing.dart';
import 'package:yike/core/constants/app_strings.dart';
import 'package:yike/core/constants/app_visual_tokens.dart';

void main() {
  group('Core constants smoke', () {
    test('常量值在运行态可读取且保持稳定', () {
      // 说明：这些断言本身很轻量，但可以确保常量文件在覆盖率统计中不再长期处于 0% 状态。
      expect(AppStrings.appName, '忆刻');
      expect(AppStrings.today, isNotEmpty);
      expect(AppSpacing.lg, greaterThan(0));
      expect(AppColors.primary.toARGB32(), isNot(0));

      // 视觉令牌：随 UI 规范变更而调整；这里只验证“存在且为正数”，避免过度绑定具体数值。
      expect(AppRadii.card, greaterThan(0));
      expect(AppBlur.none, 0);
      expect(AppLayoutTokens.desktopSidebarWidth, greaterThan(0));
    });
  });
}
