// 文件用途：ImportLearningItemsUseCase 单元测试（空输入、分批处理、失败不中断与错误文案）。
// 作者：Codex
// 创建日期：2026-02-26

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/learning_subtask.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/tag_usage_stat.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/entities/task_timeline.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/repositories/learning_subtask_repository.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/import_learning_items_usecase.dart';

void main() {
  test('execute: items 为空时直接返回 0/0/[]', () async {
    final usecase = ImportLearningItemsUseCase(
      create: CreateLearningItemUseCase(
        learningItemRepository: _FakeLearningItemRepository(),
        learningSubtaskRepository: _FakeLearningSubtaskRepository(),
        reviewTaskRepository: _FakeReviewTaskRepository(),
      ),
    );

    final result = await usecase.execute(const []);
    expect(result.successCount, 0);
    expect(result.failedCount, 0);
    expect(result.errors, isEmpty);
  });

  test('execute: 单条失败不中断，错误文案会包含标题（若标题为空则不包裹）', () async {
    final learningRepo = _FakeLearningItemRepository(failTitles: {'bad', ''});
    final usecase = ImportLearningItemsUseCase(
      create: CreateLearningItemUseCase(
        learningItemRepository: learningRepo,
        learningSubtaskRepository: _FakeLearningSubtaskRepository(),
        reviewTaskRepository: _FakeReviewTaskRepository(),
      ),
    );

    final items = [
      CreateLearningItemParams(title: 'ok'),
      CreateLearningItemParams(title: 'bad'),
      CreateLearningItemParams(title: '   '), // trim 后为空
    ];

    final result = await usecase.execute(items);
    expect(result.successCount, 1);
    expect(result.failedCount, 2);
    expect(result.errors.length, 2);

    expect(result.errors.any((e) => e.contains('「bad」导入失败')), isTrue);
    expect(result.errors.any((e) => e.startsWith('导入失败：')), isTrue);
  });
}

/// 假学习内容仓储：用于控制 CreateLearningItemUseCase 在特定标题下抛错。
class _FakeLearningItemRepository implements LearningItemRepository {
  _FakeLearningItemRepository({Set<String>? failTitles})
    : _failTitles = failTitles ?? <String>{};

  final Set<String> _failTitles;
  int _nextId = 1;

  @override
  Future<LearningItemEntity> create(LearningItemEntity item) async {
    final title = item.title.trim();
    if (_failTitles.contains(title)) {
      throw StateError('模拟失败：$title');
    }
    final saved = item.copyWith(id: _nextId++);
    return saved;
  }

  @override
  Future<LearningItemEntity> update(LearningItemEntity item) =>
      throw UnimplementedError();

  @override
  Future<void> delete(int id) => throw UnimplementedError();

  @override
  Future<LearningItemEntity?> getById(int id) => throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getAll() => throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getByDate(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<List<LearningItemEntity>> getByTag(String tag) =>
      throw UnimplementedError();

  @override
  Future<List<String>> getAllTags() => throw UnimplementedError();

  @override
  Future<List<TagUsageStatEntity>> getTagUsageRanking({
    required DateTime recentSince,
    int? limit,
  }) => throw UnimplementedError();

  @override
  Future<Map<String, int>> getTagDistribution() => throw UnimplementedError();

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

/// 假子任务仓储：用于满足 CreateLearningItemUseCase 的依赖（本测试不关注子任务写入）。
class _FakeLearningSubtaskRepository implements LearningSubtaskRepository {
  @override
  Future<LearningSubtaskEntity> create(LearningSubtaskEntity subtask) async {
    return subtask.copyWith(id: 1);
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

/// 假复习任务仓储：仅实现 createBatch 以满足 CreateLearningItemUseCase。
class _FakeReviewTaskRepository implements ReviewTaskRepository {
  int _nextId = 1;

  @override
  Future<List<ReviewTaskEntity>> createBatch(
    List<ReviewTaskEntity> tasks,
  ) async {
    return tasks.map((t) => t.copyWith(id: _nextId++)).toList();
  }

  @override
  Future<ReviewTaskEntity> create(ReviewTaskEntity task) =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTodayPendingTasks() =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks() =>
      throw UnimplementedError();

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) =>
      throw UnimplementedError();

  @override
  Future<void> completeTask(int id) => throw UnimplementedError();

  @override
  Future<void> skipTask(int id) => throw UnimplementedError();

  @override
  Future<void> completeTasks(List<int> ids) => throw UnimplementedError();

  @override
  Future<void> skipTasks(List<int> ids) => throw UnimplementedError();

  @override
  Future<(int completed, int total)> getTaskStats(DateTime date) =>
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
