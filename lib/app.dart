/// 文件用途：App 根组件，提供主题与路由。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-02-26（接入主题模式与深色主题）
library;

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'core/utils/color_utils.dart';
import 'core/theme/app_theme.dart';
import 'infrastructure/router/app_router.dart';
import 'presentation/providers/theme_provider.dart';
import 'presentation/widgets/desktop_shortcuts.dart';
import 'presentation/widgets/desktop_title_bar.dart';
import 'presentation/widgets/sync_bootstrap.dart';
import 'presentation/widgets/ux_bootstrap.dart';

class YiKeApp extends ConsumerWidget {
  /// App 根组件。
  ///
  /// 返回值：返回一个使用 `MaterialApp.router` 的根组件。
  /// 异常：无。
  const YiKeApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final router = ref.watch(appRouterProvider);
    final themeMode = ref.watch(themeModeProvider);
    final themeSettings = ref.watch(themeSettingsProvider);
    final seedColor =
        ColorUtils.tryParseHex(themeSettings.seedColorHex) ?? const Color(0xFF2196F3);

    // 关键逻辑：尊重系统“减少动态效果”设置；若系统要求关闭动画则禁用主题切换动画。
    final features =
        WidgetsBinding.instance.platformDispatcher.accessibilityFeatures;
    final disableAnimations =
        features.disableAnimations || features.accessibleNavigation;
    final themeAnimationDuration = disableAnimations
        ? Duration.zero
        : const Duration(milliseconds: 300);
    return MaterialApp.router(
      title: '忆刻',
      theme: AppTheme.light(seedColor: seedColor),
      darkTheme: AppTheme.dark(
        seedColor: seedColor,
        amoled: themeSettings.amoled,
      ),
      themeMode: themeMode.toThemeMode(),
      themeAnimationDuration: themeAnimationDuration,
      themeAnimationCurve: Curves.easeInOut,
      routerConfig: router,
      builder: (context, child) {
        final content = child ?? const SizedBox.shrink();
        // 测试环境：避免启动桌面端壳层与同步服务，减少不稳定因素（端口占用/平台限制等）。
        final bindingName = WidgetsBinding.instance.runtimeType.toString();
        final isWidgetTest = bindingName.contains('TestWidgetsFlutterBinding');
        if (isWidgetTest) return content;

        // UX 启动器：首帧后执行资源预加载、通知点击导航绑定、启动时 UX 通知检查等。
        //
        // 说明：该层级只负责“首帧后副作用”，不阻塞首屏渲染。
        final bootstrapped = UxBootstrap(child: SyncBootstrap(child: content));
        if (kIsWeb) return content;

        // v3.0（F11）：Windows 使用隐藏系统标题栏 + 自定义标题栏。
        if (Platform.isWindows) {
          // 关键逻辑（Windows 白屏修复）：
          // 自定义标题栏位于 MaterialApp 的 builder 包裹层中，层级会在 GoRouter/Navigator 之上，
          // 此时 Tooltip/菜单等依赖 Overlay 的组件会找不到 Overlay 祖先并触发断言，导致界面异常。
          // 这里额外包一层“空导航器”，仅用于提供 Overlay 容器，不改变现有路由结构。
          return Navigator(
            onGenerateRoute: (_) => PageRouteBuilder<void>(
              pageBuilder: (context, animation, secondaryAnimation) {
                return DesktopShortcuts(
                  child: DesktopWindowFrame(title: '忆刻', child: bootstrapped),
                );
              },
              transitionDuration: Duration.zero,
              reverseTransitionDuration: Duration.zero,
            ),
          );
        }

        // 交互优化（spec-user-experience-improvements.md 3.4.x）：
        // - macOS 使用 Command 修饰键（DesktopShortcuts 内部自适配）
        // - Windows 之外的桌面平台不启用自定义标题栏，但可以启用快捷键能力
        if (Platform.isMacOS || Platform.isLinux) {
          return DesktopShortcuts(child: bootstrapped);
        }

        // 其他平台保持默认行为（避免移动端外接键盘导致“全局抢焦点”等歧义）。
        return bootstrapped;
      },
      debugShowCheckedModeBanner: false,
    );
  }
}
