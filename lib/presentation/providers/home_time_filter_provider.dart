/// 文件用途：首页“全部”标签页时间筛选 Provider（会话内保持）。
/// 作者：Codex
/// 创建日期：2026-03-05
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

/// 首页“全部”标签页时间筛选枚举。
enum HomeTimeFilter {
  /// 全部任务（不限制计划日期）。
  all,

  /// 今天前（scheduledDate < 今天 00:00）。
  beforeToday,

  /// 今天后（scheduledDate >= 明天 00:00）。
  afterToday,
}

/// 首页“全部”标签页时间筛选状态 Provider。
///
/// 默认值为 [HomeTimeFilter.all]，确保首次进入时展示“全部”。
final homeTimeFilterProvider = StateProvider<HomeTimeFilter>(
  (ref) => HomeTimeFilter.all,
);
