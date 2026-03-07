/// 文件用途：领域实体 - 番茄钟配置（PomodoroSettingsEntity）。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

/// 番茄钟配置实体。
class PomodoroSettingsEntity {
  /// 构造函数。
  const PomodoroSettingsEntity({
    required this.workMinutes,
    required this.shortBreakMinutes,
    required this.longBreakMinutes,
    required this.longBreakInterval,
    required this.autoStartBreak,
    required this.autoStartWork,
  });

  /// 工作阶段时长（分钟）。
  final int workMinutes;

  /// 短休息时长（分钟）。
  final int shortBreakMinutes;

  /// 长休息时长（分钟）。
  final int longBreakMinutes;

  /// 长休息触发间隔轮数。
  final int longBreakInterval;

  /// 专注结束后是否自动开始休息阶段。
  final bool autoStartBreak;

  /// 休息结束后是否自动开始下一轮专注。
  final bool autoStartWork;

  /// 默认配置。
  static const PomodoroSettingsEntity defaults = PomodoroSettingsEntity(
    workMinutes: 25,
    shortBreakMinutes: 5,
    longBreakMinutes: 15,
    longBreakInterval: 4,
    autoStartBreak: true,
    autoStartWork: true,
  );

  /// 复制并生成新配置。
  PomodoroSettingsEntity copyWith({
    int? workMinutes,
    int? shortBreakMinutes,
    int? longBreakMinutes,
    int? longBreakInterval,
    bool? autoStartBreak,
    bool? autoStartWork,
  }) {
    return PomodoroSettingsEntity(
      workMinutes: workMinutes ?? this.workMinutes,
      shortBreakMinutes: shortBreakMinutes ?? this.shortBreakMinutes,
      longBreakMinutes: longBreakMinutes ?? this.longBreakMinutes,
      longBreakInterval: longBreakInterval ?? this.longBreakInterval,
      autoStartBreak: autoStartBreak ?? this.autoStartBreak,
      autoStartWork: autoStartWork ?? this.autoStartWork,
    );
  }
}
