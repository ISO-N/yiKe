/// 文件用途：学习目标设置页面（统计增强 P0：目标设定）。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../domain/entities/goal_settings.dart';
import '../../providers/goal_provider.dart';
import '../../widgets/glass_card.dart';

/// 学习目标设置页。
///
/// 说明：
/// - 目标值持久化到 settings 表（goal_daily/goal_streak/goal_weekly_rate）
/// - 目标进度由统计数据派生，不新增数据库字段
class GoalSettingsPage extends ConsumerWidget {
  /// 构造函数。
  const GoalSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(goalSettingsProvider);
    final notifier = ref.read(goalSettingsProvider.notifier);

    Future<void> save(GoalSettingsEntity next) async {
      await notifier.save(next);
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('目标设置已保存')),
      );
    }

    Future<int?> pickNumber({
      required String title,
      required int initial,
      required int min,
      required int max,
      required String hint,
    }) async {
      // 说明：这里避免显式持有并 dispose TextEditingController，
      // 防止在 Dialog 关闭动画期间出现“controller 已 dispose 但 TextField 仍在渲染”的断言。
      var valueText = initial.toString();
      final result = await showDialog<int>(
        context: context,
        builder: (context) {
          return AlertDialog(
            title: Text(title),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(hint, style: AppTypography.bodySecondary(context)),
                const SizedBox(height: 12),
                TextFormField(
                  initialValue: valueText,
                  autofocus: true,
                  keyboardType: TextInputType.number,
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                  onChanged: (v) => valueText = v,
                  decoration: InputDecoration(
                    border: const OutlineInputBorder(),
                    helperText: '范围：$min ~ $max',
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('取消'),
              ),
              FilledButton(
                onPressed: () {
                  final v = int.tryParse(valueText.trim());
                  if (v == null || v < min || v > max) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('请输入有效范围内的数字')),
                    );
                    return;
                  }
                  Navigator.of(context).pop(v);
                },
                child: const Text('确定'),
              ),
            ],
          );
        },
      );
      return result;
    }

    final settings = state.settings;

    return Scaffold(
      appBar: AppBar(
        title: const Text('学习目标'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () => notifier.load(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            GlassCard(
              child: Padding(
                padding: const EdgeInsets.all(AppSpacing.lg),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('目标说明', style: AppTypography.h2(context)),
                    const SizedBox(height: AppSpacing.sm),
                    Text(
                      '目标仅影响统计展示与达成反馈，不会改变任务生成与复习节奏。',
                      style: AppTypography.bodySecondary(context),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: AppSpacing.lg),
            if (state.errorMessage != null) ...[
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Text(
                    '加载失败：${state.errorMessage}',
                    style: TextStyle(color: Theme.of(context).colorScheme.error),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
            ],
            if (state.isLoading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: CircularProgressIndicator(),
                ),
              )
            else
              GlassCard(
                child: Column(
                  children: [
                    SwitchListTile(
                      title: const Text('每日完成目标'),
                      subtitle: Text(
                        settings.dailyTarget == null
                            ? '未启用'
                            : '目标：${settings.dailyTarget} 个任务',
                      ),
                      value: settings.dailyTarget != null,
                      onChanged: (enabled) async {
                        final next = enabled
                            ? settings.copyWith(
                                dailyTarget: settings.dailyTarget ?? 10,
                              )
                            : settings.copyWith(clearDaily: true);
                        await save(next);
                      },
                    ),
                    if (settings.dailyTarget != null) ...[
                      const Divider(height: 1),
                      ListTile(
                        title: const Text('设置每日目标值'),
                        subtitle: Text('${settings.dailyTarget}'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () async {
                          final v = await pickNumber(
                            title: '每日完成目标',
                            initial: settings.dailyTarget ?? 10,
                            min: 1,
                            max: 999,
                            hint: '每天完成 N 个任务（done）视为达成。',
                          );
                          if (v == null) return;
                          await save(settings.copyWith(dailyTarget: v));
                        },
                      ),
                    ],
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('连续打卡目标'),
                      subtitle: Text(
                        settings.streakTarget == null
                            ? '未启用'
                            : '目标：${settings.streakTarget} 天',
                      ),
                      value: settings.streakTarget != null,
                      onChanged: (enabled) async {
                        final next = enabled
                            ? settings.copyWith(
                                streakTarget: settings.streakTarget ?? 7,
                              )
                            : settings.copyWith(clearStreak: true);
                        await save(next);
                      },
                    ),
                    if (settings.streakTarget != null) ...[
                      const Divider(height: 1),
                      ListTile(
                        title: const Text('设置连续打卡目标值'),
                        subtitle: Text('${settings.streakTarget}'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () async {
                          final v = await pickNumber(
                            title: '连续打卡目标',
                            initial: settings.streakTarget ?? 7,
                            min: 1,
                            max: 365,
                            hint: '连续每天至少完成 1 个任务视为打卡成功。',
                          );
                          if (v == null) return;
                          await save(settings.copyWith(streakTarget: v));
                        },
                      ),
                    ],
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('本周完成率目标'),
                      subtitle: Text(
                        settings.weeklyRateTarget == null
                            ? '未启用'
                            : '目标：${settings.weeklyRateTarget}%',
                      ),
                      value: settings.weeklyRateTarget != null,
                      onChanged: (enabled) async {
                        final next = enabled
                            ? settings.copyWith(
                                weeklyRateTarget:
                                    settings.weeklyRateTarget ?? 80,
                              )
                            : settings.copyWith(clearWeeklyRate: true);
                        await save(next);
                      },
                    ),
                    if (settings.weeklyRateTarget != null) ...[
                      const Divider(height: 1),
                      ListTile(
                        title: const Text('设置本周完成率目标值'),
                        subtitle: Text('${settings.weeklyRateTarget}%'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () async {
                          final v = await pickNumber(
                            title: '本周完成率目标',
                            initial: settings.weeklyRateTarget ?? 80,
                            min: 1,
                            max: 100,
                            hint: '完成率 = done / (done + pending) × 100%。',
                          );
                          if (v == null) return;
                          await save(settings.copyWith(weeklyRateTarget: v));
                        },
                      ),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}

