/// 文件用途：ReviewTaskDao - 复习任务数据库访问封装（Drift）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../../../core/utils/date_utils.dart';
import '../../../domain/entities/task_day_stats.dart';
import '../../models/review_task_with_item_model.dart';
import '../../models/review_task_timeline_model.dart';
import '../database.dart';

/// 复习任务 DAO。
///
/// 说明：封装复习任务相关的 CRUD、状态更新及常用查询（今日/逾期/统计等）。
class ReviewTaskDao {
  /// 构造函数。
  ///
  /// 参数：
  /// - [db] 数据库实例。
  /// 异常：无。
  ReviewTaskDao(this.db);

  final AppDatabase db;

  static const Uuid _uuid = Uuid();

  /// 插入复习任务。
  ///
  /// 返回值：新记录 ID。
  /// 异常：数据库写入失败时可能抛出异常。
  Future<int> insertReviewTask(ReviewTasksCompanion companion) {
    // 性能优化（v10）：为 occurredAt 落地列兜底赋值，避免调用方漏传导致时间线排序异常。
    final ensured = _ensureOccurredAtForInsert(companion);
    return db.into(db.reviewTasks).insert(ensured);
  }

  /// 批量插入复习任务。
  ///
  /// 返回值：Future（无返回值）。
  /// 异常：数据库写入失败时可能抛出异常。
  Future<void> insertReviewTasks(List<ReviewTasksCompanion> companions) async {
    // 性能优化（v10）：批量插入同样需要兜底 occurredAt，避免时间线查询出现 NULL 排序问题。
    final ensured = companions.map(_ensureOccurredAtForInsert).toList();
    await db.batch((batch) {
      batch.insertAll(db.reviewTasks, ensured);
    });
  }

  /// 更新复习任务。
  ///
  /// 返回值：是否更新成功。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<bool> updateReviewTask(ReviewTask task) {
    return db.update(db.reviewTasks).replace(task);
  }

  /// 按轮次删除复习任务（物理删除）。
  ///
  /// 说明：
  /// - 用于“减少复习轮次”能力（删除最大轮次对应的 review_task）
  /// - review_records 对 review_tasks 具有外键级联，删除任务会同步删除行为历史
  ///
  /// 参数：
  /// - [learningItemId] 学习内容 ID
  /// - [reviewRound] 目标轮次
  /// 返回值：删除行数。
  Future<int> deleteReviewTaskByRound(int learningItemId, int reviewRound) {
    return (db.delete(db.reviewTasks)..where(
          (t) =>
              t.learningItemId.equals(learningItemId) &
              t.reviewRound.equals(reviewRound),
        ))
        .go();
  }

  /// 更新任务状态。
  ///
  /// 参数：
  /// - [id] 任务 ID
  /// - [status] 状态（pending/done/skipped）
  /// - [completedAt] 完成时间（done 时建议传入）
  /// - [skippedAt] 跳过时间（skipped 时建议传入）
  /// 返回值：更新行数。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<int> updateTaskStatus(
    int id,
    String status, {
    DateTime? completedAt,
    DateTime? skippedAt,
  }) async {
    // 规格增强：已停用学习内容禁止任何任务状态变更。
    await _assertLearningItemActiveByTaskIds([id]);
    final now = DateTime.now();

    // 性能优化（v10）：维护 occurredAt 落地列，避免时间线分页查询中反复计算 CASE/COALESCE。
    //
    // 说明：
    // - done/skipped：优先使用传入的时间戳（通常为 now），保证“发生时间”与记录一致
    // - pending：occurredAt 应回到 scheduledDate（需读取一次当前任务行）
    DateTime? occurredAt;
    if (status == 'done') {
      occurredAt = completedAt ?? now;
    } else if (status == 'skipped') {
      occurredAt = skippedAt ?? now;
    } else if (status == 'pending') {
      final row = await getReviewTaskById(id);
      occurredAt = row?.scheduledDate;
    }

    return (db.update(db.reviewTasks)..where((t) => t.id.equals(id))).write(
      ReviewTasksCompanion(
        status: Value(status),
        completedAt: Value(completedAt),
        skippedAt: Value(skippedAt),
        occurredAt: Value(occurredAt),
        updatedAt: Value(now),
      ),
    );
  }

  /// 标记任务完成（done）并写入一条复习记录（review_records）。
  ///
  /// 说明：
  /// - 使用事务确保“任务状态 + 记录”一致
  /// - occurredAt 使用当前时间（与 completedAt 一致）
  Future<void> completeTaskWithRecord(int id) async {
    final now = DateTime.now();
    await db.transaction(() async {
      await updateTaskStatus(id, 'done', completedAt: now);
      await _insertRecord(reviewTaskId: id, action: 'done', occurredAt: now);
    });
  }

  /// 标记任务跳过（skipped）并写入一条复习记录（review_records）。
  Future<void> skipTaskWithRecord(int id) async {
    final now = DateTime.now();
    await db.transaction(() async {
      await updateTaskStatus(id, 'skipped', skippedAt: now);
      await _insertRecord(reviewTaskId: id, action: 'skipped', occurredAt: now);
    });
  }

  /// 批量标记任务完成并写入复习记录（review_records）。
  ///
  /// 说明：在同一事务内执行，避免部分成功导致口径不一致。
  Future<void> completeTasksWithRecords(List<int> ids) async {
    if (ids.isEmpty) return;
    final now = DateTime.now();
    await db.transaction(() async {
      await updateTaskStatusBatch(ids, 'done', timestamp: now);
      await _insertRecords(ids, action: 'done', occurredAt: now);
    });
  }

  /// 批量标记任务跳过并写入复习记录（review_records）。
  Future<void> skipTasksWithRecords(List<int> ids) async {
    if (ids.isEmpty) return;
    final now = DateTime.now();
    await db.transaction(() async {
      await updateTaskStatusBatch(ids, 'skipped', timestamp: now);
      await _insertRecords(ids, action: 'skipped', occurredAt: now);
    });
  }

  /// 批量更新任务状态。
  ///
  /// 参数：
  /// - [ids] 任务 ID 列表
  /// - [status] 目标状态
  /// - [timestamp] 对应状态的时间戳（done 使用 completedAt，skipped 使用 skippedAt）
  /// 返回值：更新行数。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<int> updateTaskStatusBatch(
    List<int> ids,
    String status, {
    DateTime? timestamp,
  }) async {
    if (ids.isEmpty) return Future.value(0);
    // 规格增强：已停用学习内容禁止任何任务状态变更。
    await _assertLearningItemActiveByTaskIds(ids);

    final now = DateTime.now();
    // 性能优化（v10）：批量完成/跳过时直接写入 occurredAt，避免时间线查询回退到计算逻辑。
    final occurredAt = (status == 'done' || status == 'skipped')
        ? (timestamp ?? now)
        : null;
    final companion = ReviewTasksCompanion(
      status: Value(status),
      completedAt: Value(status == 'done' ? timestamp : null),
      skippedAt: Value(status == 'skipped' ? timestamp : null),
      occurredAt: Value(occurredAt),
      updatedAt: Value(now),
    );

    return (db.update(
      db.reviewTasks,
    )..where((t) => t.id.isIn(ids))).write(companion);
  }

  /// 根据 ID 查询复习任务。
  ///
  /// 返回值：复习任务或 null。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<ReviewTask?> getReviewTaskById(int id) {
    return (db.select(
      db.reviewTasks,
    )..where((t) => t.id.equals(id))).getSingleOrNull();
  }

  /// 查询指定日期的所有复习任务（包含完成/跳过）。
  ///
  /// 参数：
  /// - [date] 目标日期（按年月日）。
  /// 返回值：任务列表（按 scheduledDate 升序）。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<ReviewTask>> getTasksByDate(DateTime date) {
    final start = DateTime(date.year, date.month, date.day);
    final end = start.add(const Duration(days: 1));
    return (db.select(db.reviewTasks)
          ..where((t) => t.scheduledDate.isBiggerOrEqualValue(start))
          ..where((t) => t.scheduledDate.isSmallerThanValue(end))
          ..orderBy([(t) => OrderingTerm.asc(t.scheduledDate)]))
        .get();
  }

  /// 查询指定日期的所有复习任务（join 学习内容，用于展示/小组件）。
  ///
  /// 说明：包含 pending/done/skipped。
  Future<List<ReviewTaskWithItemModel>> getTasksByDateWithItem(
    DateTime date,
  ) async {
    final start = DateTime(date.year, date.month, date.day);
    final end = start.add(const Duration(days: 1));

    final task = db.reviewTasks;
    final item = db.learningItems;

    final query =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(item.isDeleted.equals(false))
          ..where(task.scheduledDate.isBiggerOrEqualValue(start))
          ..where(task.scheduledDate.isSmallerThanValue(end))
          ..orderBy([
            OrderingTerm.asc(task.status),
            OrderingTerm.asc(task.reviewRound),
          ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  /// 查询今日待复习任务（pending，scheduledDate=今日）。
  Future<List<ReviewTaskWithItemModel>> getTodayPendingTasksWithItem() {
    return _getTasksWithItem(
      date: DateTime.now(),
      onlyPending: true,
      onlyOverdue: false,
    );
  }

  /// 查询逾期任务（pending，scheduledDate < 今日）。
  Future<List<ReviewTaskWithItemModel>> getOverdueTasksWithItem() {
    return _getTasksWithItem(
      date: DateTime.now(),
      onlyPending: true,
      onlyOverdue: true,
    );
  }

  /// 查询今日已完成任务（done，completedAt=今日）。
  ///
  /// 说明：按 completedAt 的自然日口径统计，不受 scheduledDate 影响。
  Future<List<ReviewTaskWithItemModel>> getTodayCompletedTasksWithItem({
    DateTime? today,
  }) async {
    final now = today ?? DateTime.now();
    final start = YikeDateUtils.atStartOfDay(now);
    final end = start.add(const Duration(days: 1));

    final task = db.reviewTasks;
    final item = db.learningItems;

    final query =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(item.isDeleted.equals(false))
          ..where(task.status.equals('done'))
          ..where(task.completedAt.isBiggerOrEqualValue(start))
          ..where(task.completedAt.isSmallerThanValue(end))
          ..orderBy([
            OrderingTerm.desc(task.completedAt),
            OrderingTerm.desc(task.id),
          ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  /// 查询今日已跳过任务（skipped，skippedAt=今日）。
  ///
  /// 说明：按 skippedAt 的自然日口径统计，不受 scheduledDate 影响。
  Future<List<ReviewTaskWithItemModel>> getTodaySkippedTasksWithItem({
    DateTime? today,
  }) async {
    final now = today ?? DateTime.now();
    final start = YikeDateUtils.atStartOfDay(now);
    final end = start.add(const Duration(days: 1));

    final task = db.reviewTasks;
    final item = db.learningItems;

    final query =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(item.isDeleted.equals(false))
          ..where(task.status.equals('skipped'))
          ..where(task.skippedAt.isBiggerOrEqualValue(start))
          ..where(task.skippedAt.isSmallerThanValue(end))
          ..orderBy([
            OrderingTerm.desc(task.skippedAt),
            OrderingTerm.desc(task.id),
          ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  /// 撤销任务状态（done/skipped → pending）。
  ///
  /// 规则：
  /// - status 设置为 pending
  /// - completedAt/skippedAt 清空（两者都清空，避免历史脏数据影响口径）
  /// 返回值：更新行数。
  Future<int> undoTaskStatus(int id) async {
    // 规格增强：已停用学习内容禁止任何任务状态变更。
    await _assertLearningItemActiveByTaskIds([id]);
    final now = DateTime.now();
    // 关键逻辑：撤销后 occurredAt 需要回到 scheduledDate，确保时间线排序口径与 pending 一致。
    const sql = '''
UPDATE review_tasks
SET status = 'pending',
    completed_at = NULL,
    skipped_at = NULL,
    occurred_at = scheduled_date,
    updated_at = ?
WHERE id = ?
''';
    return db.customUpdate(
      sql,
      variables: [Variable<DateTime>(now), Variable<int>(id)],
      updates: {db.reviewTasks},
    );
  }

  /// 撤销任务状态并写入一条复习记录（review_records）。
  ///
  /// 说明：撤销同样属于行为事件，便于审计与未来扩展。
  Future<void> undoTaskStatusWithRecord(int id) async {
    final now = DateTime.now();
    await db.transaction(() async {
      await undoTaskStatus(id);
      await _insertRecord(reviewTaskId: id, action: 'undo', occurredAt: now);
    });
  }

  /// 插入单条复习记录。
  Future<void> _insertRecord({
    required int reviewTaskId,
    required String action,
    required DateTime occurredAt,
  }) async {
    await db
        .into(db.reviewRecords)
        .insert(
          ReviewRecordsCompanion.insert(
            uuid: Value(_uuid.v4()),
            reviewTaskId: reviewTaskId,
            action: action,
            occurredAt: occurredAt,
            createdAt: Value(occurredAt),
          ),
        );
  }

  /// 批量插入复习记录（同一 action/occurredAt）。
  Future<void> _insertRecords(
    List<int> reviewTaskIds, {
    required String action,
    required DateTime occurredAt,
  }) async {
    final companions = reviewTaskIds
        .map(
          (id) => ReviewRecordsCompanion.insert(
            uuid: Value(_uuid.v4()),
            reviewTaskId: id,
            action: action,
            occurredAt: occurredAt,
            createdAt: Value(occurredAt),
          ),
        )
        .toList();

    await db.batch((batch) {
      batch.insertAll(db.reviewRecords, companions);
    });
  }

  /// 获取全量任务状态计数（用于任务中心筛选栏展示）。
  ///
  /// 返回值：(all, pending, done, skipped)。
  Future<(int all, int pending, int done, int skipped)>
  getGlobalTaskStatusCounts() async {
    const sql = '''
SELECT
  COUNT(*) AS all_count,
  SUM(CASE WHEN rt.status='pending' THEN 1 ELSE 0 END) AS pending_count,
  SUM(CASE WHEN rt.status='done' THEN 1 ELSE 0 END) AS done_count,
  SUM(CASE WHEN rt.status='skipped' THEN 1 ELSE 0 END) AS skipped_count
FROM review_tasks rt
INNER JOIN learning_items li ON li.id = rt.learning_item_id
WHERE li.is_deleted = 0
''';
    final row = await db
        .customSelect(sql, readsFrom: {db.reviewTasks, db.learningItems})
        .getSingle();
    return (
      row.read<int?>('all_count') ?? 0,
      row.read<int?>('pending_count') ?? 0,
      row.read<int?>('done_count') ?? 0,
      row.read<int?>('skipped_count') ?? 0,
    );
  }

  /// 按“发生时间”倒序获取任务时间线分页数据（用于任务中心）。
  ///
  /// 说明：
  /// - occurredAt 使用落地列 occurred_at（v10 性能优化），由应用层在写入口维护
  /// - 排序：occurredAt ASC, taskId ASC（稳定排序）
  /// - 游标：下一页取“当前页最后一条”，查询条件为 (occurredAt > cursor) OR (occurredAt = cursor AND taskId > cursorId)
  Future<List<ReviewTaskTimelineModel>> getTaskTimelinePageWithItem({
    String? status,
    DateTime? scheduledDateBefore,
    DateTime? scheduledDateOnOrAfter,
    DateTime? cursorOccurredAt,
    int? cursorTaskId,
    int limit = 20,
  }) async {
    final where = StringBuffer();
    final variables = <Variable>[];

    where.write('1=1');
    if (status != null) {
      where.write(' AND rt.status = ?');
      variables.add(Variable<String>(status));
    }
    if (scheduledDateBefore != null) {
      where.write(' AND rt.scheduled_date < ?');
      variables.add(Variable<DateTime>(scheduledDateBefore));
    }
    if (scheduledDateOnOrAfter != null) {
      where.write(' AND rt.scheduled_date >= ?');
      variables.add(Variable<DateTime>(scheduledDateOnOrAfter));
    }

    final cursorWhere = StringBuffer();
    final cursorVars = <Variable>[];
    if (cursorOccurredAt != null && cursorTaskId != null) {
      cursorWhere.write(
        'WHERE (t.occurred_at > ? OR (t.occurred_at = ? AND t."rt.id" > ?))',
      );
      cursorVars.add(Variable<DateTime>(cursorOccurredAt));
      cursorVars.add(Variable<DateTime>(cursorOccurredAt));
      cursorVars.add(Variable<int>(cursorTaskId));
    }

    final sql =
        '''
 SELECT * FROM (
  SELECT
    rt.id AS "rt.id",
    rt.uuid AS "rt.uuid",
    rt.learning_item_id AS "rt.learning_item_id",
    rt.review_round AS "rt.review_round",
    rt.scheduled_date AS "rt.scheduled_date",
    rt.occurred_at AS "rt.occurred_at",
    rt.status AS "rt.status",
    rt.completed_at AS "rt.completed_at",
    rt.skipped_at AS "rt.skipped_at",
    rt.created_at AS "rt.created_at",
    rt.updated_at AS "rt.updated_at",
    rt.is_mock_data AS "rt.is_mock_data",

    li.id AS "li.id",
    li.uuid AS "li.uuid",
    li.title AS "li.title",
    li.note AS "li.note",
     li.tags AS "li.tags",
     li.learning_date AS "li.learning_date",
     li.created_at AS "li.created_at",
     li.updated_at AS "li.updated_at",
     li.is_deleted AS "li.is_deleted",
     li.deleted_at AS "li.deleted_at",
     li.is_mock_data AS "li.is_mock_data",

     rt.occurred_at AS occurred_at
   FROM review_tasks rt
   INNER JOIN learning_items li ON li.id = rt.learning_item_id
   WHERE li.is_deleted = 0 AND ${where.toString()}
 ) t
 ${cursorWhere.toString()}
 ORDER BY t.occurred_at ASC, t."rt.id" ASC
 LIMIT ?
 ''';

    final rows = await db
        .customSelect(
          sql,
          variables: [...variables, ...cursorVars, Variable<int>(limit)],
          readsFrom: {db.reviewTasks, db.learningItems},
        )
        .get();

    return rows.map((row) {
      final task = db.reviewTasks.map(row.data, tablePrefix: 'rt');
      final item = db.learningItems.map(row.data, tablePrefix: 'li');
      // 保护：极端历史脏数据（occurred_at 未回填）时回退 scheduledDate，避免时间线崩溃。
      final occurredAt =
          row.read<DateTime?>('occurred_at') ?? task.scheduledDate;
      return ReviewTaskTimelineModel(
        model: ReviewTaskWithItemModel(task: task, item: item),
        occurredAt: occurredAt,
      );
    }).toList();
  }

  /// 查询学习内容关联的所有复习任务。
  Future<List<ReviewTask>> getTasksByLearningItemId(int learningItemId) {
    return (db.select(
      db.reviewTasks,
    )..where((t) => t.learningItemId.equals(learningItemId))).get();
  }

  /// 查询学习内容的复习计划（join 学习内容，用于详情 Sheet）。
  ///
  /// 说明：
  /// - 不过滤 is_deleted，详情页需要展示“已停用只读模式”
  /// - 按 reviewRound 正序返回
  Future<List<ReviewTaskWithItemModel>> getReviewPlanWithItem(
    int learningItemId,
  ) async {
    final task = db.reviewTasks;
    final item = db.learningItems;
    final query =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(item.id.equals(learningItemId))
          ..orderBy([
            OrderingTerm.asc(task.reviewRound),
            OrderingTerm.asc(task.id),
          ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  /// 根据 learningItemId + reviewRound 定位唯一复习任务记录。
  Future<ReviewTask?> getTaskByLearningItemAndRound(
    int learningItemId,
    int reviewRound,
  ) {
    return (db.select(db.reviewTasks)
          ..where((t) => t.learningItemId.equals(learningItemId))
          ..where((t) => t.reviewRound.equals(reviewRound)))
        .getSingleOrNull();
  }

  /// 批量查询指定学习内容的“目标轮次”任务计划日期（scheduledDate）。
  ///
  /// 说明：
  /// - 用于首页「复习间隔预览增强」：在 done/skipped 卡片上展示“下次复习时间”
  /// - 仅查询 review_tasks 表，不做 learning_items 关联过滤（上层列表已保证 is_deleted=0）
  /// - 参数为“学习内容 → 目标轮次”的映射（每个学习内容最多查询 1 条）
  ///
  /// 参数：
  /// - [targets] key=learningItemId，value=目标 reviewRound（通常为 lastRound + 1）
  /// 返回值：Map（key=learningItemId，value=scheduledDate）。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<Map<int, DateTime>> getScheduledDatesByLearningItemAndRounds(
    Map<int, int> targets,
  ) async {
    if (targets.isEmpty) return const <int, DateTime>{};

    final t = db.reviewTasks;
    final predicates = <Expression<bool>>[];
    for (final e in targets.entries) {
      predicates.add(
        t.learningItemId.equals(e.key) & t.reviewRound.equals(e.value),
      );
    }
    final predicate = predicates.reduce((a, b) => a | b);

    final rows = await (db.select(t)..where((_) => predicate)).get();
    final result = <int, DateTime>{};
    for (final row in rows) {
      result[row.learningItemId] = row.scheduledDate;
    }
    return result;
  }

  /// 更新指定轮次的 scheduledDate（定位键：learningItemId + reviewRound）。
  ///
  /// 返回值：更新行数。
  Future<int> updateScheduledDateByLearningItemAndRound({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
  }) {
    final now = DateTime.now();
    // 性能优化（v10）：scheduledDate 变更可能影响 pending 任务的 occurredAt，
    // 同时也需要兼容历史脏数据（done/skipped 但 completedAt/skippedAt 为空）时的回退口径。
    const sql = '''
UPDATE review_tasks
SET scheduled_date = ?,
    occurred_at = CASE status
      WHEN 'pending' THEN ?
      WHEN 'done' THEN COALESCE(completed_at, ?)
      WHEN 'skipped' THEN COALESCE(skipped_at, ?)
      ELSE ?
    END,
    updated_at = ?
WHERE learning_item_id = ? AND review_round = ?
''';
    return db.customUpdate(
      sql,
      variables: [
        Variable<DateTime>(scheduledDate),
        Variable<DateTime>(scheduledDate),
        Variable<DateTime>(scheduledDate),
        Variable<DateTime>(scheduledDate),
        Variable<DateTime>(scheduledDate),
        Variable<DateTime>(now),
        Variable<int>(learningItemId),
        Variable<int>(reviewRound),
      ],
      updates: {db.reviewTasks},
    );
  }

  /// 获取指定学习内容当前最大复习轮次（不存在则返回 0）。
  Future<int> getMaxReviewRound(int learningItemId) async {
    final exp = db.reviewTasks.reviewRound.max();
    final row =
        await (db.selectOnly(db.reviewTasks)
              ..addColumns([exp])
              ..where(db.reviewTasks.learningItemId.equals(learningItemId)))
            .getSingle();
    return row.read(exp) ?? 0;
  }

  /// 获取指定日期任务统计（completed/total）。
  ///
  /// 说明：total 包含 done/skipped/pending，completed 仅统计 done。
  Future<(int completed, int total)> getTaskStats(DateTime date) async {
    final start = DateTime(date.year, date.month, date.day);
    final end = start.add(const Duration(days: 1));

    const sql = '''
SELECT
  COUNT(*) AS total_count,
  SUM(CASE WHEN rt.status='done' THEN 1 ELSE 0 END) AS completed_count
FROM review_tasks rt
INNER JOIN learning_items li ON li.id = rt.learning_item_id
WHERE li.is_deleted = 0
  AND rt.scheduled_date >= ?
  AND rt.scheduled_date < ?
''';
    final row = await db
        .customSelect(
          sql,
          variables: [Variable<DateTime>(start), Variable<DateTime>(end)],
          readsFrom: {db.reviewTasks, db.learningItems},
        )
        .getSingle();
    return (
      row.read<int?>('completed_count') ?? 0,
      row.read<int?>('total_count') ?? 0,
    );
  }

  /// F6：获取指定月份每天的任务统计（用于日历圆点标记）。
  ///
  /// 参数：
  /// - [year] 年份
  /// - [month] 月份（1-12）
  /// 返回值：以当天 00:00 为 key 的统计 Map。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<Map<DateTime, TaskDayStats>> getMonthlyTaskStats(
    int year,
    int month,
  ) async {
    final start = DateTime(year, month, 1);
    final end = DateTime(year, month + 1, 1);

    const sql = '''
SELECT rt.scheduled_date AS scheduled_date, rt.status AS status
FROM review_tasks rt
INNER JOIN learning_items li ON li.id = rt.learning_item_id
WHERE li.is_deleted = 0
  AND rt.scheduled_date >= ?
  AND rt.scheduled_date < ?
''';
    final rows = await db
        .customSelect(
          sql,
          variables: [Variable<DateTime>(start), Variable<DateTime>(end)],
          readsFrom: {db.reviewTasks, db.learningItems},
        )
        .get();
    final map = <DateTime, _DayStatsAccumulator>{};
    for (final row in rows) {
      final scheduled = row.read<DateTime>('scheduled_date');
      final status = row.read<String?>('status') ?? 'pending';
      final day = YikeDateUtils.atStartOfDay(scheduled);
      final stats = map.putIfAbsent(day, _DayStatsAccumulator.new);
      switch (status) {
        case 'done':
          stats.done++;
          break;
        case 'skipped':
          stats.skipped++;
          break;
        case 'pending':
        default:
          stats.pending++;
          break;
      }
    }

    return map.map(
      (day, stats) => MapEntry(
        day,
        TaskDayStats(
          pendingCount: stats.pending,
          doneCount: stats.done,
          skippedCount: stats.skipped,
        ),
      ),
    );
  }

  /// F7：获取指定日期范围内，每天的任务状态统计（用于趋势图/热力图/统计导出）。
  ///
  /// 说明：
  /// - 口径按 scheduledDate 归因到自然日（以本地 00:00 为日界线）
  /// - 返回的 key 为当天 00:00:00
  /// - 仅统计 learning_items.is_deleted=0 的任务
  ///
  /// 参数：
  /// - [start] 起始时间（包含）
  /// - [end] 结束时间（不包含）
  /// 返回值：Map（key=当天 00:00，value=TaskDayStats）
  Future<Map<DateTime, TaskDayStats>> getTaskDayStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    const sql = '''
SELECT rt.scheduled_date AS scheduled_date, rt.status AS status
FROM review_tasks rt
INNER JOIN learning_items li ON li.id = rt.learning_item_id
WHERE li.is_deleted = 0
  AND rt.scheduled_date >= ?
  AND rt.scheduled_date < ?
''';
    final rows = await db
        .customSelect(
          sql,
          variables: [Variable<DateTime>(start), Variable<DateTime>(end)],
          readsFrom: {db.reviewTasks, db.learningItems},
        )
        .get();

    final map = <DateTime, _DayStatsAccumulator>{};
    for (final row in rows) {
      final scheduled = row.read<DateTime>('scheduled_date');
      final status = row.read<String?>('status') ?? 'pending';
      final day = YikeDateUtils.atStartOfDay(scheduled);
      final stats = map.putIfAbsent(day, _DayStatsAccumulator.new);
      switch (status) {
        case 'done':
          stats.done++;
          break;
        case 'skipped':
          stats.skipped++;
          break;
        case 'pending':
        default:
          stats.pending++;
          break;
      }
    }

    return map.map(
      (day, stats) => MapEntry(
        day,
        TaskDayStats(
          pendingCount: stats.pending,
          doneCount: stats.done,
          skippedCount: stats.skipped,
        ),
      ),
    );
  }

  /// F6：获取指定日期范围的任务列表（join 学习内容）。
  ///
  /// 参数：
  /// - [start] 起始时间（包含）
  /// - [end] 结束时间（不包含）
  /// 返回值：任务列表（按 scheduledDate 升序）。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<ReviewTaskWithItemModel>> getTasksInRange(
    DateTime start,
    DateTime end,
  ) async {
    final task = db.reviewTasks;
    final item = db.learningItems;

    final query =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(item.isDeleted.equals(false))
          ..where(task.scheduledDate.isBiggerOrEqualValue(start))
          ..where(task.scheduledDate.isSmallerThanValue(end))
          ..orderBy([
            OrderingTerm.asc(task.scheduledDate),
            OrderingTerm.asc(task.status),
            OrderingTerm.asc(task.reviewRound),
          ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  /// F7：获取连续打卡天数（从今天往前计算）。
  ///
  /// 口径（连续打卡：兼容“无任务不间断”）：
  /// - **计数按完成日**：仅按 [completedAt]（实际完成时间）所在日期计入打卡，不再使用 scheduledDate 计数
  /// - **每天至少完成 1 条任务**（done>=1）计 1 天
  /// - **无任务不间断**：某天若没有任何任务，或当天任务全部为 skipped，则不计入天数但也不打断连续链
  /// - **断签规则**：某天存在非 skipped 任务（pending/done），但当天没有完成（done=0），则断签
  /// - **链尾选择**：若今天未完成但昨天有完成，则从昨天作为链尾开始累计（避免“今天还有 pending 未做完，连续直接变 0”）
  Future<int> getConsecutiveCompletedDays({DateTime? today}) async {
    final now = today ?? DateTime.now();
    final todayStart = YikeDateUtils.atStartOfDay(now);
    final todayEnd = todayStart.add(const Duration(days: 1));

    final task = db.reviewTasks;
    final item = db.learningItems;

    // 找到最早的“done 且 completedAt 有值”的记录作为遍历下界，避免无穷回溯。
    final earliestRow =
        await (db.select(task).join([
                innerJoin(item, item.id.equalsExp(task.learningItemId)),
              ])
              ..where(item.isDeleted.equals(false))
              ..where(task.completedAt.isNotNull())
              ..where(task.completedAt.isSmallerThanValue(todayEnd))
              ..where(task.status.equals('done'))
              ..orderBy([OrderingTerm.asc(task.completedAt)])
              ..limit(1))
            .getSingleOrNull();

    final earliest = earliestRow?.readTable(task).completedAt;
    if (earliest == null) return 0;
    final earliestStart = YikeDateUtils.atStartOfDay(earliest);

    // 一次性拉取范围内的 done 任务，再在 Dart 侧按天聚合（排除已停用学习内容）。
    final joined =
        await (db.select(task).join([
                innerJoin(item, item.id.equalsExp(task.learningItemId)),
              ])
              ..where(item.isDeleted.equals(false))
              ..where(task.completedAt.isNotNull())
              ..where(task.completedAt.isBiggerOrEqualValue(earliestStart))
              ..where(task.completedAt.isSmallerThanValue(todayEnd))
              ..where(task.status.equals('done')))
            .get();
    final tasks = joined.map((r) => r.readTable(task)).toList();

    final completedDays = <DateTime>{};
    for (final t in tasks) {
      final completedAt = t.completedAt;
      if (completedAt == null) continue;
      completedDays.add(YikeDateUtils.atStartOfDay(completedAt));
    }

    // 一次性拉取范围内“按计划日期落在某天”的任务，用于判断：
    // - 某天是否“无任务”（允许跳过且不中断）
    // - 某天是否“仅 skipped”（同样允许跳过）
    // - 某天是否存在非 skipped 任务（若当天无完成则断签）
    final scheduledRows =
        await (db.select(task).join([
                innerJoin(item, item.id.equalsExp(task.learningItemId)),
              ])
              ..where(item.isDeleted.equals(false))
              ..where(task.scheduledDate.isBiggerOrEqualValue(earliestStart))
              ..where(task.scheduledDate.isSmallerThanValue(todayEnd))
              ..where(task.status.isIn(const ['done', 'pending', 'skipped'])))
            .get();
    final hasNonSkippedTaskByDay = <DateTime, bool>{};
    for (final row in scheduledRows) {
      final t = row.readTable(task);
      final day = YikeDateUtils.atStartOfDay(t.scheduledDate);
      final hasNonSkipped = hasNonSkippedTaskByDay[day] ?? false;
      // 只要出现过一次非 skipped，就标记为“当天有需要完成的任务”。
      if (!hasNonSkipped && t.status != 'skipped') {
        hasNonSkippedTaskByDay[day] = true;
        continue;
      }
      // 若目前未记录该天，且当前为 skipped，则写入 false（用于区分“无任务”与“仅 skipped”）。
      hasNonSkippedTaskByDay.putIfAbsent(day, () => false);
    }

    // 链尾选择：
    // - 今天已完成：从今天算
    // - 今天未完成但昨天完成：从昨天算（避免今日 pending 导致直接归零）
    // - 否则：未形成连续链
    final yesterdayStart = todayStart.subtract(const Duration(days: 1));
    var cursor = completedDays.contains(todayStart)
        ? todayStart
        : yesterdayStart;
    if (!completedDays.contains(cursor)) return 0;

    var streak = 0;
    while (!cursor.isBefore(earliestStart)) {
      if (completedDays.contains(cursor)) {
        // 当天有完成：计入连续天数。
        streak++;
        cursor = cursor.subtract(const Duration(days: 1));
        continue;
      }

      final hasNonSkipped = hasNonSkippedTaskByDay[cursor] ?? false;
      if (!hasNonSkipped) {
        // 无任务 / 仅 skipped：不中断连续链，但不计入天数。
        cursor = cursor.subtract(const Duration(days: 1));
        continue;
      }

      // 当天存在非 skipped 任务，但没有完成：断签。
      break;
    }

    return streak;
  }

  /// F7：获取指定日期范围的完成率口径数据（completed/total）。
  ///
  /// 说明：
  /// - completed：done 数量
  /// - total：done + pending 数量（skipped 不参与）
  Future<(int completed, int total)> getTaskStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    const sql = '''
SELECT
  SUM(CASE WHEN rt.status IN ('done','pending') THEN 1 ELSE 0 END) AS total_count,
  SUM(CASE WHEN rt.status='done' THEN 1 ELSE 0 END) AS completed_count
FROM review_tasks rt
INNER JOIN learning_items li ON li.id = rt.learning_item_id
WHERE li.is_deleted = 0
  AND rt.scheduled_date >= ?
  AND rt.scheduled_date < ?
''';
    final row = await db
        .customSelect(
          sql,
          variables: [Variable<DateTime>(start), Variable<DateTime>(end)],
          readsFrom: {db.reviewTasks, db.learningItems},
        )
        .getSingle();
    final total = row.read<int?>('total_count') ?? 0;
    final completed = row.read<int?>('completed_count') ?? 0;
    return (completed, total);
  }

  /// F8：获取全部复习任务（用于数据导出）。
  Future<List<ReviewTask>> getAllTasks() {
    return db.select(db.reviewTasks).get();
  }

  /// 删除所有模拟复习任务（v3.1 Debug）。
  ///
  /// 说明：按 isMockData=true 条件删除。
  /// 返回值：删除行数。
  /// 异常：数据库删除失败时可能抛出异常。
  Future<int> deleteMockReviewTasks() {
    return (db.delete(
      db.reviewTasks,
    )..where((t) => t.isMockData.equals(true))).go();
  }

  Future<List<ReviewTaskWithItemModel>> _getTasksWithItem({
    required DateTime date,
    required bool onlyPending,
    required bool onlyOverdue,
  }) async {
    final start = DateTime(date.year, date.month, date.day);
    final end = start.add(const Duration(days: 1));

    final task = db.reviewTasks;
    final item = db.learningItems;

    final query = db.select(task).join([
      innerJoin(item, item.id.equalsExp(task.learningItemId)),
    ]);
    query.where(item.isDeleted.equals(false));

    if (onlyOverdue) {
      query.where(task.scheduledDate.isSmallerThanValue(start));
    } else {
      query.where(task.scheduledDate.isBiggerOrEqualValue(start));
      query.where(task.scheduledDate.isSmallerThanValue(end));
    }

    if (onlyPending) {
      query.where(task.status.equals('pending'));
    }

    query.orderBy([
      OrderingTerm.asc(task.scheduledDate),
      OrderingTerm.asc(task.reviewRound),
    ]);

    final rows = await query.get();
    return rows
        .map(
          (row) => ReviewTaskWithItemModel(
            task: row.readTable(task),
            item: row.readTable(item),
          ),
        )
        .toList();
  }

  Future<void> _assertLearningItemActiveByTaskIds(List<int> taskIds) async {
    if (taskIds.isEmpty) return;

    final task = db.reviewTasks;
    final item = db.learningItems;

    final q =
        db.select(task).join([
            innerJoin(item, item.id.equalsExp(task.learningItemId)),
          ])
          ..where(task.id.isIn(taskIds))
          ..where(item.isDeleted.equals(true))
          ..limit(1);

    final hit = await q.getSingleOrNull();
    if (hit != null) {
      throw StateError('学习内容已停用，无法操作');
    }
  }

  /// 为插入任务的 Companion 兜底补齐 occurredAt。
  ///
  /// 说明：
  /// - occurredAt 为性能优化新增列（用于时间线排序与游标分页）
  /// - 为兼容测试/历史调用方，DAO 层兜底一次，避免遗漏导致 NULL 排序与口径错误
  ReviewTasksCompanion _ensureOccurredAtForInsert(ReviewTasksCompanion c) {
    if (c.occurredAt.present) return c;

    final scheduled = c.scheduledDate.value;
    final status = c.status.present ? c.status.value : 'pending';
    final completed = c.completedAt.present ? c.completedAt.value : null;
    final skipped = c.skippedAt.present ? c.skippedAt.value : null;

    final occurredAt = switch (status) {
      'pending' => scheduled,
      'done' => completed ?? scheduled,
      'skipped' => skipped ?? scheduled,
      _ => scheduled,
    };

    return c.copyWith(occurredAt: Value<DateTime?>(occurredAt));
  }
}

class _DayStatsAccumulator {
  int pending = 0;
  int done = 0;
  int skipped = 0;
}
