/// 文件用途：任务中心时间线列表（供任务中心页与首页 tab=all 复用）。
/// 作者：Codex
/// 创建日期：2026-03-01
library;

import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/utils/date_utils.dart';
import '../../../../domain/entities/review_task.dart';
import '../../../providers/task_hub_provider.dart';
import '../../../widgets/glass_card.dart';

/// 任务中心时间线列表（不包含筛选栏与滚动容器）。
///
/// 设计说明：
/// - 该组件只负责“列表内容”渲染与卡片交互（展开/完成/跳过/撤销/详情）。
/// - 滚动、下拉刷新、游标分页触发（loadMore）由上层页面负责。
class TaskHubTimelineSliver extends StatelessWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [state] 任务中心状态。
  /// - [notifier] 任务中心 Notifier（用于执行操作与更新展开状态）。
  /// - [blurEnabled] 是否启用任务列表毛玻璃（性能开关，默认由设置项控制）。
  /// 返回值：Widget。
  /// 异常：无。
  const TaskHubTimelineSliver({
    super.key,
    required this.state,
    required this.notifier,
    required this.blurEnabled,
    this.emptyState,
  });

  final TaskHubState state;
  final TaskHubNotifier notifier;
  final bool blurEnabled;

  /// 列表为空时的替换 UI（仅用于首页 tab=all 的空状态引导增强）。
  ///
  /// 说明：任务中心页面本身不展示首页 CTA，因此默认保持“暂无任务”文案。
  final Widget? emptyState;

  @override
  Widget build(BuildContext context) {
    void showSnack(String text) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    }

    Future<bool> runAction(Future<void> Function() action, {String? ok}) async {
      try {
        await action();
        if (ok != null) showSnack(ok);
        return true;
      } catch (_) {
        showSnack('操作失败，请重试');
        return false;
      }
    }

    Future<void> confirmUndo(int taskId) async {
      final confirmed = await showDialog<bool>(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: const Text('撤销任务状态？'),
            content: const Text('该任务将恢复为待复习状态，是否确认撤销？'),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(false),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () => Navigator.of(context).pop(true),
                child: const Text('确认撤销'),
              ),
            ],
          );
        },
      );
      if (confirmed != true) return;
      await runAction(() => notifier.undoTaskStatus(taskId), ok: '已撤销');
    }

    // 性能优化（spec-performance-optimization.md / Phase 1）：
    // 使用 SliverList 实现虚拟化渲染，并且避免在 build 中遍历全量 timelineRows（O(n)），
    // 仅对“当前可见 index”做 O(1) 映射，从而在展开态切换时保持稳定帧时间。
    final hasError = state.errorMessage != null;
    final hasItems = state.items.isNotEmpty;
    final isInitialLoading = state.isLoading && !hasItems;

    final errorCount = hasError ? 1 : 0;
    final baseCount = hasItems ? (state.timelineRows.length + 2) : 1;
    final childCount = errorCount + baseCount;

    Widget buildErrorRow() {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          GlassCard(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.lg),
              child: Text(
                '加载失败：${state.errorMessage}',
                style: const TextStyle(color: Colors.red),
              ),
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
        ],
      );
    }

    Widget buildLoadingRow() {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      );
    }

    Widget buildEmptyRow() {
      return emptyState ??
          GlassCard(
            child: Padding(
              padding: const EdgeInsets.all(AppSpacing.xl),
              child: Text(
                '暂无任务',
                style: AppTypography.bodySecondary(context),
                textAlign: TextAlign.center,
              ),
            ),
          );
    }

    Widget buildHeaderRow(TaskHubTimelineHeaderRow row) {
      return KeyedSubtree(
        key: ValueKey<String>('day_${row.day.toIso8601String()}'),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _GroupHeader(label: _groupLabel(row.day)),
            const SizedBox(height: AppSpacing.sm),
          ],
        ),
      );
    }

    Widget buildTaskRow(TaskHubTimelineTaskRow row) {
      final onComplete = row.status == ReviewTaskStatus.pending
          ? () async {
              await runAction(() => notifier.completeTask(row.taskId), ok: '已完成');
            }
          : null;
      final onSkip = row.status == ReviewTaskStatus.pending
          ? () async {
              await runAction(() => notifier.skipTask(row.taskId), ok: '已跳过');
            }
          : null;

      return KeyedSubtree(
        key: ValueKey<int>(row.taskId),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // 性能优化（Phase 1）：卡片级 RepaintBoundary，减少滚动时重绘传播。
            RepaintBoundary(
              child: _TaskTimelineCard(
                item: row,
                blurEnabled: blurEnabled,
                expanded: state.expandedTaskIds.contains(row.taskId),
                onToggleExpanded: () => notifier.toggleExpanded(row.taskId),
                onComplete: onComplete,
                onSkip: onSkip,
                onUndo: row.status == ReviewTaskStatus.pending
                    ? null
                    : () => confirmUndo(row.taskId),
                onOpenDetail: () => context.push('/tasks/detail/${row.learningItemId}'),
              ),
            ),
            SizedBox(
              height: row.isLastInGroup ? AppSpacing.lg : AppSpacing.md,
            ),
          ],
        ),
      );
    }

    Widget buildFooterRow() {
      if (state.isLoadingMore) {
        return const Center(
          child: Padding(
            padding: EdgeInsets.all(16),
            child: CircularProgressIndicator(),
          ),
        );
      }
      if (state.nextCursor == null) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 12),
          child: Text(
            '已加载全部任务',
            style: AppTypography.bodySecondary(context),
            textAlign: TextAlign.center,
          ),
        );
      }
      return const SizedBox.shrink();
    }

    Widget buildBottomSpacerRow() => const SizedBox(height: 48);

    return SliverList(
      delegate: SliverChildBuilderDelegate(
        (context, index) {
          var i = index;
          if (hasError) {
            if (i == 0) return buildErrorRow();
            i -= 1;
          }

          if (!hasItems) {
            return isInitialLoading ? buildLoadingRow() : buildEmptyRow();
          }

          // items 非空：按 index 映射到 timelineRows / footer / spacer。
          final rowsLen = state.timelineRows.length;
          if (i < rowsLen) {
            final row = state.timelineRows[i];
            return switch (row) {
              TaskHubTimelineHeaderRow() =>
                buildHeaderRow(row as TaskHubTimelineHeaderRow),
              TaskHubTimelineTaskRow() => buildTaskRow(row as TaskHubTimelineTaskRow),
            };
          }

          i -= rowsLen;
          if (i == 0) return buildFooterRow();
          return buildBottomSpacerRow();
        },
        childCount: childCount,
      ),
    );
  }

  String _groupLabel(DateTime day) {
    // 日期分组标题语义：今天/昨天/具体日期（M月d日）。
    final today = YikeDateUtils.atStartOfDay(DateTime.now());
    final yesterday = today.subtract(const Duration(days: 1));
    if (YikeDateUtils.isSameDay(day, today)) return '今天';
    if (YikeDateUtils.isSameDay(day, yesterday)) return '昨天';
    return DateFormat('M月d日').format(day);
  }
}

/// 日期分组标题。
class _GroupHeader extends StatelessWidget {
  const _GroupHeader({required this.label});

  final String label;

  @override
  Widget build(BuildContext context) {
    return Text(label, style: AppTypography.h2(context));
  }
}

/// 时间线任务卡片：点击展开操作区（完成/跳过/撤销）。
class _TaskTimelineCard extends StatelessWidget {
  const _TaskTimelineCard({
    required this.item,
    required this.expanded,
    required this.onToggleExpanded,
    required this.onComplete,
    required this.onSkip,
    required this.onUndo,
    required this.onOpenDetail,
    required this.blurEnabled,
  });

  final TaskHubTimelineTaskRow item;
  final bool expanded;
  final VoidCallback onToggleExpanded;
  final VoidCallback? onComplete;
  final VoidCallback? onSkip;
  final VoidCallback? onUndo;
  final VoidCallback onOpenDetail;
  final bool blurEnabled;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? Colors.white : Colors.black;
    final secondary =
        Theme.of(context).textTheme.bodySmall?.color ?? Colors.grey;

    final tag = switch (item.status) {
      ReviewTaskStatus.done => const _StatusTag(
        label: '已完成',
        color: Colors.green,
      ),
      ReviewTaskStatus.skipped => const _StatusTag(
        label: '已跳过',
        color: Colors.orange,
      ),
      ReviewTaskStatus.pending => null,
    };

    final subtitle = _subtitleText(context);
    final info = _infoText();
    final detailLabel = _expandedDetailLabel();
    final detailText = _expandedDetailText();

    return InkWell(
      onTap: onToggleExpanded,
      borderRadius: BorderRadius.circular(16),
      child: GlassCard(
        blurSigma: blurEnabled ? 14 : 0,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '${item.title}（第${item.reviewRound}次）',
                          style: AppTypography.body(context).copyWith(
                            fontWeight: FontWeight.w700,
                            color: primary,
                          ),
                        ),
                        const SizedBox(height: AppSpacing.xs),
                        Text(
                          subtitle,
                          style: AppTypography.bodySecondary(context),
                        ),
                        if (info != null) ...[
                          const SizedBox(height: 6),
                          Text(
                            info,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: AppTypography.bodySecondary(context),
                          ),
                        ],
                      ],
                    ),
                  ),
                  tag ?? const SizedBox.shrink(),
                ],
              ),
              if (item.tags.isNotEmpty) ...[
                const SizedBox(height: AppSpacing.sm),
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: item.tags
                      .take(5)
                      .map(
                        (t) => Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 4,
                          ),
                          decoration: BoxDecoration(
                            color: Theme.of(
                              context,
                            ).colorScheme.primary.withAlpha(24),
                            borderRadius: BorderRadius.circular(999),
                            border: Border.all(
                              color: Theme.of(
                                context,
                              ).colorScheme.primary.withAlpha(80),
                            ),
                          ),
                          child: Text(
                            t,
                            style: AppTypography.bodySecondary(
                              context,
                            ).copyWith(fontSize: 12),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ],
              AnimatedCrossFade(
                // 交互要求：点击卡片展开/收起操作区。
                crossFadeState: expanded
                    ? CrossFadeState.showSecond
                    : CrossFadeState.showFirst,
                duration: const Duration(milliseconds: 160),
                firstChild: const SizedBox.shrink(),
                secondChild: Padding(
                  padding: const EdgeInsets.only(top: AppSpacing.md),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      if (detailLabel != null && detailText != null) ...[
                        Text(
                          detailLabel,
                          style: AppTypography.h2(
                            context,
                          ).copyWith(fontSize: 14),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          detailText,
                          style: AppTypography.bodySecondary(context),
                        ),
                        const SizedBox(height: AppSpacing.md),
                      ],
                      Row(
                        children: [
                          if (onComplete != null)
                            FilledButton(
                              onPressed: onComplete,
                              child: const Text('完成'),
                            ),
                          if (onComplete != null)
                            const SizedBox(width: AppSpacing.sm),
                          if (onSkip != null)
                            OutlinedButton(
                              onPressed: onSkip,
                              child: const Text('跳过'),
                            ),
                          const SizedBox(width: AppSpacing.sm),
                          OutlinedButton(
                            onPressed: onOpenDetail,
                            child: const Text('详情'),
                          ),
                          if (onUndo != null)
                            OutlinedButton(
                              onPressed: onUndo,
                              child: const Text('撤销'),
                            ),
                        ],
                      ),
                      const SizedBox(height: 2),
                      Text(
                        expanded ? '点击卡片可收起操作区' : '',
                        style: TextStyle(color: secondary, fontSize: 12),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  String _subtitleText(BuildContext context) {
    return switch (item.status) {
      ReviewTaskStatus.pending => '待复习',
      ReviewTaskStatus.done =>
        item.completedAt == null
            ? '已完成'
            : '完成于 ${TimeOfDay.fromDateTime(item.completedAt!).format(context)}',
      ReviewTaskStatus.skipped =>
        item.skippedAt == null
            ? '已跳过'
            : '跳过于 ${TimeOfDay.fromDateTime(item.skippedAt!).format(context)}',
    };
  }

  /// 生成时间线卡片的信息摘要（v2.6：description 优先，其次子任务数量，最后 fallback 到旧 note）。
  String? _infoText() {
    final desc = (item.description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    if (item.subtaskCount > 0) return '${item.subtaskCount} 个子任务';

    final legacy = (item.legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注：$legacy';

    return null;
  }

  String? _expandedDetailLabel() {
    final desc = (item.description ?? '').trim();
    if (desc.isNotEmpty) return '描述';

    final legacy = (item.legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return '旧备注（待迁移）';

    if (item.subtaskCount > 0) return '子任务';
    return null;
  }

  String? _expandedDetailText() {
    final desc = (item.description ?? '').trim();
    if (desc.isNotEmpty) return desc;

    final legacy = (item.legacyNote ?? '').trim();
    if (legacy.isNotEmpty) return legacy;

    if (item.subtaskCount > 0) return '共 ${item.subtaskCount} 个子任务（详见任务详情）';
    return null;
  }
}

class _StatusTag extends StatelessWidget {
  const _StatusTag({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withAlpha(22),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withAlpha(110)),
      ),
      child: Text(
        label,
        style: AppTypography.bodySecondary(
          context,
        ).copyWith(color: color, fontWeight: FontWeight.w700, fontSize: 12),
      ),
    );
  }
}
