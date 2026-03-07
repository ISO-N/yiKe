/// 文件用途：番茄钟计时状态管理（Riverpod StateNotifier），负责倒计时、阶段切换与恢复。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../di/providers.dart';
import '../../domain/entities/pomodoro_record.dart';
import '../../domain/entities/pomodoro_settings.dart';
import '../../infrastructure/notification/notification_service.dart';
import 'pomodoro_stats_provider.dart';

/// 当前时间提供器。
typedef PomodoroNow = DateTime Function();

/// 阶段结束通知发送器。
typedef PomodoroPhaseNotificationSender =
    Future<void> Function(PomodoroPhase phase);

/// 可覆盖的时钟 Provider，用于测试倒计时与恢复逻辑。
final pomodoroClockProvider = Provider<PomodoroNow>((ref) => DateTime.now);

/// 可覆盖的通知发送 Provider，用于测试通知触发时机。
final pomodoroPhaseNotificationSenderProvider =
    Provider<PomodoroPhaseNotificationSender>((ref) {
      return (phase) async {
        if (phase == PomodoroPhase.work) {
          await NotificationService.instance.showPomodoroNotification(
            id: 301,
            title: '专注时间到！',
            body: '休息一下吧~',
            payloadRoute: '/pomodoro',
          );
          return;
        }
        await NotificationService.instance.showPomodoroNotification(
          id: 302,
          title: '休息结束！',
          body: '准备好继续了吗？',
          payloadRoute: '/pomodoro',
        );
      };
    });

/// 番茄钟阶段。
enum PomodoroPhase {
  /// 工作阶段。
  work,

  /// 短休息阶段。
  shortBreak,

  /// 长休息阶段。
  longBreak,
}

/// 番茄钟运行状态。
enum PomodoroRunStatus {
  /// 未运行。
  idle,

  /// 运行中。
  running,

  /// 已暂停。
  paused,
}

/// 番茄钟状态。
class PomodoroState {
  /// 构造函数。
  const PomodoroState({
    required this.isReady,
    required this.settings,
    required this.phase,
    required this.status,
    required this.currentPhaseTotalSeconds,
    required this.remainingSeconds,
    required this.completedRounds,
    required this.elapsedSecondsBeforeRun,
    this.phaseStartedAt,
    this.lastResumedAt,
    this.errorMessage,
  });

  /// 是否已完成初始化。
  final bool isReady;

  /// 当前生效的番茄钟配置。
  final PomodoroSettingsEntity settings;

  /// 当前阶段。
  final PomodoroPhase phase;

  /// 当前运行状态。
  final PomodoroRunStatus status;

  /// 当前阶段总秒数。
  ///
  /// 说明：
  /// - 当前阶段一旦开始，就固定使用该值，避免用户中途改配置导致进行中的倒计时跳变
  final int currentPhaseTotalSeconds;

  /// 当前剩余秒数。
  final int remainingSeconds;

  /// 已完成的工作轮数。
  final int completedRounds;

  /// 当前阶段在最近一次恢复前已累计的秒数。
  final int elapsedSecondsBeforeRun;

  /// 当前阶段的实际开始时间。
  final DateTime? phaseStartedAt;

  /// 最近一次开始/继续运行的时间。
  final DateTime? lastResumedAt;

  /// 错误信息。
  final String? errorMessage;

  /// 是否正在运行。
  bool get isRunning => status == PomodoroRunStatus.running;

  /// 是否处于暂停状态。
  bool get isPaused => status == PomodoroRunStatus.paused;

  /// 是否处于空闲状态。
  bool get isIdle => status == PomodoroRunStatus.idle;

  /// 初始状态。
  factory PomodoroState.initial() {
    final defaults = PomodoroSettingsEntity.defaults;
    return PomodoroState(
      isReady: false,
      settings: defaults,
      phase: PomodoroPhase.work,
      status: PomodoroRunStatus.idle,
      currentPhaseTotalSeconds: defaults.workMinutes * 60,
      remainingSeconds: defaults.workMinutes * 60,
      completedRounds: 0,
      elapsedSecondsBeforeRun: 0,
    );
  }

  static const Object _unset = Object();

  /// 复制状态。
  PomodoroState copyWith({
    bool? isReady,
    PomodoroSettingsEntity? settings,
    PomodoroPhase? phase,
    PomodoroRunStatus? status,
    int? currentPhaseTotalSeconds,
    int? remainingSeconds,
    int? completedRounds,
    int? elapsedSecondsBeforeRun,
    Object? phaseStartedAt = _unset,
    Object? lastResumedAt = _unset,
    Object? errorMessage = _unset,
  }) {
    return PomodoroState(
      isReady: isReady ?? this.isReady,
      settings: settings ?? this.settings,
      phase: phase ?? this.phase,
      status: status ?? this.status,
      currentPhaseTotalSeconds:
          currentPhaseTotalSeconds ?? this.currentPhaseTotalSeconds,
      remainingSeconds: remainingSeconds ?? this.remainingSeconds,
      completedRounds: completedRounds ?? this.completedRounds,
      elapsedSecondsBeforeRun:
          elapsedSecondsBeforeRun ?? this.elapsedSecondsBeforeRun,
      phaseStartedAt: identical(phaseStartedAt, _unset)
          ? this.phaseStartedAt
          : phaseStartedAt as DateTime?,
      lastResumedAt: identical(lastResumedAt, _unset)
          ? this.lastResumedAt
          : lastResumedAt as DateTime?,
      errorMessage: identical(errorMessage, _unset)
          ? this.errorMessage
          : errorMessage as String?,
    );
  }
}

/// 番茄钟计时 Notifier。
class PomodoroNotifier extends StateNotifier<PomodoroState> {
  /// 构造函数。
  PomodoroNotifier(this._ref)
    : _now = _ref.read(pomodoroClockProvider),
      _phaseNotificationSender = _ref.read(
        pomodoroPhaseNotificationSenderProvider,
      ),
      super(PomodoroState.initial()) {
    _initialize();
  }

  final Ref _ref;
  final PomodoroNow _now;
  final PomodoroPhaseNotificationSender _phaseNotificationSender;

  Timer? _ticker;
  bool _isSynchronizing = false;

  static const String _prefsPrefix = 'pomodoro_timer';
  static const String _keyPhase = '$_prefsPrefix.phase';
  static const String _keyStatus = '$_prefsPrefix.status';
  static const String _keyCurrentPhaseTotalSeconds =
      '$_prefsPrefix.current_phase_total_seconds';
  static const String _keyRemainingSeconds = '$_prefsPrefix.remaining_seconds';
  static const String _keyCompletedRounds = '$_prefsPrefix.completed_rounds';
  static const String _keyElapsedSecondsBeforeRun =
      '$_prefsPrefix.elapsed_seconds_before_run';
  static const String _keyPhaseStartedAt = '$_prefsPrefix.phase_started_at_ms';
  static const String _keyLastResumedAt = '$_prefsPrefix.last_resumed_at_ms';

  /// 初始化 Provider：加载设置并尝试恢复持久化状态。
  Future<void> _initialize() async {
    try {
      final settings = await _ref.read(
        pomodoroSettingsRepositoryProvider,
      ).getSettings();
      state = state.copyWith(
        isReady: true,
        settings: settings,
        currentPhaseTotalSeconds: settings.workMinutes * 60,
        remainingSeconds: settings.workMinutes * 60,
        errorMessage: null,
      );
      await restore();
    } catch (e) {
      state = state.copyWith(isReady: true, errorMessage: e.toString());
    }
  }

  /// 启动番茄钟。
  Future<void> start() async {
    if (!state.isReady) return;
    if (state.isRunning) return;
    if (state.isPaused) {
      await resume();
      return;
    }

    final now = _now();
    state = state.copyWith(
      status: PomodoroRunStatus.running,
      phaseStartedAt: now,
      lastResumedAt: now,
      elapsedSecondsBeforeRun: 0,
      remainingSeconds: state.currentPhaseTotalSeconds,
      errorMessage: null,
    );
    _startTicker();
    await _persistState();
  }

  /// 暂停番茄钟。
  Future<void> pause() async {
    if (!state.isRunning) return;
    await _synchronizeWithClock(notifyOnTransition: true);
    if (!state.isRunning) return;

    state = state.copyWith(
      status: PomodoroRunStatus.paused,
      lastResumedAt: null,
      errorMessage: null,
    );
    _stopTicker();
    await _persistState();
  }

  /// 恢复番茄钟。
  Future<void> resume() async {
    if (!state.isPaused) return;
    final now = _now();
    state = state.copyWith(
      status: PomodoroRunStatus.running,
      lastResumedAt: now,
      errorMessage: null,
    );
    _startTicker();
    await _persistState();
  }

  /// 重置当前阶段。
  Future<void> reset() async {
    if (!state.isReady) return;
    _stopTicker();
    state = state.copyWith(
      status: PomodoroRunStatus.idle,
      elapsedSecondsBeforeRun: 0,
      remainingSeconds: state.currentPhaseTotalSeconds,
      phaseStartedAt: null,
      lastResumedAt: null,
      errorMessage: null,
    );
    await _persistState();
  }

  /// 跳过当前阶段。
  Future<void> skip() async {
    if (!state.isReady) return;
    _stopTicker();
    final nextState = _buildNextPhaseState(
      current: state,
      nextPhase: _resolveNextPhaseAfterSkip(state),
      completedRounds: state.phase == PomodoroPhase.longBreak
          ? 0
          : state.completedRounds,
      phaseStart: null,
      status: PomodoroRunStatus.idle,
    );
    state = nextState;
    await _persistState();
  }

  /// 从持久化存储恢复状态。
  ///
  /// 说明：
  /// - 应用启动和回到前台时都可以调用
  /// - 若恢复后仍处于运行状态，会自动继续计时
  Future<void> restore() async {
    if (!state.isReady) return;

    final prefs = await SharedPreferences.getInstance();
    final rawPhase = prefs.getString(_keyPhase);
    if (rawPhase == null || rawPhase.isEmpty) {
      final initialSeconds = _phaseDurationSeconds(
        phase: PomodoroPhase.work,
        settings: state.settings,
      );
      state = state.copyWith(
        phase: PomodoroPhase.work,
        status: PomodoroRunStatus.idle,
        currentPhaseTotalSeconds: initialSeconds,
        remainingSeconds: initialSeconds,
        completedRounds: 0,
        elapsedSecondsBeforeRun: 0,
        phaseStartedAt: null,
        lastResumedAt: null,
        errorMessage: null,
      );
      return;
    }

    final restored = PomodoroState(
      isReady: true,
      settings: state.settings,
      phase: _phaseFromStorage(rawPhase),
      status: _statusFromStorage(prefs.getString(_keyStatus)),
      currentPhaseTotalSeconds:
          prefs.getInt(_keyCurrentPhaseTotalSeconds) ??
          _phaseDurationSeconds(
            phase: _phaseFromStorage(rawPhase),
            settings: state.settings,
          ),
      remainingSeconds:
          prefs.getInt(_keyRemainingSeconds) ??
          _phaseDurationSeconds(
            phase: _phaseFromStorage(rawPhase),
            settings: state.settings,
          ),
      completedRounds: prefs.getInt(_keyCompletedRounds) ?? 0,
      elapsedSecondsBeforeRun: prefs.getInt(_keyElapsedSecondsBeforeRun) ?? 0,
      phaseStartedAt: _readDateTime(prefs, _keyPhaseStartedAt),
      lastResumedAt: _readDateTime(prefs, _keyLastResumedAt),
    );

    if (restored.isRunning) {
      state = await _resolveElapsedState(
        restored,
        now: _now(),
        notifyOnTransition: true,
      );
      if (state.isRunning) {
        _startTicker();
      } else {
        _stopTicker();
      }
      await _persistState();
      return;
    }

    final boundedRemaining = (restored.currentPhaseTotalSeconds -
            restored.elapsedSecondsBeforeRun)
        .clamp(0, restored.currentPhaseTotalSeconds)
        .toInt();
    state = restored.copyWith(
      remainingSeconds: boundedRemaining,
      errorMessage: null,
    );
    _stopTicker();
  }

  /// 刷新配置。
  ///
  /// 说明：
  /// - 进行中的阶段不改时长，只影响后续阶段
  /// - 若当前阶段尚未开始，则同步刷新当前默认时长
  Future<void> refreshSettings() async {
    final settings = await _ref.read(
      pomodoroSettingsRepositoryProvider,
    ).getSettings();

    if (state.isIdle && state.phaseStartedAt == null && state.elapsedSecondsBeforeRun == 0) {
      final totalSeconds = _phaseDurationSeconds(
        phase: state.phase,
        settings: settings,
      );
      state = state.copyWith(
        settings: settings,
        currentPhaseTotalSeconds: totalSeconds,
        remainingSeconds: totalSeconds,
        errorMessage: null,
      );
    } else {
      state = state.copyWith(settings: settings, errorMessage: null);
    }
    await _persistState();
  }

  /// 处理应用生命周期变化。
  ///
  /// 参数：
  /// - [isForeground] 是否回到前台。
  Future<void> handleAppVisibilityChanged({required bool isForeground}) async {
    if (!state.isReady) return;
    if (isForeground) {
      await restore();
      return;
    }
    await _persistState();
  }

  /// 同步当前时钟状态，处理倒计时流逝和自动切换。
  Future<void> _synchronizeWithClock({required bool notifyOnTransition}) async {
    if (_isSynchronizing) return;
    if (!state.isRunning) return;
    _isSynchronizing = true;
    try {
      state = await _resolveElapsedState(
        state,
        now: _now(),
        notifyOnTransition: notifyOnTransition,
      );
      if (!state.isRunning) {
        _stopTicker();
      }
      await _persistState();
    } finally {
      _isSynchronizing = false;
    }
  }

  /// 基于当前时间解析运行中的状态。
  ///
  /// 说明：
  /// - 支持一次恢复跨越多个阶段
  /// - 只有工作阶段自然完成时才会写入统计记录
  Future<PomodoroState> _resolveElapsedState(
    PomodoroState source, {
    required DateTime now,
    required bool notifyOnTransition,
  }) async {
    if (!source.isRunning) return source;

    final resumedAt = source.lastResumedAt ?? now;
    var elapsed =
        source.elapsedSecondsBeforeRun +
        now.difference(resumedAt).inSeconds.clamp(0, 1 << 30).toInt();
    var current = source;
    PomodoroPhase? lastFinishedPhase;

    while (current.isRunning &&
        elapsed >= current.currentPhaseTotalSeconds &&
        current.currentPhaseTotalSeconds > 0) {
      elapsed -= current.currentPhaseTotalSeconds;
      lastFinishedPhase = current.phase;

      final finishedAt =
          (current.phaseStartedAt ?? now).add(
            Duration(seconds: current.currentPhaseTotalSeconds),
          );
      current = await _handleNaturalPhaseCompletion(
        current,
        finishedAt: finishedAt,
      );
      if (!current.isRunning) {
        elapsed = 0;
        break;
      }
    }

    if (!current.isRunning) {
      final idleState = current.copyWith(
        elapsedSecondsBeforeRun: 0,
        lastResumedAt: null,
        phaseStartedAt: null,
        remainingSeconds: current.currentPhaseTotalSeconds,
        errorMessage: null,
      );
      if (notifyOnTransition && lastFinishedPhase != null) {
        await _phaseNotificationSender(lastFinishedPhase);
      }
      return idleState;
    }

    final phaseStartedAt = current.phaseStartedAt == null
        ? null
        : now.subtract(Duration(seconds: elapsed));
    final nextState = current.copyWith(
      status: PomodoroRunStatus.running,
      elapsedSecondsBeforeRun: elapsed,
      lastResumedAt: now,
      phaseStartedAt: phaseStartedAt ?? current.phaseStartedAt,
      remainingSeconds: (current.currentPhaseTotalSeconds - elapsed)
          .clamp(0, current.currentPhaseTotalSeconds)
          .toInt(),
      errorMessage: null,
    );

    if (notifyOnTransition && lastFinishedPhase != null) {
      await _phaseNotificationSender(lastFinishedPhase);
    }
    return nextState;
  }

  /// 处理阶段自然完成后的状态转移。
  Future<PomodoroState> _handleNaturalPhaseCompletion(
    PomodoroState current, {
    required DateTime finishedAt,
  }) async {
    var completedRounds = current.completedRounds;

    if (current.phase == PomodoroPhase.work) {
      completedRounds += 1;
      // 关键逻辑：仅完整结束的工作阶段会记入统计，手动跳过不会写入记录。
      await _ref.read(pomodoroRepositoryProvider).createRecord(
        PomodoroRecordEntity(
          startTime: current.phaseStartedAt ?? finishedAt,
          durationMinutes: current.currentPhaseTotalSeconds ~/ 60,
          phaseType: 'work',
          completed: true,
        ),
      );
      await _ref.read(pomodoroStatsProvider.notifier).load();
    } else if (current.phase == PomodoroPhase.longBreak) {
      // 规格要求：长休息结束后轮次归零，进入下一轮工作周期。
      completedRounds = 0;
    }

    final nextPhase = _resolveNextPhaseAfterNaturalFinish(
      currentPhase: current.phase,
      completedRounds: completedRounds,
      settings: current.settings,
    );
    final shouldAutoStart = _shouldAutoStartNextPhase(
      nextPhase: nextPhase,
      settings: current.settings,
    );
    return _buildNextPhaseState(
      current: current,
      nextPhase: nextPhase,
      completedRounds: completedRounds,
      phaseStart: shouldAutoStart ? finishedAt : null,
      status: shouldAutoStart
          ? PomodoroRunStatus.running
          : PomodoroRunStatus.idle,
    );
  }

  /// 构建切换到下一阶段后的状态。
  PomodoroState _buildNextPhaseState({
    required PomodoroState current,
    required PomodoroPhase nextPhase,
    required int completedRounds,
    required DateTime? phaseStart,
    required PomodoroRunStatus status,
  }) {
    final totalSeconds = _phaseDurationSeconds(
      phase: nextPhase,
      settings: current.settings,
    );
    return current.copyWith(
      phase: nextPhase,
      status: status,
      currentPhaseTotalSeconds: totalSeconds,
      remainingSeconds: totalSeconds,
      completedRounds: completedRounds,
      elapsedSecondsBeforeRun: 0,
      phaseStartedAt: phaseStart,
      lastResumedAt: status == PomodoroRunStatus.running ? phaseStart : null,
      errorMessage: null,
    );
  }

  /// 根据下一阶段判断是否自动继续。
  bool _shouldAutoStartNextPhase({
    required PomodoroPhase nextPhase,
    required PomodoroSettingsEntity settings,
  }) {
    return switch (nextPhase) {
      PomodoroPhase.work => settings.autoStartWork,
      PomodoroPhase.shortBreak || PomodoroPhase.longBreak =>
        settings.autoStartBreak,
    };
  }

  /// 启动定时器。
  void _startTicker() {
    _ticker?.cancel();
    _ticker = Timer.periodic(const Duration(seconds: 1), (_) async {
      await _synchronizeWithClock(notifyOnTransition: true);
    });
  }

  /// 停止定时器。
  void _stopTicker() {
    _ticker?.cancel();
    _ticker = null;
  }

  /// 将当前状态持久化到 SharedPreferences。
  Future<void> _persistState() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyPhase, state.phase.name);
    await prefs.setString(_keyStatus, state.status.name);
    await prefs.setInt(
      _keyCurrentPhaseTotalSeconds,
      state.currentPhaseTotalSeconds,
    );
    await prefs.setInt(_keyRemainingSeconds, state.remainingSeconds);
    await prefs.setInt(_keyCompletedRounds, state.completedRounds);
    await prefs.setInt(
      _keyElapsedSecondsBeforeRun,
      state.elapsedSecondsBeforeRun,
    );

    final phaseStartedAtMs = state.phaseStartedAt?.millisecondsSinceEpoch;
    final lastResumedAtMs = state.lastResumedAt?.millisecondsSinceEpoch;
    if (phaseStartedAtMs == null) {
      await prefs.remove(_keyPhaseStartedAt);
    } else {
      await prefs.setInt(_keyPhaseStartedAt, phaseStartedAtMs);
    }

    if (lastResumedAtMs == null) {
      await prefs.remove(_keyLastResumedAt);
    } else {
      await prefs.setInt(_keyLastResumedAt, lastResumedAtMs);
    }
  }

  /// 根据自然结束逻辑计算下一阶段。
  PomodoroPhase _resolveNextPhaseAfterNaturalFinish({
    required PomodoroPhase currentPhase,
    required int completedRounds,
    required PomodoroSettingsEntity settings,
  }) {
    switch (currentPhase) {
      case PomodoroPhase.work:
        if (completedRounds > 0 &&
            completedRounds % settings.longBreakInterval == 0) {
          return PomodoroPhase.longBreak;
        }
        return PomodoroPhase.shortBreak;
      case PomodoroPhase.shortBreak:
      case PomodoroPhase.longBreak:
        return PomodoroPhase.work;
    }
  }

  /// 根据手动跳过逻辑计算下一阶段。
  PomodoroPhase _resolveNextPhaseAfterSkip(PomodoroState current) {
    switch (current.phase) {
      case PomodoroPhase.work:
        return PomodoroPhase.shortBreak;
      case PomodoroPhase.shortBreak:
      case PomodoroPhase.longBreak:
        return PomodoroPhase.work;
    }
  }

  /// 计算指定阶段对应的秒数。
  int _phaseDurationSeconds({
    required PomodoroPhase phase,
    required PomodoroSettingsEntity settings,
  }) {
    final minutes = switch (phase) {
      PomodoroPhase.work => settings.workMinutes,
      PomodoroPhase.shortBreak => settings.shortBreakMinutes,
      PomodoroPhase.longBreak => settings.longBreakMinutes,
    };
    return minutes * 60;
  }

  /// 读取持久化时间戳并转成 DateTime。
  DateTime? _readDateTime(SharedPreferences prefs, String key) {
    final raw = prefs.getInt(key);
    if (raw == null) return null;
    return DateTime.fromMillisecondsSinceEpoch(raw);
  }

  /// 解析持久化阶段字符串。
  PomodoroPhase _phaseFromStorage(String raw) {
    return PomodoroPhase.values.firstWhere(
      (phase) => phase.name == raw,
      orElse: () => PomodoroPhase.work,
    );
  }

  /// 解析持久化状态字符串。
  PomodoroRunStatus _statusFromStorage(String? raw) {
    if (raw == null || raw.isEmpty) return PomodoroRunStatus.idle;
    return PomodoroRunStatus.values.firstWhere(
      (status) => status.name == raw,
      orElse: () => PomodoroRunStatus.idle,
    );
  }

  @override
  void dispose() {
    _stopTicker();
    super.dispose();
  }
}

/// 番茄钟状态 Provider。
final pomodoroProvider =
    StateNotifierProvider<PomodoroNotifier, PomodoroState>((ref) {
      return PomodoroNotifier(ref);
    });
