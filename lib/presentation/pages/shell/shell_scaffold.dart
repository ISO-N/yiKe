/// 文件用途：底部导航壳层（今日/计划/专注/我的），承载子路由页面。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_strings.dart';
import 'shell_fab.dart';

class ShellScaffold extends StatefulWidget {
  /// 底部导航壳层。
  ///
  /// 参数：
  /// - [child] 当前选中 tab 对应的页面。
  /// 返回值：返回 Scaffold。
  /// 异常：无。
  const ShellScaffold({super.key, required this.child});

  final Widget child;

  @override
  State<ShellScaffold> createState() => _ShellScaffoldState();
}

class _ShellScaffoldState extends State<ShellScaffold> {
  // PageStorage：用于在“路由切换导致页面重建”时，尽量保留滚动位置等状态。
  //
  // 说明：当前使用的是 ShellRoute（非 StatefulShellRoute.indexedStack），因此页面会被替换重建。
  // 这里通过 PageStorageBucket 做一个轻量级的状态保持，满足 spec-user-experience-improvements.md 的体验诉求。
  final PageStorageBucket _bucket = PageStorageBucket();

  int _locationToIndex(String location) {
    if (location.startsWith('/settings')) return 3;
    if (location.startsWith('/pomodoro')) return 2;
    if (location.startsWith('/calendar')) return 1;
    return 0;
  }

  void _onTap(BuildContext context, int index) {
    switch (index) {
      case 0:
        context.go('/home');
        return;
      case 1:
        context.go('/calendar');
        return;
      case 2:
        context.go('/pomodoro');
        return;
      case 3:
        context.go('/settings');
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    final location = GoRouter.of(
      context,
    ).routeInformationProvider.value.uri.toString();
    final currentIndex = _locationToIndex(location);
    final shouldShowFab = !location.startsWith('/settings');
    return Scaffold(
      body: PageStorage(bucket: _bucket, child: widget.child),
      // 交互规范：录入入口由 Shell 层统一提供；“我的”页不显示 FAB。
      floatingActionButton: shouldShowFab ? const ShellFAB() : null,
      bottomNavigationBar: NavigationBar(
        selectedIndex: currentIndex,
        onDestinationSelected: (index) => _onTap(context, index),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.today_outlined),
            selectedIcon: Icon(Icons.today),
            label: AppStrings.today,
          ),
          NavigationDestination(
            icon: Icon(Icons.calendar_month_outlined),
            selectedIcon: Icon(Icons.calendar_month),
            label: AppStrings.plan,
          ),
          NavigationDestination(
            icon: Icon(Icons.timer_outlined),
            selectedIcon: Icon(Icons.timer),
            label: AppStrings.pomodoro,
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: AppStrings.mine,
          ),
        ],
      ),
    );
  }
}
