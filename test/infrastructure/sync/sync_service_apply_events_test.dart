// 文件用途：SyncService 远端事件应用与交换测试，覆盖主要实体同步、映射更新与首次交换快照兜底。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/settings_repository_impl.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';
import 'package:yike/infrastructure/sync/sync_models.dart';
import 'package:yike/infrastructure/sync/sync_service.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late SyncService service;
  late SyncEntityMappingDao mappingDao;
  late SyncLogDao syncLogDao;
  late SettingsDao settingsDao;
  late SecureStorageService secureStorage;

  const remoteDeviceId = 'remote-device';
  const localDeviceId = 'local-device';

  /// 构造一个稳定时间戳事件，便于验证 Last-Write-Wins 与映射推进。
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

  setUp(() {
    db = createInMemoryDatabase();
    mappingDao = SyncEntityMappingDao(db);
    syncLogDao = SyncLogDao(db);
    settingsDao = SettingsDao(db);
    secureStorage = SecureStorageService();
    service = SyncService(
      db: db,
      syncLogDao: syncLogDao,
      syncEntityMappingDao: mappingDao,
      settingsDao: settingsDao,
      secureStorageService: secureStorage,
      localDeviceId: localDeviceId,
    );
  });

  tearDown(() async {
    await db.close();
  });

  group('SyncService.applyIncomingEvents', () {
    test('可创建、更新、删除主要实体并同步写入设置包', () async {
      const baseTs = 1_710_000_000_000;
      final itemCreatedAt = DateTime(2026, 3, 6, 9);
      final taskScheduledAt = DateTime(2026, 3, 7);

      final createEvents = <SyncEvent>[
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 101,
          operation: SyncOperation.create,
          timestampMs: baseTs,
          data: <String, dynamic>{
            'title': '远端学习内容',
            'description': '远端描述',
            'note': '旧备注',
            'tags': <String>['同步', '英语'],
            'learning_date': itemCreatedAt.toIso8601String(),
            'created_at': itemCreatedAt.toIso8601String(),
            'updated_at': itemCreatedAt.toIso8601String(),
            'is_deleted': false,
            'is_mock_data': false,
          },
        ),
        buildEvent(
          entityType: SyncService.entityLearningSubtask,
          entityId: 201,
          operation: SyncOperation.create,
          timestampMs: baseTs + 1,
          data: <String, dynamic>{
            'uuid': 'remote-subtask-uuid',
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 101,
            'content': '远端子任务',
            'sort_order': 0,
            'created_at': itemCreatedAt.toIso8601String(),
            'updated_at': itemCreatedAt.toIso8601String(),
            'is_mock_data': false,
          },
        ),
        buildEvent(
          entityType: SyncService.entityReviewTask,
          entityId: 301,
          operation: SyncOperation.create,
          timestampMs: baseTs + 2,
          data: <String, dynamic>{
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 101,
            'review_round': 1,
            'scheduled_date': taskScheduledAt.toIso8601String(),
            'occurred_at': taskScheduledAt.toIso8601String(),
            'status': 'pending',
            'created_at': itemCreatedAt.toIso8601String(),
            'updated_at': itemCreatedAt.toIso8601String(),
            'is_mock_data': false,
          },
        ),
        buildEvent(
          entityType: SyncService.entityTemplate,
          entityId: 401,
          operation: SyncOperation.create,
          timestampMs: baseTs + 3,
          data: <String, dynamic>{
            'name': '远端模板',
            'title_pattern': '模板 {date}',
            'note_pattern': '模板备注',
            'tags': <String>['模板'],
            'sort_order': 1,
            'created_at': itemCreatedAt.toIso8601String(),
            'updated_at': itemCreatedAt.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityTopic,
          entityId: 501,
          operation: SyncOperation.create,
          timestampMs: baseTs + 4,
          data: <String, dynamic>{
            'name': '远端主题',
            'description': '主题描述',
            'created_at': itemCreatedAt.toIso8601String(),
            'updated_at': itemCreatedAt.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityTopicItemRelation,
          entityId: 601,
          operation: SyncOperation.create,
          timestampMs: baseTs + 5,
          data: <String, dynamic>{
            'topic_origin_device_id': remoteDeviceId,
            'topic_origin_entity_id': 501,
            'item_origin_device_id': remoteDeviceId,
            'item_origin_entity_id': 101,
            'created_at': itemCreatedAt.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entitySettingsBundle,
          entityId: 1,
          operation: SyncOperation.update,
          timestampMs: baseTs + 6,
          data: <String, dynamic>{
            'notification_time': '08:15',
            'do_not_disturb_start': '23:30',
            'do_not_disturb_end': '07:30',
            'notification_enabled': false,
            'notification_overdue_enabled': false,
            'notification_goal_enabled': true,
            'notification_streak_enabled': false,
            'review_intervals': <Map<String, dynamic>>[
              <String, dynamic>{'round': 1, 'interval': 1, 'enabled': true},
            ],
            'theme_mode': 'dark',
            'theme_seed_color': '#4CAF50',
            'theme_amoled': true,
          },
        ),
      ];

      await service.applyIncomingEvents(createEvents, isMaster: false);

      final learningItem = await db.select(db.learningItems).getSingle();
      final subtask = await db.select(db.learningSubtasks).getSingle();
      final reviewTask = await db.select(db.reviewTasks).getSingle();
      final template = await db.select(db.learningTemplates).getSingle();
      final topic = await db.select(db.learningTopics).getSingle();
      final relation = await db.select(db.topicItemRelations).getSingle();

      expect(learningItem.title, '远端学习内容');
      expect(jsonDecode(learningItem.tags), <dynamic>['同步', '英语']);
      expect(subtask.content, '远端子任务');
      expect(reviewTask.status, 'pending');
      expect(template.name, '远端模板');
      expect(topic.name, '远端主题');
      expect(relation.topicId, topic.id);
      expect(relation.learningItemId, learningItem.id);

      final itemMapping = await mappingDao.getMapping(
        entityType: SyncService.entityLearningItem,
        originDeviceId: remoteDeviceId,
        originEntityId: 101,
      );
      final relationMapping = await mappingDao.getMapping(
        entityType: SyncService.entityTopicItemRelation,
        originDeviceId: remoteDeviceId,
        originEntityId: 601,
      );
      expect(itemMapping?.localEntityId, learningItem.id);
      expect(relationMapping?.localEntityId, relation.id);

      final settingsRepo = SettingsRepositoryImpl(
        dao: settingsDao,
        secureStorageService: secureStorage,
      );
      final settings = await settingsRepo.getSettings();
      expect(settings.reminderTime, '08:15');
      expect(settings.doNotDisturbStart, '23:30');
      expect(settings.notificationsEnabled, isFalse);
      expect(await settingsDao.getValue('theme_mode'), isNotNull);

      final updateAndDeleteEvents = <SyncEvent>[
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 101,
          operation: SyncOperation.update,
          timestampMs: baseTs + 10,
          data: <String, dynamic>{
            'title': '远端学习内容-更新',
            'description': '描述已更新',
            'note': null,
            'tags': <String>['同步', '更新'],
            'learning_date': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
            'updated_at': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
            'is_deleted': true,
            'deleted_at': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
            'is_mock_data': true,
          },
        ),
        buildEvent(
          entityType: SyncService.entityLearningSubtask,
          entityId: 201,
          operation: SyncOperation.update,
          timestampMs: baseTs + 11,
          data: <String, dynamic>{
            'uuid': 'remote-subtask-uuid',
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 101,
            'content': '远端子任务-更新',
            'sort_order': 2,
            'updated_at': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
            'is_mock_data': true,
          },
        ),
        buildEvent(
          entityType: SyncService.entityReviewTask,
          entityId: 301,
          operation: SyncOperation.update,
          timestampMs: baseTs + 12,
          data: <String, dynamic>{
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 101,
            'review_round': 1,
            'scheduled_date': taskScheduledAt.toIso8601String(),
            'status': 'done',
            'completed_at': taskScheduledAt.add(const Duration(hours: 8)).toIso8601String(),
            'updated_at': taskScheduledAt.add(const Duration(hours: 8)).toIso8601String(),
            'is_mock_data': true,
          },
        ),
        buildEvent(
          entityType: SyncService.entityTemplate,
          entityId: 401,
          operation: SyncOperation.update,
          timestampMs: baseTs + 13,
          data: <String, dynamic>{
            'name': '远端模板-更新',
            'title_pattern': '模板升级 {weekday}',
            'note_pattern': null,
            'tags': <String>['更新'],
            'sort_order': 9,
            'updated_at': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityTopic,
          entityId: 501,
          operation: SyncOperation.update,
          timestampMs: baseTs + 14,
          data: <String, dynamic>{
            'name': '远端主题-更新',
            'description': '主题描述已更新',
            'updated_at': itemCreatedAt.add(const Duration(days: 1)).toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityTopicItemRelation,
          entityId: 601,
          operation: SyncOperation.create,
          timestampMs: baseTs + 15,
          data: <String, dynamic>{
            'topic_origin_device_id': remoteDeviceId,
            'topic_origin_entity_id': 501,
            'item_origin_device_id': remoteDeviceId,
            'item_origin_entity_id': 101,
            'created_at': itemCreatedAt.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityLearningSubtask,
          entityId: 201,
          operation: SyncOperation.delete,
          timestampMs: baseTs + 16,
          data: const <String, dynamic>{},
        ),
        buildEvent(
          entityType: SyncService.entityTemplate,
          entityId: 401,
          operation: SyncOperation.delete,
          timestampMs: baseTs + 17,
          data: const <String, dynamic>{},
        ),
        buildEvent(
          entityType: SyncService.entityTopicItemRelation,
          entityId: 601,
          operation: SyncOperation.delete,
          timestampMs: baseTs + 18,
          data: const <String, dynamic>{},
        ),
      ];

      await service.applyIncomingEvents(updateAndDeleteEvents, isMaster: false);

      final updatedItem = await db.select(db.learningItems).getSingle();
      final updatedTask = await db.select(db.reviewTasks).getSingle();
      final updatedTopic = await db.select(db.learningTopics).getSingle();

      expect(updatedItem.title, '远端学习内容-更新');
      expect(updatedItem.isDeleted, isTrue);
      expect(updatedItem.isMockData, isTrue);
      expect(await db.select(db.learningSubtasks).get(), isEmpty);
      expect(updatedTask.status, 'done');
      expect(updatedTask.completedAt, isNotNull);
      expect(updatedTopic.name, '远端主题-更新');
      expect(await db.select(db.learningTemplates).get(), isEmpty);
      expect(await db.select(db.topicItemRelations).get(), isEmpty);

      final deletedSubtaskMapping = await mappingDao.getMapping(
        entityType: SyncService.entityLearningSubtask,
        originDeviceId: remoteDeviceId,
        originEntityId: 201,
      );
      final deletedTemplateMapping = await mappingDao.getMapping(
        entityType: SyncService.entityTemplate,
        originDeviceId: remoteDeviceId,
        originEntityId: 401,
      );
      expect(deletedSubtaskMapping?.isDeleted, isTrue);
      expect(deletedTemplateMapping?.isDeleted, isTrue);
    });

    test('会忽略主机设置覆盖、过期事件与无效实体事件', () async {
      const baseTs = 1_720_000_000_000;
      final now = DateTime(2026, 3, 7, 10);

      final settingsRepo = SettingsRepositoryImpl(
        dao: settingsDao,
        secureStorageService: secureStorage,
      );
      await settingsRepo.saveSettings(
        const AppSettingsEntity(
          reminderTime: '09:00',
          doNotDisturbStart: '22:00',
          doNotDisturbEnd: '08:00',
          notificationsEnabled: true,
          overdueNotificationEnabled: true,
          goalNotificationEnabled: true,
          streakNotificationEnabled: true,
          notificationPermissionGuideDismissed: false,
          topicGuideDismissed: false,
        ),
      );

      await service.applyIncomingEvents(<SyncEvent>[
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 1,
          operation: SyncOperation.create,
          timestampMs: baseTs,
          data: <String, dynamic>{
            'title': '最新标题',
            'description': '初始描述',
            'learning_date': now.toIso8601String(),
            'created_at': now.toIso8601String(),
            'updated_at': now.toIso8601String(),
            'is_deleted': false,
            'is_mock_data': false,
          },
        ),
      ], isMaster: false);

      await service.applyIncomingEvents(<SyncEvent>[
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 1,
          operation: SyncOperation.update,
          timestampMs: baseTs + 100,
          data: <String, dynamic>{
            'title': '最新标题-更新',
            'description': '更新描述',
            'learning_date': now.toIso8601String(),
            'updated_at': now.add(const Duration(minutes: 10)).toIso8601String(),
            'is_deleted': false,
            'is_mock_data': false,
          },
        ),
        buildEvent(
          entityType: SyncService.entitySettingsBundle,
          entityId: 1,
          operation: SyncOperation.update,
          timestampMs: baseTs + 101,
          data: const <String, dynamic>{'notification_time': '18:30'},
        ),
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 99,
          operation: SyncOperation.create,
          timestampMs: baseTs + 102,
          data: const <String, dynamic>{'title': '   '},
        ),
        buildEvent(
          entityType: SyncService.entityLearningSubtask,
          entityId: 88,
          operation: SyncOperation.create,
          timestampMs: baseTs + 103,
          data: <String, dynamic>{
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 999,
            'content': '缺少父项映射',
            'created_at': now.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: SyncService.entityReviewTask,
          entityId: 77,
          operation: SyncOperation.create,
          timestampMs: baseTs + 104,
          data: <String, dynamic>{
            'learning_origin_device_id': remoteDeviceId,
            'learning_origin_entity_id': 999,
            'review_round': 1,
            'scheduled_date': now.toIso8601String(),
            'status': 'pending',
            'created_at': now.toIso8601String(),
          },
        ),
        buildEvent(
          entityType: 'unknown_entity',
          entityId: 66,
          operation: SyncOperation.create,
          timestampMs: baseTs + 105,
          data: const <String, dynamic>{'foo': 'bar'},
        ),
      ], isMaster: true);

      await service.applyIncomingEvents(<SyncEvent>[
        buildEvent(
          entityType: SyncService.entityLearningItem,
          entityId: 1,
          operation: SyncOperation.update,
          timestampMs: baseTs + 50,
          data: <String, dynamic>{
            'title': '过期标题',
            'description': '过期描述',
            'learning_date': now.toIso8601String(),
            'updated_at': now.subtract(const Duration(minutes: 10)).toIso8601String(),
            'is_deleted': false,
            'is_mock_data': false,
          },
        ),
      ], isMaster: false);

      final learningItems = await db.select(db.learningItems).get();
      final subtasks = await db.select(db.learningSubtasks).get();
      final reviewTasks = await db.select(db.reviewTasks).get();
      final settings = await settingsRepo.getSettings();

      expect(learningItems, hasLength(1));
      expect(learningItems.single.title, '最新标题-更新');
      expect(learningItems.single.description, '更新描述');
      expect(subtasks, isEmpty);
      expect(reviewTasks, isEmpty);
      expect(settings.reminderTime, '09:00');
    });
  });

  group('SyncService.handleExchangeRequest', () {
    test('首次交换会兜底生成本地快照并返回排除请求方后的增量', () async {
      final now = DateTime(2026, 3, 6, 10);
      final localItemId = await db.into(db.learningItems).insert(
        LearningItemsCompanion.insert(
          title: '本地学习内容',
          note: const drift.Value('本地备注'),
          tags: const drift.Value('["本地"]'),
          learningDate: now,
          createdAt: drift.Value(now),
          updatedAt: drift.Value(now),
        ),
      );

      final request = SyncExchangeRequest(
        fromDeviceId: 'peer-device',
        sinceMs: 0,
        events: <SyncEvent>[
          buildEvent(
            entityType: SyncService.entityTemplate,
            entityId: 900,
            operation: SyncOperation.create,
            timestampMs: now.millisecondsSinceEpoch,
            data: <String, dynamic>{
              'name': '交换进来的模板',
              'title_pattern': '交换 {date}',
              'note_pattern': null,
              'tags': <String>['交换'],
              'sort_order': 3,
              'created_at': now.toIso8601String(),
              'updated_at': now.toIso8601String(),
            },
          ),
        ],
      );

      final response = await service.handleExchangeRequest(
        request,
        isMaster: false,
        includeMockData: false,
      );

      expect(response.serverNowMs, greaterThan(0));
      expect(
        response.events.any(
          (event) =>
              event.deviceId == localDeviceId &&
              event.entityType == SyncService.entityLearningItem &&
              event.entityId == localItemId &&
              event.operation == SyncOperation.create,
        ),
        isTrue,
      );
      expect(
        response.events.any((event) => event.deviceId == request.fromDeviceId),
        isFalse,
      );

      final incomingTemplate = await db.select(db.learningTemplates).getSingle();
      expect(incomingTemplate.name, '交换进来的模板');

      final persistedLogs = await syncLogDao.getLogsFromDeviceSince(
        remoteDeviceId,
        0,
      );
      expect(persistedLogs, hasLength(1));

      final localLogs = await syncLogDao.getLogsFromDeviceSince(localDeviceId, 0);
      expect(
        localLogs.any((log) => log.entityType == SyncService.entityLearningItem),
        isTrue,
      );
    });
  });

  group('SyncService 日志构建与持久化', () {
    test('会构建本地增量、排除指定设备并持久化远端日志', () async {
      const baseTs = 1_730_000_000_000;
      await syncLogDao.insertLogs(<SyncLogsCompanion>[
        SyncLogsCompanion(
          deviceId: const drift.Value(localDeviceId),
          entityType: const drift.Value(SyncService.entityLearningItem),
          entityId: const drift.Value(1),
          operation: const drift.Value('create'),
          data: const drift.Value('{"title":"本地 1"}'),
          timestampMs: const drift.Value(baseTs),
          localVersion: const drift.Value(0),
        ),
        SyncLogsCompanion(
          deviceId: const drift.Value(localDeviceId),
          entityType: const drift.Value(SyncService.entityReviewTask),
          entityId: const drift.Value(2),
          operation: const drift.Value('update'),
          data: const drift.Value('{"status":"done"}'),
          timestampMs: const drift.Value(baseTs + 10),
          localVersion: const drift.Value(2),
        ),
        SyncLogsCompanion(
          deviceId: const drift.Value(remoteDeviceId),
          entityType: const drift.Value(SyncService.entityTemplate),
          entityId: const drift.Value(3),
          operation: const drift.Value('delete'),
          data: const drift.Value('{}'),
          timestampMs: const drift.Value(baseTs + 20),
          localVersion: const drift.Value(0),
        ),
      ]);

      final localEvents = await service.buildLocalEventsSince(baseTs + 1);
      expect(localEvents, hasLength(1));
      expect(localEvents.single.entityType, SyncService.entityReviewTask);
      expect(localEvents.single.operation, SyncOperation.update);
      expect(localEvents.single.localVersion, 2);

      final outgoing = await service.buildOutgoingEventsSince(
        baseTs,
        excludeDeviceId: remoteDeviceId,
      );
      expect(outgoing, hasLength(1));
      expect(outgoing.single.deviceId, localDeviceId);
      expect(outgoing.single.entityId, 2);

      final incomingEvent = buildEvent(
        entityType: SyncService.entityTopic,
        entityId: 8,
        operation: SyncOperation.create,
        timestampMs: baseTs + 30,
        data: <String, dynamic>{
          'name': '远端主题日志',
          'description': '用于校验持久化',
          'created_at': DateTime(2026, 3, 7, 9).toIso8601String(),
          'updated_at': DateTime(2026, 3, 7, 9).toIso8601String(),
        },
      );

      await service.persistIncomingEvents(<SyncEvent>[incomingEvent]);

      final remoteLogs = await syncLogDao.getLogsFromDeviceSince(remoteDeviceId, 0);
      expect(remoteLogs, hasLength(2));
      expect(
        remoteLogs.any(
          (row) =>
              row.entityType == SyncService.entityTopic &&
              row.entityId == 8 &&
              row.operation == 'create',
        ),
        isTrue,
      );
    });
  });
}
