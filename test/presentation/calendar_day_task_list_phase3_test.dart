// 文件用途：DayTaskListContent / DayTaskListSheet Phase 3 Widget 测试，覆盖加载态、错误态、空态、筛选空态与完成/撤销交互。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/complete_review_task_usecase.dart';
import 'package:yike/domain/usecases/get_calendar_tasks_usecase.dart';
import 'package:yike/domain/usecases/skip_review_task_usecase.dart';
import 'package:yike/domain/usecases/undo_task_status_usecase.dart';
import 'package:yike/presentation/pages/calendar/widgets/day_task_list.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';

import '../helpers/test_database.dart';

class _DummyReviewTaskRepository implements ReviewTaskRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// 仅用于 CalendarNotifier 的“月份统计”查询，避免真实数据库依赖。
class _FakeGetCalendarTasksUseCase extends GetCalendarTasksUseCase {
  _FakeGetCalendarTasksUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  @override
  Future<CalendarMonthResult> execute({required int year, required int month}) async {
    return const CalendarMonthResult(dayStats: <DateTime, TaskDayStats>{});
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) async {
    return const <ReviewTaskViewEntity>[];
  }
}

class _NoopCompleteReviewTaskUseCase extends CompleteReviewTaskUseCase {
  _NoopCompleteReviewTaskUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  @override
  Future<void> execute(int id) async {}
}

class _NoopSkipReviewTaskUseCase extends SkipReviewTaskUseCase {
  _NoopSkipReviewTaskUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  @override
  Future<void> execute(int id) async {}
}

class _NoopUndoTaskStatusUseCase extends UndoTaskStatusUseCase {
  _NoopUndoTaskStatusUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  @override
  Future<void> execute(int id) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  ReviewTaskViewEntity task({
    required int id,
    required String title,
    required ReviewTaskStatus status,
    required DateTime scheduledDate,
  }) {
    return ReviewTaskViewEntity(
      taskId: id,
      learningItemId: 1,
      title: title,
      description: null,
      note: null,
      subtaskCount: 0,
      tags: const <String>[],
      reviewRound: 1,
      scheduledDate: scheduledDate,
      status: status,
      completedAt: status == ReviewTaskStatus.done ? scheduledDate : null,
      skippedAt: status == ReviewTaskStatus.skipped ? scheduledDate : null,
      isDeleted: false,
      deletedAt: null,
    );
  }

  Future<ProviderContainer> pumpContent(
    WidgetTester tester, {
    required DateTime selectedDay,
  }) async {
    SharedPreferences.setMockInitialValues(const <String, Object>{
      // 说明：开启撤销 Snackbar，覆盖 showUndoSnack 分支。
      'ui_undo_snackbar_enabled': true,
      // 说明：关闭触觉反馈，避免依赖平台实现（覆盖 HapticUtils 的短路分支）。
      'ui_haptic_feedback_enabled': false,
    });

    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final db = createInMemoryDatabase();
    addTearDown(() async => db.close());

    final container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        getCalendarTasksUseCaseProvider.overrideWithValue(_FakeGetCalendarTasksUseCase()),
        completeReviewTaskUseCaseProvider.overrideWithValue(
          _NoopCompleteReviewTaskUseCase(),
        ),
        skipReviewTaskUseCaseProvider.overrideWithValue(_NoopSkipReviewTaskUseCase()),
        undoTaskStatusUseCaseProvider.overrideWithValue(_NoopUndoTaskStatusUseCase()),
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          home: Scaffold(
            body: DayTaskListContent(selectedDay: selectedDay),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    return container;
  }

  group('DayTaskListContent Phase3', () {
    testWidgets('加载态会展示进度指示器', (tester) async {
      final selectedDay = DateTime(2026, 3, 7);
      final container = await pumpContent(tester, selectedDay: selectedDay);

      container.read(calendarProvider.notifier).state = CalendarState.initial().copyWith(
        selectedDay: selectedDay,
        isLoadingTasks: true,
        isLoadingMonth: false,
        errorMessage: null,
      );
      await tester.pump();

      expect(find.byType(CircularProgressIndicator), findsWidgets);
    });

    testWidgets('错误态会展示错误卡片', (tester) async {
      final selectedDay = DateTime(2026, 3, 7);
      final container = await pumpContent(tester, selectedDay: selectedDay);

      container.read(calendarProvider.notifier).state = CalendarState.initial().copyWith(
        selectedDay: selectedDay,
        isLoadingTasks: false,
        isLoadingMonth: false,
        errorMessage: '加载失败：主题不存在',
      );
      await tester.pumpAndSettle();

      expect(find.textContaining('加载失败'), findsOneWidget);
    });

    testWidgets('空态与筛选空态会展示不同提示', (tester) async {
      final selectedDay = DateTime(2026, 3, 7);
      final container = await pumpContent(tester, selectedDay: selectedDay);

      container.read(calendarProvider.notifier).state = CalendarState.initial().copyWith(
        selectedDay: selectedDay,
        isLoadingTasks: false,
        isLoadingMonth: false,
        selectedDayTasks: const <ReviewTaskViewEntity>[],
      );
      await tester.pumpAndSettle();
      expect(find.text('当天暂无复习任务'), findsOneWidget);

      final tasks = <ReviewTaskViewEntity>[
        task(
          id: 1,
          title: '待复习条目',
          status: ReviewTaskStatus.pending,
          scheduledDate: selectedDay,
        ),
      ];
      container.read(calendarProvider.notifier).state = container
          .read(calendarProvider.notifier)
          .state
          .copyWith(selectedDayTasks: tasks);
      container.read(reviewTaskFilterProvider.notifier).state = ReviewTaskFilter.done;
      await tester.pumpAndSettle();
      expect(find.text('当前筛选下暂无任务'), findsOneWidget);
    });

    testWidgets('完成任务后会展示撤销 Snackbar，并可触发撤销动作', (tester) async {
      final selectedDay = DateTime(2026, 3, 7);
      final container = await pumpContent(tester, selectedDay: selectedDay);

      container.read(calendarProvider.notifier).state = CalendarState.initial().copyWith(
        selectedDay: selectedDay,
        isLoadingTasks: false,
        isLoadingMonth: false,
        selectedDayTasks: <ReviewTaskViewEntity>[
          task(
            id: 1,
            title: '可完成任务',
            status: ReviewTaskStatus.pending,
            scheduledDate: selectedDay,
          ),
        ],
      );
      container.read(reviewTaskFilterProvider.notifier).state = ReviewTaskFilter.all;
      await tester.pumpAndSettle();

      await tester.tap(find.text('完成'));
      await tester.pumpAndSettle();

      expect(find.text('任务已完成'), findsOneWidget);
      expect(find.text('撤销'), findsOneWidget);

      await tester.tap(find.text('撤销'));
      await tester.pumpAndSettle();

      expect(find.text('已撤销'), findsOneWidget);
    });
  });
}
