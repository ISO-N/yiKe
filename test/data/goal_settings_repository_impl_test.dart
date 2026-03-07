// 文件用途：GoalSettingsRepositoryImpl 单元测试，覆盖默认值、显式 null、异常回退与同步日志写入。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/goal_settings_repository_impl.dart';
import 'package:yike/data/sync/sync_log_writer.dart';
import 'package:yike/domain/entities/goal_settings.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late SettingsDao settingsDao;
  late GoalSettingsRepositoryImpl repository;
  late SecureStorageService secureStorageService;

  setUp(() {
    db = createInMemoryDatabase();
    settingsDao = SettingsDao(db);
    secureStorageService = SecureStorageService();
    repository = GoalSettingsRepositoryImpl(
      dao: settingsDao,
      secureStorageService: secureStorageService,
    );
  });

  tearDown(() async {
    await db.close();
  });

  group('GoalSettingsRepositoryImpl', () {
    test('getGoalSettings 会处理默认值、显式 null 与损坏数据回退', () async {
      final defaults = await repository.getGoalSettings();
      expect(defaults.dailyTarget, 10);
      expect(defaults.streakTarget, 7);
      expect(defaults.weeklyRateTarget, 80);

      await repository.saveGoalSettings(
        const GoalSettingsEntity(
          dailyTarget: null,
          streakTarget: 14,
          weeklyRateTarget: null,
        ),
      );

      final saved = await repository.getGoalSettings();
      expect(saved.dailyTarget, isNull);
      expect(saved.streakTarget, 14);
      expect(saved.weeklyRateTarget, isNull);

      await settingsDao.upsertValue(GoalSettingsRepositoryImpl.keyGoalDaily, '坏数据');
      final degraded = await repository.getGoalSettings();
      expect(degraded.dailyTarget, 10);
      expect(degraded.streakTarget, 14);
      expect(degraded.weeklyRateTarget, isNull);
    });

    test('saveGoalSettings 会写入加密设置并同步 settings_bundle 日志', () async {
      final syncLogDao = SyncLogDao(db);
      final mappingDao = SyncEntityMappingDao(db);
      final syncLogWriter = SyncLogWriter(
        syncLogDao: syncLogDao,
        syncEntityMappingDao: mappingDao,
        localDeviceId: 'goal-device',
      );
      final syncedRepository = GoalSettingsRepositoryImpl(
        dao: settingsDao,
        secureStorageService: secureStorageService,
        syncLogWriter: syncLogWriter,
      );

      await syncedRepository.saveGoalSettings(
        const GoalSettingsEntity(
          dailyTarget: 12,
          streakTarget: null,
          weeklyRateTarget: 95,
        ),
      );

      final stored = await syncedRepository.getGoalSettings();
      final logs = await syncLogDao.getLogsFromDeviceSince('goal-device', 0);
      final mapping = await mappingDao.getByLocalEntityId(
        entityType: 'settings_bundle',
        localEntityId: 1,
      );

      expect(stored.dailyTarget, 12);
      expect(stored.streakTarget, isNull);
      expect(stored.weeklyRateTarget, 95);
      expect(logs, hasLength(1));
      expect(logs.single.entityType, 'settings_bundle');
      expect(logs.single.operation, 'update');
      expect(jsonDecode(logs.single.data), <String, dynamic>{
        'goal_daily': 12,
        'goal_streak': null,
        'goal_weekly_rate': 95,
      });
      expect(mapping?.originDeviceId, 'goal-device');
      expect(mapping?.originEntityId, 1);
    });
  });
}
