/// 文件用途：统一管理文字样式规范（支持深浅色主题的动态颜色）。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-02-26（改为基于 Theme 的动态颜色）
library;

import 'package:flutter/material.dart';

class AppTypography {
  AppTypography._();

  /// 展示级数字或大标题。
  static TextStyle display(BuildContext context) {
    final base = Theme.of(context).textTheme.headlineLarge;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 32,
      fontWeight: FontWeight.w800,
      height: 1.1,
    );
  }

  /// 一级标题。
  static TextStyle h1(BuildContext context) {
    final base = Theme.of(context).textTheme.headlineSmall;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 24,
      fontWeight: FontWeight.w700,
      height: 1.2,
    );
  }

  /// 二级标题。
  static TextStyle h2(BuildContext context) {
    final base = Theme.of(context).textTheme.titleLarge;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 18,
      fontWeight: FontWeight.w700,
      height: 1.25,
    );
  }

  /// 区块标题。
  static TextStyle title(BuildContext context) {
    final base = Theme.of(context).textTheme.titleMedium;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 16,
      fontWeight: FontWeight.w600,
      height: 1.3,
    );
  }

  /// 正文。
  static TextStyle body(BuildContext context) {
    final base = Theme.of(context).textTheme.bodyMedium;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 14,
      fontWeight: FontWeight.w400,
      height: 1.4,
    );
  }

  /// 次要正文（用于说明文案、辅助信息）。
  static TextStyle bodySecondary(BuildContext context) {
    final base = Theme.of(context).textTheme.bodySmall;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 13,
      fontWeight: FontWeight.w400,
      height: 1.4,
    );
  }

  /// 元信息文本（标签、说明、时间等）。
  static TextStyle meta(BuildContext context) {
    final base = Theme.of(context).textTheme.bodySmall;
    return (base ?? const TextStyle()).copyWith(
      fontSize: 12,
      fontWeight: FontWeight.w500,
      height: 1.3,
    );
  }
}
