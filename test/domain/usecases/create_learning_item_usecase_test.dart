// 文件用途：CreateLearningItemUseCase 单元测试（参数清洗与复习任务生成）。
// 作者：Codex
// 创建日期：2026-02-25

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/review_config.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/learning_subtask.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/entities/task_timeline.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/repositories/learning_subtask_repository.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';

/// 用于验证 CreateLearningItemUseCase 的学习内容入库行为。
class _FakeLearningItemRepository implements LearningItemRepository {
  LearningItemEntity? lastCreated;

  @override
  Future<LearningItemEntity> create(LearningItemEntity item) async {
    lastCreated = item;
    return item.copyWith(id: 42);
  }

  @override
  Future<void> delete(int id) => throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getAll() => throw UnimplementedError();

  @override
  Future<List<String>> getAllTags() => throw UnimplementedError();

  @override
  Future<Map<String, int>> getTagDistribution() => throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getByDate(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<LearningItemEntity?> getById(int id) => throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getByTag(String tag) =>
      throw UnimplementedError();

  @override
  Future<LearningItemEntity> update(LearningItemEntity item) =>
      throw UnimplementedError();

  @override
  Future<void> updateNote({required int id, required String? note}) =>
      throw UnimplementedError();

  @override
  Future<void> updateDescription({
    required int id,
    required String? description,
  }) => throw UnimplementedError();

  @override
  Future<void> deactivate(int id) => throw UnimplementedError();
}

/// 用于满足 CreateLearningItemUseCase 的子任务写入依赖（本测试不关注子任务细节）。
class _FakeLearningSubtaskRepository implements LearningSubtaskRepository {
  final List<LearningSubtaskEntity> created = [];

  @override
  Future<LearningSubtaskEntity> create(LearningSubtaskEntity subtask) async {
    created.add(subtask);
    return subtask.copyWith(id: created.length);
  }

  @override
  Future<void> delete(int id) => throw UnimplementedError();

  @override
  Future<List<LearningSubtaskEntity>> getByLearningItemId(int learningItemId) =>
      throw UnimplementedError();

  @override
  Future<List<LearningSubtaskEntity>> getByLearningItemIds(
    List<int> learningItemIds,
  ) => throw UnimplementedError();

  @override
  Future<void> reorder(int learningItemId, List<int> subtaskIds) =>
      throw UnimplementedError();

  @override
  Future<LearningSubtaskEntity> update(LearningSubtaskEntity subtask) =>
      throw UnimplementedError();
}

/// 用于验证 CreateLearningItemUseCase 的复习任务批量入库行为。
class _FakeReviewTaskRepository implements ReviewTaskRepository {
  List<ReviewTaskEntity>? lastBatch;

  @override
  Future<List<ReviewTaskEntity>> createBatch(
    List<ReviewTaskEntity> tasks,
  ) async {
    lastBatch = tasks;
    // 模拟数据库回写 ID。
    return [
      for (var i = 0; i < tasks.length; i++) tasks[i].copyWith(id: i + 1),
    ];
  }

  @override
  Future<ReviewTaskEntity> create(ReviewTaskEntity task) =>
      throw UnimplementedError();

  @override
  Future<void> completeTask(int id) => throw UnimplementedError();

  @override
  Future<void> completeTasks(List<int> ids) => throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks() =>
      throw UnimplementedError();

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
  ) => throw UnimplementedError();

  @override
  Future<List<ReviewTaskEntity>> getAllTasks() => throw UnimplementedError();

  @override
  Future<(int completed, int total)> getTaskStats(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTodayPendingTasks() =>
      throw UnimplementedError();

  @override
  Future<void> skipTask(int id) => throw UnimplementedError();

  @override
  Future<void> skipTasks(List<int> ids) => throw UnimplementedError();

  // 以下为当前测试不涉及的方法（为满足接口编译要求）。
  @override
  Future<List<ReviewTaskViewEntity>> getTodayCompletedTasks() =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTodaySkippedTasks() =>
      throw UnimplementedError();

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
  test('会清洗 title/note/tags，并按默认间隔生成复习任务', () async {
    final learningRepo = _FakeLearningItemRepository();
    final subtaskRepo = _FakeLearningSubtaskRepository();
    final taskRepo = _FakeReviewTaskRepository();
    final useCase = CreateLearningItemUseCase(
      learningItemRepository: learningRepo,
      learningSubtaskRepository: subtaskRepo,
      reviewTaskRepository: taskRepo,
    );

    final result = await useCase.execute(
      CreateLearningItemParams(
        title: '  标题  ',
        note: '   ',
        tags: const ['  a ', '', ' b', '  '],
        learningDate: DateTime(2026, 2, 25, 13, 30),
      ),
    );

    // 1) 学习内容清洗：title trim；空 note 变为 null；tags trim + 去空。
    final created = learningRepo.lastCreated!;
    expect(created.title, '标题');
    expect(created.note, null);
    expect(created.tags, const ['a', 'b']);
    expect(subtaskRepo.created, isEmpty);

    // 2) learningDate 会截断到当天零点。
    expect(created.learningDate, DateTime(2026, 2, 25));

    // 3) 生成的任务数量与轮次正确，且 scheduledDate 按 ReviewConfig 计算。
    expect(result.generatedTasks.length, ReviewConfig.defaultIntervals.length);
    expect(taskRepo.lastBatch!.length, ReviewConfig.defaultIntervals.length);

    for (var i = 0; i < result.generatedTasks.length; i++) {
      final round = i + 1;
      final task = taskRepo.lastBatch![i];
      expect(task.learningItemId, 42);
      expect(task.reviewRound, round);
      expect(task.status, ReviewTaskStatus.pending);
      expect(
        task.scheduledDate,
        ReviewConfig.calculateReviewDate(DateTime(2026, 2, 25), round),
      );
    }
  });
}
