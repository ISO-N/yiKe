// 文件用途：Phase 2 统计内容高价值测试，覆盖移动端/桌面端布局、趋势区块与错误展示。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/statistics_insights.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/repositories/ui_preferences_repository.dart';
import 'package:yike/presentation/providers/goal_provider.dart';
import 'package:yike/presentation/providers/statistics_heatmap_provider.dart';
import 'package:yike/presentation/providers/statistics_insights_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/widgets/statistics_content.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;

  Future<void> pumpStatisticsContent(
    WidgetTester tester, {
    required StatisticsState state,
    required List<Override> overrides,
    Size size = const Size(390, 844),
    Future<void> Function()? onRefresh,
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: overrides,
        child: MaterialApp(
          home: Scaffold(
            body: StatisticsContent(
              state: state,
              onRefresh: onRefresh,
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pumpAndSettle();
  }

  StatisticsInsightsEntity buildInsights() {
    final today = DateTime.now();
    return StatisticsInsightsEntity(
      weekPoints: List<StatisticsTrendPointEntity>.generate(
        7,
        (index) => StatisticsTrendPointEntity(
          date: today.subtract(Duration(days: 6 - index)),
          completed: index + 1,
          total: index + 2,
        ),
      ),
      monthPoints: List<StatisticsTrendPointEntity>.generate(
        5,
        (index) => StatisticsTrendPointEntity(
          date: DateTime(today.year, today.month, index + 1),
          completed: index + 2,
          total: index + 3,
        ),
      ),
      yearPoints: List<StatisticsTrendPointEntity>.generate(
        4,
        (index) => StatisticsTrendPointEntity(
          date: DateTime(today.year, index + 1, 1),
          completed: index + 3,
          total: index + 4,
        ),
      ),
      todayStats: const TaskDayStats(
        pendingCount: 1,
        doneCount: 2,
        skippedCount: 0,
      ),
      weekCompare: const WeekCompareEntity(
        thisCompleted: 5,
        thisTotal: 7,
        lastCompleted: 3,
        lastTotal: 7,
        daysInPeriod: 5,
        isInProgress: true,
      ),
    );
  }

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  testWidgets('StatisticsContent 支持桌面端趋势、热力图与标签分布展示', (tester) async {
    await pumpStatisticsContent(
      tester,
      size: const Size(1280, 900),
      state: const StatisticsState(
        isLoading: false,
        consecutiveCompletedDays: 6,
        weekCompleted: 5,
        weekTotal: 7,
        monthCompleted: 18,
        monthTotal: 24,
        tagDistribution: <String, int>{'Flutter': 4, 'Riverpod': 2},
      ),
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        uiPreferencesRepositoryProvider.overrideWithValue(
          _FakeUiPreferencesRepository(),
        ),
        goalProgressProvider.overrideWithValue(
          const AsyncValue.data(<GoalProgressItem>[]),
        ),
        statisticsInsightsProvider.overrideWith(
          (ref) async => buildInsights(),
        ),
        statisticsHeatmapProvider.overrideWith(
          (ref, year) async => <DateTime, TaskDayStats>{
            DateTime(year, 1, 1): const TaskDayStats(
              pendingCount: 1,
              doneCount: 2,
              skippedCount: 0,
            ),
            DateTime(year, 1, 2): const TaskDayStats(
              pendingCount: 0,
              doneCount: 0,
              skippedCount: 0,
            ),
          },
        ),
      ],
      onRefresh: () async {},
    );

    expect(find.text('学习统计'), findsOneWidget);
    expect(find.text('最近趋势'), findsOneWidget);
    expect(find.text('本周对比'), findsOneWidget);

    await tester.tap(find.text('本年'));
    await tester.pumpAndSettle();
  });

  testWidgets('StatisticsContent 支持移动端错误、空标签与加载提示展示', (tester) async {
    await pumpStatisticsContent(
      tester,
      state: const StatisticsState(
        isLoading: true,
        consecutiveCompletedDays: 0,
        weekCompleted: 0,
        weekTotal: 0,
        monthCompleted: 0,
        monthTotal: 0,
        tagDistribution: <String, int>{},
        errorMessage: '统计摘要加载失败',
      ),
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        uiPreferencesRepositoryProvider.overrideWithValue(
          _FakeUiPreferencesRepository(),
        ),
        goalProgressProvider.overrideWithValue(
          const AsyncValue.data(<GoalProgressItem>[]),
        ),
        statisticsInsightsProvider.overrideWith(
          (ref) async => throw Exception('趋势异常'),
        ),
        statisticsHeatmapProvider.overrideWith(
          (ref, year) async => <DateTime, TaskDayStats>{},
        ),
      ],
    );

    expect(find.text('先完成今天的复习，开始形成稳定节奏。'), findsOneWidget);
    expect(find.text('未启用目标'), findsOneWidget);
    expect(find.textContaining('趋势数据加载失败'), findsOneWidget);
  });
}

class _FakeUiPreferencesRepository implements UiPreferencesRepository {
  @override
  Future<bool> getHapticFeedbackEnabled() async => false;

  @override
  Future<String> getSkeletonStrategy() async => 'off';

  @override
  Future<bool> getTaskListBlurEnabled() async => true;

  @override
  Future<bool> getUndoSnackbarEnabled() async => true;

  @override
  Future<void> setHapticFeedbackEnabled(bool enabled) async {}

  @override
  Future<void> setSkeletonStrategy(String strategy) async {}

  @override
  Future<void> setTaskListBlurEnabled(bool enabled) async {}

  @override
  Future<void> setUndoSnackbarEnabled(bool enabled) async {}
}
