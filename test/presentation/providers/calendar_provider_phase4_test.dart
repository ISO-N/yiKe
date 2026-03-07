// 文件用途：CalendarProvider Phase 4 测试，覆盖翻月并发回写与加载中保留月历本体。
// 作者：Codex
// 创建日期：2026-03-08

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/get_calendar_tasks_usecase.dart';
import 'package:yike/presentation/pages/calendar/widgets/calendar_grid.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';

import '../../helpers/test_database.dart';

class _DummyReviewTaskRepository implements ReviewTaskRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _ControlledCalendarUseCase extends GetCalendarTasksUseCase {
  _ControlledCalendarUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  final Map<String, Completer<CalendarMonthResult>> _monthCompleters =
      <String, Completer<CalendarMonthResult>>{};

  String _key(int year, int month) => '$year-$month';

  @override
  Future<CalendarMonthResult> execute({
    required int year,
    required int month,
  }) {
    return _monthCompleters.putIfAbsent(
      _key(year, month),
      Completer<CalendarMonthResult>.new,
    ).future;
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) async {
    return const <ReviewTaskViewEntity>[];
  }

  void completeMonth(
    int year,
    int month, {
    required Map<DateTime, TaskDayStats> stats,
  }) {
    _monthCompleters.putIfAbsent(
      _key(year, month),
      Completer<CalendarMonthResult>.new,
    ).complete(CalendarMonthResult(dayStats: stats));
  }
}

void main() {
  late AppDatabase db;

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  Future<void> settle() async {
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);
  }

  test('CalendarNotifier 会忽略过期的翻月请求回写', () async {
    final useCase = _ControlledCalendarUseCase();
    final container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        getCalendarTasksUseCaseProvider.overrideWithValue(useCase),
      ],
    );
    addTearDown(container.dispose);

    final notifier = container.read(calendarProvider.notifier);
    final now = DateTime.now();
    useCase.completeMonth(now.year, now.month, stats: const <DateTime, TaskDayStats>{});
    await settle();

    final aprilFuture = notifier.loadMonth(2026, 4);
    final mayFuture = notifier.loadMonth(2026, 5);

    useCase.completeMonth(
      2026,
      5,
      stats: <DateTime, TaskDayStats>{
        DateTime(2026, 5, 2): const TaskDayStats(
          pendingCount: 1,
          doneCount: 0,
          skippedCount: 0,
        ),
      },
    );
    await settle();

    useCase.completeMonth(
      2026,
      4,
      stats: <DateTime, TaskDayStats>{
        DateTime(2026, 4, 1): const TaskDayStats(
          pendingCount: 9,
          doneCount: 0,
          skippedCount: 0,
        ),
      },
    );
    await Future.wait(<Future<void>>[aprilFuture, mayFuture]);
    await settle();

    final state = container.read(calendarProvider);
    expect(state.loadedMonth, DateTime(2026, 5, 1));
    expect(state.monthStats.keys, <DateTime>[DateTime(2026, 5, 2)]);
  });

  testWidgets('CalendarGrid 在已加载后可展示加载态并响应按钮翻页', (tester) async {
    DateTime? changedMonth;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CalendarGrid(
            initialMonth: DateTime(2026, 3, 1),
            selectedDay: DateTime(2026, 3, 7),
            loadedMonth: DateTime(2026, 3, 1),
            dayStats: <DateTime, TaskDayStats>{
              DateTime(2026, 3, 7): const TaskDayStats(
                pendingCount: 1,
                doneCount: 0,
                skippedCount: 0,
              ),
            },
            hasLoadedMonth: true,
            isLoading: true,
            skeletonStrategy: 'off',
            onMonthChanged: (month) => changedMonth = month,
            onDaySelected: (_) {},
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));

    expect(find.text('2026年3月'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    await tester.tap(find.byTooltip('下个月'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 260));

    expect(changedMonth, DateTime(2026, 4, 1));
    expect(find.text('2026年4月'), findsOneWidget);
  });

  testWidgets('CalendarGrid 支持手势横滑翻页', (tester) async {
    DateTime? changedMonth;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: CalendarGrid(
            initialMonth: DateTime(2026, 3, 1),
            selectedDay: null,
            loadedMonth: DateTime(2026, 3, 1),
            dayStats: const <DateTime, TaskDayStats>{},
            hasLoadedMonth: true,
            isLoading: false,
            skeletonStrategy: 'off',
            onMonthChanged: (month) => changedMonth = month,
            onDaySelected: (_) {},
          ),
        ),
      ),
    );
    await tester.pump();

    await tester.fling(find.byType(PageView), const Offset(-600, 0), 1200);
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));

    expect(changedMonth, DateTime(2026, 4, 1));
    expect(find.text('2026年4月'), findsOneWidget);
  });
}
