/// 文件用途：番茄钟专注页面，展示倒计时、控制按钮、轮次和统计信息。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../providers/pomodoro_provider.dart';
import '../../providers/pomodoro_stats_provider.dart';
import '../../widgets/glass_card.dart';

/// 番茄钟主页。
class PomodoroPage extends ConsumerWidget {
  /// 构造函数。
  const PomodoroPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final timerState = ref.watch(pomodoroProvider);
    final timerNotifier = ref.read(pomodoroProvider.notifier);
    final statsState = ref.watch(pomodoroStatsProvider);
    final width = MediaQuery.of(context).size.width;
    final useTwoColumns = width >= 1024;

    Widget timerPanel = GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Wrap(
              spacing: AppSpacing.sm,
              runSpacing: AppSpacing.sm,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                _StatusChip(
                  icon: _phaseIcon(timerState.phase),
                  label: _phaseLabel(timerState.phase),
                ),
                _StatusChip(
                  icon: timerState.isRunning
                      ? Icons.play_circle_outline
                      : timerState.isPaused
                      ? Icons.pause_circle_outline
                      : Icons.hourglass_bottom_outlined,
                  label: _statusLabel(timerState.status),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.lg),
            Text('专注计时', style: AppTypography.h1(context)),
            const SizedBox(height: AppSpacing.sm),
            Text(
              '当前阶段会固定使用开始时的配置；修改设置后，会在下一阶段自动接管。',
              style: AppTypography.bodySecondary(context),
            ),
            const SizedBox(height: AppSpacing.xl),
            Center(
              child: Text(
                _formatSeconds(timerState.remainingSeconds),
                style: Theme.of(context).textTheme.displayMedium?.copyWith(
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2,
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.md),
            Center(
              child: Text(
                '本阶段共 ${timerState.currentPhaseTotalSeconds ~/ 60} 分钟',
                style: AppTypography.bodySecondary(context),
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            Wrap(
              spacing: AppSpacing.md,
              runSpacing: AppSpacing.md,
              children: [
                FilledButton.icon(
                  onPressed: !timerState.isReady
                      ? null
                      : timerState.isRunning
                      ? timerNotifier.pause
                      : timerState.isPaused
                      ? timerNotifier.resume
                      : timerNotifier.start,
                  icon: Icon(
                    timerState.isRunning ? Icons.pause : Icons.play_arrow,
                  ),
                  label: Text(
                    timerState.isRunning
                        ? '暂停'
                        : timerState.isPaused
                        ? '继续'
                        : '开始',
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: timerState.isReady ? timerNotifier.reset : null,
                  icon: const Icon(Icons.replay),
                  label: const Text('重置'),
                ),
                OutlinedButton.icon(
                  onPressed: timerState.isReady ? timerNotifier.skip : null,
                  icon: const Icon(Icons.skip_next),
                  label: const Text('跳过'),
                ),
              ],
            ),
          ],
        ),
      ),
    );

    Widget sidePanel = Column(
      children: [
        GlassCard(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('轮次进度', style: AppTypography.h2(context)),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  '本周期已完成 ${timerState.completedRounds} / ${timerState.settings.longBreakInterval} 轮',
                  style: AppTypography.body(context),
                ),
                const SizedBox(height: AppSpacing.sm),
                LinearProgressIndicator(
                  value: timerState.settings.longBreakInterval <= 0
                      ? 0
                      : (timerState.completedRounds %
                                timerState.settings.longBreakInterval) /
                            timerState.settings.longBreakInterval,
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        GlassCard(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('专注统计', style: AppTypography.h2(context)),
                const SizedBox(height: AppSpacing.md),
                if (statsState.isLoading)
                  const Center(child: CircularProgressIndicator())
                else ...[
                  _StatTile(
                    label: '今日完成',
                    value: '${statsState.stats.todayCompletedCount} 个',
                  ),
                  const Divider(height: 24),
                  _StatTile(
                    label: '本周完成',
                    value: '${statsState.stats.weekCompletedCount} 个',
                  ),
                  const Divider(height: 24),
                  _StatTile(
                    label: '累计专注',
                    value: _formatFocusMinutes(statsState.stats.totalFocusMinutes),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.lg),
        GlassCard(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('当前配置', style: AppTypography.h2(context)),
                const SizedBox(height: AppSpacing.md),
                _StatTile(
                  label: '工作时长',
                  value: '${timerState.settings.workMinutes} 分钟',
                ),
                const Divider(height: 24),
                _StatTile(
                  label: '短休息',
                  value: '${timerState.settings.shortBreakMinutes} 分钟',
                ),
                const Divider(height: 24),
                _StatTile(
                  label: '长休息',
                  value: '${timerState.settings.longBreakMinutes} 分钟',
                ),
                const Divider(height: 24),
                _StatTile(
                  label: '长休息间隔',
                  value: '${timerState.settings.longBreakInterval} 轮',
                ),
                const SizedBox(height: AppSpacing.md),
                Align(
                  alignment: Alignment.centerLeft,
                  child: FilledButton.tonalIcon(
                    onPressed: () => context.push('/settings/pomodoro'),
                    icon: const Icon(Icons.tune),
                    label: const Text('调整设置'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text('专注'),
        actions: [
          IconButton(
            tooltip: '刷新统计',
            onPressed: () => ref.read(pomodoroStatsProvider.notifier).load(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: DecoratedBox(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            colors: [
              Theme.of(context).colorScheme.primary.withValues(alpha: 0.12),
              Theme.of(context).colorScheme.surface,
              Theme.of(context).colorScheme.tertiary.withValues(alpha: 0.08),
            ],
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
          ),
        ),
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: useTwoColumns
                ? Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(flex: 3, child: timerPanel),
                      const SizedBox(width: AppSpacing.lg),
                      Expanded(
                        flex: 2,
                        child: SingleChildScrollView(child: sidePanel),
                      ),
                    ],
                  )
                : ListView(
                    key: const PageStorageKey('pomodoro_scroll'),
                    children: [
                      timerPanel,
                      const SizedBox(height: AppSpacing.lg),
                      sidePanel,
                    ],
                  ),
          ),
        ),
      ),
    );
  }

  /// 格式化剩余秒数为 `MM:SS`。
  String _formatSeconds(int totalSeconds) {
    final minutes = totalSeconds ~/ 60;
    final seconds = totalSeconds % 60;
    final mm = minutes.toString().padLeft(2, '0');
    final ss = seconds.toString().padLeft(2, '0');
    return '$mm:$ss';
  }

  /// 将累计分钟格式化为人类可读文本。
  String _formatFocusMinutes(int minutes) {
    final hours = minutes ~/ 60;
    final remainMinutes = minutes % 60;
    if (hours <= 0) return '$minutes 分钟';
    if (remainMinutes == 0) return '$hours 小时';
    return '$hours 小时 $remainMinutes 分钟';
  }

  /// 返回阶段名称。
  String _phaseLabel(PomodoroPhase phase) {
    return switch (phase) {
      PomodoroPhase.work => '工作阶段',
      PomodoroPhase.shortBreak => '短休息',
      PomodoroPhase.longBreak => '长休息',
    };
  }

  /// 返回阶段图标。
  IconData _phaseIcon(PomodoroPhase phase) {
    return switch (phase) {
      PomodoroPhase.work => Icons.local_fire_department_outlined,
      PomodoroPhase.shortBreak => Icons.coffee_outlined,
      PomodoroPhase.longBreak => Icons.spa_outlined,
    };
  }

  /// 返回状态名称。
  String _statusLabel(PomodoroRunStatus status) {
    return switch (status) {
      PomodoroRunStatus.idle => '未开始',
      PomodoroRunStatus.running => '进行中',
      PomodoroRunStatus.paused => '已暂停',
    };
  }
}

/// 轻量状态标签。
class _StatusChip extends StatelessWidget {
  /// 构造函数。
  const _StatusChip({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Chip(
      avatar: Icon(icon, size: 18),
      label: Text(label),
      visualDensity: VisualDensity.compact,
    );
  }
}

/// 简单的统计行组件。
class _StatTile extends StatelessWidget {
  /// 构造函数。
  const _StatTile({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(label, style: AppTypography.bodySecondary(context)),
        ),
        Text(value, style: AppTypography.body(context)),
      ],
    );
  }
}
