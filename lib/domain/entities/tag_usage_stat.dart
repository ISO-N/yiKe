/// 文件用途：领域实体 - 标签使用统计（用于录入页快捷标签与全部标签排序）。
/// 作者：Codex
/// 创建日期：2026-03-08
library;

/// 标签使用统计实体。
class TagUsageStatEntity {
  /// 构造函数。
  const TagUsageStatEntity({
    required this.tag,
    required this.recentUseCount,
    required this.totalUseCount,
    required this.lastUsedAt,
  });

  /// 标签名。
  final String tag;

  /// 近 7 天使用次数。
  final int recentUseCount;

  /// 累计使用次数。
  final int totalUseCount;

  /// 最近一次使用时间。
  final DateTime lastUsedAt;
}
