/// 文件用途：学习目标进度展示组件（统计增强 P0：目标设定）。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/haptic_utils.dart';
import '../providers/goal_provider.dart';
import '../providers/ui_preferences_provider.dart';
import 'completion_animation.dart';
import 'glass_card.dart';

/// 学习目标进度卡片。
///
/// 说明：
/// - 进度数据来自 [goalProgressProvider]（完全由任务统计派生）
/// - 若用户未启用任何目标，提示跳转到设置页开启
class GoalProgressCard extends ConsumerStatefulWidget {
  /// 构造函数。
  const GoalProgressCard({super.key});

  @override
  ConsumerState<GoalProgressCard> createState() => _GoalProgressCardState();
}

class _GoalProgressCardState extends ConsumerState<GoalProgressCard> {
  final Set<String> _celebratedGoalIds = <String>{};
  Timer? _celebrationTimer;
  String? _celebrationText;
  bool _playCelebrationFade = false;

  @override
  void dispose() {
    _celebrationTimer?.cancel();
    _celebrationTimer = null;
    super.dispose();
  }

  void _startCelebration(String goalTitle) {
    _celebrationTimer?.cancel();
    _celebrationTimer = null;

    // 触觉反馈（3.2.3）：目标达成触发重反馈（忽略“减少动态效果”）。
    // 说明：桌面端会被 HapticUtils 自动禁用。
    final hapticEnabled = ref.read(hapticFeedbackEnabledProvider);
    HapticUtils.heavyImpact(context, enabledByUser: hapticEnabled);

    setState(() {
      _celebrationText = '目标达成：$goalTitle';
      _playCelebrationFade = false;
    });

    // 先展示一段时间，再使用 CompletionAnimation 做“淡出收尾”。
    _celebrationTimer = Timer(const Duration(milliseconds: 1200), () {
      if (!mounted) return;
      setState(() => _playCelebrationFade = true);
    });
  }

  void _maybeCelebrate(List<GoalProgressItem> items) {
    for (final item in items) {
      if (!item.achieved) continue;
      if (_celebratedGoalIds.contains(item.id)) continue;
      _celebratedGoalIds.add(item.id);
      // 避免在 build 内 setState。
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        _startCelebration(item.title);
      });
      break;
    }
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(goalProgressProvider);

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text('学习目标', style: AppTypography.h2(context))),
                IconButton(
                  tooltip: '设置目标',
                  onPressed: () => context.push('/settings/goals'),
                  icon: const Icon(Icons.tune),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            if (_celebrationText != null) ...[
              CompletionAnimation(
                play: _playCelebrationFade,
                enabled:
                    !(MediaQuery.of(context).disableAnimations ||
                        MediaQuery.of(context).accessibleNavigation),
                onCompleted: () {
                  if (!mounted) return;
                  setState(() {
                    _celebrationText = null;
                    _playCelebrationFade = false;
                  });
                },
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.success.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: AppColors.success.withValues(alpha: 0.30),
                    ),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.celebration_outlined,
                        color: AppColors.success,
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Text(
                          _celebrationText!,
                          style: AppTypography.body(context),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      TextButton(
                        onPressed: () => context.push('/statistics'),
                        child: const Text('查看'),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.md),
            ],
            async.when(
              loading: () => const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Center(child: CircularProgressIndicator()),
              ),
              error: (e, _) => Text(
                '目标进度加载失败：$e',
                style: TextStyle(color: Theme.of(context).colorScheme.error),
              ),
              data: (items) {
                _maybeCelebrate(items);
                if (items.isEmpty) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '未启用目标',
                        style: AppTypography.bodySecondary(context),
                      ),
                      const SizedBox(height: AppSpacing.md),
                      FilledButton(
                        onPressed: () => context.push('/settings/goals'),
                        child: const Text('去设置开启'),
                      ),
                    ],
                  );
                }

                return Column(
                  children: [
                    for (final item in items) ...[
                      _GoalRow(item: item),
                      const SizedBox(height: AppSpacing.md),
                    ],
                  ],
                );
              },
            ),
          ],
        ),
      ),
    );
  }
}

class _GoalRow extends StatelessWidget {
  const _GoalRow({required this.item});

  final GoalProgressItem item;

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    final ringColor = item.achieved ? AppColors.success : primary;

    final percent = (item.progress * 100).clamp(0, 100).toStringAsFixed(0);

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        SizedBox(
          width: 44,
          height: 44,
          child: Stack(
            alignment: Alignment.center,
            children: [
              CircularProgressIndicator(
                value: item.progress.isNaN ? 0 : item.progress.clamp(0, 1),
                strokeWidth: 6,
                backgroundColor: Theme.of(context).dividerColor.withAlpha(60),
                color: ringColor,
              ),
              Icon(
                item.achieved
                    ? Icons.celebration_outlined
                    : Icons.flag_outlined,
                size: 18,
                color: ringColor,
              ),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(item.title, style: AppTypography.body(context)),
              const SizedBox(height: 2),
              Text(item.subtitle, style: AppTypography.bodySecondary(context)),
            ],
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          children: [
            Text(
              '$percent%',
              style: TextStyle(fontWeight: FontWeight.w700, color: ringColor),
            ),
            const SizedBox(height: 2),
            Text(
              '${item.currentText} · ${item.targetText}',
              style: AppTypography.bodySecondary(
                context,
              ).copyWith(fontSize: 11),
              textAlign: TextAlign.right,
            ),
          ],
        ),
      ],
    );
  }
}
