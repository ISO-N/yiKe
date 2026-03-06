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
    final undoSnackbarEnabled = ref.watch(undoSnackbarEnabledProvider);
    final undoSnackbarNotifier = ref.read(
      undoSnackbarEnabledProvider.notifier,
    );
    final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);
    final hapticNotifier = ref.read(hapticFeedbackEnabledProvider.notifier);
    final skeletonStrategy = ref.watch(skeletonStrategyProvider);
    final skeletonStrategyNotifier = ref.read(
      skeletonStrategyProvider.notifier,
    );

    Future<void> save(AppSettingsEntity next) async {
      await notifier.save(next);

      // v1.4.0：精准定时通知（zonedSchedule）。
      //
      // 说明：
      // - 只要用户在设置页保存了提醒时间/开关，就尝试重新调度一次，确保“修改后立即生效”
      // - 调度失败不应影响设置保存主流程（例如平台不支持/权限被拒绝）
      try {
        await NotificationService.instance.initialize();
        if (next.notificationsEnabled) {
          final t = TimeUtils.parseHHmm(next.reminderTime);
          await NotificationService.instance.scheduleDailyReviewReminder(
            id: 1,
            time: t,
            title: '今日复习提醒',
            body: '打开忆刻开始复习吧',
            payloadRoute: '/home',
          );
        } else {
          await NotificationService.instance.cancelAll();
        }
      } catch (_) {}

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

    Future<void> saveUndoSnackbarEnabled(bool enabled) async {
      try {
        await undoSnackbarNotifier.setEnabled(enabled);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已更新撤销提示设置')),
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

    Future<void> saveHapticEnabled(bool enabled) async {
      try {
        await hapticNotifier.setEnabled(enabled);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已更新触觉反馈设置')),
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

    Future<void> saveSkeletonStrategy(String next) async {
      try {
        await skeletonStrategyNotifier.setStrategy(next);
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('已更新骨架屏策略')),
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
              key: const PageStorageKey('settings_scroll'),
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
                        '移动端优先使用精准定时提醒（系统可能允许少量延迟）；桌面端会按平台能力降级。',
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
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('任务逾期通知'),
                      subtitle: const Text('启动时检测逾期超过 3 天的任务并提醒'),
                      value: state.settings.overdueNotificationEnabled,
                      onChanged:
                          state.isLoading
                              ? null
                              : (v) => save(
                                state.settings.copyWith(
                                  overdueNotificationEnabled: v,
                                ),
                              ),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('目标达成通知'),
                      subtitle: const Text('达成学习目标时提醒，并支持跳转到统计页查看详情'),
                      value: state.settings.goalNotificationEnabled,
                      onChanged:
                          state.isLoading
                              ? null
                              : (v) => save(
                                state.settings.copyWith(
                                  goalNotificationEnabled: v,
                                ),
                              ),
                    ),
                    const Divider(height: 1),
                    SwitchListTile(
                      title: const Text('打卡里程碑通知'),
                      subtitle: const Text('连续打卡 7/30/100 天时提醒'),
                      value: state.settings.streakNotificationEnabled,
                      onChanged:
                          state.isLoading
                              ? null
                              : (v) => save(
                                 state.settings.copyWith(
                                   streakNotificationEnabled: v,
                                 ),
                               ),
                    ),
                    if (kDebugMode) ...[
                      const Divider(height: 1),
                      ListTile(
                        title: const Text('测试通知（仅调试模式）'),
                        subtitle: const Text('用于验证 Android/Windows 通知通道是否正常'),
                        trailing: const Icon(Icons.notifications_active_outlined),
                        onTap: state.isLoading
                            ? null
                            : () async {
                                try {
                                  // 说明：测试按钮尽量走同一套初始化/发送逻辑，便于复现实机问题。
                                  await NotificationService.instance.initialize();
                                  await NotificationService.instance
                                      .requestPermission();

                                  final id = DateTime.now()
                                      .millisecondsSinceEpoch
                                      .remainder(100000);
                                  await NotificationService.instance
                                      .showReviewNotification(
                                    id: id,
                                    title: '测试通知',
                                    body: '如果你看到了这条通知，说明通知功能已正常工作。',
                                    payloadRoute: '/settings',
                                  );

                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(content: Text('已发送测试通知')),
                                    );
                                  }
                                } catch (e, st) {
                                  // 调试期需要可见的失败信息，避免“无效果”无法定位。
                                  debugPrint('测试通知发送失败：$e\n$st');
                                  if (context.mounted) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      const SnackBar(
                                        content: Text('发送失败，请检查权限或查看调试日志'),
                                      ),
                                    );
                                  }
                                }
                              },
                      ),
                    ],
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
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.color_lens_outlined),
                        title: const Text('主题颜色与AMOLED'),
                        subtitle: const Text('自定义主题色并支持 AMOLED 深色'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('/settings/theme'),
                      ),
                      const Divider(height: 1),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('任务列表毛玻璃效果'),
                        subtitle: const Text('关闭可提升滚动流畅度（仅本机设置，不参与同步）'),
                        value: taskListBlurEnabled,
                        onChanged: saveTaskListBlurEnabled,
                      ),
                      const Divider(height: 1),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('撤销提示（Snackbar）'),
                        subtitle: const Text('完成/跳过后显示可撤销提示（仅本机设置）'),
                        value: undoSnackbarEnabled,
                        onChanged: saveUndoSnackbarEnabled,
                      ),
                      const Divider(height: 1),
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        title: const Text('触觉反馈'),
                        subtitle: const Text('关键交互点触发触觉反馈（仅移动端，受系统减少动效影响）'),
                        value: hapticEnabled,
                        onChanged: saveHapticEnabled,
                      ),
                      const Divider(height: 1),
                      ListTile(
                        contentPadding: EdgeInsets.zero,
                        leading: const Icon(Icons.layers_outlined),
                        title: const Text('骨架屏策略'),
                        subtitle: Text(
                          switch (skeletonStrategy) {
                            'on' => '总是显示',
                            'off' => '从不显示',
                            _ => '自动（加载 ≥200ms 显示）',
                          },
                        ),
                        trailing: DropdownButton<String>(
                          value: skeletonStrategy,
                          items: const [
                            DropdownMenuItem(value: 'auto', child: Text('自动')),
                            DropdownMenuItem(value: 'on', child: Text('总是')),
                            DropdownMenuItem(value: 'off', child: Text('关闭')),
                          ],
                          onChanged: (v) {
                            if (v == null) return;
                            saveSkeletonStrategy(v);
                          },
                        ),
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
                        leading: const Icon(Icons.flag_outlined),
                        title: const Text('学习目标'),
                        subtitle: const Text('设置每日完成/连续打卡/本周完成率目标'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => context.push('/settings/goals'),
                      ),
                      const Divider(height: 1),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.topic_outlined),
                      title: const Text('主题管理'),
                      subtitle: const Text('管理主题与内容关联（v2.1）'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/topics'),
                    ),
                    const Divider(height: 1),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      leading: const Icon(Icons.timer_outlined),
                      title: const Text('番茄钟设置'),
                      subtitle: const Text('配置工作时长、休息时长和长休息间隔'),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => context.push('/settings/pomodoro'),
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
