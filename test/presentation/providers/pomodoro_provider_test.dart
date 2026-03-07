// 文件用途：PomodoroProvider 单元测试（倒计时恢复、阶段切换、通知触发）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/pomodoro_settings.dart';
import 'package:yike/domain/repositories/pomodoro_settings_repository.dart';
import 'package:yike/presentation/providers/pomodoro_provider.dart';

import '../../helpers/test_database.dart';

class _FakePomodoroSettingsRepository implements PomodoroSettingsRepository {
  _FakePomodoroSettingsRepository(this._settings);

  PomodoroSettingsEntity _settings;

  @override
  Future<PomodoroSettingsEntity> getSettings() async => _settings;

  @override
  Future<void> saveSettings(PomodoroSettingsEntity settings) async {
    _settings = settings;
  }
}

void main() {
  late AppDatabase db;
  late DateTime fakeNow;
  late List<PomodoroPhase> notifications;
  late _FakePomodoroSettingsRepository settingsRepository;

  Future<void> settle() async {
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);
  }

  ProviderContainer createContainer() {
    return ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
        pomodoroSettingsRepositoryProvider.overrideWithValue(settingsRepository),
        pomodoroClockProvider.overrideWithValue(() => fakeNow),
        pomodoroPhaseNotificationSenderProvider.overrideWithValue((phase) async {
          notifications.add(phase);
        }),
      ],
    );
  }

  setUp(() {
    db = createInMemoryDatabase();
    fakeNow = DateTime(2026, 3, 6, 10, 0, 0);
    notifications = <PomodoroPhase>[];
    settingsRepository = _FakePomodoroSettingsRepository(
      PomodoroSettingsEntity.defaults,
    );
    SharedPreferences.setMockInitialValues({});
  });

  tearDown(() async {
    await db.close();
  });

  test('restore 会在工作阶段结束后切换到短休息并发送通知', () async {
    SharedPreferences.setMockInitialValues({
      'pomodoro_timer.phase': 'work',
      'pomodoro_timer.status': 'running',
      'pomodoro_timer.current_phase_total_seconds': 1500,
      'pomodoro_timer.remaining_seconds': 1500,
      'pomodoro_timer.completed_rounds': 0,
      'pomodoro_timer.elapsed_seconds_before_run': 0,
      'pomodoro_timer.phase_started_at_ms':
          fakeNow.subtract(const Duration(minutes: 25)).millisecondsSinceEpoch,
      'pomodoro_timer.last_resumed_at_ms':
          fakeNow.subtract(const Duration(minutes: 25)).millisecondsSinceEpoch,
    });

    final container = createContainer();
    addTearDown(container.dispose);
    container.read(pomodoroProvider.notifier);
    await settle();

    final state = container.read(pomodoroProvider);
    expect(state.phase, PomodoroPhase.shortBreak);
    expect(state.status, PomodoroRunStatus.running);
    expect(state.completedRounds, 1);
    expect(state.remainingSeconds, 300);
    expect(notifications, [PomodoroPhase.work]);
  });

  test('重新启动应用后可恢复运行中的倒计时剩余时间', () async {
    var container = createContainer();
    addTearDown(container.dispose);
    final notifier = container.read(pomodoroProvider.notifier);
    await settle();
    await notifier.start();
    container.dispose();

    fakeNow = fakeNow.add(const Duration(seconds: 90));
    container = createContainer();
    addTearDown(container.dispose);
    container.read(pomodoroProvider.notifier);
    await settle();

    final state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.running);
    expect(state.phase, PomodoroPhase.work);
    expect(state.remainingSeconds, 1410);
    expect(notifications, isEmpty);
  });

  test('休息阶段结束后会切换回工作阶段并发送休息结束通知', () async {
    SharedPreferences.setMockInitialValues({
      'pomodoro_timer.phase': 'shortBreak',
      'pomodoro_timer.status': 'running',
      'pomodoro_timer.current_phase_total_seconds': 300,
      'pomodoro_timer.remaining_seconds': 300,
      'pomodoro_timer.completed_rounds': 1,
      'pomodoro_timer.elapsed_seconds_before_run': 0,
      'pomodoro_timer.phase_started_at_ms':
          fakeNow.subtract(const Duration(minutes: 5)).millisecondsSinceEpoch,
      'pomodoro_timer.last_resumed_at_ms':
          fakeNow.subtract(const Duration(minutes: 5)).millisecondsSinceEpoch,
    });

    final container = createContainer();
    addTearDown(container.dispose);
    container.read(pomodoroProvider.notifier);
    await settle();

    final state = container.read(pomodoroProvider);
    expect(state.phase, PomodoroPhase.work);
    expect(state.status, PomodoroRunStatus.running);
    expect(state.completedRounds, 1);
    expect(state.remainingSeconds, 1500);
    expect(notifications, [PomodoroPhase.shortBreak]);
  });

  test('start/pause/resume/reset 支持状态切换与剩余时间同步', () async {
    final container = createContainer();
    addTearDown(container.dispose);
    final notifier = container.read(pomodoroProvider.notifier);
    await settle();

    await notifier.start();
    var state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.running);
    expect(state.phase, PomodoroPhase.work);

    // 模拟运行 90 秒后暂停：pause 内部会先同步时钟再切到 paused。
    fakeNow = fakeNow.add(const Duration(seconds: 90));
    await notifier.pause();
    state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.paused);
    expect(state.remainingSeconds, lessThan(state.currentPhaseTotalSeconds));

    // 恢复后继续运行。
    fakeNow = fakeNow.add(const Duration(seconds: 10));
    await notifier.resume();
    state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.running);

    // 重置会回到 idle 且清空 startedAt/resumedAt。
    await notifier.reset();
    state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.idle);
    expect(state.phaseStartedAt, isNull);
    expect(state.lastResumedAt, isNull);
  });

  test('skip 会切换到下一阶段并回到 idle', () async {
    final container = createContainer();
    addTearDown(container.dispose);
    final notifier = container.read(pomodoroProvider.notifier);
    await settle();

    await notifier.start();
    await notifier.skip();

    final state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.idle);
    // 说明：work 被手动跳过后应进入休息阶段（短休息或长休息，取决于 completedRounds）。
    expect(state.phase, isNot(PomodoroPhase.work));
    expect(state.phaseStartedAt, isNull);
    expect(state.lastResumedAt, isNull);
  });

  test('refreshSettings 在 idle 且未开始时会同步刷新当前阶段时长', () async {
    final container = createContainer();
    addTearDown(container.dispose);
    final notifier = container.read(pomodoroProvider.notifier);
    await settle();

    // 将默认 workMinutes 改为 1 分钟，验证 refreshSettings 会同步更新 currentPhaseTotalSeconds。
    await settingsRepository.saveSettings(
      PomodoroSettingsEntity(
        workMinutes: 1,
        shortBreakMinutes: 1,
        longBreakMinutes: 1,
        longBreakInterval: 2,
        autoStartBreak: true,
        autoStartWork: true,
      ),
    );

    await notifier.refreshSettings();
    final state = container.read(pomodoroProvider);
    expect(state.currentPhaseTotalSeconds, 60);
    expect(state.remainingSeconds, 60);
  });

  test('handleAppVisibilityChanged 前后台切换会触发 restore/persist 分支', () async {
    final container = createContainer();
    addTearDown(container.dispose);
    final notifier = container.read(pomodoroProvider.notifier);
    await settle();

    await notifier.start();
    fakeNow = fakeNow.add(const Duration(seconds: 30));
    await notifier.handleAppVisibilityChanged(isForeground: false);

    // 前台恢复会调用 restore：由于仍处于 running，会同步剩余时间。
    fakeNow = fakeNow.add(const Duration(seconds: 20));
    await notifier.handleAppVisibilityChanged(isForeground: true);
    final state = container.read(pomodoroProvider);
    expect(state.status, PomodoroRunStatus.running);
    expect(state.remainingSeconds, lessThan(state.currentPhaseTotalSeconds));
  });

  test('专注结束后关闭自动休息时，会停在休息阶段等待手动开始', () async {
    settingsRepository = _FakePomodoroSettingsRepository(
      PomodoroSettingsEntity.defaults.copyWith(autoStartBreak: false),
    );
    SharedPreferences.setMockInitialValues({
      'pomodoro_timer.phase': 'work',
      'pomodoro_timer.status': 'running',
      'pomodoro_timer.current_phase_total_seconds': 1500,
      'pomodoro_timer.remaining_seconds': 1500,
      'pomodoro_timer.completed_rounds': 0,
      'pomodoro_timer.elapsed_seconds_before_run': 0,
      'pomodoro_timer.phase_started_at_ms':
          fakeNow.subtract(const Duration(minutes: 25)).millisecondsSinceEpoch,
      'pomodoro_timer.last_resumed_at_ms':
          fakeNow.subtract(const Duration(minutes: 25)).millisecondsSinceEpoch,
    });

    final container = createContainer();
    addTearDown(container.dispose);
    container.read(pomodoroProvider.notifier);
    await settle();

    final state = container.read(pomodoroProvider);
    expect(state.phase, PomodoroPhase.shortBreak);
    expect(state.status, PomodoroRunStatus.idle);
    expect(state.remainingSeconds, 300);
    expect(notifications, [PomodoroPhase.work]);
  });

  test('休息结束后关闭自动专注时，会停在工作阶段等待手动开始', () async {
    settingsRepository = _FakePomodoroSettingsRepository(
      PomodoroSettingsEntity.defaults.copyWith(autoStartWork: false),
    );
    SharedPreferences.setMockInitialValues({
      'pomodoro_timer.phase': 'shortBreak',
      'pomodoro_timer.status': 'running',
      'pomodoro_timer.current_phase_total_seconds': 300,
      'pomodoro_timer.remaining_seconds': 300,
      'pomodoro_timer.completed_rounds': 1,
      'pomodoro_timer.elapsed_seconds_before_run': 0,
      'pomodoro_timer.phase_started_at_ms':
          fakeNow.subtract(const Duration(minutes: 5)).millisecondsSinceEpoch,
      'pomodoro_timer.last_resumed_at_ms':
          fakeNow.subtract(const Duration(minutes: 5)).millisecondsSinceEpoch,
    });

    final container = createContainer();
    addTearDown(container.dispose);
    container.read(pomodoroProvider.notifier);
    await settle();

    final state = container.read(pomodoroProvider);
    expect(state.phase, PomodoroPhase.work);
    expect(state.status, PomodoroRunStatus.idle);
    expect(state.remainingSeconds, 1500);
    expect(notifications, [PomodoroPhase.shortBreak]);
  });
}
