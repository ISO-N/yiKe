// 文件用途：SyncService Phase 3 服务集成测试，补齐冲突处理、去重与早返回分支覆盖。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';
import 'package:yike/infrastructure/sync/sync_models.dart';
import 'package:yike/infrastructure/sync/sync_service.dart';

import '../../helpers/test_database.dart';

void main() {
  const remoteDeviceId = 'remote-device';
  const localDeviceId = 'local-device';

  /// 构造稳定时间戳事件，便于验证 LWW 与幂等。
  SyncEvent buildEvent({
    required String entityType,
    required int entityId,
    required SyncOperation operation,
    required Map<String, dynamic> data,
    required int timestampMs,
    String deviceId = remoteDeviceId,
  }) {
    return SyncEvent(
      deviceId: deviceId,
      entityType: entityType,
      entityId: entityId,
      operation: operation,
      data: data,
      timestampMs: timestampMs,
    );
  }

  group('SyncService Phase3', () {
    test('persistIncomingEvents 空列表会直接返回（不写入日志）', () async {
      final db = createInMemoryDatabase();
      try {
        final service = SyncService(
          db: db,
          syncLogDao: SyncLogDao(db),
          syncEntityMappingDao: SyncEntityMappingDao(db),
          settingsDao: SettingsDao(db),
          secureStorageService: SecureStorageService(),
          localDeviceId: localDeviceId,
        );

        await service.persistIncomingEvents(const <SyncEvent>[]);
        final logs = await db.select(db.syncLogs).get();
        expect(logs, isEmpty);
      } finally {
        await db.close();
      }
    });

    test('persistIncomingEvents 重复事件会被 insertOrIgnore 去重', () async {
      final db = createInMemoryDatabase();
      try {
        final service = SyncService(
          db: db,
          syncLogDao: SyncLogDao(db),
          syncEntityMappingDao: SyncEntityMappingDao(db),
          settingsDao: SettingsDao(db),
          secureStorageService: SecureStorageService(),
          localDeviceId: localDeviceId,
        );

        final event = buildEvent(
          entityType: SyncService.entityTopic,
          entityId: 88,
          operation: SyncOperation.create,
          timestampMs: 1_700_000_000_000,
          data: <String, dynamic>{
            'name': '去重主题',
            'description': '用于校验 insertOrIgnore',
            'created_at': DateTime(2026, 3, 7, 9).toIso8601String(),
            'updated_at': DateTime(2026, 3, 7, 9).toIso8601String(),
          },
        );

        await service.persistIncomingEvents(<SyncEvent>[event, event]);
        await service.persistIncomingEvents(<SyncEvent>[event]);

        final logs = await db.select(db.syncLogs).get();
        expect(logs, hasLength(1));
        expect(logs.single.entityType, SyncService.entityTopic);
        expect(logs.single.entityId, 88);
      } finally {
        await db.close();
      }
    });

    test('applyIncomingEvents 会忽略空标题学习内容，并对未知实体类型静默跳过', () async {
      final db = createInMemoryDatabase();
      try {
        final service = SyncService(
          db: db,
          syncLogDao: SyncLogDao(db),
          syncEntityMappingDao: SyncEntityMappingDao(db),
          settingsDao: SettingsDao(db),
          secureStorageService: SecureStorageService(),
          localDeviceId: localDeviceId,
        );

        await service.applyIncomingEvents(
          <SyncEvent>[
          buildEvent(
            entityType: SyncService.entityLearningItem,
            entityId: 101,
            operation: SyncOperation.create,
            timestampMs: 1,
            data: <String, dynamic>{
              'title': '   ',
              'description': '空标题应该被忽略',
              'tags': <String>['忽略'],
              'learning_date': DateTime(2026, 3, 7).toIso8601String(),
              'created_at': DateTime(2026, 3, 7).toIso8601String(),
              'updated_at': DateTime(2026, 3, 7).toIso8601String(),
              'is_deleted': false,
              'is_mock_data': false,
            },
          ),
          buildEvent(
            entityType: 'unknown_entity',
            entityId: 1,
            operation: SyncOperation.create,
            timestampMs: 2,
            data: const <String, dynamic>{'foo': 'bar'},
          ),
        ],
          isMaster: false,
        );

        final items = await db.select(db.learningItems).get();
        expect(items, isEmpty);
      } finally {
        await db.close();
      }
    });

    test('applyIncomingEvents 对 learning_item 支持 Last-Write-Wins（较新事件覆盖）', () async {
      final db = createInMemoryDatabase();
      try {
        final service = SyncService(
          db: db,
          syncLogDao: SyncLogDao(db),
          syncEntityMappingDao: SyncEntityMappingDao(db),
          settingsDao: SettingsDao(db),
          secureStorageService: SecureStorageService(),
          localDeviceId: localDeviceId,
        );

        final baseDate = DateTime(2026, 3, 7, 9);

        await service.applyIncomingEvents(
          <SyncEvent>[
          buildEvent(
            entityType: SyncService.entityLearningItem,
            entityId: 777,
            operation: SyncOperation.create,
            timestampMs: 1000,
            data: <String, dynamic>{
              'title': '旧标题',
              'description': '旧描述',
              'note': null,
              'tags': <String>['同步'],
              'learning_date': baseDate.toIso8601String(),
              'created_at': baseDate.toIso8601String(),
              'updated_at': baseDate.toIso8601String(),
              'deleted_at': null,
              'is_deleted': false,
              'is_mock_data': false,
            },
          ),
        ],
          isMaster: false,
        );

        var items = await db.select(db.learningItems).get();
        expect(items, hasLength(1));
        expect(items.single.title, '旧标题');

        await service.applyIncomingEvents(
          <SyncEvent>[
          buildEvent(
            entityType: SyncService.entityLearningItem,
            entityId: 777,
            operation: SyncOperation.update,
            timestampMs: 2000,
            data: <String, dynamic>{
              'title': '新标题',
              'description': '新描述',
              'tags': <String>['同步', '覆盖'],
              'learning_date': baseDate.toIso8601String(),
              'created_at': baseDate.toIso8601String(),
              'updated_at': baseDate.add(const Duration(hours: 1)).toIso8601String(),
              'is_deleted': false,
              'is_mock_data': false,
            },
          ),
        ],
          isMaster: false,
        );

        items = await db.select(db.learningItems).get();
        expect(items.single.title, '新标题');

        // 应用更旧的事件，不应回滚到旧标题。
        await service.applyIncomingEvents(
          <SyncEvent>[
          buildEvent(
            entityType: SyncService.entityLearningItem,
            entityId: 777,
            operation: SyncOperation.update,
            timestampMs: 1500,
            data: <String, dynamic>{
              'title': '更旧标题',
              'learning_date': baseDate.toIso8601String(),
              'created_at': baseDate.toIso8601String(),
              'updated_at': baseDate.toIso8601String(),
              'is_deleted': false,
              'is_mock_data': false,
            },
          ),
        ],
          isMaster: false,
        );

        items = await db.select(db.learningItems).get();
        expect(items.single.title, '新标题');
      } finally {
        await db.close();
      }
    });
  });
}
