// 文件用途：AppVisualTokens 常量测试，确保统一视觉令牌值稳定可回归。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/constants/app_visual_tokens.dart';

void main() {
  test('视觉令牌常量保持预期值', () {
    expect(AppRadii.hero, 24);
    expect(AppRadii.card, 18);
    expect(AppRadii.soft, 14);
    expect(AppRadii.input, 16);

    expect(AppBlur.hero, 18);
    expect(AppBlur.card, 14);
    expect(AppBlur.section, 10);
    expect(AppBlur.strip, 6);
    expect(AppBlur.none, 0);

    expect(AppLayoutTokens.pagePadding, 20);
    expect(AppLayoutTokens.sectionGap, 16);
    expect(AppLayoutTokens.blockGap, 12);
    expect(AppLayoutTokens.desktopSidebarWidth, 320);
    expect(AppLayoutTokens.desktopContentMaxWidth, 1440);
  });
}
