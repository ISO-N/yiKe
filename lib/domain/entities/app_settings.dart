/// 文件用途：领域实体 - 应用设置（通知开关、提醒时间、免打扰时段等）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

/// 应用设置实体。
///
/// 说明：
/// - v1.0 MVP：复习间隔固定，不开放自定义；但保留字段以便后续扩展。
/// - 时间字段使用 "HH:mm" 字符串，避免领域层依赖 Flutter 的 TimeOfDay。
class AppSettingsEntity {
  /// 构造函数。
  const AppSettingsEntity({
    required this.reminderTime,
    required this.doNotDisturbStart,
    required this.doNotDisturbEnd,
    required this.notificationsEnabled,
    required this.overdueNotificationEnabled,
    required this.goalNotificationEnabled,
    required this.streakNotificationEnabled,
    required this.notificationPermissionGuideDismissed,
    required this.topicGuideDismissed,
    this.lastNotifiedDate,
  });

  /// 每日提醒时间（HH:mm），默认 09:00。
  final String reminderTime;

  /// 免打扰开始时间（HH:mm），默认 22:00。
  final String doNotDisturbStart;

  /// 免打扰结束时间（HH:mm），默认 08:00。
  final String doNotDisturbEnd;

  /// 通知开关。
  final bool notificationsEnabled;

  /// 任务逾期通知开关（pending 超过阈值天数）。
  final bool overdueNotificationEnabled;

  /// 目标达成通知开关。
  final bool goalNotificationEnabled;

  /// 连续打卡里程碑通知开关。
  final bool streakNotificationEnabled;

  /// 通知权限引导是否已被用户关闭（避免频繁弹窗）。
  final bool notificationPermissionGuideDismissed;

  /// 主题功能说明是否已被用户关闭（避免频繁弹窗）。
  final bool topicGuideDismissed;

  /// 今日是否已发送过提醒（YYYY-MM-DD）。
  ///
  /// 说明：用于后台任务防重复发送。
  final String? lastNotifiedDate;

  AppSettingsEntity copyWith({
    String? reminderTime,
    String? doNotDisturbStart,
    String? doNotDisturbEnd,
    bool? notificationsEnabled,
    bool? overdueNotificationEnabled,
    bool? goalNotificationEnabled,
    bool? streakNotificationEnabled,
    bool? notificationPermissionGuideDismissed,
    bool? topicGuideDismissed,
    String? lastNotifiedDate,
  }) {
    return AppSettingsEntity(
      reminderTime: reminderTime ?? this.reminderTime,
      doNotDisturbStart: doNotDisturbStart ?? this.doNotDisturbStart,
      doNotDisturbEnd: doNotDisturbEnd ?? this.doNotDisturbEnd,
      notificationsEnabled: notificationsEnabled ?? this.notificationsEnabled,
      overdueNotificationEnabled:
          overdueNotificationEnabled ?? this.overdueNotificationEnabled,
      goalNotificationEnabled:
          goalNotificationEnabled ?? this.goalNotificationEnabled,
      streakNotificationEnabled:
          streakNotificationEnabled ?? this.streakNotificationEnabled,
      notificationPermissionGuideDismissed:
          notificationPermissionGuideDismissed ??
          this.notificationPermissionGuideDismissed,
      topicGuideDismissed: topicGuideDismissed ?? this.topicGuideDismissed,
      lastNotifiedDate: lastNotifiedDate ?? this.lastNotifiedDate,
    );
  }

  /// v1.0 MVP 默认设置。
  static const AppSettingsEntity defaults = AppSettingsEntity(
    reminderTime: '09:00',
    doNotDisturbStart: '22:00',
    doNotDisturbEnd: '08:00',
    notificationsEnabled: true,
    overdueNotificationEnabled: true,
    goalNotificationEnabled: true,
    streakNotificationEnabled: true,
    notificationPermissionGuideDismissed: false,
    topicGuideDismissed: false,
  );
}
