/// 文件用途：后台任务调度（Workmanager），用于定期检查并发送通知（允许 ±30 分钟误差）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:workmanager/workmanager.dart';

import '../../core/utils/date_utils.dart';
import '../../core/utils/time_utils.dart';
import '../../data/database/daos/review_task_dao.dart';
import '../../data/database/daos/settings_dao.dart';
import '../../data/database/database.dart';
import '../../data/repositories/settings_repository_impl.dart';
import '../widget/widget_service.dart';
import 'notification_service.dart';
import '../storage/secure_storage_service.dart';

/// 后台任务服务。
///
/// 说明：v1.0 MVP 采用“每小时检查一次”的方式，在接近提醒时间时发送通知。
class BackgroundTaskService {
  static const String _dailyReviewCheckTaskName = 'dailyReviewCheck';
  static const String _dailyReviewCheckUniqueName = 'daily_review_check';

  /// Workmanager 当前是否可用。
  ///
  /// 说明：workmanager 仅在 Android / iOS 有平台实现；桌面端（Windows/macOS/Linux）
  /// 会走到占位实现并抛出 UnimplementedError，因此必须在调用前做平台判断。
  static bool get _isWorkmanagerSupported {
    if (kIsWeb) return false;
    return defaultTargetPlatform == TargetPlatform.android ||
        defaultTargetPlatform == TargetPlatform.iOS;
  }

  /// 初始化后台任务系统。
  ///
  /// 返回值：Future（无返回值）。
  /// 异常：注册失败时可能抛出异常。
  static Future<void> initialize() async {
    // Windows 白屏修复：桌面端跳过 workmanager 初始化，避免启动阶段直接崩溃。
    if (!_isWorkmanagerSupported) {
      if (kDebugMode) {
        debugPrint('后台任务：当前平台不支持 Workmanager，已跳过初始化。');
      }
      return;
    }

    await Workmanager().initialize(callbackDispatcher);

    // 每小时检查一次（在提醒时间附近触发通知）。
    await Workmanager().registerPeriodicTask(
      _dailyReviewCheckUniqueName,
      _dailyReviewCheckTaskName,
      frequency: const Duration(hours: 1),
      constraints: Constraints(networkType: NetworkType.notRequired),
    );
  }
}

/// WorkManager 回调入口。
///
/// 说明：必须为顶层函数，且标记 entry-point，避免 AOT 裁剪。
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    WidgetsFlutterBinding.ensureInitialized();

    switch (task) {
      case BackgroundTaskService._dailyReviewCheckTaskName:
        await _checkAndSendDailyReviewNotification();
        break;
    }

    return Future.value(true);
  });
}

/// 检查并发送“今日复习”提醒（v1.0 MVP：允许 ±30 分钟误差）。
///
/// 逻辑：
/// 1) 读取设置（通知开关、提醒时间、免打扰时段、今日是否已通知）
/// 2) 更新桌面小组件数据（与通知共用后台检查频率）
/// 3) 若在提醒时间窗口内且今日未通知，则查询今日待复习任务并发送通知
Future<void> _checkAndSendDailyReviewNotification() async {
  final db = await AppDatabase.open();
  try {
    final settingsRepo = SettingsRepositoryImpl(
      dao: SettingsDao(db),
      secureStorageService: SecureStorageService(),
    );
    final settings = await settingsRepo.getSettings();

    // 1) 同步小组件（每小时一次）。
    await _syncWidget(db);

    // 2) 通知开关关闭则不发送。
    if (!settings.notificationsEnabled) return;

    final now = TimeOfDay.now();
    final reminder = TimeUtils.parseHHmm(settings.reminderTime);
    final dndStart = TimeUtils.parseHHmm(settings.doNotDisturbStart);
    final dndEnd = TimeUtils.parseHHmm(settings.doNotDisturbEnd);

    // 3) 免打扰时段内跳过。
    if (TimeUtils.isInDoNotDisturb(now, dndStart, dndEnd)) return;

    // 4) 仅在提醒时间的 ±30 分钟窗口内尝试发送，避免每小时都打扰。
    if (!_isWithinWindow(now, reminder, windowMinutes: 30)) return;

    final todayKey = YikeDateUtils.formatYmd(DateTime.now());
    if (settings.lastNotifiedDate == todayKey) return;

    // 5) 查询今日待复习任务。
    final reviewTaskDao = ReviewTaskDao(db);
    final pending = await reviewTaskDao.getTodayPendingTasksWithItem();
    if (pending.isEmpty) {
      // 没有任务也记录今日已检查，避免窗口内重复检查。
      await settingsRepo.saveSettings(
        settings.copyWith(lastNotifiedDate: todayKey),
      );
      return;
    }

    await NotificationService.instance.initialize();

    // 若已存在精准定时的计划通知，则后台检查不再重复发送（避免双重提醒）。
    final hasScheduled = await NotificationService.instance.hasPendingNotification(1);
    if (hasScheduled) return;

    final topTitles = pending.take(3).map((e) => e.item.title).toList();
    final body = topTitles.isEmpty
        ? '你有 ${pending.length} 条复习任务待完成'
        : '你有 ${pending.length} 条复习任务待完成：${topTitles.join('、')}';

    await NotificationService.instance.showReviewNotification(
      id: 1,
      title: '今日复习提醒',
      body: body,
      payloadRoute: '/home',
    );

    // 6) 记录今日已通知，防止重复发送。
    await settingsRepo.saveSettings(
      settings.copyWith(lastNotifiedDate: todayKey),
    );
  } finally {
    await db.close();
  }
}

Future<void> _syncWidget(AppDatabase db) async {
  try {
    final dao = ReviewTaskDao(db);
    final tasks = await dao.getTasksByDateWithItem(DateTime.now());
    final (completed, total) = await dao.getTaskStats(DateTime.now());
    await WidgetService.updateWidgetData(
      totalCount: total,
      completedCount: completed,
      pendingCount: tasks.where((t) => t.task.status == 'pending').length,
      tasks: tasks
          .take(3)
          .map(
            (t) => WidgetTaskItem(title: t.item.title, status: t.task.status),
          )
          .toList(),
    );
  } catch (_) {
    // 小组件更新失败不影响后台通知任务。
  }
}

bool _isWithinWindow(
  TimeOfDay now,
  TimeOfDay target, {
  required int windowMinutes,
}) {
  final n = now.hour * 60 + now.minute;
  final t = target.hour * 60 + target.minute;
  final diff = (n - t).abs();
  // 处理跨午夜的最短距离（例如 23:50 与 00:10 的差值应为 20 分钟）。
  final wrappedDiff = diff > 720 ? 1440 - diff : diff;
  return wrappedDiff <= windowMinutes;
}
