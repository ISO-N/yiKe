// 文件用途：BackupRepositoryImpl 单元测试，覆盖导出、摘要读取、覆盖导入、统计与导入标记等关键链路。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/data/database/daos/backup_dao.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/backup_repository_impl.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/backup_file.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/entities/theme_settings.dart';
import 'package:yike/domain/repositories/settings_repository.dart';
import 'package:yike/domain/repositories/theme_settings_repository.dart';
import 'package:yike/infrastructure/storage/backup_storage.dart';

import '../helpers/test_database.dart';

/// 设置仓储假实现，用于控制导出输入与记录导入回写。
class _FakeSettingsRepository implements SettingsRepository {
  _FakeSettingsRepository({
    required this.appSettings,
    required this.reviewIntervals,
  });

  AppSettingsEntity appSettings;
  List<ReviewIntervalConfigEntity> reviewIntervals;
  AppSettingsEntity? savedSettings;
  List<ReviewIntervalConfigEntity>? savedIntervals;

  @override
  Future<AppSettingsEntity> getSettings() async => appSettings;

  @override
  Future<List<ReviewIntervalConfigEntity>> getReviewIntervalConfigs() async {
    return List<ReviewIntervalConfigEntity>.from(reviewIntervals);
  }

  @override
  Future<void> saveReviewIntervalConfigs(
    List<ReviewIntervalConfigEntity> configs,
  ) async {
    savedIntervals = List<ReviewIntervalConfigEntity>.from(configs);
    reviewIntervals = List<ReviewIntervalConfigEntity>.from(configs);
  }

  @override
  Future<void> saveSettings(AppSettingsEntity settings) async {
    savedSettings = settings;
    appSettings = settings;
  }
}

/// 主题仓储假实现，用于验证导入是否触发主题覆盖。
class _FakeThemeSettingsRepository implements ThemeSettingsRepository {
  _FakeThemeSettingsRepository(this.themeSettings);

  ThemeSettingsEntity themeSettings;
  ThemeSettingsEntity? savedTheme;

  @override
  Future<ThemeSettingsEntity> getThemeSettings() async => themeSettings;

  @override
  Future<void> saveThemeSettings(ThemeSettingsEntity settings) async {
    savedTheme = settings;
    themeSettings = settings;
  }
}

void main() {
  late AppDatabase db;
  late Directory tempDir;
  late SettingsDao settingsDao;
  late _FakeSettingsRepository settingsRepository;
  late _FakeThemeSettingsRepository themeRepository;
  late BackupRepositoryImpl repository;

  Future<({int itemId, int taskId})> seedExistingData({
    required String suffix,
    required bool isMockData,
  }) async {
    final createdAt = DateTime(2026, 3, 7, 9, 30);
    final itemId = await db.into(db.learningItems).insert(
      LearningItemsCompanion.insert(
        uuid: drift.Value('item-$suffix'),
        title: '原始内容-$suffix',
        description: drift.Value('原始描述-$suffix'),
        note: drift.Value('原始备注-$suffix'),
        tags: drift.Value(jsonEncode(<String>['历史', suffix])),
        learningDate: createdAt,
        createdAt: drift.Value(createdAt),
        updatedAt: drift.Value(createdAt),
        isMockData: drift.Value(isMockData),
      ),
    );

    await db.into(db.learningSubtasks).insert(
      LearningSubtasksCompanion.insert(
        uuid: drift.Value('subtask-$suffix'),
        learningItemId: itemId,
        content: '原始子任务-$suffix',
        sortOrder: const drift.Value(0),
        createdAt: createdAt,
        updatedAt: drift.Value(createdAt),
        isMockData: drift.Value(isMockData),
      ),
    );

    final taskId = await db.into(db.reviewTasks).insert(
      ReviewTasksCompanion.insert(
        uuid: drift.Value('task-$suffix'),
        learningItemId: itemId,
        reviewRound: 1,
        scheduledDate: createdAt.add(const Duration(days: 1)),
        occurredAt: drift.Value(createdAt.add(const Duration(days: 1))),
        status: const drift.Value('done'),
        completedAt: drift.Value(createdAt.add(const Duration(days: 1))),
        createdAt: drift.Value(createdAt),
        updatedAt: drift.Value(createdAt),
        isMockData: drift.Value(isMockData),
      ),
    );

    await db.into(db.reviewRecords).insert(
      ReviewRecordsCompanion.insert(
        uuid: drift.Value('record-$suffix'),
        reviewTaskId: taskId,
        action: 'done',
        occurredAt: createdAt.add(const Duration(days: 1)),
        createdAt: drift.Value(createdAt.add(const Duration(days: 1))),
      ),
    );

    return (itemId: itemId, taskId: taskId);
  }

  Future<BackupFileEntity> buildImportBackup() async {
    final data = BackupDataEntity(
      learningItems: const <BackupLearningItemEntity>[
        BackupLearningItemEntity(
          uuid: 'import-item',
          title: '导入内容',
          description: null,
          note: '主描述\n- 子任务一\n- 子任务二',
          tags: <String>['导入', 'Phase3'],
          learningDate: '2026-03-07T00:00:00.000',
          createdAt: '2026-03-07T08:00:00.000',
          updatedAt: '2026-03-07T08:30:00.000',
          isDeleted: false,
          deletedAt: null,
        ),
      ],
      learningSubtasks: const <BackupLearningSubtaskEntity>[],
      reviewTasks: const <BackupReviewTaskEntity>[
        BackupReviewTaskEntity(
          uuid: 'import-task',
          learningItemUuid: 'import-item',
          reviewRound: 2,
          scheduledDate: '2026-03-09T00:00:00.000',
          status: 'done',
          completedAt: '2026-03-09T09:00:00.000',
          skippedAt: null,
          createdAt: '2026-03-07T08:00:00.000',
          updatedAt: '2026-03-09T09:00:00.000',
        ),
      ],
      reviewRecords: const <BackupReviewRecordEntity>[
        BackupReviewRecordEntity(
          uuid: 'import-record',
          reviewTaskUuid: 'import-task',
          action: 'done',
          occurredAt: '2026-03-09T09:00:00.000',
          createdAt: '2026-03-09T09:00:00.000',
        ),
      ],
      settings: <String, dynamic>{
        'theme_mode': 'dark',
        'language': 'zh-CN',
        'review_intervals': <Map<String, dynamic>>[
          <String, dynamic>{'round': 2, 'interval': 3, 'enabled': true},
          <String, dynamic>{'round': 1, 'interval': 1, 'enabled': true},
        ],
        'notifications_enabled': false,
        'reminder_time': '10:45',
      },
    );
    final checksum = await BackupUtils.computeChecksumForDataInIsolate(
      data.toJson(),
    );
    return BackupFileEntity(
      schemaVersion: '1.1',
      appVersion: '1.2.3',
      dbSchemaVersion: 9,
      backupId: 'backup-import-id',
      createdAt: '2026-03-07T08:00:00+08:00',
      createdAtUtc: '2026-03-07T00:00:00.000Z',
      checksum: checksum.checksum,
      stats: BackupStatsEntity(
        learningItems: 1,
        reviewTasks: 1,
        reviewRecords: 1,
        payloadSize: checksum.payloadSize,
      ),
      data: data,
      platform: 'desktop',
      deviceModel: 'test-device',
    );
  }

  setUp(() async {
    db = createInMemoryDatabase();
    tempDir = await Directory.systemTemp.createTemp(
      'yike_backup_repository_test_',
    );
    settingsDao = SettingsDao(db);
    settingsRepository = _FakeSettingsRepository(
      appSettings: AppSettingsEntity.defaults.copyWith(
        reminderTime: '08:30',
        notificationPermissionGuideDismissed: true,
      ),
      reviewIntervals: <ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
      ],
    );
    themeRepository = _FakeThemeSettingsRepository(
      const ThemeSettingsEntity(
        mode: 'system',
        seedColorHex: '#2196F3',
        amoled: false,
      ),
    );
    repository = BackupRepositoryImpl(
      db: db,
      backupDao: BackupDao(db),
      settingsRepository: settingsRepository,
      themeSettingsRepository: themeRepository,
      storage: BackupStorage(baseDir: tempDir),
    );
  });

  tearDown(() async {
    await db.close();
    if (await tempDir.exists()) {
      await tempDir.delete(recursive: true);
    }
  });

  group('BackupRepositoryImpl', () {
    test('exportBackup 与快照摘要链路会写入文件、meta 并可回读列表', () async {
      await seedExistingData(suffix: 'live', isMockData: false);
      await seedExistingData(suffix: 'mock', isMockData: true);

      final summary = await repository.exportBackup(
        cancelToken: BackupCancelToken(),
      );
      final latestList = await repository.getBackupList();

      expect(await summary.file.exists(), isTrue);
      expect(summary.fileName, endsWith('.yikebackup'));
      expect(summary.stats.learningItems, 1);
      expect(summary.stats.reviewTasks, 1);
      expect(summary.stats.reviewRecords, 1);
      expect(await File('${summary.file.path}.meta.json').exists(), isTrue);
      expect(latestList, hasLength(1));
      expect(latestList.single.backupId, summary.backupId);

      final payload =
          jsonDecode(await summary.file.readAsString()) as Map<String, dynamic>;
      final data = payload['data'] as Map<String, dynamic>;
      expect((data['learningItems'] as List<dynamic>), hasLength(1));
      expect((data['learningSubtasks'] as List<dynamic>), hasLength(1));
      expect((data['reviewTasks'] as List<dynamic>), hasLength(1));
      expect((data['reviewRecords'] as List<dynamic>), hasLength(1));

      final snapshot = await repository.createImportSnapshot(
        cancelToken: BackupCancelToken(),
      );
      final latestSnapshot = await repository.getLatestSnapshot();
      expect(snapshot.isSnapshot, isTrue);
      expect(latestSnapshot?.backupId, snapshot.backupId);
      expect(latestSnapshot?.isSnapshot, isTrue);
    });

    test('readBackupFile 会规范化旧备份缺失的顶层字段', () async {
      final file = File('${tempDir.path}${Platform.pathSeparator}legacy.yikebackup');
      await file.writeAsString(
        jsonEncode(<String, dynamic>{
          'schemaVersion': ' ',
          'appVersion': '0.9.0',
          'dbSchemaVersion': 7,
          'backupId': ' ',
          'createdAt': ' ',
          'createdAtUtc': '',
          'checksum': 'sha256:legacy',
          'platform': 'desktop',
          'deviceModel': 'old-pc',
          'data': const <String, dynamic>{
            'learningItems': <dynamic>[],
            'learningSubtasks': <dynamic>[],
            'reviewTasks': <dynamic>[],
            'reviewRecords': <dynamic>[],
            'settings': <String, dynamic>{},
          },
        }),
        flush: true,
      );

      final parsed = await repository.readBackupFile(
        file: file,
        cancelToken: BackupCancelToken(),
      );

      expect(parsed.schemaVersion, '1.1');
      expect(parsed.backupId, isNotEmpty);
      expect(parsed.createdAt, isNotEmpty);
      expect(parsed.createdAtUtc, isNotEmpty);
      expect(parsed.platform, 'desktop');
      expect(parsed.deviceModel, 'old-pc');
    });

    test('overwrite 导入会保留 mock 数据、迁移旧 note 结构并只导入允许的设置', () async {
      await seedExistingData(suffix: 'live-old', isMockData: false);
      await seedExistingData(suffix: 'mock-old', isMockData: true);
      await settingsDao.upsertValue('reminder_time', '"06:30"');
      await settingsDao.upsertValue('notifications_enabled', 'true');

      final backup = await buildImportBackup();
      await repository.importBackup(
        backup: backup,
        overwrite: true,
        createSnapshotBeforeOverwrite: true,
        cancelToken: BackupCancelToken(),
      );

      final snapshot = await repository.getLatestSnapshot();
      final items = await db.select(db.learningItems).get();
      final importedItem = items.singleWhere((item) => item.uuid == 'import-item');
      final subtasks = await (db.select(
        db.learningSubtasks,
      )..where((t) => t.learningItemId.equals(importedItem.id))).get();
      final importedTask = await (db.select(
        db.reviewTasks,
      )..where((t) => t.uuid.equals('import-task'))).getSingle();
      final importedRecords = await db.select(db.reviewRecords).get();

      expect(snapshot, isNotNull);
      expect(items.any((item) => item.uuid == 'item-live-old'), isFalse);
      expect(items.any((item) => item.uuid == 'item-mock-old'), isTrue);
      expect(importedItem.description, isNull);
      expect(importedItem.note, isNull);
      expect(importedItem.isMockData, isFalse);
      expect(subtasks.map((item) => item.content).toList(), <String>[
        '主描述',
        '子任务一',
        '子任务二',
      ]);
      expect(importedTask.status, 'done');
      expect(importedTask.completedAt, isNotNull);
      expect(importedTask.occurredAt, importedTask.completedAt);
      expect(
        importedRecords.any((record) => record.uuid == 'import-record'),
        isTrue,
      );

      expect(themeRepository.savedTheme?.mode, 'dark');
      expect(
        settingsRepository.savedIntervals?.map((item) => item.round).toList(),
        <int>[1, 2],
      );
      expect(await settingsDao.getValue('language'), isNotNull);
      expect(await settingsDao.getValue('reminder_time'), isNull);
      expect(await settingsDao.getValue('notifications_enabled'), isNull);
    });

    test('统计与导入标记会过滤 mock 数据并支持空白保护', () async {
      await seedExistingData(suffix: 'live-stats', isMockData: false);
      await seedExistingData(suffix: 'mock-stats', isMockData: true);

      final stats = await repository.getCurrentUserDataStats();
      expect(stats.learningItems, 1);
      expect(stats.reviewTasks, 1);
      expect(stats.reviewRecords, 1);

      expect(await repository.hasImportedBackupId('   '), isFalse);
      expect(await repository.hasImportedChecksum('   '), isFalse);

      await repository.markBackupImported(
        backupId: 'imported-id',
        checksum: 'sha256:imported',
        importedAtUtc: '2026-03-07T00:00:00.000Z',
      );

      expect(await repository.hasImportedBackupId('imported-id'), isTrue);
      expect(await repository.hasImportedChecksum('sha256:imported'), isTrue);
    });
  });
}
