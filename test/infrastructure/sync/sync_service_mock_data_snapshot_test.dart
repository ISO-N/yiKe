// 文件用途：同步服务快照回归测试——验证“模拟数据”在开启开关后可参与同步快照日志生成。
// 作者：Codex
// 创建日期：2026-02-28

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/learning_topic_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';
import 'package:yike/infrastructure/sync/sync_service.dart';

import '../../helpers/test_database.dart';

void main() {
  test('ensureLocalSnapshotLogs：默认不包含 Mock 数据；开启后会写入 sync_logs', () async {
    final db = createInMemoryDatabase();
    try {
      final now = DateTime.now();

      // 插入一条模拟学习内容 + 一条模拟复习任务（都标记 isMockData=true）。
      final itemId = await db
          .into(db.learningItems)
          .insert(
            LearningItemsCompanion.insert(
              title: 'Mock Item',
              note: const Value('仅调试'),
              tags: const Value('[]'),
              learningDate: now,
              createdAt: Value(now),
              updatedAt: const Value.absent(),
              isMockData: const Value(true),
            ),
          );
      await db
          .into(db.reviewTasks)
          .insert(
            ReviewTasksCompanion.insert(
              learningItemId: itemId,
              reviewRound: 1,
              scheduledDate: now,
              status: const Value('pending'),
              completedAt: const Value.absent(),
              skippedAt: const Value.absent(),
              createdAt: Value(now),
              updatedAt: const Value.absent(),
              isMockData: const Value(true),
            ),
          );

      final service = SyncService(
        db: db,
        syncLogDao: SyncLogDao(db),
        syncEntityMappingDao: SyncEntityMappingDao(db),
        settingsDao: SettingsDao(db),
        secureStorageService: SecureStorageService(),
        localDeviceId: 'local_device',
      );

      // 默认：不包含 Mock 数据 -> 不产生快照日志。
      await service.ensureLocalSnapshotLogs(includeMockData: false);
      final logs1 = await db.select(db.syncLogs).get();
      expect(logs1.length, 0);

      // 开启开关：Mock 数据也会写入快照日志（学习内容 + 复习任务）。
      await service.ensureLocalSnapshotLogs(includeMockData: true);
      final logs2 = await db.select(db.syncLogs).get();
      expect(logs2.length, 2);

      // 额外验证：日志数据会携带 is_mock_data=true，便于对端保持“可一键清理”的语义。
      final decoded = (jsonDecode(logs2.first.data) as Map)
          .cast<String, dynamic>();
      expect(decoded['is_mock_data'], true);
    } finally {
      await db.close();
    }
  });

  test('ensureLocalSnapshotLogs 会为真实模板、主题、关联与子任务生成快照且具备幂等性', () async {
    final db = createInMemoryDatabase();
    try {
      final now = DateTime(2026, 3, 7, 9);
      final topicDao = LearningTopicDao(db);
      final service = SyncService(
        db: db,
        syncLogDao: SyncLogDao(db),
        syncEntityMappingDao: SyncEntityMappingDao(db),
        settingsDao: SettingsDao(db),
        secureStorageService: SecureStorageService(),
        localDeviceId: 'desktop_device',
      );

      final itemId = await db.into(db.learningItems).insert(
        LearningItemsCompanion.insert(
          title: '快照内容',
          note: const Value('用于快照验证'),
          tags: const Value('["快照"]'),
          learningDate: now,
          createdAt: Value(now),
          updatedAt: Value(now),
        ),
      );
      await db.into(db.learningSubtasks).insert(
        LearningSubtasksCompanion.insert(
          learningItemId: itemId,
          content: '快照子任务',
          sortOrder: const Value(0),
          createdAt: now,
          updatedAt: Value(now),
        ),
      );
      await db.into(db.reviewTasks).insert(
        ReviewTasksCompanion.insert(
          learningItemId: itemId,
          reviewRound: 1,
          scheduledDate: now.add(const Duration(days: 1)),
          occurredAt: Value(now.add(const Duration(days: 1))),
          status: const Value('pending'),
          createdAt: Value(now),
          updatedAt: Value(now),
        ),
      );
      await db.into(db.learningTemplates).insert(
        LearningTemplatesCompanion.insert(
          name: '快照模板',
          titlePattern: '模板 {date}',
          tags: const Value('["模板"]'),
          sortOrder: const Value(0),
          createdAt: Value(now),
          updatedAt: Value(now),
        ),
      );
      final topicId = await topicDao.insertTopic(
        LearningTopicsCompanion.insert(
          name: '快照主题',
          description: const Value('主题描述'),
          createdAt: Value(now),
          updatedAt: Value(now),
        ),
      );
      await topicDao.addItemToTopic(topicId, itemId);

      await service.ensureLocalSnapshotLogs(includeMockData: false);
      final firstLogs = await db.select(db.syncLogs).get();
      final firstTypes = firstLogs.map((row) => row.entityType).toSet();

      expect(firstLogs, hasLength(6));
      expect(firstTypes, containsAll(<String>{
        SyncService.entityLearningItem,
        SyncService.entityLearningSubtask,
        SyncService.entityReviewTask,
        SyncService.entityTemplate,
        SyncService.entityTopic,
        SyncService.entityTopicItemRelation,
      }));

      await service.ensureLocalSnapshotLogs(includeMockData: false);
      final secondLogs = await db.select(db.syncLogs).get();
      expect(secondLogs, hasLength(6));
    } finally {
      await db.close();
    }
  });
}
