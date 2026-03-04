/// 文件用途：启动后资源预加载（图片/字体等），用于减少首帧渲染抖动，符合 spec-user-experience-improvements.md。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'dart:async';

import 'package:flutter/material.dart';

/// 应用资源预加载服务。
///
/// 说明：
/// - 预加载应发生在首帧之后（避免阻塞启动）
/// - 收到内存压力信号后跳过预加载（尽量降低低端设备风险）
class AppPreloadService {
  AppPreloadService._();

  static bool _started = false;
  static bool _skipDueToMemoryPressure = false;
  static Timer? _delayedTimer;
  static final WidgetsBindingObserver _observer = _AppPreloadBindingObserver();

  /// 确保预加载逻辑已启动（可重复调用，内部做幂等）。
  ///
  /// 参数：
  /// - [context] 用于 `precacheImage` 的 BuildContext
  static void ensureStarted(BuildContext context) {
    if (_started) return;
    _started = true;

    // 监听内存压力：一旦触发，后续预加载全部跳过。
    WidgetsBinding.instance.addObserver(_observer);

    // 首帧后执行图片预加载。
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (_skipDueToMemoryPressure) return;

      // 规格：首页顶部图标等关键资源优先预加载（这里选取 App 图标系列）。
      await _precacheImages(context, const [
        'assets/icons/app_icon.png',
        'assets/icons/app_icon_syncing.png',
        'assets/icons/app_icon_offline.png',
      ]);

      // 规格：常用 SVG 可延迟 500ms 再预加载（当前项目无 SVG 资源，保留结构以便未来扩展）。
      _delayedTimer?.cancel();
      _delayedTimer = Timer(const Duration(milliseconds: 500), () async {
        if (_skipDueToMemoryPressure) return;
        // TODO: 若后续引入 flutter_svg，可在此处通过 precachePicture 预热常用 SVG。
      });
    });
  }

  static Future<void> _precacheImages(
    BuildContext context,
    List<String> assets,
  ) async {
    for (final path in assets) {
      try {
        await precacheImage(AssetImage(path), context);
      } catch (_) {
        // 预加载失败不影响主流程。
      }
    }
  }
}

/// 内存压力观察者。
///
/// 说明：该回调由引擎触发，通常发生在系统回收内存、后台切前台等场景。
class _AppPreloadBindingObserver extends WidgetsBindingObserver {
  @override
  void didHaveMemoryPressure() {
    // 关键逻辑：一旦收到内存压力信号，后续所有预加载全部跳过。
    AppPreloadService._skipDueToMemoryPressure = true;
  }
}
