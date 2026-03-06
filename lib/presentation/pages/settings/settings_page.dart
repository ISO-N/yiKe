/// 文件用途：“我的”页与设置中心，统一承载个人摘要、偏好设置与学习配置入口。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-03-06（重构为分组式设置中心与桌面侧栏布局）
library;

import 'package:app_settings/app_settings.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_strings.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/responsive_utils.dart';
import '../../../core/utils/time_utils.dart';
import '../../../domain/entities/app_settings.dart';
import '../../../infrastructure/notification/notification_service.dart';
import '../../providers/notification_permission_provider.dart';
import '../../providers/settings_provider.dart';
import '../../providers/sync_provider.dart';
import '../../providers/theme_provider.dart';
import '../../providers/ui_preferences_provider.dart';
import '../../widgets/error_card.dart';
import '../../widgets/glass_card.dart';
import '../../widgets/gradient_background.dart';
import '../../widgets/semantic_panels.dart';
import 'widgets/data_management_section.dart';
import 'widgets/theme_mode_sheet.dart';

enum _SettingsPane { overview, appearance, notifications, data, learning, help }

class SettingsPage extends ConsumerStatefulWidget {
  /// 设置页。
  const SettingsPage({super.key});

  @override
  ConsumerState<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends ConsumerState<SettingsPage> {
  _SettingsPane _selectedPane = _SettingsPane.overview;

  /// 保存设置并同步通知调度。
  Future<void> _save(BuildContext context, AppSettingsEntity next) async {
    await ref.read(settingsProvider.notifier).save(next);
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

  /// 保存本地偏好开关。
  Future<void> _saveLocalPreference<T>({
    required BuildContext context,
    required Future<void> Function(T) action,
    required T value,
    required String successText,
  }) async {
    try {
      await action(value);
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(successText)));
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('保存失败，请重试')));
      }
    }
  }

  /// 选择时间并回写设置。
  Future<void> _pickTime({
    required BuildContext context,
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

  /// 打开系统通知设置。
  Future<void> _openNotificationSettings() async {
    await AppSettings.openAppSettings(type: AppSettingsType.notification);
    ref.invalidate(notificationPermissionProvider);
  }

  /// 打开主题模式弹层。
  void _showThemeModeSheet(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (_) => const ThemeModeSheet(),
    );
  }

  /// 转换权限摘要文案。
  String _permissionText(NotificationPermissionState? permission) {
    return switch (permission) {
      NotificationPermissionState.enabled => '已开启',
      NotificationPermissionState.disabled => '未开启',
      NotificationPermissionState.unknown => '平台未知',
      null => '读取中…',
    };
  }

  /// 转换同步状态文案。
  String _syncText(SyncState state) {
    return switch (state) {
      SyncState.disconnected => '未配对',
      SyncState.connecting => '连接中…',
      SyncState.connected => '已配对',
      SyncState.syncing => '同步中…',
      SyncState.synced => '同步完成',
      SyncState.error => '同步失败',
    };
  }

  @override
  Widget build(BuildContext context) {
    final settingsState = ref.watch(settingsProvider);
    final settings = settingsState.settings;
    final permissionAsync = ref.watch(notificationPermissionProvider);
    final permission = permissionAsync.valueOrNull;
    final currentThemeMode = ref.watch(themeModeProvider);
    final syncUi = ref.watch(syncControllerProvider);
    final taskListBlurEnabled = ref.watch(taskListBlurEnabledProvider);
    final undoSnackbarEnabled = ref.watch(undoSnackbarEnabledProvider);
    final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);
    final skeletonStrategy = ref.watch(skeletonStrategyProvider);
    final isDesktop = ResponsiveUtils.isDesktop(context);

    Widget overviewSection = SectionCard(
      title: '个人概览',
      subtitle: '从这里快速进入统计、同步、目标和帮助。',
      child: Column(
        children: [
          ActionRow(
            icon: Icons.insights_outlined,
            title: '学习统计',
            subtitle: '查看趋势、热力图和目标进度',
            onTap: () => context.push('/statistics'),
          ),
          const SizedBox(height: AppSpacing.md),
          ActionRow(
            icon: Icons.sync,
            title: '同步设置',
            subtitle: _syncText(syncUi.state),
            onTap: () => context.push('/settings/sync'),
          ),
          const SizedBox(height: AppSpacing.md),
          ActionRow(
            icon: Icons.flag_outlined,
            title: '学习目标',
            subtitle: '设置每日完成、连续打卡和周完成率目标',
            onTap: () => context.push('/settings/goals'),
          ),
          const SizedBox(height: AppSpacing.md),
          ActionRow(
            icon: Icons.help_outline,
            title: AppStrings.help,
            subtitle: '查看忆刻学习指南与使用说明',
            onTap: () => context.push('/help'),
          ),
        ],
      ),
    );

    Widget appearanceSection = SectionCard(
      title: '外观与体验',
      subtitle: '管理主题、视觉层级和交互反馈。',
      child: Column(
        children: [
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.palette_outlined),
            title: const Text('主题模式'),
            subtitle: Text(currentThemeMode.label),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => _showThemeModeSheet(context),
          ),
          const Divider(height: 1),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.color_lens_outlined),
            title: const Text('主题颜色与 AMOLED'),
            subtitle: const Text('自定义主题色并支持 AMOLED 深色'),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => context.push('/settings/theme'),
          ),
          const Divider(height: 1),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('任务列表毛玻璃效果'),
            subtitle: const Text('关闭可提升滚动流畅度（仅本机设置）'),
            value: taskListBlurEnabled,
            onChanged: (v) => _saveLocalPreference(
              context: context,
              action: ref.read(taskListBlurEnabledProvider.notifier).setEnabled,
              value: v,
              successText: '已更新任务列表外观设置',
            ),
          ),
          const Divider(height: 1),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('撤销提示（Snackbar）'),
            subtitle: const Text('完成/跳过后显示可撤销提示（仅本机设置）'),
            value: undoSnackbarEnabled,
            onChanged: (v) => _saveLocalPreference(
              context: context,
              action: ref.read(undoSnackbarEnabledProvider.notifier).setEnabled,
              value: v,
              successText: '已更新撤销提示设置',
            ),
          ),
          const Divider(height: 1),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: const Text('触觉反馈'),
            subtitle: const Text('关键交互点触发触觉反馈（仅移动端）'),
            value: hapticEnabled,
            onChanged: (v) => _saveLocalPreference(
              context: context,
              action: ref
                  .read(hapticFeedbackEnabledProvider.notifier)
                  .setEnabled,
              value: v,
              successText: '已更新触觉反馈设置',
            ),
          ),
          const Divider(height: 1),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.layers_outlined),
            title: const Text('骨架屏策略'),
            subtitle: Text(switch (skeletonStrategy) {
              'on' => '总是显示',
              'off' => '从不显示',
              _ => '自动（加载 ≥200ms 显示）',
            }),
            trailing: DropdownButton<String>(
              value: skeletonStrategy,
              items: const [
                DropdownMenuItem(value: 'auto', child: Text('自动')),
                DropdownMenuItem(value: 'on', child: Text('总是')),
                DropdownMenuItem(value: 'off', child: Text('关闭')),
              ],
              onChanged: (v) {
                if (v == null) return;
                _saveLocalPreference(
                  context: context,
                  action: ref
                      .read(skeletonStrategyProvider.notifier)
                      .setStrategy,
                  value: v,
                  successText: '已更新骨架屏策略',
                );
              },
            ),
          ),
        ],
      ),
    );

    Widget notificationSection = SectionCard(
      title: '通知与提醒',
      subtitle: '管理提醒时间、免打扰和权限。',
      child: Column(
        children: [
          SwitchListTile(
            title: const Text('开启通知提醒'),
            subtitle: const Text('关闭后将不再发送复习提醒'),
            value: settings.notificationsEnabled,
            onChanged: settingsState.isLoading
                ? null
                : (v) => _save(
                    context,
                    settings.copyWith(notificationsEnabled: v),
                  ),
          ),
          const Divider(height: 1),
          ListTile(
            title: const Text('每日提醒时间'),
            subtitle: Text(settings.reminderTime),
            trailing: const Icon(Icons.chevron_right),
            onTap: settingsState.isLoading
                ? null
                : () => _pickTime(
                    context: context,
                    title: '选择每日提醒时间',
                    current: settings.reminderTime,
                    onPicked: (v) =>
                        _save(context, settings.copyWith(reminderTime: v)),
                  ),
          ),
          const Divider(height: 1),
          ListTile(
            title: const Text('免打扰开始时间'),
            subtitle: Text(settings.doNotDisturbStart),
            trailing: const Icon(Icons.chevron_right),
            onTap: settingsState.isLoading
                ? null
                : () => _pickTime(
                    context: context,
                    title: '选择免打扰开始时间',
                    current: settings.doNotDisturbStart,
                    onPicked: (v) =>
                        _save(context, settings.copyWith(doNotDisturbStart: v)),
                  ),
          ),
          const Divider(height: 1),
          ListTile(
            title: const Text('免打扰结束时间'),
            subtitle: Text(settings.doNotDisturbEnd),
            trailing: const Icon(Icons.chevron_right),
            onTap: settingsState.isLoading
                ? null
                : () => _pickTime(
                    context: context,
                    title: '选择免打扰结束时间',
                    current: settings.doNotDisturbEnd,
                    onPicked: (v) =>
                        _save(context, settings.copyWith(doNotDisturbEnd: v)),
                  ),
          ),
          const Divider(height: 1),
          SwitchListTile(
            title: const Text('任务逾期通知'),
            subtitle: const Text('启动时检测逾期超过 3 天的任务并提醒'),
            value: settings.overdueNotificationEnabled,
            onChanged: settingsState.isLoading
                ? null
                : (v) => _save(
                    context,
                    settings.copyWith(overdueNotificationEnabled: v),
                  ),
          ),
          const Divider(height: 1),
          SwitchListTile(
            title: const Text('目标达成通知'),
            subtitle: const Text('达成学习目标时提醒，并支持跳转到统计页查看详情'),
            value: settings.goalNotificationEnabled,
            onChanged: settingsState.isLoading
                ? null
                : (v) => _save(
                    context,
                    settings.copyWith(goalNotificationEnabled: v),
                  ),
          ),
          const Divider(height: 1),
          SwitchListTile(
            title: const Text('打卡里程碑通知'),
            subtitle: const Text('连续打卡 7/30/100 天时提醒'),
            value: settings.streakNotificationEnabled,
            onChanged: settingsState.isLoading
                ? null
                : (v) => _save(
                    context,
                    settings.copyWith(streakNotificationEnabled: v),
                  ),
          ),
        ],
      ),
    );

    Widget permissionSection = SectionCard(
      title: '通知权限',
      subtitle: '可重新请求权限或打开系统设置。',
      child: permissionAsync.when(
        data: (permissionState) {
          final showOpenSettings =
              permissionState != NotificationPermissionState.enabled;
          return Column(
            children: [
              SummaryStrip(
                child: Row(
                  children: [
                    const Icon(Icons.notifications_outlined, size: 18),
                    const SizedBox(width: AppSpacing.sm),
                    Expanded(
                      child: Text(
                        '当前状态：${_permissionText(permissionState)}',
                        style: AppTypography.bodySecondary(context),
                      ),
                    ),
                    OutlinedButton(
                      onPressed: () async {
                        final ok = await NotificationService.instance
                            .requestPermission();
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                ok == true ? '已请求通知权限' : '未获取到通知权限',
                              ),
                            ),
                          );
                        }
                        ref.invalidate(notificationPermissionProvider);
                      },
                      child: const Text('请求权限'),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: AppSpacing.md),
              ActionRow(
                icon: Icons.open_in_new,
                title: '打开系统通知设置',
                subtitle: '若已拒绝权限，请在系统设置中手动开启',
                onTap: showOpenSettings ? _openNotificationSettings : null,
              ),
              const SizedBox(height: AppSpacing.md),
              GlassCard(
                style: GlassCardStyle.plain,
                child: SwitchListTile(
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.lg,
                  ),
                  title: const Text('首页通知引导弹窗'),
                  subtitle: const Text('关闭后首页不再弹出“去开启通知权限”的提示'),
                  value: !settings.notificationPermissionGuideDismissed,
                  onChanged: settingsState.isLoading
                      ? null
                      : (v) => _save(
                          context,
                          settings.copyWith(
                            notificationPermissionGuideDismissed: !v,
                          ),
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
        error: (e, _) =>
            Text('权限状态读取失败：$e', style: AppTypography.bodySecondary(context)),
      ),
    );

    Widget dataSection = SectionCard(
      title: '数据与同步',
      subtitle: '统一管理统计、导出、备份与局域网同步。',
      child: Column(
        children: [
          ActionRow(
            icon: Icons.insights_outlined,
            title: '查看学习统计',
            subtitle: '在统计页查看趋势、热力图和标签分布',
            onTap: () => context.push('/statistics'),
          ),
          const SizedBox(height: AppSpacing.lg),
          DataManagementSection(syncState: syncUi.state),
        ],
      ),
    );

    Widget learningSection = SectionCard(
      title: '学习配置',
      subtitle: '管理目标、主题结构和专注参数。',
      child: Column(
        children: [
          ActionRow(
            icon: Icons.flag_outlined,
            title: '学习目标',
            subtitle: '设置每日完成、连续打卡和本周完成率目标',
            onTap: () => context.push('/settings/goals'),
          ),
          const SizedBox(height: AppSpacing.md),
          ActionRow(
            icon: Icons.topic_outlined,
            title: '主题管理',
            subtitle: '管理主题与内容关联（v2.1）',
            onTap: () => context.push('/topics'),
          ),
          const SizedBox(height: AppSpacing.md),
          ActionRow(
            icon: Icons.timer_outlined,
            title: '番茄钟设置',
            subtitle: '配置工作时长、休息时长和长休息间隔',
            onTap: () => context.push('/settings/pomodoro'),
          ),
        ],
      ),
    );

    Widget helpSection = SectionCard(
      title: '帮助与工具',
      subtitle: '查看帮助文档与调试工具。',
      child: Column(
        children: [
          ActionRow(
            icon: Icons.help_outline,
            title: AppStrings.help,
            subtitle: '查看忆刻学习指南与使用说明',
            onTap: () => context.push('/help'),
          ),
          if (kDebugMode) ...[
            const SizedBox(height: AppSpacing.md),
            ActionRow(
              icon: Icons.developer_mode_outlined,
              title: '模拟数据生成器',
              subtitle: '一键生成 / 清理 Mock 学习内容与复习任务',
              onTap: () => context.push('/settings/debug/mock-data'),
            ),
          ],
        ],
      ),
    );

    final mobileSections = <Widget>[
      overviewSection,
      const SizedBox(height: AppSpacing.lg),
      appearanceSection,
      const SizedBox(height: AppSpacing.lg),
      notificationSection,
      const SizedBox(height: AppSpacing.lg),
      permissionSection,
      const SizedBox(height: AppSpacing.lg),
      dataSection,
      const SizedBox(height: AppSpacing.lg),
      learningSection,
      const SizedBox(height: AppSpacing.lg),
      helpSection,
    ];

    final desktopContent = switch (_selectedPane) {
      _SettingsPane.overview => [overviewSection],
      _SettingsPane.appearance => [appearanceSection],
      _SettingsPane.notifications => [
        notificationSection,
        const SizedBox(height: AppSpacing.lg),
        permissionSection,
      ],
      _SettingsPane.data => [dataSection],
      _SettingsPane.learning => [learningSection],
      _SettingsPane.help => [helpSection],
    };

    final summaryHero = HeroCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(AppStrings.mine, style: AppTypography.h2(context)),
          const SizedBox(height: AppSpacing.sm),
          Text('管理提醒、外观和学习偏好', style: AppTypography.display(context)),
          const SizedBox(height: AppSpacing.sm),
          Text(
            '在这里统一查看主题模式、通知权限、同步状态以及常用学习配置入口。',
            style: AppTypography.bodySecondary(context),
          ),
          const SizedBox(height: AppSpacing.lg),
          SummaryStrip(
            child: Row(
              children: [
                Expanded(
                  child: _SummaryMetric(
                    label: '主题模式',
                    value: currentThemeMode.label,
                  ),
                ),
                Expanded(
                  child: _SummaryMetric(
                    label: '通知权限',
                    value: _permissionText(permission),
                  ),
                ),
                Expanded(
                  child: _SummaryMetric(
                    label: '同步状态',
                    value: _syncText(syncUi.state),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: AppSpacing.lg),
          Wrap(
            spacing: AppSpacing.md,
            runSpacing: AppSpacing.md,
            children: [
              FilledButton(
                onPressed: () => context.push('/statistics'),
                child: const Text('查看统计'),
              ),
              FilledButton.tonal(
                onPressed: () => context.push('/settings/sync'),
                child: const Text('同步设置'),
              ),
              OutlinedButton(
                onPressed: () => context.push('/settings/goals'),
                child: const Text('学习目标'),
              ),
            ],
          ),
        ],
      ),
    );

    return Scaffold(
      appBar: AppBar(
        title: const Text(AppStrings.mine),
        actions: [
          IconButton(
            tooltip: '刷新',
            onPressed: () => ref.read(settingsProvider.notifier).load(),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: GradientBackground(
        child: SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: isDesktop
                ? Column(
                    children: [
                      summaryHero,
                      const SizedBox(height: AppSpacing.lg),
                      Expanded(
                        child: Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            PlainPanel(
                              child: SizedBox(
                                width: 240,
                                child: NavigationRail(
                                  selectedIndex: _SettingsPane.values.indexOf(
                                    _selectedPane,
                                  ),
                                  scrollable: true,
                                  onDestinationSelected: (index) {
                                    setState(() {
                                      _selectedPane =
                                          _SettingsPane.values[index];
                                    });
                                  },
                                  labelType: NavigationRailLabelType.all,
                                  destinations: const [
                                    NavigationRailDestination(
                                      icon: Icon(Icons.dashboard_outlined),
                                      selectedIcon: Icon(Icons.dashboard),
                                      label: Text('概览'),
                                    ),
                                    NavigationRailDestination(
                                      icon: Icon(Icons.palette_outlined),
                                      selectedIcon: Icon(Icons.palette),
                                      label: Text('外观'),
                                    ),
                                    NavigationRailDestination(
                                      icon: Icon(Icons.notifications_outlined),
                                      selectedIcon: Icon(Icons.notifications),
                                      label: Text('通知'),
                                    ),
                                    NavigationRailDestination(
                                      icon: Icon(Icons.storage_outlined),
                                      selectedIcon: Icon(Icons.storage),
                                      label: Text('数据'),
                                    ),
                                    NavigationRailDestination(
                                      icon: Icon(Icons.school_outlined),
                                      selectedIcon: Icon(Icons.school),
                                      label: Text('学习'),
                                    ),
                                    NavigationRailDestination(
                                      icon: Icon(Icons.help_outline),
                                      selectedIcon: Icon(Icons.help),
                                      label: Text('帮助'),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            const SizedBox(width: AppSpacing.lg),
                            Expanded(
                              child: ListView(
                                key: const PageStorageKey(
                                  'settings_desktop_scroll',
                                ),
                                children: [
                                  ...desktopContent,
                                  if (settingsState.errorMessage != null) ...[
                                    const SizedBox(height: AppSpacing.lg),
                                    ErrorCard(
                                      message: settingsState.errorMessage!,
                                    ),
                                  ],
                                  const SizedBox(height: 24),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  )
                : ListView(
                    key: const PageStorageKey('settings_scroll'),
                    children: [
                      summaryHero,
                      const SizedBox(height: AppSpacing.lg),
                      ...mobileSections,
                      if (settingsState.errorMessage != null) ...[
                        const SizedBox(height: AppSpacing.lg),
                        ErrorCard(message: settingsState.errorMessage!),
                      ],
                      const SizedBox(height: 96),
                    ],
                  ),
          ),
        ),
      ),
    );
  }
}

class _SummaryMetric extends StatelessWidget {
  const _SummaryMetric({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(value, style: AppTypography.title(context)),
        const SizedBox(height: 2),
        Text(label, style: AppTypography.meta(context)),
      ],
    );
  }
}
