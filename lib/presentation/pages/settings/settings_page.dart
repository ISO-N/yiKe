/// 文件用途：设置页（通知开关、提醒时间、免打扰时段等）。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-02-26（新增主题模式设置入口）
library;

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:app_settings/app_settings.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/time_utils.dart';
import '../../../domain/entities/app_settings.dart';
import '../../../infrastructure/notification/notification_service.dart';
import '../../providers/notification_permission_provider.dart';
import '../../providers/settings_provider.dart';
import '../../providers/sync_provider.dart';
import '../../providers/theme_provider.dart';
import '../../providers/ui_preferences_provider.dart';
import '../../widgets/glass_card.dart';
import 'widgets/data_management_section.dart';
import 'widgets/theme_mode_sheet.dart';

class SettingsPage extends ConsumerWidget {
  /// 设置页。
  ///
  /// 返回值：页面 Widget。
  /// 异常：无。
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(settingsProvider);
    final notifier = ref.read(settingsProvider.notifier);
    final permissionAsync = ref.watch(notificationPermissionProvider);
    final currentThemeMode = ref.watch(themeModeProvider);
    final syncUi = ref.watch(syncControllerProvider);
    final taskListBlurEnabled = ref.watch(taskListBlurEnabledProvider);
    final taskListBlurNotifier = ref.read(taskListBlurEnabledProvider.notifier);

    Future<void> save(AppSettingsEntity next) async {
      await notifier.save(next);
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('设置已保存')));
      }
    }

    Future<void> openNotificationSettings() async {
      // 优先打开”通知”面板；若平台不支持，插件会降级处理。
      await AppSettings.openAppSettings(type: AppSettingsType.notification);
      // 打开系统设置返回后，刷新一次设置页的权限状态。
      ref.invalidate(notificationPermissionProvider);
    }

    Future<void> saveTaskListBlurEnabled(bool enabled) async {
      try {
        await taskListBlurNotifier.setEnabled(enabled);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已更新任务列表外观设置')),
          );
        }
      } catch (_) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('保存失败，请重试')),
          );
        }
      }
    }

    Future<void> pickTime({
      required String title,
      required String current,
      required ValueChanged<String> onPicked,
    }) async {
      final initial = TimeUtils.parseHHmm(current);
      final picked = await showTimePicker(
        context: context,
        initialTime: initial,
        helpText: title,
      );
      if (picked == null) return;
      onPicked(TimeUtils.formatHHmm(picked));
    }

    void showThemeModeSheet() {
      showModalBottomSheet(
        context: context,
        builder: (context) => const ThemeModeSheet(),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('设置'),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () => notifier.load(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: ListView(
            children: [
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('通知与提醒', style: AppTypography.h2(context)),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        '使用后台定时检查方式提醒，时间精度约 ±30 分钟。',
                        style: AppTypography.bodySecondary(context),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              GlassCard(
                child: Column(
                  children: [
                    SwitchListTile(
                      title: const Text('开启通知提醒'),
                      subtitle: const Text('关闭后将不再发送复习提醒'),
                      value: state.settings.notificationsEnabled,
                      onChanged: state.isLoading
                          ? null
                          : (v) => save(
                              state.settings.copyWith(notificationsEnabled: v),
                            ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('每日提醒时间'),
                      subtitle: Text(state.settings.reminderTime),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: state.isLoading
                          ? null
                          : () => pickTime(
                              title: '选择每日提醒时间',
                              current: state.settings.reminderTime,
                              onPicked: (v) => save(
                                state.settings.copyWith(reminderTime: v),
                              ),
                            ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('免打扰开始时间'),
                      subtitle: Text(state.settings.doNotDisturbStart),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: state.isLoading
                          ? null
                          : () => pickTime(
                              title: '选择免打扰开始时间',
                              current: state.settings.doNotDisturbStart,
                              onPicked: (v) => save(
                                state.settings.copyWith(doNotDisturbStart: v),
                              ),
                            ),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      title: const Text('免打扰结束时间'),
                      subtitle: Text(state.settings.doNotDisturbEnd),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: state.isLoading
                          ? null
                          : () => pickTime(
                              title: '选择免打扰结束时间',
                              current: state.settings.doNotDisturbEnd,
                              onPicked: (v) => save(
                                state.settings.copyWith(doNotDisturbEnd: v),
                              ),
                            ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              GlassCard(
                child: Column(
                  children: [
                    ListTile(
                      title: const Text('帮助'),
                      subtitle: const Text('查看忆刻学习指南与使用说明'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/help'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('通知权限', style: AppTypography.h2(context)),
                      const SizedBox(height: AppSpacing.sm),
                      permissionAsync.when(
                        data: (permission) {
                          final text = switch (permission) {
                            NotificationPermissionState.enabled => '已开启',
                            NotificationPermissionState.disabled =>
                              '未开启（可能收不到提醒）',
                            NotificationPermissionState.unknown =>
                              '当前平台不支持读取权限状态',
                          };

                          final showOpenSettings =
                              permission != NotificationPermissionState.enabled;

                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      text,
                                      style: AppTypography.bodySecondary(
                                        context,
                                      ),
                                    ),
                                  ),
                                  OutlinedButton(
                                    onPressed: () async {
                                      final ok = await NotificationService
                                          .instance
                                          .requestPermission();
                                      if (context.mounted) {
                                        ScaffoldMessenger.of(
                                          context,
                                        ).showSnackBar(
                                          SnackBar(
                                            content: Text(
                                              ok == true
                                                  ? '已请求通知权限'
                                                  : '未获取到通知权限',
                                            ),
                                          ),
                                        );
                                      }
                                      ref.invalidate(
                                        notificationPermissionProvider,
                                      );
                                    },
                                    child: const Text('请求权限'),
                                  ),
                                ],
                              ),
                              const SizedBox(height: AppSpacing.sm),
                              ListTile(
                                contentPadding: EdgeInsets.zero,
                                title: const Text('打开系统通知设置'),
                                subtitle: const Text('若已拒绝权限，请在系统设置中手动开启'),
                                trailing: const Icon(Icons.open_in_new),
                                onTap: showOpenSettings
                                    ? openNotificationSettings
                                    : null,
                              ),
                              const Divider(height: 1),
                              SwitchListTile(
                                contentPadding: EdgeInsets.zero,
                                title: const Text('首页通知引导弹窗'),
                                subtitle: const Text('关闭后首页不再弹出“去开启通知权限”的提示'),
                                value: !state
                                    .settings
                                    .notificationPermissionGuideDismissed,
                                onChanged: state.isLoading
                                    ? null
                                    : (v) => save(
                                        state.settings.copyWith(
                                          notificationPermissionGuideDismissed:
                                              !v,
                                        ),
                                      ),
                              ),
                            ],
                          );
                        },
                        loading: () => const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Center(child: CircularProgressIndicator()),
                        ),
                        error: (e, _) => Text(
                          '权限状态读取失败：$e',
                          style: AppTypography.bodySecondary(context),
                        ),
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
                      Text('外观设置', style: AppTypography.h2(context)),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        '可选择跟随系统、浅色或深色主题。',
                        style: AppTypography.bodySecondary(context),
                      ),
                      const SizedBox(height: AppSpacing.md),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.palette_outlined),
                        title: const Text('主题模式'),
                        subtitle: Text(currentThemeMode.label),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: showThemeModeSheet,
                      ),
                      const Divider(height: 1),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('任务列表毛玻璃效果'),
                        subtitle: const Text('关闭可提升滚动流畅度（仅本机设置，不参与同步）'),
                        value: taskListBlurEnabled,
                        onChanged: saveTaskListBlurEnabled,
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              DataManagementSection(syncState: syncUi.state),
              const SizedBox(height: AppSpacing.lg),
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('主题与内容', style: AppTypography.h2(context)),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        '管理主题与内容关联，用于筛选与结构化复习。',
                        style: AppTypography.bodySecondary(context),
                      ),
                      const SizedBox(height: AppSpacing.md),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.topic_outlined),
                        title: const Text('主题管理'),
                        subtitle: const Text('管理主题与内容关联（v2.1）'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('/topics'),
                      ),
                    ],
                  ),
                ),
              ),
              if (kDebugMode) ...[
                const SizedBox(height: AppSpacing.lg),
                GlassCard(
                  child: Padding(
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('开发工具', style: AppTypography.h2(context)),
                        const SizedBox(height: AppSpacing.sm),
                        Text(
                          '仅 Debug 模式下可见，用于快速生成测试数据。',
                          style: AppTypography.bodySecondary(context),
                        ),
                        const SizedBox(height: AppSpacing.md),
                        ListTile(
                          contentPadding: EdgeInsets.zero,
                          leading: const Icon(Icons.developer_mode_outlined),
                          title: const Text('模拟数据生成器'),
                          subtitle: const Text('一键生成/清理 Mock 学习内容与复习任务'),
                          trailing: const Icon(Icons.chevron_right),
                          onTap: () =>
                              context.push('/settings/debug/mock-data'),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
              if (state.errorMessage != null) ...[
                const SizedBox(height: AppSpacing.lg),
                GlassCard(
                  child: Padding(
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    child: Text(
                      '错误：${state.errorMessage}',
                      style: const TextStyle(color: Colors.red),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 96),
            ],
          ),
        ),
      ),
    );
  }

}
