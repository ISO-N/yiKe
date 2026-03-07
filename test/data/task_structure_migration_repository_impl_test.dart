// 文件用途：TaskStructureMigrationRepositoryImpl 单元测试，覆盖待迁移查询、迁移写库与同步日志。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/learning_subtask_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/task_structure_migration_repository_impl.dart';
import 'package:yike/data/sync/sync_log_writer.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;

  Future<int> insertLearningItem({
    required String uuid,
    required String title,
    String? description,
    String? note,
    bool isMockData = false,
  }) {
    final now = DateTime(2026, 3, 7, 10);
    return db.into(db.learningItems).insert(
      LearningItemsCompanion.insert(
        uuid: drift.Value(uuid),
        title: title,
        description: description == null
            ? const drift.Value.absent()
            : drift.Value(description),
        note: note == null ? const drift.Value.absent() : drift.Value(note),
        tags: const drift.Value('["迁移"]'),
        learningDate: now,
        createdAt: drift.Value(now),
        updatedAt: drift.Value(now),
        isMockData: drift.Value(isMockData),
      ),
    );
  }

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  group('TaskStructureMigrationRepositoryImpl', () {
    test('getPendingLegacyNoteItems 会按 limit 返回非空 note 项', () async {
      await insertLearningItem(uuid: 'item-1', title: '有备注一', note: '说明一');
      await insertLearningItem(uuid: 'item-2', title: '无备注', note: '   ');
      await insertLearningItem(
        uuid: 'item-3',
        title: '有备注二',
        note: '说明二',
        isMockData: true,
      );

      final repository = TaskStructureMigrationRepositoryImpl(
        db: db,
        learningSubtaskDao: LearningSubtaskDao(db),
      );

      final items = await repository.getPendingLegacyNoteItems(limit: 2);
      expect(items, hasLength(2));
      expect(items.map((item) => item.note), <String>['说明一', '说明二']);
      expect(items.map((item) => item.isMockData), <bool>[false, true]);
    });

    test('applyMigrationForItem 会写入描述/子任务并为真实数据生成同步日志', () async {
      final itemId = await insertLearningItem(
        uuid: 'item-real',
        title: '待迁移内容',
        note: '旧备注',
      );
      final syncLogDao = SyncLogDao(db);
      final mappingDao = SyncEntityMappingDao(db);
      final repository = TaskStructureMigrationRepositoryImpl(
        db: db,
        learningSubtaskDao: LearningSubtaskDao(db),
        syncLogWriter: SyncLogWriter(
          syncLogDao: syncLogDao,
          syncEntityMappingDao: mappingDao,
          localDeviceId: 'migration-device',
        ),
      );

      await repository.applyMigrationForItem(
        learningItemId: itemId,
        isMockData: false,
        migratedDescription: '迁移后的描述',
        migratedSubtasks: const <String>['子任务一', '子任务二'],
      );

      final item = await (db.select(db.learningItems)
            ..where((t) => t.id.equals(itemId)))
          .getSingle();
      final subtasks = await (db.select(
        db.learningSubtasks,
      )..where((t) => t.learningItemId.equals(itemId))).get();
      final logs = await syncLogDao.getLogsFromDeviceSince('migration-device', 0);

      expect(item.description, '迁移后的描述');
      expect(item.note, isNull);
      expect(subtasks.map((row) => row.content).toList(), <String>[
        '子任务一',
        '子任务二',
      ]);
      expect(logs, hasLength(3));
      expect(logs.first.entityType, 'learning_item');
      expect(jsonDecode(logs.first.data), containsPair('description', '迁移后的描述'));
      expect(
        logs.where((row) => row.entityType == 'learning_subtask'),
        hasLength(2),
      );
    });

    test('applyMigrationForItem 遇到已有 description 与已有子任务时保持幂等', () async {
      final itemId = await insertLearningItem(
        uuid: 'item-idempotent',
        title: '幂等内容',
        description: '已有描述',
        note: '旧备注',
      );
      await db.into(db.learningSubtasks).insert(
        LearningSubtasksCompanion.insert(
          learningItemId: itemId,
          content: '已有子任务',
          sortOrder: const drift.Value(0),
          createdAt: DateTime(2026, 3, 7, 10),
        ),
      );

      final repository = TaskStructureMigrationRepositoryImpl(
        db: db,
        learningSubtaskDao: LearningSubtaskDao(db),
      );
      await repository.applyMigrationForItem(
        learningItemId: itemId,
        isMockData: true,
        migratedDescription: '不应覆盖',
        migratedSubtasks: const <String>['新子任务'],
      );

      final item = await (db.select(db.learningItems)
            ..where((t) => t.id.equals(itemId)))
          .getSingle();
      final subtasks = await (db.select(
        db.learningSubtasks,
      )..where((t) => t.learningItemId.equals(itemId))).get();

      expect(item.description, '已有描述');
      expect(item.note, isNull);
      expect(subtasks.map((row) => row.content).toList(), <String>['已有子任务']);
    });
  });
}
