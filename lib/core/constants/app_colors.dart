/// 文件用途：统一管理应用颜色常量（含 v2.2 深色主题配色）。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-02-26（新增深色主题配色）
library;

import 'package:flutter/material.dart';

/// 应用颜色常量。
///
/// 说明：颜色值来自 UI/UX 设计稿（Teal/Orange 为主）。
class AppColors {
  AppColors._();

  static const Color primary = Color(0xFF0D9488); // Teal 600
  static const Color primaryLight = Color(0xFF14B8A6); // Teal 500
  static const Color primaryDark = Color(0xFF134E4A); // Teal 800

  static const Color cta = Color(0xFFF97316); // Orange 500

  static const Color success = Color(0xFF22C55E);
  static const Color warning = Color(0xFFF59E0B);
  static const Color error = Color(0xFFEF4444);

  static const Color background = Color(0xFFF0FDFA); // Teal 50
  static const Color backgroundElevated = Color(0xFFFFFFFF);
  static const Color backgroundMuted = Color(0xFFF8FAFC);
  static const Color heroSurface = Color(0xFFDFFAF6);
  static const Color textPrimary = Color(0xFF0F172A); // Slate 900
  static const Color textSecondary = Color(0xFF64748B); // Slate 500
  static const Color divider = Color(0xFFE2E8F0); // Slate 200

  /// 深色背景色（深蓝黑）。
  static const Color darkBackground = Color(0xFF1A1A2E);

  /// 深色表面色（卡片/底部导航等）。
  static const Color darkSurface = Color(0xFF16213E);

  /// 深色高层级表面色。
  static const Color darkSurfaceElevated = Color(0xFF1C2B4F);

  /// 深色轻面板底色。
  static const Color darkSurfaceMuted = Color(0xFF223153);

  /// 深色 Hero 底色。
  static const Color darkHeroSurface = Color(0xFF173A46);

  /// 深色文字主色。
  static const Color darkTextPrimary = Color(0xFFF1F5F9); // Slate 100

  /// 深色文字次色。
  static const Color darkTextSecondary = Color(0xFF94A3B8); // Slate 400

  /// 毛玻璃卡片底色（80% 不透明）。
  static const Color glassSurface = Color.fromRGBO(255, 255, 255, 0.80);

  /// 轻面板底色（92% 不透明）。
  static const Color plainSurface = Color.fromRGBO(248, 250, 252, 0.92);

  /// 毛玻璃边框（40% 不透明）。
  static const Color glassBorder = Color.fromRGBO(255, 255, 255, 0.40);

  /// 深色毛玻璃底色（80% 不透明）。
  static const Color darkGlassSurface = Color.fromRGBO(22, 33, 62, 0.80);

  /// 深色轻面板底色（92% 不透明）。
  static const Color darkPlainSurface = Color.fromRGBO(34, 49, 83, 0.92);

  /// 深色毛玻璃边框（10% 不透明）。
  static const Color darkGlassBorder = Color.fromRGBO(255, 255, 255, 0.10);

  /// 深色分割线（10% 不透明）。
  static const Color darkDivider = Color.fromRGBO(255, 255, 255, 0.10);
}
