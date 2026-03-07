// 文件用途：UxBootstrap Widget 测试，覆盖启动副作用、通知点击导航与启动通知分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/goal_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/repositories/goal_settings_repository.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/repositories/settings_repository.dart';
import 'package:yike/presentation/widgets/ux_bootstrap.dart';

import '../helpers/test_database.dart';

class _FakeSettingsRepository extends Fake implements SettingsRepository {
  _FakeSettingsRepository(this.settings);

  final AppSettingsEntity settings;

  @override
  Future<AppSettingsEntity> getSettings() async => settings;

  @override
  Future<void> saveSettings(AppSettingsEntity settings) async {}

  @override
  Future<List<ReviewIntervalConfigEntity>> getReviewIntervalConfigs() async {
    return <ReviewIntervalConfigEntity>[];
  }

  @override
  Future<void> saveReviewIntervalConfigs(
    List<ReviewIntervalConfigEntity> configs,
  ) async {}
}

class _FakeGoalSettingsRepository extends Fake
    implements GoalSettingsRepository {
  _FakeGoalSettingsRepository(this.settings);

  final GoalSettingsEntity settings;

  @override
  Future<GoalSettingsEntity> getGoalSettings() async => settings;

  @override
  Future<void> saveGoalSettings(GoalSettingsEntity settings) async {}
}

class _FakeReviewTaskRepository extends Fake implements ReviewTaskRepository {
  _FakeReviewTaskRepository({
    this.overdueTasks = const <ReviewTaskViewEntity>[],
    this.todayStats = const TaskDayStats(
      pendingCount: 0,
      doneCount: 0,
      skippedCount: 0,
    ),
    this.streakDays = 0,
    this.weekStats = (0, 0),
  });

  final List<ReviewTaskViewEntity> overdueTasks;
  final TaskDayStats todayStats;
  final int streakDays;
  final (int completed, int total) weekStats;

  @override
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks() async {
    return overdueTasks;
  }

  @override
  Future<Map<DateTime, TaskDayStats>> getTaskDayStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    return <DateTime, TaskDayStats>{start: todayStats};
  }

  @override
  Future<int> getConsecutiveCompletedDays({DateTime? today}) async {
    return streakDays;
  }

  @override
  Future<(int completed, int total)> getTaskStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    return weekStats;
  }
}

class _NotificationRecord {
  const _NotificationRecord({
    required this.id,
    required this.title,
    required this.body,
    required this.payloadRoute,
  });

  final int id;
  final String title;
  final String body;
  final String? payloadRoute;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    await UxBootstrap.debugResetForTest();
  });

  tearDown(() async {
    await UxBootstrap.debugResetForTest();
  });

  group('UxBootstrap', () {
    testWidgets('会执行预加载、过滤无效路由并响应通知点击导航', (tester) async {
      final db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final tapController = StreamController<String>.broadcast();
      addTearDown(tapController.close);

      late GoRouter router;
      var preloadCallCount = 0;
      final container = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(db),
          uxBootstrapEnabledProvider.overrideWithValue(true),
          uxBootstrapPreloadStarterProvider.overrideWithValue((context) {
            preloadCallCount++;
          }),
          uxBootstrapNotificationTapStreamProvider.overrideWithValue(
            tapController.stream,
          ),
          uxBootstrapNavigateProvider.overrideWithValue((context, route) {
            router.go(route);
          }),
        ],
      );
      addTearDown(container.dispose);

      router = GoRouter(
        initialLocation: '/home',
        routes: <RouteBase>[
          GoRoute(
            path: '/home',
            builder: (context, state) {
              return const Scaffold(body: Center(child: Text('首页')));
            },
          ),
          GoRoute(
            path: '/statistics',
            builder: (context, state) {
              return const Scaffold(body: Center(child: Text('统计页')));
            },
          ),
        ],
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp.router(
            routerConfig: router,
            builder: (context, child) {
              return UxBootstrap(child: child ?? const SizedBox.shrink());
            },
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(preloadCallCount, 1);
      expect(find.text('首页'), findsOneWidget);

      tapController.add('   ');
      await tester.pumpAndSettle();
      expect(find.text('首页'), findsOneWidget);

      tapController.add('statistics');
      await tester.pumpAndSettle();
      expect(find.text('首页'), findsOneWidget);

      tapController.add(' /statistics ');
      await tester.pumpAndSettle();
      expect(find.text('统计页'), findsOneWidget);

      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump();
    });

    testWidgets('启动后会发送逾期任务提醒', (tester) async {
      final db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final now = DateTime(2026, 3, 9, 10);
      final records = <_NotificationRecord>[];
      final container = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(db),
          uxBootstrapEnabledProvider.overrideWithValue(true),
          uxBootstrapNowProvider.overrideWithValue(() => now),
          uxBootstrapPreloadStarterProvider.overrideWithValue((context) {}),
          settingsRepositoryProvider.overrideWithValue(
            _FakeSettingsRepository(
              AppSettingsEntity.defaults.copyWith(
                goalNotificationEnabled: false,
                streakNotificationEnabled: false,
              ),
            ),
          ),
          goalSettingsRepositoryProvider.overrideWithValue(
            _FakeGoalSettingsRepository(
              const GoalSettingsEntity(
                dailyTarget: 2,
                streakTarget: 7,
                weeklyRateTarget: 80,
              ),
            ),
          ),
          reviewTaskRepositoryProvider.overrideWithValue(
            _FakeReviewTaskRepository(
              overdueTasks: <ReviewTaskViewEntity>[
                ReviewTaskViewEntity(
                  taskId: 1,
                  learningItemId: 1,
                  title: '逾期任务',
                  description: null,
                  note: null,
                  subtaskCount: 0,
                  tags: const <String>[],
                  reviewRound: 1,
                  scheduledDate: DateTime(2026, 3, 1),
                  status: ReviewTaskStatus.pending,
                  completedAt: null,
                  skippedAt: null,
                  isDeleted: false,
                  deletedAt: null,
                ),
              ],
              todayStats: const TaskDayStats(
                pendingCount: 0,
                doneCount: 2,
                skippedCount: 0,
              ),
              streakDays: 7,
              weekStats: (4, 4),
            ),
          ),
          uxBootstrapShowNotificationProvider.overrideWithValue(({
            required int id,
            required String title,
            required String body,
            String? payloadRoute,
          }) async {
            records.add(
              _NotificationRecord(
                id: id,
                title: title,
                body: body,
                payloadRoute: payloadRoute,
              ),
            );
          }),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: UxBootstrap(
              child: const Scaffold(body: Center(child: Text('启动页'))),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(records, hasLength(1));
      expect(records.single.id, 21);
      expect(records[0].payloadRoute, '/home?tab=today&focus=overdue');

    });

    testWidgets('启动后会发送目标达成提醒', (tester) async {
      final db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final now = DateTime(2026, 3, 9, 10);
      final records = <_NotificationRecord>[];
      final container = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(db),
          uxBootstrapEnabledProvider.overrideWithValue(true),
          uxBootstrapNowProvider.overrideWithValue(() => now),
          uxBootstrapPreloadStarterProvider.overrideWithValue((context) {}),
          settingsRepositoryProvider.overrideWithValue(
            _FakeSettingsRepository(
              AppSettingsEntity.defaults.copyWith(
                overdueNotificationEnabled: false,
                streakNotificationEnabled: false,
              ),
            ),
          ),
          goalSettingsRepositoryProvider.overrideWithValue(
            _FakeGoalSettingsRepository(
              const GoalSettingsEntity(
                dailyTarget: 2,
                streakTarget: 7,
                weeklyRateTarget: 80,
              ),
            ),
          ),
          reviewTaskRepositoryProvider.overrideWithValue(
            _FakeReviewTaskRepository(
              todayStats: const TaskDayStats(
                pendingCount: 0,
                doneCount: 2,
                skippedCount: 0,
              ),
              streakDays: 7,
              weekStats: (4, 4),
            ),
          ),
          uxBootstrapShowNotificationProvider.overrideWithValue(({
            required int id,
            required String title,
            required String body,
            String? payloadRoute,
          }) async {
            records.add(
              _NotificationRecord(
                id: id,
                title: title,
                body: body,
                payloadRoute: payloadRoute,
              ),
            );
          }),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: UxBootstrap(
              child: const Scaffold(body: Center(child: Text('启动页'))),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(records, hasLength(1));
      expect(records.single.id, 22);
      expect(records.single.payloadRoute, '/statistics');
      expect(records.single.body, contains('每日完成'));
      expect(records.single.body, contains('连续打卡'));
      expect(records.single.body, contains('本周完成率'));

    });

    testWidgets('启动后会发送连续打卡里程碑提醒', (tester) async {
      final db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final now = DateTime(2026, 3, 9, 10);
      final records = <_NotificationRecord>[];
      final container = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(db),
          uxBootstrapEnabledProvider.overrideWithValue(true),
          uxBootstrapNowProvider.overrideWithValue(() => now),
          uxBootstrapPreloadStarterProvider.overrideWithValue((context) {}),
          settingsRepositoryProvider.overrideWithValue(
            _FakeSettingsRepository(
              AppSettingsEntity.defaults.copyWith(
                overdueNotificationEnabled: false,
                goalNotificationEnabled: false,
              ),
            ),
          ),
          goalSettingsRepositoryProvider.overrideWithValue(
            _FakeGoalSettingsRepository(GoalSettingsEntity.defaults()),
          ),
          reviewTaskRepositoryProvider.overrideWithValue(
            _FakeReviewTaskRepository(streakDays: 30),
          ),
          uxBootstrapShowNotificationProvider.overrideWithValue(({
            required int id,
            required String title,
            required String body,
            String? payloadRoute,
          }) async {
            records.add(
              _NotificationRecord(
                id: id,
                title: title,
                body: body,
                payloadRoute: payloadRoute,
              ),
            );
          }),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: UxBootstrap(
              child: const Scaffold(body: Center(child: Text('启动页'))),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(seconds: 1));
      await tester.pumpAndSettle();

      expect(records, hasLength(1));
      expect(records.single.id, 23);
      expect(records.single.payloadRoute, '/home');
      expect(records.single.body, contains('连续打卡 30 天'));

    });

    testWidgets('免打扰时段内会跳过启动通知', (tester) async {
      final db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final records = <_NotificationRecord>[];
      final container = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(db),
          uxBootstrapEnabledProvider.overrideWithValue(true),
          uxBootstrapNowProvider.overrideWithValue(
            () => DateTime(2026, 3, 9, 22, 30),
          ),
          uxBootstrapPreloadStarterProvider.overrideWithValue((context) {}),
          settingsRepositoryProvider.overrideWithValue(
            _FakeSettingsRepository(
              AppSettingsEntity.defaults.copyWith(
                doNotDisturbStart: '22:00',
                doNotDisturbEnd: '23:00',
              ),
            ),
          ),
          goalSettingsRepositoryProvider.overrideWithValue(
            _FakeGoalSettingsRepository(GoalSettingsEntity.defaults()),
          ),
          reviewTaskRepositoryProvider.overrideWithValue(
            _FakeReviewTaskRepository(
              overdueTasks: <ReviewTaskViewEntity>[
                ReviewTaskViewEntity(
                  taskId: 1,
                  learningItemId: 1,
                  title: '不会发送的任务',
                  description: null,
                  note: null,
                  subtaskCount: 0,
                  tags: const <String>[],
                  reviewRound: 1,
                  scheduledDate: DateTime(2026, 3, 1),
                  status: ReviewTaskStatus.pending,
                  completedAt: null,
                  skippedAt: null,
                  isDeleted: false,
                  deletedAt: null,
                ),
              ],
              todayStats: const TaskDayStats(
                pendingCount: 0,
                doneCount: 9,
                skippedCount: 0,
              ),
              streakDays: 30,
              weekStats: (9, 9),
            ),
          ),
          uxBootstrapShowNotificationProvider.overrideWithValue(({
            required int id,
            required String title,
            required String body,
            String? payloadRoute,
          }) async {
            records.add(
              _NotificationRecord(
                id: id,
                title: title,
                body: body,
                payloadRoute: payloadRoute,
              ),
            );
          }),
        ],
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: UxBootstrap(
              child: const Scaffold(body: Center(child: Text('启动页'))),
            ),
          ),
        ),
      );
      await tester.pump();
      await tester.pumpAndSettle();

      expect(records, isEmpty);
    });
  });
}
