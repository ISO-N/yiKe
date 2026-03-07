// 文件用途：LearningItemDao 单元测试（插入、按条件查询、标签聚合）。
// 作者：Codex
// 创建日期：2026-02-25

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/learning_item_dao.dart';
import 'package:yike/data/database/database.dart';

import '../helpers/test_database.dart';
import '../helpers/test_uuid.dart';

void main() {
  late AppDatabase db;
  late LearningItemDao dao;
  var uuidSeed = 1;

  setUp(() {
    db = createInMemoryDatabase();
    dao = LearningItemDao(db);
  });

  tearDown(() async {
    await db.close();
  });

  test('insertLearningItem / getLearningItemById 正常读写', () async {
    final id = await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'T1',
        tags: drift.Value(jsonEncode(['a', 'b'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
        updatedAt: drift.Value(DateTime(2026, 2, 25, 10)),
      ),
    );

    final row = await dao.getLearningItemById(id);
    expect(row, isNotNull);
    expect(row!.title, 'T1');
    expect(row.tags, '["a","b"]');
  });

  test('getAllLearningItems 按 createdAt 倒序', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Old',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'New',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
      ),
    );

    final rows = await dao.getAllLearningItems();
    expect(rows.map((e) => e.title).toList(), ['New', 'Old']);
  });

  test('getLearningItemCount 仅统计未停用内容（包含模拟数据）', () async {
    expect(await dao.getLearningItemCount(), 0);

    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Real',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 3, 1),
        createdAt: drift.Value(DateTime(2026, 3, 1, 10)),
        isMockData: const drift.Value(false),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Mock',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 3, 1),
        createdAt: drift.Value(DateTime(2026, 3, 1, 11)),
        isMockData: const drift.Value(true),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Deleted',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 3, 1),
        createdAt: drift.Value(DateTime(2026, 3, 1, 12)),
        isDeleted: const drift.Value(true),
        deletedAt: drift.Value(DateTime(2026, 3, 1, 12)),
      ),
    );

    expect(await dao.getLearningItemCount(), 2);
  });

  test('getLearningItemsByDate 仅返回当天数据', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'D1',
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 2, 25, 23, 59),
        createdAt: drift.Value(DateTime(2026, 2, 25, 12)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'D2',
        tags: const drift.Value('[]'),
        // 边界：DAO 使用 isBetweenValues(start, end)，此处避免落在 end 的包含边界上。
        learningDate: DateTime(2026, 2, 26, 0, 0, 1),
        createdAt: drift.Value(DateTime(2026, 2, 26, 12)),
      ),
    );

    final rows = await dao.getLearningItemsByDate(DateTime(2026, 2, 25));
    expect(rows.length, 1);
    expect(rows.single.title, 'D1');
  });

  test('getLearningItemsByTag 使用 LIKE 匹配 JSON 文本', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'TagA',
        tags: drift.Value(jsonEncode(['a'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'TagB',
        tags: drift.Value(jsonEncode(['b'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 11)),
      ),
    );

    final rows = await dao.getLearningItemsByTag('a');
    expect(rows.length, 1);
    expect(rows.single.title, 'TagA');
  });

  test('getAllTags 会去重、trim、排序，且容错非法 JSON', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'A',
        tags: drift.Value(jsonEncode(['  z  ', 'a', '', 'a'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'B',
        tags: const drift.Value('not-json'),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 11)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'C',
        tags: drift.Value(jsonEncode(['b'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 12)),
      ),
    );

    final tags = await dao.getAllTags();
    expect(tags, ['a', 'b', 'z']);
  });

  test('getTagDistribution 按标签统计数量（多标签各计一次）', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I1',
        tags: drift.Value(jsonEncode(['a', 'b', 'a'])),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I2',
        tags: drift.Value(jsonEncode(['b', 'c'])),
        learningDate: DateTime(2026, 2, 26),
        createdAt: drift.Value(DateTime(2026, 2, 26, 10)),
      ),
    );

    final dist = await dao.getTagDistribution();
    expect(dist['a'], 1); // 同一条记录中重复标签不应重复计数
    expect(dist['b'], 2);
    expect(dist['c'], 1);
  });

  test('getTagUsageRanking 按近 7 天次数、累计次数与最近使用时间排序', () async {
    final recentSince = DateTime(2026, 3, 2);
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I1',
        tags: drift.Value(jsonEncode(['数学', '英语'])),
        learningDate: DateTime(2026, 3, 7),
        createdAt: drift.Value(DateTime(2026, 3, 7, 10)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I2',
        tags: drift.Value(jsonEncode(['数学'])),
        learningDate: DateTime(2026, 3, 6),
        createdAt: drift.Value(DateTime(2026, 3, 6, 8)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I3',
        tags: drift.Value(jsonEncode(['英语'])),
        learningDate: DateTime(2026, 2, 20),
        createdAt: drift.Value(DateTime(2026, 2, 20, 9)),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'I4',
        tags: drift.Value(jsonEncode(['物理'])),
        learningDate: DateTime(2026, 3, 5),
        createdAt: drift.Value(DateTime(2026, 3, 5, 12)),
      ),
    );

    final ranked = await dao.getTagUsageRanking(recentSince: recentSince);
    expect(ranked.map((entry) => entry.tag).toList(), <String>['数学', '英语', '物理']);
    expect(ranked.first.recentUseCount, 2);
    expect(ranked.first.totalUseCount, 2);
    expect(ranked[1].recentUseCount, 1);
    expect(ranked[1].totalUseCount, 2);
    expect(ranked.last.recentUseCount, 1);
    expect(ranked.last.totalUseCount, 1);
  });

  test('searchLearningItems: keyword 为空时返回空列表', () async {
    final rows = await dao.searchLearningItems(keyword: '   ');
    expect(rows, isEmpty);
  });

  test(
    'searchLearningItems: 可匹配 title/note，且会排除已停用学习内容并按 createdAt 倒序',
    () async {
      await dao.insertLearningItem(
        LearningItemsCompanion.insert(
          uuid: drift.Value(testUuid(uuidSeed++)),
          title: 'Apple',
          note: const drift.Value.absent(),
          tags: const drift.Value('[]'),
          learningDate: DateTime(2026, 2, 25),
          createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
          updatedAt: drift.Value(DateTime(2026, 2, 25, 9)),
        ),
      );
      await dao.insertLearningItem(
        LearningItemsCompanion.insert(
          uuid: drift.Value(testUuid(uuidSeed++)),
          title: 'Banana',
          note: const drift.Value('contains apple in note'),
          tags: const drift.Value('[]'),
          learningDate: DateTime(2026, 2, 25),
          createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
          updatedAt: drift.Value(DateTime(2026, 2, 25, 10)),
        ),
      );
      await dao.insertLearningItem(
        LearningItemsCompanion.insert(
          uuid: drift.Value(testUuid(uuidSeed++)),
          title: 'Deleted Apple',
          note: const drift.Value.absent(),
          tags: const drift.Value('[]'),
          learningDate: DateTime(2026, 2, 25),
          createdAt: drift.Value(DateTime(2026, 2, 25, 11)),
          updatedAt: drift.Value(DateTime(2026, 2, 25, 11)),
          isDeleted: const drift.Value(true),
          deletedAt: drift.Value(DateTime(2026, 2, 25, 11)),
        ),
      );

      final rows = await dao.searchLearningItems(keyword: 'apple', limit: 200);
      expect(rows.map((e) => e.title).toList(), ['Banana', 'Apple']);

      // limit 会被 clamp 到最小 1。
      final one = await dao.searchLearningItems(keyword: 'apple', limit: 0);
      expect(one.length, 1);
    },
  );

  test('deleteMockLearningItems: 仅删除 isMockData=true 的学习内容', () async {
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Mock',
        note: const drift.Value.absent(),
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
        isMockData: const drift.Value(true),
      ),
    );
    await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: drift.Value(testUuid(uuidSeed++)),
        title: 'Real',
        note: const drift.Value.absent(),
        tags: const drift.Value('[]'),
        learningDate: DateTime(2026, 2, 25),
        createdAt: drift.Value(DateTime(2026, 2, 25, 11)),
        isMockData: const drift.Value(false),
      ),
    );

    final deleted = await dao.deleteMockLearningItems();
    expect(deleted, 1);

    final left = await dao.getAllLearningItems();
    expect(left.map((e) => e.title).toList(), ['Real']);
  });

  test(
    'updateLearningItemNote / deactivateLearningItem 会更新字段并回写 updatedAt',
    () async {
      final id = await dao.insertLearningItem(
        LearningItemsCompanion.insert(
          uuid: drift.Value(testUuid(uuidSeed++)),
          title: 'T',
          note: const drift.Value.absent(),
          tags: const drift.Value('[]'),
          learningDate: DateTime(2026, 2, 25),
          createdAt: drift.Value(DateTime(2026, 2, 25, 10)),
        ),
      );

      final updated1 = await dao.updateLearningItemNote(id, 'n');
      expect(updated1, 1);
      final row1 = await dao.getLearningItemById(id);
      expect(row1!.note, 'n');
      expect(row1.updatedAt, isNotNull);

      final updated2 = await dao.deactivateLearningItem(id);
      expect(updated2, 1);
      final row2 = await dao.getLearningItemById(id);
      expect(row2!.isDeleted, true);
      expect(row2.deletedAt, isNotNull);
      expect(row2.updatedAt, isNotNull);
    },
  );
}
