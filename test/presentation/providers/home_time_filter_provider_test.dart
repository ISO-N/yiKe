// 文件用途：首页时间筛选 Provider 单元测试（默认值与 Tab 切换保持）。
// 作者：Codex
// 创建日期：2026-03-05

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yike/presentation/pages/home/widgets/home_tab_switcher.dart';
import 'package:yike/presentation/providers/home_task_tab_provider.dart';
import 'package:yike/presentation/providers/home_time_filter_provider.dart';

void main() {
  test('homeTimeFilterProvider 默认值为 all', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    expect(container.read(homeTimeFilterProvider), HomeTimeFilter.all);
  });

  test('切换 Tab 后返回，时间筛选值保持不变（会话内）', () {
    final container = ProviderContainer();
    addTearDown(container.dispose);

    container.read(homeTimeFilterProvider.notifier).state =
        HomeTimeFilter.beforeToday;

    container.read(homeTaskTabProvider.notifier).state = HomeTaskTab.today;
    container.read(homeTaskTabProvider.notifier).state = HomeTaskTab.all;

    expect(container.read(homeTimeFilterProvider), HomeTimeFilter.beforeToday);
  });
}
