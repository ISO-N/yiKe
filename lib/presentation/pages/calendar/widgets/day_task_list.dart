/// 文件用途：日历当日任务内容与底部弹窗，支持移动端弹层与桌面端常驻详情复用。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-03-06（抽离可复用的 DayTaskListContent）
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../../core/utils/haptic_utils.dart';
import '../../../../domain/entities/review_task.dart';
import '../../../providers/calendar_provider.dart';
import '../../../providers/task_filter_provider.dart';
import '../../../providers/ui_preferences_provider.dart';
import '../../../widgets/error_card.dart';
import '../../../widgets/glass_card.dart';
import '../../../widgets/task_filter_bar.dart';

/// 当日任务 BottomSheet。
class DayTaskListSheet extends ConsumerWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [selectedDay] 选中日期（当天 00:00:00）
  const DayTaskListSheet({super.key, required this.selectedDay});

  final DateTime selectedDay;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return SafeArea(
      child: DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.75,
        minChildSize: 0.35,
        maxChildSize: 0.95,
        builder: (context, controller) {
          return DayTaskListContent(
            selectedDay: selectedDay,
            controller: controller,
          );
        },
      ),
    );
  }
}

/// 当日任务内容。
class DayTaskListContent extends ConsumerWidget {
  /// 构造函数。
  const DayTaskListContent({
    super.key,
    required this.selectedDay,
    this.controller,
    this.showInteractionHint = true,
  });

  final DateTime selectedDay;
  final ScrollController? controller;
  final bool showInteractionHint;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final secondaryText =
        Theme.of(context).textTheme.bodySmall?.color ?? AppColors.textSecondary;

    // 使用 select 精准获取当日任务相关字段，避免月统计数据变化触发重建。
    final taskState = ref.watch(
      calendarProvider.select(
        (s) => (
          isLoadingTasks: s.isLoadingTasks,
          errorMessage: s.errorMessage,
          selectedDayTasks: s.selectedDayTasks,
        ),
      ),
    );
    final notifier = ref.read(calendarProvider.notifier);
    final undoSnackbarEnabled = ref.watch(undoSnackbarEnabledProvider);
    final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);

    void showSnack(String text) {
      if (!context.mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      messenger.hideCurrentSnackBar();
      messenger.showSnackBar(
        SnackBar(content: Text(text), duration: const Duration(seconds: 2)),
      );
    }

    void showUndoSnack({required String text, required int taskId}) {
      if (!context.mounted) return;
      final messenger = ScaffoldMessenger.of(context);
      messenger.hideCurrentSnackBar();
      messenger.showSnackBar(
        SnackBar(
          content: Text(text),
          duration: const Duration(seconds: 3),
          action: SnackBarAction(
            label: '撤销',
            onPressed: () async {
              try {
                await notifier.undoTaskStatus(taskId);
                showSnack('已撤销');
              } catch (_) {
                showSnack('撤销失败，请重试');
              }
            },
          ),
        ),
      );
    }

    final filter = ref.watch(reviewTaskFilterProvider);
    final counts = ref.watch(selectedDayTaskCountsProvider);
    final filteredTasks = ref.watch(filteredSelectedDayTasksProvider);

    return Padding(
      padding: const EdgeInsets.fromLTRB(
        AppSpacing.lg,
        AppSpacing.sm,
        AppSpacing.lg,
        AppSpacing.lg,
      ),
      child: ListView(
        controller: controller,
        children: [
          Text(
            '当天任务 · ${YikeDateUtils.formatYmd(selectedDay)}',
            style: AppTypography.h2(context),
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            showInteractionHint
                ? '移动端可快速查看当天安排；桌面端支持常驻查看。'
                : '在这里查看选中日期的任务详情与处理状态。',
            style: AppTypography.bodySecondary(context),
          ),
          const SizedBox(height: AppSpacing.lg),
          if (!taskState.isLoadingTasks && taskState.errorMessage == null) ...[
            TaskFilterBar(
              filter: filter,
              counts: counts,
              onChanged: (next) {
                ref.read(reviewTaskFilterProvider.notifier).state = next;
              },
            ),
            const SizedBox(height: AppSpacing.lg),
          ],
          if (taskState.isLoadingTasks) ...[
            const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: CircularProgressIndicator(),
              ),
            ),
          ] else if (taskState.errorMessage != null) ...[
            ErrorCard(message: taskState.errorMessage!),
          ] else if (taskState.selectedDayTasks.isEmpty) ...[
            GlassCard(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  children: [
                    Icon(Icons.event_busy, size: 48, color: secondaryText),
                    const SizedBox(height: AppSpacing.md),
                    Text(
                      '当天暂无复习任务',
                      style: AppTypography.bodySecondary(context),
                    ),
                  ],
                ),
              ),
            ),
          ] else if (filteredTasks.isEmpty) ...[
            GlassCard(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.xl),
                child: Column(
                  children: [
                    Icon(Icons.filter_alt_off, size: 48, color: secondaryText),
                    const SizedBox(height: AppSpacing.md),
                    Text(
                      '当前筛选下暂无任务',
                      style: AppTypography.bodySecondary(context),
                    ),
                  ],
                ),
              ),
            ),
          ] else ...[
            for (final task in filteredTasks) ...[
              _TaskCard(
                task: task,
                onComplete: task.status == ReviewTaskStatus.pending
                    ? () async {
                        await notifier.completeTask(task.taskId);
                        if (context.mounted) {
                          HapticUtils.lightImpact(
                            context,
                            enabledByUser: hapticEnabled,
                          );
                          if (undoSnackbarEnabled) {
                            showUndoSnack(text: '任务已完成', taskId: task.taskId);
                          } else {
                            showSnack('任务已完成');
                          }
                        }
                      }
                    : null,
                onSkip: task.status == ReviewTaskStatus.pending
                    ? () async {
                        await notifier.skipTask(task.taskId);
                        if (context.mounted) {
                          HapticUtils.lightImpact(
                            context,
                            enabledByUser: hapticEnabled,
                          );
                          if (undoSnackbarEnabled) {
                            showUndoSnack(text: '任务已跳过', taskId: task.taskId);
                          } else {
                            showSnack('任务已跳过');
                          }
                        }
                      }
                    : null,
              ),
              const SizedBox(height: AppSpacing.md),
            ],
          ],
          const SizedBox(height: 16),
        ],
      ),
    );
  }
}

class _TaskCard extends StatelessWidget {
  const _TaskCard({
    required this.task,
    required this.onComplete,
    required this.onSkip,
  });

  final ReviewTaskViewEntity task;
  final VoidCallback? onComplete;
  final VoidCallback? onSkip;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;
    final info = _infoText();

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    task.title,
                    style: AppTypography.h2(context).copyWith(fontSize: 16),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                const SizedBox(width: AppSpacing.sm),
                _StatusChip(status: task.status),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '第 ${task.reviewRound} 次复习',
              style: AppTypography.bodySecondary(context),
            ),
            if (task.tags.isNotEmpty) ...[
              const SizedBox(height: AppSpacing.sm),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: task.tags
                    .take(6)
                    .map(
                      (t) => Chip(
                        label: Text(t),
                        labelStyle: const TextStyle(fontSize: 12),
                        backgroundColor: primary.withValues(alpha: 0.18),
                        side: BorderSide(
                          color: primary.withValues(alpha: 0.35),
                        ),
                        visualDensity: VisualDensity.compact,
                      ),
                    )
                    .toList(),
              ),
            ],
            if (info != null) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                info,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: AppTypography.bodySecondary(context),
              ),
            ],
            if (onComplete != null || onSkip != null) ...[
              const SizedBox(height: AppSpacing.lg),
              Row(
                children: [
                  Expanded(
                    child: FilledButton(
                      onPressed: onComplete,
                      style: FilledButton.styleFrom(
                        backgroundColor: AppColors.success,
                      ),
                      child: const Text('完成'),
                    ),
                  ),
                  const SizedBox(width: AppSpacing.sm),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: onSkip,
                      child: const Text('跳过'),
                    ),
                  ),
                ],
              ),
            ] else ...[
              const SizedBox(height: AppSpacing.sm),
              Text(
                _timestampText(),
                style: AppTypography.bodySecondary(context),
              ),
            ],
          ],
        ),
      ),
    );
  }

  String _timestampText() {
    switch (task.status) {
      case ReviewTaskStatus.done:
        return '完成时间：${task.completedAt?.toIso8601String() ?? '-'}';
      case ReviewTaskStatus.skipped:
        return '跳过时间：${task.skippedAt?.toIso8601String() ?? '-'}';
      case ReviewTaskStatus.pending:
        return '状态：待复习';
    }
  }

  /// 生成日历任务卡片的信息摘要（v2.6：description 优先，其次子任务数量，最后 fallback 到旧 note）。
  String? _infoText() {
    final desc = (task.description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    if (task.subtaskCount > 0) return '${task.subtaskCount} 个子任务';

    final legacy = (task.note ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注：$legacy';

    return null;
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final ReviewTaskStatus status;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;

    final (text, color) = switch (status) {
      ReviewTaskStatus.pending => ('待复习', primary),
      ReviewTaskStatus.done => ('已完成', AppColors.success),
      ReviewTaskStatus.skipped => ('已跳过', AppColors.warning),
    };

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withAlpha(24),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withAlpha(90)),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 12,
          fontWeight: FontWeight.w700,
        ),
      ),
    );
  }
}
