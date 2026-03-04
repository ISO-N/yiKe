// 文件用途：带 SyncLogWriter 的仓储实现集成测试，用于覆盖各仓储的同步分支（写入 sync_logs 与 mappings）。
// 作者：Codex
// 创建日期：2026-02-26

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/learning_item_dao.dart';
import 'package:yike/data/database/daos/learning_template_dao.dart';
import 'package:yike/data/database/daos/learning_topic_dao.dart';
import 'package:yike/data/database/daos/review_task_dao.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/daos/sync_entity_mapping_dao.dart';
import 'package:yike/data/database/daos/sync_log_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/learning_item_repository_impl.dart';
import 'package:yike/data/repositories/learning_template_repository_impl.dart';
import 'package:yike/data/repositories/learning_topic_repository_impl.dart';
import 'package:yike/data/repositories/review_task_repository_impl.dart';
import 'package:yike/data/repositories/theme_settings_repository_impl.dart';
import 'package:yike/data/sync/sync_log_writer.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/learning_template.dart';
import 'package:yike/domain/entities/learning_topic.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/theme_settings.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../helpers/test_database.dart';
import '../helpers/test_uuid.dart';

void main() {
  late AppDatabase db;
  late SyncLogDao logDao;
  late SyncEntityMappingDao mappingDao;
  late SyncLogWriter writer;
  var uuidSeed = 1;

  setUp(() {
    db = createInMemoryDatabase();
    logDao = SyncLogDao(db);
    mappingDao = SyncEntityMappingDao(db);
    writer = SyncLogWriter(
      syncLogDao: logDao,
      syncEntityMappingDao: mappingDao,
      localDeviceId: 'local',
    );
  });

  tearDown(() async {
    await db.close();
  });

  test('LearningItemRepositoryImpl：create/update/delete 会写入同步日志', () async {
    final repo = LearningItemRepositoryImpl(
      LearningItemDao(db),
      syncLogWriter: writer,
    );

    final created = await repo.create(
      LearningItemEntity(
        uuid: testUuid(uuidSeed++),
        id: null,
        title: 't',
        note: 'n',
        tags: const ['a', 'b'],
        learningDate: DateTime(2026, 2, 26),
        createdAt: DateTime(2026, 2, 26),
        updatedAt: null,
      ),
    );

    final updated = await repo.update(created.copyWith(title: 't2'));
    await repo.delete(updated.id!);

    final logs = await logDao.getLogsSince(0);
    expect(logs.where((e) => e.entityType == 'learning_item').length, 3);
  });

  test(
    'LearningTemplateRepositoryImpl：create/update/updateSortOrders/delete 会写入同步日志',
    () async {
      final repo = LearningTemplateRepositoryImpl(
        LearningTemplateDao(db),
        syncLogWriter: writer,
      );

      final t1 = await repo.create(
        LearningTemplateEntity(
          uuid: testUuid(uuidSeed++),
          id: null,
          name: 'T1',
          titlePattern: '{date} - T1',
          notePattern: null,
          tags: const ['x'],
          sortOrder: 1,
          createdAt: DateTime(2026, 2, 26),
          updatedAt: null,
        ),
      );
      final t2 = await repo.create(
        LearningTemplateEntity(
          uuid: testUuid(uuidSeed++),
          id: null,
          name: 'T2',
          titlePattern: '{date} - T2',
          notePattern: 'note',
          tags: const [],
          sortOrder: 2,
          createdAt: DateTime(2026, 2, 26),
          updatedAt: null,
        ),
      );

      await repo.update(t1.copyWith(name: 'T1b'));
      await repo.updateSortOrders({t1.id!: 10, t2.id!: 20});
      await repo.delete(t2.id!);

      final logs = await logDao.getLogsSince(0);
      final templateLogs = logs
          .where((e) => e.entityType == 'learning_template')
          .toList();
      // 2 次 create + 1 次 update + 2 次 sortOrder update + 1 次 delete
      expect(templateLogs.length, 6);
    },
  );

  test(
    'LearningTopicRepositoryImpl：create/update/add/remove/delete 会写入同步日志',
    () async {
      final itemRepo = LearningItemRepositoryImpl(
        LearningItemDao(db),
        syncLogWriter: writer,
      );
      final topicRepo = LearningTopicRepositoryImpl(
        LearningTopicDao(db),
        syncLogWriter: writer,
      );

      final item = await itemRepo.create(
        LearningItemEntity(
          uuid: testUuid(uuidSeed++),
          id: null,
          title: 'item',
          note: null,
          tags: const [],
          learningDate: DateTime(2026, 2, 26),
          createdAt: DateTime(2026, 2, 26),
          updatedAt: null,
        ),
      );

      final topic = await topicRepo.create(
        LearningTopicEntity(
          uuid: testUuid(uuidSeed++),
          id: null,
          name: 'topic',
          description: 'desc',
          itemIds: const [],
          createdAt: DateTime(2026, 2, 26),
          updatedAt: null,
        ),
      );

      await topicRepo.update(topic.copyWith(name: 'topic2'));
      await topicRepo.addItemToTopic(topic.id!, item.id!);
      await topicRepo.removeItemFromTopic(topic.id!, item.id!);
      await topicRepo.delete(topic.id!);

      final logs = await logDao.getLogsSince(0);
      expect(logs.any((e) => e.entityType == 'learning_topic'), isTrue);
      expect(logs.any((e) => e.entityType == 'topic_item_relation'), isTrue);
    },
  );

  test('ReviewTaskRepositoryImpl：create/complete/skip/批量 会写入同步日志', () async {
    final itemRepo = LearningItemRepositoryImpl(
      LearningItemDao(db),
      syncLogWriter: writer,
    );
    final taskRepo = ReviewTaskRepositoryImpl(
      dao: ReviewTaskDao(db),
      syncLogWriter: writer,
    );

    final item = await itemRepo.create(
      LearningItemEntity(
        uuid: testUuid(uuidSeed++),
        id: null,
        title: 'item',
        note: null,
        tags: const [],
        learningDate: DateTime(2026, 2, 26),
        createdAt: DateTime(2026, 2, 26),
        updatedAt: null,
      ),
    );

    final t1 = await taskRepo.create(
      ReviewTaskEntity(
        uuid: testUuid(uuidSeed++),
        id: null,
        learningItemId: item.id!,
        reviewRound: 1,
        scheduledDate: DateTime(2026, 2, 27),
        status: ReviewTaskStatus.pending,
        completedAt: null,
        skippedAt: null,
        createdAt: DateTime(2026, 2, 26),
      ),
    );
    final t2 = await taskRepo.create(
      ReviewTaskEntity(
        uuid: testUuid(uuidSeed++),
        id: null,
        learningItemId: item.id!,
        reviewRound: 2,
        scheduledDate: DateTime(2026, 2, 28),
        status: ReviewTaskStatus.pending,
        completedAt: null,
        skippedAt: null,
        createdAt: DateTime(2026, 2, 26),
      ),
    );

    await taskRepo.completeTask(t1.id!);
    await taskRepo.skipTask(t2.id!);
    await taskRepo.completeTasks([t1.id!, t2.id!]);
    await taskRepo.skipTasks([t1.id!, t2.id!]);

    final logs = await logDao.getLogsSince(0);
    final taskLogs = logs.where((e) => e.entityType == 'review_task').toList();
    expect(taskLogs.isNotEmpty, isTrue);
  });

  test('ThemeSettingsRepositoryImpl：读写兼容 + 同步日志写入', () async {
    final repo = ThemeSettingsRepositoryImpl(
      dao: SettingsDao(db),
      secureStorageService: SecureStorageService(),
      syncLogWriter: writer,
    );

    // 1) 空数据返回默认值
    expect((await repo.getThemeSettings()).mode, 'system');

    // 2) 兼容：解密失败 + 兜底 normalize
    await SettingsDao(db).upsertValue('theme_mode', 'enc:v1:invalid');
    expect((await repo.getThemeSettings()).mode, 'system');

    // 3) 兼容：明文 JSON string
    await SettingsDao(db).upsertValue('theme_mode', jsonEncode('dark'));
    expect((await repo.getThemeSettings()).mode, 'dark');

    // 4) 保存后可读取，并写入 sync_logs（settings_bundle:update）
    await repo.saveThemeSettings(
      const ThemeSettingsEntity(
        mode: 'light',
        seedColorHex: '#2196F3',
        amoled: false,
      ),
    );
    expect((await repo.getThemeSettings()).mode, 'light');

    final logs = await logDao.getLogsSince(0);
    final settingsLogs = logs
        .where((e) => e.entityType == 'settings_bundle')
        .toList();
    expect(settingsLogs.isNotEmpty, isTrue);
    expect(settingsLogs.last.operation, 'update');
    final decoded = jsonDecode(settingsLogs.last.data) as Map<String, dynamic>;
    expect(decoded['theme_mode'], 'light');
    expect(decoded['theme_seed_color'], '#2196F3');
    expect(decoded['theme_amoled'], false);
  });
}
