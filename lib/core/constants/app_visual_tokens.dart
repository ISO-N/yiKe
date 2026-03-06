/// 文件用途：统一管理 UI 视觉令牌（圆角、模糊、布局宽度等）。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

/// 圆角令牌。
class AppRadii {
  AppRadii._();

  static const double hero = 24;
  static const double card = 18;
  static const double soft = 14;
  static const double input = 16;
}

/// 模糊强度令牌。
class AppBlur {
  AppBlur._();

  static const double hero = 18;
  static const double card = 14;
  static const double section = 10;
  static const double strip = 6;
  static const double none = 0;
}

/// 页面布局令牌。
class AppLayoutTokens {
  AppLayoutTokens._();

  static const double pagePadding = 20;
  static const double sectionGap = 16;
  static const double blockGap = 12;
  static const double desktopSidebarWidth = 320;
  static const double desktopContentMaxWidth = 1440;
}
