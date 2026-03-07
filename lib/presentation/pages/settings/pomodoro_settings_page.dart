/// 文件用途：番茄钟设置页面，用于配置工作/休息时长与长休息间隔。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../domain/entities/pomodoro_settings.dart';
import '../../providers/pomodoro_provider.dart';
import '../../providers/pomodoro_settings_provider.dart';
import '../../widgets/glass_card.dart';

/// 番茄钟设置页。
class PomodoroSettingsPage extends ConsumerWidget {
  /// 构造函数。
  const PomodoroSettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(pomodoroSettingsProvider);
    final notifier = ref.read(pomodoroSettingsProvider.notifier);

    Future<void> save(PomodoroSettingsEntity next) async {
      await notifier.save(next);
      await ref.read(pomodoroProvider.notifier).refreshSettings();
      if (!context.mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('番茄钟设置已保存')));
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
                const SizedBox(height: AppSpacing.md),
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
                  final value = int.tryParse(valueText.trim());
                  if (value == null || value < min || value > max) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('请输入有效范围内的数字')),
                    );
                    return;
                  }
                  Navigator.of(context).pop(value);
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
        title: const Text('番茄钟设置'),
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
                    Text('配置说明', style: AppTypography.h2(context)),
                    const SizedBox(height: AppSpacing.sm),
                    Text(
                      '修改后的时长会在下一阶段自动生效；当前已经开始的倒计时不会被强制改写。',
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
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 48),
                child: Center(child: CircularProgressIndicator()),
              )
            else
              GlassCard(
                child: Column(
                  children: [
                    ListTile(
                      title: const Text('工作时长'),
                      subtitle: Text('${settings.workMinutes} 分钟'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final value = await pickNumber(
                          title: '工作时长',
                          initial: settings.workMinutes,
                          min: 1,
                          max: 180,
                          hint: '每一轮专注持续的分钟数。',
                        );
                        if (value == null) return;
                        await save(settings.copyWith(workMinutes: value));
                      },
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('短休息时长'),
                      subtitle: Text('${settings.shortBreakMinutes} 分钟'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final value = await pickNumber(
                          title: '短休息时长',
                          initial: settings.shortBreakMinutes,
                          min: 1,
                          max: 60,
                          hint: '普通休息阶段持续的分钟数。',
                        );
                        if (value == null) return;
                        await save(settings.copyWith(shortBreakMinutes: value));
                      },
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('长休息时长'),
                      subtitle: Text('${settings.longBreakMinutes} 分钟'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final value = await pickNumber(
                          title: '长休息时长',
                          initial: settings.longBreakMinutes,
                          min: 1,
                          max: 120,
                          hint: '达到长休息条件后持续的分钟数。',
                        );
                        if (value == null) return;
                        await save(settings.copyWith(longBreakMinutes: value));
                      },
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('长休息间隔轮数'),
                      subtitle: Text('每完成 ${settings.longBreakInterval} 轮触发一次'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () async {
                        final value = await pickNumber(
                          title: '长休息间隔轮数',
                          initial: settings.longBreakInterval,
                          min: 1,
                          max: 12,
                          hint: '每完成 N 轮工作后进入一次长休息。',
                        );
                        if (value == null) return;
                        await save(settings.copyWith(longBreakInterval: value));
                      },
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }
}
