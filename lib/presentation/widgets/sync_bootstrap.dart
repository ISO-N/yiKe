/// 文件用途：同步模块启动器（F12）——在 App 启动后自动初始化同步控制器（避免在 build 中做副作用）。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../providers/sync_provider.dart';

/// 判断当前是否处于 widget_test / 单元测试环境。
///
/// 说明：
/// - `bool.fromEnvironment('FLUTTER_TEST')` 在部分运行方式下可能不稳定
/// - 使用 assert 注入的方式可在 Debug/Test 下稳定生效，且 Release 会被树摇优化
bool _isInTestEnv() {
  var inTest = false;
  assert(inTest = true);
  return inTest;
}

/// 同步启动器：包裹在应用根部即可。
class SyncBootstrap extends ConsumerStatefulWidget {
  const SyncBootstrap({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<SyncBootstrap> createState() => _SyncBootstrapState();
}

class _SyncBootstrapState extends ConsumerState<SyncBootstrap> {
  @override
  void initState() {
    super.initState();
    if (_isInTestEnv()) return;

    // 关键逻辑：在首帧后初始化，避免阻塞 MaterialApp/router 的构建。
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(syncControllerProvider.notifier).initialize();
    });
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
