// 文件用途：ReviewTaskDao 单元测试（join 查询、今日/逾期筛选、批量状态更新、统计）。
// 作者：Codex
// 创建日期：2026-02-25

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/review_task_dao.dart';
import 'package:yike/data/database/database.dart';

import '../helpers/test_database.dart';
import '../helpers/test_uuid.dart';

void main() {
  late AppDatabase db;
  late ReviewTaskDao dao;
  var uuidSeed = 1;

  setUp(() {
    db = createInMemoryDatabase();
    dao = ReviewTaskDao(db);
  });

  tearDown(() async {
    await db.close();
  });

  Future<int> insertItem({required String tags, bool isDeleted = false}) {
    return db
        .into(db.learningItems)
        .insert(
          LearningItemsCompanion.insert(
            uuid: drift.Value(testUuid(uuidSeed++)),
            title: 'Item',
            note: const drift.Value.absent(),
            tags: drift.Value(tags),
            learningDate: DateTime(2026, 2, 25),
            createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
            isDeleted: drift.Value(isDeleted),
            deletedAt: isDeleted
                ? drift.Value(DateTime(2026, 2, 28, 12))
                : const drift.Value.absent(),
          ),
        );
  }

  test('getTasksByDateWithItem 会 join 并返回模型', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final day = DateTime(2026, 2, 25);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: DateTime(2026, 2, 25, 9),
        status: const drift.Value('pending'),
        createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
      ),
    );

    final rows = await dao.getTasksByDateWithItem(day);
    expect(rows.length, 1);
    expect(rows.single.item.id, itemId);
    expect(rows.single.task.reviewRound, 1);
  });

  test('getTodayPendingTasksWithItem 仅返回今日 pending', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: todayStart.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    // 边界条件：明天 00:00 不应算作“今天”。
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 5,
        scheduledDate: todayStart.add(const Duration(days: 1)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: todayStart.add(const Duration(hours: 10)),
        status: const drift.Value('done'),
        completedAt: drift.Value(todayStart.add(const Duration(hours: 10))),
        createdAt: drift.Value(todayStart),
      ),
    );

    final rows = await dao.getTodayPendingTasksWithItem();
    expect(rows.length, 1);
    expect(rows.single.task.status, 'pending');
  });

  test('getOverdueTasksWithItem 仅返回逾期 pending', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);
    final yesterday = todayStart.subtract(const Duration(days: 1));

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: yesterday.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(yesterday),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: yesterday.add(const Duration(hours: 10)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(yesterday.add(const Duration(hours: 10))),
        createdAt: drift.Value(yesterday),
      ),
    );

    final rows = await dao.getOverdueTasksWithItem();
    expect(rows.length, 1);
    expect(rows.single.task.status, 'pending');
  });

  test('已停用学习内容的任务不会出现在今日列表查询中', () async {
    final activeId = await insertItem(
      tags: jsonEncode(['a']),
      isDeleted: false,
    );
    final deletedId = await insertItem(
      tags: jsonEncode(['b']),
      isDeleted: true,
    );

    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 1,
        scheduledDate: todayStart.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 1,
        scheduledDate: todayStart.add(const Duration(hours: 10)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );

    final rows = await dao.getTodayPendingTasksWithItem();
    expect(rows.length, 1);
    expect(rows.single.item.id, activeId);
  });

  test('已停用学习内容禁止任务状态变更（complete/skip/undo）', () async {
    final deletedId = await insertItem(
      tags: jsonEncode(['a']),
      isDeleted: true,
    );
    final base = DateTime(2026, 2, 25);
    final taskId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 1,
        scheduledDate: base,
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    await expectLater(
      dao.updateTaskStatus(taskId, 'done', completedAt: DateTime.now()),
      throwsA(isA<StateError>()),
    );
    await expectLater(dao.undoTaskStatus(taskId), throwsA(isA<StateError>()));
  });

  test('updateTaskStatusBatch 会更新状态与对应时间戳字段', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final base = DateTime(2026, 2, 25);

    final id1 = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: base,
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );
    final id2 = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: base,
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    final ts = DateTime(2026, 2, 25, 12);
    final updated = await dao.updateTaskStatusBatch(
      [id1, id2],
      'done',
      timestamp: ts,
    );
    expect(updated, 2);

    final r1 = await dao.getReviewTaskById(id1);
    final r2 = await dao.getReviewTaskById(id2);
    expect(r1!.status, 'done');
    expect(r1.completedAt, ts);
    expect(r1.skippedAt, null);
    expect(r2!.status, 'done');
    expect(r2.completedAt, ts);
  });

  test('getTaskStats 统计 done/total（total 含 skipped/pending）', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final day = DateTime(2026, 2, 25);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: day.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(day.add(const Duration(hours: 9))),
        createdAt: drift.Value(day),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: day.add(const Duration(hours: 10)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(day.add(const Duration(hours: 10))),
        createdAt: drift.Value(day),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: day.add(const Duration(hours: 11)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(day),
      ),
    );

    final (completed, total) = await dao.getTaskStats(day);
    expect(completed, 1);
    expect(total, 3);
  });

  test('getMonthlyTaskStats 返回单日 pending/done/skipped 统计', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final d = DateTime(2026, 2, 10);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: d.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(d),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: d.add(const Duration(hours: 10)),
        status: const drift.Value('done'),
        completedAt: drift.Value(d.add(const Duration(hours: 10))),
        createdAt: drift.Value(d),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: d.add(const Duration(hours: 11)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(d.add(const Duration(hours: 11))),
        createdAt: drift.Value(d),
      ),
    );

    final stats = await dao.getMonthlyTaskStats(2026, 2);
    final key = DateTime(2026, 2, 10);
    expect(stats.containsKey(key), true);
    expect(stats[key]!.pendingCount, 1);
    expect(stats[key]!.doneCount, 1);
    expect(stats[key]!.skippedCount, 1);
  });

  test('getTasksInRange 按 start 包含、end 不包含返回 join 结果', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final start = DateTime(2026, 2, 10);
    final end = DateTime(2026, 2, 11);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: DateTime(2026, 2, 10, 9),
        status: const drift.Value('pending'),
        createdAt: drift.Value(start),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: DateTime(2026, 2, 11, 0, 0, 0),
        status: const drift.Value('pending'),
        createdAt: drift.Value(end),
      ),
    );

    final rows = await dao.getTasksInRange(start, end);
    expect(rows.length, 1);
    expect(rows.single.item.id, itemId);
    expect(rows.single.task.scheduledDate, DateTime(2026, 2, 10, 9));
  });

  test('getTaskStatsInRange 统计 done/(done+pending)，skipped 不计入', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final start = DateTime(2026, 2, 10);
    final end = DateTime(2026, 2, 11);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: DateTime(2026, 2, 10, 9),
        status: const drift.Value('done'),
        completedAt: drift.Value(DateTime(2026, 2, 10, 9)),
        createdAt: drift.Value(start),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: DateTime(2026, 2, 10, 10),
        status: const drift.Value('pending'),
        createdAt: drift.Value(start),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: DateTime(2026, 2, 10, 11),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(DateTime(2026, 2, 10, 11)),
        createdAt: drift.Value(start),
      ),
    );

    final (completed, total) = await dao.getTaskStatsInRange(start, end);
    expect(completed, 1);
    expect(total, 2);
  });

  test('getScheduledDatesByLearningItemAndRounds 可批量查询目标轮次的计划日期', () async {
    final item1 = await insertItem(tags: jsonEncode(['a']));
    final item2 = await insertItem(tags: jsonEncode(['b']));
    final base = DateTime(2026, 3, 1);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: item1,
        reviewRound: 2,
        scheduledDate: base.add(const Duration(days: 2)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        learningItemId: item2,
        reviewRound: 3,
        scheduledDate: base.add(const Duration(days: 5)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    final map = await dao.getScheduledDatesByLearningItemAndRounds({
      item1: 2,
      item2: 3,
    });
    expect(map[item1], base.add(const Duration(days: 2)));
    expect(map[item2], base.add(const Duration(days: 5)));

    final missing = await dao.getScheduledDatesByLearningItemAndRounds({
      item1: 9, // 不存在
    });
    expect(missing.containsKey(item1), false);
  });

  test('updateTaskStatusBatch: ids 为空时直接返回 0', () async {
    final updated = await dao.updateTaskStatusBatch(const [], 'done');
    expect(updated, 0);
  });

  test('getGlobalTaskStatusCounts 会统计全量状态计数并排除已停用学习内容', () async {
    final activeId = await insertItem(
      tags: jsonEncode(['a']),
      isDeleted: false,
    );
    final deletedId = await insertItem(
      tags: jsonEncode(['b']),
      isDeleted: true,
    );

    final base = DateTime(2026, 2, 10);
    // active: pending/done/done/skipped 共 4 条
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 1,
        scheduledDate: base.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 2,
        scheduledDate: base.add(const Duration(hours: 10)),
        status: const drift.Value('done'),
        completedAt: drift.Value(base.add(const Duration(hours: 8))),
        createdAt: drift.Value(base),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 3,
        scheduledDate: base.add(const Duration(hours: 7)),
        status: const drift.Value('done'),
        // completedAt 为空时不影响计数口径（按 status 统计）
        createdAt: drift.Value(base),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 4,
        scheduledDate: base.add(const Duration(hours: 11)),
        status: const drift.Value('skipped'),
        createdAt: drift.Value(base),
      ),
    );

    // deleted item 的任务应被排除
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 1,
        scheduledDate: base.add(const Duration(hours: 6)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    final (all, pending, done, skipped) = await dao.getGlobalTaskStatusCounts();
    expect(all, 4);
    expect(pending, 1);
    expect(done, 2);
    expect(skipped, 1);
  });

  test('getTaskTimelinePageWithItem: occurredAt 口径、状态筛选与游标分页', () async {
    final activeId = await insertItem(
      tags: jsonEncode(['a']),
      isDeleted: false,
    );
    final deletedId = await insertItem(
      tags: jsonEncode(['b']),
      isDeleted: true,
    );
    final base = DateTime(2026, 2, 10);

    // done2：completedAt 为空，occurredAt 回退 scheduledDate（07:00）
    final done2Id = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 1,
        scheduledDate: base.add(const Duration(hours: 7)),
        status: const drift.Value('done'),
        createdAt: drift.Value(base),
      ),
    );
    // done1：occurredAt=completedAt（08:00）
    final done1Id = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 2,
        scheduledDate: base.add(const Duration(hours: 10)),
        status: const drift.Value('done'),
        completedAt: drift.Value(base.add(const Duration(hours: 8))),
        createdAt: drift.Value(base),
      ),
    );
    // pending：occurredAt=scheduledDate（09:00）
    final pendingId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 3,
        scheduledDate: base.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );
    // skipped：skippedAt 为空，occurredAt 回退 scheduledDate（11:00）
    final skippedId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: activeId,
        reviewRound: 4,
        scheduledDate: base.add(const Duration(hours: 11)),
        status: const drift.Value('skipped'),
        createdAt: drift.Value(base),
      ),
    );

    // deleted item 的任务应被排除（即使 occurredAt 更早）
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 1,
        scheduledDate: base.add(const Duration(hours: 6)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    final all = await dao.getTaskTimelinePageWithItem(limit: 10);
    expect(all.map((e) => e.model.task.id).toList(), [
      done2Id,
      done1Id,
      pendingId,
      skippedId,
    ]);
    expect(all.map((e) => e.occurredAt).toList(), [
      base.add(const Duration(hours: 7)),
      base.add(const Duration(hours: 8)),
      base.add(const Duration(hours: 9)),
      base.add(const Duration(hours: 11)),
    ]);

    final onlyPending = await dao.getTaskTimelinePageWithItem(
      status: 'pending',
      limit: 10,
    );
    expect(onlyPending.map((e) => e.model.task.id).toList(), [pendingId]);

    // 游标分页：先取前 2 条，再从“第二条”之后继续取。
    final firstPage = await dao.getTaskTimelinePageWithItem(limit: 2);
    expect(firstPage.map((e) => e.model.task.id).toList(), [done2Id, done1Id]);

    final cursorOccurredAt = firstPage.last.occurredAt;
    final cursorTaskId = firstPage.last.model.task.id;
    final nextPage = await dao.getTaskTimelinePageWithItem(
      cursorOccurredAt: cursorOccurredAt,
      cursorTaskId: cursorTaskId,
      limit: 10,
    );
    expect(nextPage.map((e) => e.model.task.id).toList(), [
      pendingId,
      skippedId,
    ]);
  });

  test('getTaskTimelinePageWithItem: 支持状态×时间筛选，且“今天”任务仅在时间筛选=全部时出现', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']), isDeleted: false);
    final todayStart = DateTime(2026, 3, 5);
    final tomorrowStart = todayStart.add(const Duration(days: 1));
    final yesterdayStart = todayStart.subtract(const Duration(days: 1));

    // 构造“昨天/今天/明天”三段数据，覆盖 pending/done/skipped。
    final beforePendingId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: yesterdayStart.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    final todayPendingId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: todayStart.add(const Duration(hours: 10)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    final afterPendingId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: tomorrowStart.add(const Duration(hours: 11)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );
    final beforeDoneId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 4,
        scheduledDate: yesterdayStart.add(const Duration(hours: 12)),
        status: const drift.Value('done'),
        completedAt: drift.Value(todayStart.add(const Duration(hours: 8))),
        createdAt: drift.Value(todayStart),
      ),
    );
    final afterDoneId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 5,
        scheduledDate: tomorrowStart.add(const Duration(hours: 13)),
        status: const drift.Value('done'),
        completedAt: drift.Value(todayStart.add(const Duration(hours: 9))),
        createdAt: drift.Value(todayStart),
      ),
    );
    final beforeSkippedId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 6,
        scheduledDate: yesterdayStart.add(const Duration(hours: 14)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(todayStart.add(const Duration(hours: 7))),
        createdAt: drift.Value(todayStart),
      ),
    );
    final afterSkippedId = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 7,
        scheduledDate: tomorrowStart.add(const Duration(hours: 15)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(todayStart.add(const Duration(hours: 6))),
        createdAt: drift.Value(todayStart),
      ),
    );

    final allRows = await dao.getTaskTimelinePageWithItem(limit: 20);
    final allIds = allRows.map((e) => e.model.task.id).toSet();
    expect(allIds, contains(todayPendingId));

    final beforeRows = await dao.getTaskTimelinePageWithItem(
      scheduledDateBefore: todayStart,
      limit: 20,
    );
    final beforeIds = beforeRows.map((e) => e.model.task.id).toSet();
    expect(beforeIds, {beforePendingId, beforeDoneId, beforeSkippedId});

    final afterRows = await dao.getTaskTimelinePageWithItem(
      scheduledDateOnOrAfter: tomorrowStart,
      limit: 20,
    );
    final afterIds = afterRows.map((e) => e.model.task.id).toSet();
    expect(afterIds, {afterPendingId, afterDoneId, afterSkippedId});

    final pendingBeforeRows = await dao.getTaskTimelinePageWithItem(
      status: 'pending',
      scheduledDateBefore: todayStart,
      limit: 20,
    );
    expect(pendingBeforeRows.map((e) => e.model.task.id).toSet(), {
      beforePendingId,
    });

    final pendingAfterRows = await dao.getTaskTimelinePageWithItem(
      status: 'pending',
      scheduledDateOnOrAfter: tomorrowStart,
      limit: 20,
    );
    expect(pendingAfterRows.map((e) => e.model.task.id).toSet(), {
      afterPendingId,
    });

    final doneBeforeRows = await dao.getTaskTimelinePageWithItem(
      status: 'done',
      scheduledDateBefore: todayStart,
      limit: 20,
    );
    expect(doneBeforeRows.map((e) => e.model.task.id).toSet(), {beforeDoneId});

    final doneAfterRows = await dao.getTaskTimelinePageWithItem(
      status: 'done',
      scheduledDateOnOrAfter: tomorrowStart,
      limit: 20,
    );
    expect(doneAfterRows.map((e) => e.model.task.id).toSet(), {afterDoneId});

    final skippedBeforeRows = await dao.getTaskTimelinePageWithItem(
      status: 'skipped',
      scheduledDateBefore: todayStart,
      limit: 20,
    );
    expect(skippedBeforeRows.map((e) => e.model.task.id).toSet(), {
      beforeSkippedId,
    });

    final skippedAfterRows = await dao.getTaskTimelinePageWithItem(
      status: 'skipped',
      scheduledDateOnOrAfter: tomorrowStart,
      limit: 20,
    );
    expect(skippedAfterRows.map((e) => e.model.task.id).toSet(), {
      afterSkippedId,
    });
  });

  test('getReviewPlanWithItem: 不过滤 is_deleted（详情页只读模式需要）', () async {
    final deletedId = await insertItem(
      tags: jsonEncode(['a']),
      isDeleted: true,
    );
    final base = DateTime(2026, 2, 10);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 2,
        scheduledDate: base.add(const Duration(days: 2)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: deletedId,
        reviewRound: 1,
        scheduledDate: base.add(const Duration(days: 1)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(base),
      ),
    );

    final plan = await dao.getReviewPlanWithItem(deletedId);
    expect(plan.map((e) => e.task.reviewRound).toList(), [1, 2]);
    expect(plan.first.item.isDeleted, true);
  });

  test(
    'insertReviewTasks / getTasksByDate: 可批量插入并按 scheduledDate 升序返回',
    () async {
      final itemId = await insertItem(tags: jsonEncode(['a']));
      final day = DateTime(2026, 2, 10);

      await dao.insertReviewTasks([
        ReviewTasksCompanion.insert(
          learningItemId: itemId,
          reviewRound: 1,
          scheduledDate: day.add(const Duration(hours: 10)),
          status: const drift.Value('pending'),
          createdAt: drift.Value(day),
        ),
        ReviewTasksCompanion.insert(
          learningItemId: itemId,
          reviewRound: 2,
          scheduledDate: day.add(const Duration(hours: 9)),
          status: const drift.Value('pending'),
          createdAt: drift.Value(day),
        ),
      ]);

      final rows = await dao.getTasksByDate(day);
      expect(rows.length, 2);
      expect(rows.first.scheduledDate, day.add(const Duration(hours: 9)));
      expect(rows.last.scheduledDate, day.add(const Duration(hours: 10)));
    },
  );

  test('updateReviewTask: replace 可更新字段', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final day = DateTime(2026, 2, 10);
    final id = await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: day.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(day),
      ),
    );

    final existing = await dao.getReviewTaskById(id);
    expect(existing, isNotNull);

    final ok = await dao.updateReviewTask(
      existing!.copyWith(
        status: 'done',
        completedAt: drift.Value(day.add(const Duration(hours: 9))),
      ),
    );
    expect(ok, true);

    final updated = await dao.getReviewTaskById(id);
    expect(updated!.status, 'done');
    expect(updated.completedAt, day.add(const Duration(hours: 9)));
  });

  test('getAllTasks / deleteMockReviewTasks: 可查询全量并仅删除 Mock 数据', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final day = DateTime(2026, 2, 10);

    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: day.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(day),
        isMockData: const drift.Value(true),
      ),
    );
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: day.add(const Duration(hours: 10)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(day),
        isMockData: const drift.Value(false),
      ),
    );

    final all = await dao.getAllTasks();
    expect(all.length, 2);

    final deleted = await dao.deleteMockReviewTasks();
    expect(deleted, 1);

    final left = await dao.getAllTasks();
    expect(left.length, 1);
    expect(left.single.isMockData, false);
  });

  test('getConsecutiveCompletedDays 支持“无任务/仅 skipped 不间断”口径', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    // 今天 done（计 1 天）
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: todayStart.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(todayStart.add(const Duration(hours: 9))),
        createdAt: drift.Value(todayStart),
      ),
    );

    // 昨天 done（计 1 天）
    final yesterday = todayStart.subtract(const Duration(days: 1));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: yesterday.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(yesterday.add(const Duration(hours: 9))),
        createdAt: drift.Value(yesterday),
      ),
    );

    // 前天仅 skipped（不计完成，也不断签）
    final twoDaysAgo = todayStart.subtract(const Duration(days: 2));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: twoDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('skipped'),
        skippedAt: drift.Value(twoDaysAgo.add(const Duration(hours: 9))),
        createdAt: drift.Value(twoDaysAgo),
      ),
    );

    // 大前天 done（计 1 天）
    final threeDaysAgo = todayStart.subtract(const Duration(days: 3));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 4,
        scheduledDate: threeDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(threeDaysAgo.add(const Duration(hours: 9))),
        createdAt: drift.Value(threeDaysAgo),
      ),
    );

    // 再往前一天 pending（断签）
    final fourDaysAgo = todayStart.subtract(const Duration(days: 4));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 5,
        scheduledDate: fourDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(fourDaysAgo),
      ),
    );

    final streak = await dao.getConsecutiveCompletedDays(today: now);
    // skipped 不计完成且不间断，因此可跨过“前天 skipped”继续累计到大前天 done。
    expect(streak, 3);
  });

  test('getConsecutiveCompletedDays 今日未完成但昨日完成时，不应直接归零', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    // 前天 done
    final twoDaysAgo = todayStart.subtract(const Duration(days: 2));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: twoDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(twoDaysAgo.add(const Duration(hours: 9))),
        createdAt: drift.Value(twoDaysAgo),
      ),
    );

    // 昨天 done
    final yesterday = todayStart.subtract(const Duration(days: 1));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: yesterday.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(yesterday.add(const Duration(hours: 9))),
        createdAt: drift.Value(yesterday),
      ),
    );

    // 今天 pending（不应导致连续归零）
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: todayStart.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(todayStart),
      ),
    );

    final streak = await dao.getConsecutiveCompletedDays(today: now);
    expect(streak, 2);
  });

  test('getConsecutiveCompletedDays 无任务日期不应打断连续链', () async {
    final itemId = await insertItem(tags: jsonEncode(['a']));
    final now = DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    // 今天 done（计 1 天）
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: todayStart.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(todayStart.add(const Duration(hours: 9))),
        createdAt: drift.Value(todayStart),
      ),
    );

    // 昨天“无任务”：不插入任何记录

    // 前天 done（计 1 天）
    final twoDaysAgo = todayStart.subtract(const Duration(days: 2));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 2,
        scheduledDate: twoDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('done'),
        completedAt: drift.Value(twoDaysAgo.add(const Duration(hours: 9))),
        createdAt: drift.Value(twoDaysAgo),
      ),
    );

    // 再往前一天 pending：存在非 skipped 任务但无完成，应断签（确保不会继续回溯计数）
    final threeDaysAgo = todayStart.subtract(const Duration(days: 3));
    await dao.insertReviewTask(
      ReviewTasksCompanion.insert(
        learningItemId: itemId,
        reviewRound: 3,
        scheduledDate: threeDaysAgo.add(const Duration(hours: 9)),
        status: const drift.Value('pending'),
        createdAt: drift.Value(threeDaysAgo),
      ),
    );

    final streak = await dao.getConsecutiveCompletedDays(today: now);
    // 昨天无任务不间断：连续链为 今天 + 前天 = 2 天。
    expect(streak, 2);
  });
}
