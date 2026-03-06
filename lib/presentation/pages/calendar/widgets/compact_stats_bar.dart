/// 文件用途：日历页顶部紧凑统计栏（连续打卡 + 本周完成率）。
/// 作者：Codex
/// 创建日期：2026-03-01
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../providers/statistics_provider.dart';
import '../../../widgets/glass_card.dart';

/// 紧凑统计栏：点击展开统计详情 Sheet。
///
/// 规范要求：
/// - 数据来源直接 watch `statisticsProvider`，避免口径分裂。
/// - 上层可通过 [onTap] 控制是否允许触发（例如 Sheet 已打开时禁用再次触发）。
class CompactStatsBar extends ConsumerWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [onTap] 点击回调；为空时展示为不可点击态。
  /// 返回值：Widget。
  /// 异常：无。
  const CompactStatsBar({super.key, required this.onTap});

  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(statisticsProvider);
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    final disabled = onTap == null;

    String weekText() {
      if (state.weekTotal == 0) return '本周 0%';
      final p = (state.weekCompletionRate * 100).clamp(0, 100);
      return '本周 ${p.toStringAsFixed(0)}%';
    }

    String streakText() {
      if (state.consecutiveCompletedDays == 0) return '连续 0 天';
      return '连续 ${state.consecutiveCompletedDays} 天';
    }

    final streakSummary = Row(
      children: [
        Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(
            color: AppColors.cta.withAlpha(22),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.cta.withAlpha(70)),
          ),
          child: const Icon(
            Icons.local_fire_department,
            color: AppColors.cta,
            size: 20,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('连续打卡', style: AppTypography.body(context)),
              const SizedBox(height: 2),
              Text(
                state.isLoading ? '加载中…' : streakText(),
                style: AppTypography.bodySecondary(context),
              ),
            ],
          ),
        ),
      ],
    );

    final completionSummary = Row(
      children: [
        Icon(Icons.trending_up, color: primary, size: 20),
        const SizedBox(width: 6),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('完成率', style: AppTypography.body(context)),
              const SizedBox(height: 2),
              Text(
                state.isLoading ? '加载中…' : weekText(),
                style: AppTypography.bodySecondary(context),
              ),
            ],
          ),
        ),
      ],
    );

    final content = Padding(
      padding: const EdgeInsets.all(AppSpacing.lg),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final useStacked = constraints.maxWidth < 320;
          if (useStacked) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                streakSummary,
                const SizedBox(height: AppSpacing.md),
                Divider(color: Theme.of(context).dividerColor.withAlpha(90)),
                const SizedBox(height: AppSpacing.md),
                Row(
                  children: [
                    Expanded(child: completionSummary),
                    const SizedBox(width: AppSpacing.sm),
                    Icon(
                      Icons.chevron_right,
                      color: Theme.of(context).iconTheme.color?.withAlpha(160),
                    ),
                  ],
                ),
              ],
            );
          }

          return Row(
            children: [
              Expanded(child: streakSummary),
              Container(
                width: 1,
                height: 36,
                color: Theme.of(context).dividerColor.withAlpha(90),
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(child: completionSummary),
              const SizedBox(width: AppSpacing.sm),
              Icon(
                Icons.chevron_right,
                color: Theme.of(context).iconTheme.color?.withAlpha(160),
              ),
            ],
          );
        },
      ),
    );

    return Semantics(
      button: !disabled,
      label: '统计概览',
      hint: disabled ? '统计详情已打开' : '点击展开统计详情',
      child: MouseRegion(
        cursor: disabled ? SystemMouseCursors.basic : SystemMouseCursors.click,
        child: Opacity(
          opacity: disabled ? 0.7 : 1,
          child: GlassCard(
            child: InkWell(
              borderRadius: BorderRadius.circular(16),
              onTap: onTap,
              child: content,
            ),
          ),
        ),
      ),
    );
  }
}
