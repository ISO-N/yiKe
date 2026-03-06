/// 文件用途：首页底部统计栏（延迟加载的非关键组件，spec-user-experience-improvements.md 3.3.4）。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/haptic_utils.dart';
import '../providers/statistics_provider.dart';
import '../providers/ui_preferences_provider.dart';
import 'glass_card.dart';

/// 首页底部统计栏。
///
/// 说明：
/// - 该组件用于提供“看一眼的统计摘要 + 进入统计页的入口”
/// - 配合 [DeferredVisibilityBuilder] 使用，避免阻塞首屏渲染
class HomeBottomStatsBar extends ConsumerWidget {
  /// 构造函数。
  const HomeBottomStatsBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // 性能优化（3.3.1）：只订阅首页底部统计栏展示所需字段，避免统计状态其他字段变化导致重建。
    final (weekRate, streakDays, isLoading) = ref.watch(
      statisticsProvider.select(
        (s) => (s.weekCompletionRate, s.consecutiveCompletedDays, s.isLoading),
      ),
    );
    final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);

    final rateText = isLoading
        ? '加载中…'
        : '${(weekRate * 100).toStringAsFixed(0)}%';
    final streakText = isLoading ? '—' : '$streakDays 天';

    return Semantics(
      container: true,
      label: '统计摘要，本周完成率 $rateText，连续打卡 $streakText',
      child: GlassCard(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: Theme.of(
                    context,
                  ).colorScheme.primary.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: Theme.of(
                      context,
                    ).colorScheme.primary.withValues(alpha: 0.30),
                  ),
                ),
                child: Icon(
                  Icons.insights_outlined,
                  color: Theme.of(context).colorScheme.primary,
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '统计摘要',
                      style: AppTypography.h2(context),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '本周完成率 $rateText · 连续打卡 $streakText',
                      style: AppTypography.bodySecondary(context),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              FilledButton(
                onPressed: () {
                  // 触觉反馈：按钮点击轻反馈（桌面端会被工具类禁用）。
                  HapticUtils.lightImpact(
                    context,
                    enabledByUser: hapticEnabled,
                  );
                  // 统计页已调整为次级入口，保留独立页面访问。
                  context.push('/statistics');
                },
                child: const Text('查看'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
