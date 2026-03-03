/// 文件用途：复习任务仓储实现（ReviewTaskRepositoryImpl）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../../core/utils/ebbinghaus_utils.dart';
import '../../domain/entities/review_task.dart';
import '../../domain/entities/task_day_stats.dart';
import '../../domain/entities/task_timeline.dart';
import '../../domain/repositories/review_task_repository.dart';
import '../models/review_task_with_item_model.dart';
import '../database/daos/review_task_dao.dart';
import '../database/daos/learning_subtask_dao.dart';
import '../database/database.dart';
import '../sync/sync_log_writer.dart';

/// 复习任务仓储实现。
class ReviewTaskRepositoryImpl implements ReviewTaskRepository {
  /// 构造函数。
  ///
  /// 参数：
  /// - [dao] 复习任务 DAO。
  /// 异常：无。
  ReviewTaskRepositoryImpl({required this.dao, SyncLogWriter? syncLogWriter})
    : _sync = syncLogWriter,
      _learningSubtaskDao = null;

  /// 构造函数（带子任务 DAO，用于列表摘要展示）。
  ///
  /// 说明：为避免 UI 层 N+1 查询，仓储层在返回 ReviewTaskViewEntity 时可批量补齐 subtaskCount。
  ReviewTaskRepositoryImpl.withSubtasks({
    required this.dao,
    required LearningSubtaskDao learningSubtaskDao,
    SyncLogWriter? syncLogWriter,
  }) : _sync = syncLogWriter,
       _learningSubtaskDao = learningSubtaskDao;

  final ReviewTaskDao dao;
  final SyncLogWriter? _sync;
  final LearningSubtaskDao? _learningSubtaskDao;

  static const Uuid _uuid = Uuid();

  @override
  Future<ReviewTaskEntity> create(ReviewTaskEntity task) async {
    final now = DateTime.now();
    final ensuredUuid = task.uuid.trim().isEmpty ? _uuid.v4() : task.uuid.trim();
    // 性能优化（v10）：occurredAt 落地列用于任务中心时间线排序与游标分页。
    // 这里在写入口统一维护口径，避免查询阶段计算导致索引失效。
    final occurredAt = switch (task.status) {
      ReviewTaskStatus.pending => task.scheduledDate,
      ReviewTaskStatus.done => task.completedAt ?? task.scheduledDate,
      ReviewTaskStatus.skipped => task.skippedAt ?? task.scheduledDate,
    };
    final id = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: Value(ensuredUuid),
        learningItemId: task.learningItemId,
        reviewRound: task.reviewRound,
        scheduledDate: task.scheduledDate,
        occurredAt: Value(occurredAt),
        status: Value(task.status.toDbValue()),
        completedAt: Value(task.completedAt),
        skippedAt: Value(task.skippedAt),
        createdAt: Value(task.createdAt),
        updatedAt: Value(now),
      ),
    );

    final saved = task.copyWith(id: id, uuid: ensuredUuid, updatedAt: now);

    final sync = _sync;
    if (sync != null) {
      final ts = now.millisecondsSinceEpoch;
      final origin = await sync.resolveOriginKey(
        entityType: 'review_task',
        localEntityId: id,
        appliedAtMs: ts,
      );
      final learningOrigin = await sync.resolveOriginKey(
        entityType: 'learning_item',
        localEntityId: task.learningItemId,
        appliedAtMs: ts,
      );
      await sync.logEvent(
        origin: origin,
        entityType: 'review_task',
        operation: 'create',
        data: {
          'learning_origin_device_id': learningOrigin.deviceId,
          'learning_origin_entity_id': learningOrigin.entityId,
          'review_round': task.reviewRound,
          'scheduled_date': task.scheduledDate.toIso8601String(),
          'occurred_at': occurredAt.toIso8601String(),
          'status': task.status.toDbValue(),
          'completed_at': task.completedAt?.toIso8601String(),
          'skipped_at': task.skippedAt?.toIso8601String(),
          'created_at': task.createdAt.toIso8601String(),
          'updated_at': now.toIso8601String(),
        },
        timestampMs: ts,
      );
    }

    return saved;
  }

  @override
  Future<List<ReviewTaskEntity>> createBatch(
    List<ReviewTaskEntity> tasks,
  ) async {
    // 规格增强：默认复习计划可扩展至 10 轮，逐条插入以获得 ID，便于后续扩展（如按任务调度通知）。
    final saved = <ReviewTaskEntity>[];
    for (final task in tasks) {
      saved.add(await create(task));
    }
    return saved;
  }

  @override
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks() async {
    final rows = await dao.getOverdueTasksWithItem();
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTodayPendingTasks() async {
    final rows = await dao.getTodayPendingTasksWithItem();
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTodayCompletedTasks() async {
    final rows = await dao.getTodayCompletedTasksWithItem();
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTodaySkippedTasks() async {
    final rows = await dao.getTodaySkippedTasksWithItem();
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date) async {
    final rows = await dao.getTasksByDateWithItem(date);
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<void> completeTask(int id) async {
    await dao.completeTaskWithRecord(id);
    await _logTaskUpdateById(id);
  }

  @override
  Future<void> skipTask(int id) async {
    await dao.skipTaskWithRecord(id);
    await _logTaskUpdateById(id);
  }

  @override
  Future<void> completeTasks(List<int> ids) async {
    await dao.completeTasksWithRecords(ids);
    for (final id in ids) {
      await _logTaskUpdateById(id);
    }
  }

  @override
  Future<void> skipTasks(List<int> ids) async {
    await dao.skipTasksWithRecords(ids);
    for (final id in ids) {
      await _logTaskUpdateById(id);
    }
  }

  @override
  Future<void> undoTaskStatus(int id) async {
    await dao.undoTaskStatusWithRecord(id);
    await _logTaskUpdateById(id);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getReviewPlan(int learningItemId) async {
    final rows = await dao.getReviewPlanWithItem(learningItemId);
    final list = await _mapWithSubtaskCounts(rows);
    list.sort((a, b) => a.reviewRound.compareTo(b.reviewRound));
    return list;
  }

  @override
  Future<void> adjustReviewDate({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
  }) async {
    // 规格增强：已停用学习内容禁止任何操作。
    final itemRow =
        await (dao.db.select(dao.db.learningItems)
              ..where((t) => t.id.equals(learningItemId)))
            .getSingleOrNull();
    if (itemRow == null) {
      throw StateError('学习内容不存在（id=$learningItemId）');
    }
    if (itemRow.isDeleted) {
      throw StateError('学习内容已停用，无法调整计划');
    }

    final taskRow = await dao.getTaskByLearningItemAndRound(
      learningItemId,
      reviewRound,
    );
    if (taskRow == null) {
      throw StateError(
        '复习任务不存在（learningItemId=$learningItemId, reviewRound=$reviewRound）',
      );
    }

    final updated = await dao.updateScheduledDateByLearningItemAndRound(
      learningItemId: learningItemId,
      reviewRound: reviewRound,
      scheduledDate: scheduledDate,
    );
    if (updated <= 0) {
      throw StateError('调整计划失败（learningItemId=$learningItemId, round=$reviewRound）');
    }
    await _logTaskUpdateById(taskRow.id);
  }

  @override
  Future<void> addReviewRound(int learningItemId) async {
    // 规格增强：已停用学习内容禁止任何操作。
    final itemRow =
        await (dao.db.select(dao.db.learningItems)
              ..where((t) => t.id.equals(learningItemId)))
            .getSingleOrNull();
    if (itemRow == null) {
      throw StateError('学习内容不存在（id=$learningItemId）');
    }
    if (itemRow.isDeleted) {
      throw StateError('学习内容已停用，无法增加轮次');
    }

    final maxRound = await dao.getMaxReviewRound(learningItemId);
    if (maxRound >= 10) {
      throw StateError('已达到最大轮次（10）');
    }

    final tasks = await dao.getTasksByLearningItemId(learningItemId);
    if (tasks.isEmpty) {
      throw StateError('学习内容缺少复习任务，无法增加轮次（id=$learningItemId）');
    }
    tasks.sort((a, b) => a.reviewRound.compareTo(b.reviewRound));
    final last = tasks.last;

    final nextRound = maxRound + 1;
    final interval = EbbinghausUtils.defaultIntervalsDays[nextRound - 1];
    final nextDate = last.scheduledDate.add(Duration(days: interval));

    final now = DateTime.now();
    await create(
      ReviewTaskEntity(
        uuid: _uuid.v4(),
        learningItemId: learningItemId,
        reviewRound: nextRound,
        scheduledDate: nextDate,
        status: ReviewTaskStatus.pending,
        createdAt: now,
        updatedAt: now,
      ),
    );
  }

  @override
  Future<(int completed, int total)> getTaskStats(DateTime date) {
    return dao.getTaskStats(date);
  }

  @override
  Future<Map<DateTime, TaskDayStats>> getMonthlyTaskStats(int year, int month) {
    return dao.getMonthlyTaskStats(year, month);
  }

  @override
  Future<List<ReviewTaskViewEntity>> getTasksInRange(
    DateTime start,
    DateTime end,
  ) async {
    final rows = await dao.getTasksInRange(start, end);
    return _mapWithSubtaskCounts(rows);
  }

  @override
  Future<int> getConsecutiveCompletedDays({DateTime? today}) {
    return dao.getConsecutiveCompletedDays(today: today);
  }

  @override
  Future<(int completed, int total)> getTaskStatsInRange(
    DateTime start,
    DateTime end,
  ) {
    return dao.getTaskStatsInRange(start, end);
  }

  @override
  Future<List<ReviewTaskEntity>> getAllTasks() async {
    final rows = await dao.getAllTasks();
    return rows
        .map(
          (row) => ReviewTaskEntity(
            id: row.id,
            uuid: row.uuid,
            learningItemId: row.learningItemId,
            reviewRound: row.reviewRound,
            scheduledDate: row.scheduledDate,
            status: ReviewTaskStatusX.fromDbValue(row.status),
            completedAt: row.completedAt,
            skippedAt: row.skippedAt,
            createdAt: row.createdAt,
            updatedAt: row.updatedAt,
            isMockData: row.isMockData,
          ),
        )
        .toList();
  }

  @override
  Future<(int all, int pending, int done, int skipped)>
  getGlobalTaskStatusCounts() {
    return dao.getGlobalTaskStatusCounts();
  }

  @override
  Future<TaskTimelinePageEntity> getTaskTimelinePage({
    ReviewTaskStatus? status,
    TaskTimelineCursorEntity? cursor,
    int limit = 20,
  }) async {
    // 取 limit+1 判断是否还有下一页，避免 UI 误判“已到底”。
    final fetchSize = limit + 1;
    final rows = await dao.getTaskTimelinePageWithItem(
      status: status?.toDbValue(),
      cursorOccurredAt: cursor?.occurredAt,
      cursorTaskId: cursor?.taskId,
      limit: fetchSize,
    );

    final hasMore = rows.length > limit;
    final pageRows = hasMore ? rows.take(limit).toList() : rows;

    final subtaskDao = _learningSubtaskDao;
    final counts =
        subtaskDao == null
            ? const <int, int>{}
            : await subtaskDao.getCountsByLearningItemIds(
                pageRows.map((e) => e.model.item.id).toSet().toList(),
              );

    final items =
        pageRows
            .map(
              (r) => ReviewTaskTimelineItemEntity(
                task: _toViewEntity(
                  r.model,
                  subtaskCount: counts[r.model.item.id] ?? 0,
                ),
                occurredAt: r.occurredAt,
              ),
            )
            .toList();

    TaskTimelineCursorEntity? nextCursor;
    if (hasMore && items.isNotEmpty) {
      final last = items.last;
      nextCursor = TaskTimelineCursorEntity(
        occurredAt: last.occurredAt,
        taskId: last.task.taskId,
      );
    }

    return TaskTimelinePageEntity(items: items, nextCursor: nextCursor);
  }

  ReviewTaskViewEntity _toViewEntity(
    ReviewTaskWithItemModel model, {
    required int subtaskCount,
  }) {
    final task = model.task;
    final item = model.item;

    return ReviewTaskViewEntity(
      taskId: task.id,
      learningItemId: item.id,
      title: item.title,
      description: item.description,
      note: item.note,
      subtaskCount: subtaskCount,
      tags: _parseTags(item.tags),
      reviewRound: task.reviewRound,
      scheduledDate: task.scheduledDate,
      status: ReviewTaskStatusX.fromDbValue(task.status),
      completedAt: task.completedAt,
      skippedAt: task.skippedAt,
      isDeleted: item.isDeleted,
      deletedAt: item.deletedAt,
    );
  }

  Future<List<ReviewTaskViewEntity>> _mapWithSubtaskCounts(
    List<ReviewTaskWithItemModel> models,
  ) async {
    if (models.isEmpty) return const <ReviewTaskViewEntity>[];

    final subtaskDao = _learningSubtaskDao;
    final counts =
        subtaskDao == null
            ? const <int, int>{}
            : await subtaskDao.getCountsByLearningItemIds(
                models.map((e) => e.item.id).toSet().toList(),
              );

    return models
        .map(
          (m) => _toViewEntity(
            m,
            subtaskCount: counts[m.item.id] ?? 0,
          ),
        )
        .toList();
  }

  @override
  Future<void> removeLatestReviewRound(int learningItemId) async {
    // 规格增强：已停用学习内容禁止任何操作。
    final itemRow =
        await (dao.db.select(dao.db.learningItems)
              ..where((t) => t.id.equals(learningItemId)))
            .getSingleOrNull();
    if (itemRow == null) {
      throw StateError('学习内容不存在（id=$learningItemId）');
    }
    if (itemRow.isDeleted) {
      throw StateError('学习内容已停用，无法减少轮次');
    }

    final maxRound = await dao.getMaxReviewRound(learningItemId);
    if (maxRound <= 1) {
      throw StateError('已达到最小轮次（1）');
    }

    final taskRow = await dao.getTaskByLearningItemAndRound(
      learningItemId,
      maxRound,
    );
    if (taskRow == null) {
      throw StateError(
        '复习任务不存在（learningItemId=$learningItemId, reviewRound=$maxRound）',
      );
    }

    final now = DateTime.now();
    final ts = now.millisecondsSinceEpoch;

    // v3.1：Mock 数据不参与同步，因此不写入 delete 日志。
    if (!taskRow.isMockData) {
      await _sync?.logDelete(
        entityType: 'review_task',
        localEntityId: taskRow.id,
        timestampMs: ts,
      );
    }

    final deleted = await dao.deleteReviewTaskByRound(learningItemId, maxRound);
    if (deleted <= 0) {
      throw StateError('减少轮次失败（learningItemId=$learningItemId, round=$maxRound）');
    }
  }

  List<String> _parseTags(String tagsJson) {
    try {
      final decoded = jsonDecode(tagsJson);
      if (decoded is List) {
        return decoded
            .whereType<String>()
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
      }
      return const [];
    } catch (_) {
      return const [];
    }
  }

  Future<void> _logTaskUpdateById(int id) async {
    final sync = _sync;
    if (sync == null) return;

    final row = await dao.getReviewTaskById(id);
    if (row == null) return;

    // v3.1：Mock 数据不参与同步，因此不写入 update 日志。
    if (row.isMockData) return;

    final ts = DateTime.now().millisecondsSinceEpoch;
    final origin = await sync.resolveOriginKey(
      entityType: 'review_task',
      localEntityId: row.id,
      appliedAtMs: ts,
    );
    final learningOrigin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: row.learningItemId,
      appliedAtMs: ts,
    );

    await sync.logEvent(
      origin: origin,
      entityType: 'review_task',
      operation: 'update',
      data: {
        'learning_origin_device_id': learningOrigin.deviceId,
        'learning_origin_entity_id': learningOrigin.entityId,
        'review_round': row.reviewRound,
        'scheduled_date': row.scheduledDate.toIso8601String(),
        // 性能优化（v10）：occurred_at 为落地列，优先读列值；缺失时按口径回推，避免同步后丢失排序依据。
        'occurred_at':
            (row.occurredAt ??
                    switch (row.status) {
                      'pending' => row.scheduledDate,
                      'done' => row.completedAt ?? row.scheduledDate,
                      'skipped' => row.skippedAt ?? row.scheduledDate,
                      _ => row.scheduledDate,
                    })
                .toIso8601String(),
        'status': row.status,
        'completed_at': row.completedAt?.toIso8601String(),
        'skipped_at': row.skippedAt?.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': (row.updatedAt ?? row.createdAt).toIso8601String(),
      },
      timestampMs: ts,
    );
  }
}
