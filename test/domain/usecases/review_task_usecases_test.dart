// 文件用途：CompleteReviewTaskUseCase / SkipReviewTaskUseCase / GetHomeTasksUseCase 单元测试。
// 作者：Codex
// 创建日期：2026-02-25

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/entities/task_timeline.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/complete_review_task_usecase.dart';
import 'package:yike/domain/usecases/get_home_tasks_usecase.dart';
import 'package:yike/domain/usecases/skip_review_task_usecase.dart';

class _FakeReviewTaskRepository implements ReviewTaskRepository {
  int? lastCompleteId;
  List<int>? lastCompleteIds;
  int? lastSkipId;
  List<int>? lastSkipIds;
  DateTime? lastStatsDate;
  DateTime? lastStatsStart;
  DateTime? lastStatsEnd;

  @override
  Future<void> completeTask(int id) async {
    lastCompleteId = id;
  }

  @override
  Future<void> completeTasks(List<int> ids) async {
    lastCompleteIds = ids;
  }

  @override
  Future<void> skipTask(int id) async {
    lastSkipId = id;
  }

  @override
  Future<void> skipTasks(List<int> ids) async {
    lastSkipIds = ids;
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTodayPendingTasks() async {
    return [
      ReviewTaskViewEntity(
        taskId: 1,
        learningItemId: 1,
        title: 'T1',
        description: null,
        note: null,
        subtaskCount: 0,
        tags: <String>[],
        reviewRound: 1,
        scheduledDate: DateTime(2026, 2, 25),
        status: ReviewTaskStatus.pending,
        completedAt: null,
        skippedAt: null,
        isDeleted: false,
        deletedAt: null,
      ),
    ];
  }

  @override
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks() async {
    return const [];
  }

  @override
  Future<(int completed, int total)> getTaskStats(DateTime date) async {
    lastStatsDate = date;
    return (2, 5);
  }

  @override
  Future<Map<DateTime, TaskDayStats>> getMonthlyTaskStats(
    int year,
    int month,
  ) => throw UnimplementedError();

  @override
  Future<Map<DateTime, TaskDayStats>> getTaskDayStatsInRange(
    DateTime start,
    DateTime end,
  ) => throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTasksInRange(
    DateTime start,
    DateTime end,
  ) => throw UnimplementedError();

  @override
  Future<int> getConsecutiveCompletedDays({DateTime? today}) =>
      throw UnimplementedError();

  @override
  Future<(int completed, int total)> getTaskStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    lastStatsStart = start;
    lastStatsEnd = end;
    return (2, 5);
  }

  @override
  Future<List<ReviewTaskEntity>> getAllTasks() => throw UnimplementedError();

  // 以下为当前测试不涉及的方法。
  @override
  Future<ReviewTaskEntity> create(ReviewTaskEntity task) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskEntity>> createBatch(List<ReviewTaskEntity> tasks) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTodayCompletedTasks() async {
    return const [];
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTodaySkippedTasks() async {
    return const [];
  }

  @override
  Future<void> undoTaskStatus(int id) => throw UnimplementedError();

  @override
  Future<(int all, int pending, int done, int skipped)>
  getGlobalTaskStatusCounts() => throw UnimplementedError();

  @override
  Future<TaskTimelinePageEntity> getTaskTimelinePage({
    ReviewTaskStatus? status,
    DateTime? scheduledDateBefore,
    DateTime? scheduledDateOnOrAfter,
    TaskTimelineCursorEntity? cursor,
    int limit = 20,
  }) => throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getReviewPlan(int learningItemId) =>
      throw UnimplementedError();

  @override
  Future<void> adjustReviewDate({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
  }) => throw UnimplementedError();

  @override
  Future<void> addReviewRound(int learningItemId) => throw UnimplementedError();

  @override
  Future<void> removeLatestReviewRound(int learningItemId) =>
      throw UnimplementedError();
}

void main() {
  test('CompleteReviewTaskUseCase 会透传单个/批量完成', () async {
    final repo = _FakeReviewTaskRepository();
    final uc = CompleteReviewTaskUseCase(reviewTaskRepository: repo);
    await uc.execute(10);
    await uc.executeBatch([1, 2, 3]);
    expect(repo.lastCompleteId, 10);
    expect(repo.lastCompleteIds, [1, 2, 3]);
  });

  test('SkipReviewTaskUseCase 会透传单个/批量跳过', () async {
    final repo = _FakeReviewTaskRepository();
    final uc = SkipReviewTaskUseCase(reviewTaskRepository: repo);
    await uc.execute(11);
    await uc.executeBatch([4, 5]);
    expect(repo.lastSkipId, 11);
    expect(repo.lastSkipIds, [4, 5]);
  });

  test(
    'GetHomeTasksUseCase 会聚合今日/逾期/统计数据，并按当天范围调用 getTaskStatsInRange',
    () async {
      final repo = _FakeReviewTaskRepository();
      final uc = GetHomeTasksUseCase(reviewTaskRepository: repo);
      final target = DateTime(2026, 2, 25, 8, 0);
      final result = await uc.execute(date: target);
      expect(result.todayPending.length, 1);
      expect(result.overduePending.length, 0);
      expect(result.completedCount, 2);
      expect(result.totalCount, 5);
      expect(repo.lastStatsStart, DateTime(2026, 2, 25));
      expect(repo.lastStatsEnd, DateTime(2026, 2, 26));
    },
  );
}
