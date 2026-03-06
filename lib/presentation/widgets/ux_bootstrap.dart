/// 文件用途：用户体验启动器（首帧后执行预加载、通知点击导航绑定、启动时 UX 通知检查等）。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/utils/date_utils.dart';
import '../../core/utils/time_utils.dart';
import '../../di/providers.dart';
import '../../domain/entities/task_day_stats.dart';
import '../../infrastructure/notification/notification_service.dart';
import '../../infrastructure/preload/app_preload_service.dart';
import '../providers/pomodoro_provider.dart';

/// UX 启动器：包裹在应用根部即可。
///
/// 说明：
/// - 避免在 build 中做副作用，所有动作都在首帧后触发
/// - 以“尽量不阻塞首屏”为优先级
class UxBootstrap extends ConsumerStatefulWidget {
  const UxBootstrap({super.key, required this.child});

  final Widget child;

  @override
  ConsumerState<UxBootstrap> createState() => _UxBootstrapState();
}

class _UxBootstrapState extends ConsumerState<UxBootstrap>
    with WidgetsBindingObserver {
  static StreamSubscription<String>? _tapSub;
  bool _checkedStartupNotifications = false;

  @override
  void initState() {
    super.initState();

    const isFlutterTest = bool.fromEnvironment('FLUTTER_TEST');
    if (isFlutterTest) return;

    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _ensureNotificationTapBound();
      // 关键逻辑：应用启动时主动创建番茄钟 Provider，确保即使用户尚未进入页面也能恢复计时状态。
      ref.read(pomodoroProvider.notifier);
      AppPreloadService.ensureStarted(context);
      _checkAndSendStartupNotifications();
    });
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (!mounted) return;

    switch (state) {
      case AppLifecycleState.resumed:
        unawaited(
          ref.read(pomodoroProvider.notifier).handleAppVisibilityChanged(
                isForeground: true,
              ),
        );
        break;
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
      case AppLifecycleState.hidden:
        unawaited(
          ref.read(pomodoroProvider.notifier).handleAppVisibilityChanged(
                isForeground: false,
              ),
        );
        break;
      case AppLifecycleState.inactive:
        break;
    }
  }

  void _ensureNotificationTapBound() {
    // 关键逻辑：通知点击监听只需要绑定一次，避免多次订阅导致重复导航。
    if (_tapSub != null) return;

    _tapSub = NotificationService.instance.onTapRouteStream.listen((route) {
      final r = route.trim();
      if (r.isEmpty) return;
      if (!mounted) return;

      // 约定：payload 为应用内路由（以 / 开头）。
      if (!r.startsWith('/')) return;

      try {
        GoRouter.of(context).go(r);
      } catch (_) {
        // 导航失败不影响后续通知处理。
      }
    });
  }

  Future<void> _checkAndSendStartupNotifications() async {
    if (_checkedStartupNotifications) return;
    _checkedStartupNotifications = true;

    final settingsRepo = ref.read(settingsRepositoryProvider);
    final goalRepo = ref.read(goalSettingsRepositoryProvider);
    final reviewRepo = ref.read(reviewTaskRepositoryProvider);
    final dedup = ref.read(notificationDedupStoreProvider);

    final settings = await settingsRepo.getSettings();
    if (!settings.notificationsEnabled) return;

    final now = DateTime.now();
    final nowTime = TimeOfDay.fromDateTime(now);
    final dndStart = TimeUtils.parseHHmm(settings.doNotDisturbStart);
    final dndEnd = TimeUtils.parseHHmm(settings.doNotDisturbEnd);
    if (TimeUtils.isInDoNotDisturb(nowTime, dndStart, dndEnd)) {
      // 免打扰时段内跳过启动通知（避免打扰）。
      return;
    }

    // 1) 任务逾期通知：启动时检测 pending 超过 3 天。
    if (settings.overdueNotificationEnabled &&
        await dedup.shouldSend('overdue', now: now)) {
      final overdue = await reviewRepo.getOverduePendingTasks();
      final todayStart = YikeDateUtils.atStartOfDay(now);
      final threshold = todayStart.subtract(const Duration(days: 3));
      final hardOverdue =
          overdue.where((t) => t.scheduledDate.isBefore(threshold)).toList();

      if (hardOverdue.isNotEmpty) {
        await NotificationService.instance.showReviewNotification(
          id: 21,
          title: '任务逾期提醒',
          body: '你有 ${hardOverdue.length} 条任务已逾期超过 3 天，建议优先处理。',
          payloadRoute: '/home?tab=today&focus=overdue',
        );
        await dedup.setLastSent('overdue', now);
      }
    }

    // 2) 目标达成通知：达成任一学习目标即触发（启动时兜底检查）。
    if (settings.goalNotificationEnabled &&
        await dedup.shouldSend('goal', now: now)) {
      final goalSettings = await goalRepo.getGoalSettings();
      final achieved = <String>[];

      // 今日完成数目标（done 数量）。
      final todayStart = YikeDateUtils.atStartOfDay(now);
      final tomorrowStart = todayStart.add(const Duration(days: 1));
      final todayStatsMap = await reviewRepo.getTaskDayStatsInRange(
        todayStart,
        tomorrowStart,
      );
      final todayStats = todayStatsMap[todayStart] ?? _emptyDayStats();
      final dailyTarget = goalSettings.dailyTarget;
      if (dailyTarget != null && dailyTarget > 0 && todayStats.doneCount >= dailyTarget) {
        achieved.add('每日完成');
      }

      // 连续打卡目标。
      final streakTarget = goalSettings.streakTarget;
      final streak = await reviewRepo.getConsecutiveCompletedDays(today: now);
      if (streakTarget != null && streakTarget > 0 && streak >= streakTarget) {
        achieved.add('连续打卡');
      }

      // 本周完成率目标。
      final weeklyRateTarget = goalSettings.weeklyRateTarget;
      if (weeklyRateTarget != null && weeklyRateTarget > 0) {
        final weekStart = todayStart.subtract(
          Duration(days: todayStart.weekday - DateTime.monday),
        );
        final weekEnd = weekStart.add(const Duration(days: 7));
        final (completed, total) = await reviewRepo.getTaskStatsInRange(
          weekStart,
          weekEnd,
        );
        final ratePercent = total <= 0 ? 0.0 : (completed / total) * 100;
        if (ratePercent >= weeklyRateTarget) {
          achieved.add('本周完成率');
        }
      }

      if (achieved.isNotEmpty) {
        final summary = achieved.join('、');
        await NotificationService.instance.showReviewNotification(
          id: 22,
          title: '目标达成',
          body: '已达成：$summary。打开统计页查看进度详情。',
          payloadRoute: '/statistics',
        );
        await dedup.setLastSent('goal', now);
      }
    }

    // 3) 连续打卡里程碑通知：7/30/100 天，按里程碑只提醒一次。
    if (settings.streakNotificationEnabled) {
      final streak = await reviewRepo.getConsecutiveCompletedDays(today: now);
      final reached = _maxReachedMilestone(streak);
      if (reached > 0) {
        final state = await dedup.getStreakMilestoneState();
        final lastMilestone = state.milestone;
        final lastSentAt = state.sentAt;
        final canSendByTtl =
            lastSentAt == null || now.difference(lastSentAt) >= const Duration(hours: 24);
        if (reached > lastMilestone && canSendByTtl) {
          await NotificationService.instance.showReviewNotification(
            id: 23,
            title: '打卡里程碑',
            body: '连续打卡 $reached 天，继续保持。',
            payloadRoute: '/home',
          );
          await dedup.setStreakMilestoneState(milestone: reached, sentAt: now);
          await dedup.setLastSent('streak', now);
        }
      }
    }
  }

  int _maxReachedMilestone(int streakDays) {
    // 规格：7/30/100 天里程碑。
    const milestones = [7, 30, 100];
    var reached = 0;
    for (final m in milestones) {
      if (streakDays >= m) reached = m;
    }
    return reached;
  }

  TaskDayStats _emptyDayStats() {
    return const TaskDayStats(pendingCount: 0, doneCount: 0, skippedCount: 0);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
