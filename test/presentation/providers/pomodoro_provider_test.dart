// 文件用途：PomodoroProvider 单元测试（倒计时恢复、阶段切换、通知触发）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/presentation/providers/pomodoro_provider.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late DateTime fakeNow;
  late List<PomodoroPhase> notifications;

  Future<void> settle() async {
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);
  }

  ProviderContainer createContainer() {
    return ProviderContainer(
      overrides: [
        appDatabaseProvider.overrideWithValue(db),
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
}
