/// 文件用途：Shell 层统一悬浮按钮（FAB），用于触发“录入”入口。
/// 作者：Codex
/// 创建日期：2026-03-01
library;

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_strings.dart';
import '../../widgets/shortcut_hint.dart';

/// Shell 层统一 FAB。
///
/// 设计说明：
/// - 入口统一：录入功能只保留一个入口（符合 UI 布局精简规范）。
/// - 路由保持：点击后始终跳转 `/input`，桌面端/移动端具体呈现由路由层策略决定。
/// - 显示策略：仅在“今日 / 计划 / 专注”主路径显示；“我的”页隐藏。
class ShellFAB extends StatefulWidget {
  /// 构造函数。
  ///
  /// 返回值：Widget。
  /// 异常：无。
  const ShellFAB({super.key});

  @override
  State<ShellFAB> createState() => _ShellFABState();
}

class _ShellFABState extends State<ShellFAB> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final isDesktop =
        !kIsWeb &&
        (defaultTargetPlatform == TargetPlatform.windows ||
            defaultTargetPlatform == TargetPlatform.macOS ||
            defaultTargetPlatform == TargetPlatform.linux);
    final isWindows =
        !kIsWeb && defaultTargetPlatform == TargetPlatform.windows;
    final hint = defaultTargetPlatform == TargetPlatform.macOS
        ? '⌘N'
        : 'Ctrl+N';
    final tooltip = isDesktop ? '${AppStrings.input}（$hint）' : AppStrings.input;

    // 快捷键提示 UI（spec-user-experience-improvements.md）：
    // - 仅 Windows 桌面端显示
    // - 鼠标悬停时展示，避免常驻占用空间
    final scope = ShortcutHintScope.maybeOf(context);
    final showHint = isWindows && (scope?.shouldShowHints ?? false) && _hovered;

    final label = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        const Text(AppStrings.input),
        if (showHint) ...[
          const SizedBox(width: 10),
          ShortcutHintPill(hint: hint),
        ],
      ],
    );

    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: Semantics(
        button: true,
        label: tooltip,
        child: FloatingActionButton.extended(
          tooltip: tooltip,
          onPressed: () => context.push('/input'),
          icon: const Icon(Icons.add),
          label: label,
        ),
      ),
    );
  }
}
