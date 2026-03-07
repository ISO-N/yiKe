// 文件用途：Phase 2 数据安全主链路集成测试，覆盖导出、备份、恢复与去重行为。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';
import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/data/database/daos/backup_dao.dart';
import 'package:yike/data/database/daos/learning_item_dao.dart';
import 'package:yike/data/database/daos/learning_subtask_dao.dart';
import 'package:yike/data/database/daos/review_task_dao.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/repositories/backup_repository_impl.dart';
import 'package:yike/data/repositories/learning_item_repository_impl.dart';
import 'package:yike/data/repositories/learning_subtask_repository_impl.dart';
import 'package:yike/data/repositories/review_task_repository_impl.dart';
import 'package:yike/data/repositories/settings_repository_impl.dart';
import 'package:yike/data/repositories/theme_settings_repository_impl.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/usecases/export_backup_usecase.dart';
import 'package:yike/domain/usecases/export_data_usecase.dart';
import 'package:yike/domain/usecases/import_backup_usecase.dart';
import 'package:yike/infrastructure/storage/backup_storage.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../helpers/test_data_factory.dart';
import '../helpers/test_database.dart';

/// PathProviderPlatform 假实现：将导出文件写入测试临时目录。
class _FakePathProviderPlatform extends PathProviderPlatform {
  _FakePathProviderPlatform(this.documentsPath);

  final String documentsPath;

  @override
  Future<String?> getApplicationDocumentsPath() async => documentsPath;
}

void main() {
  test('4.4 导出、备份、恢复链路会保持数据一致且重复恢复不产生重复记录', () async {
    final db = createInMemoryDatabase();
    final tempDir = await Directory.systemTemp.createTemp(
      'yike_phase2_backup_',
    );
    final originalPathProvider = PathProviderPlatform.instance;
    PathProviderPlatform.instance = _FakePathProviderPlatform(tempDir.path);

    addTearDown(() async {
      PathProviderPlatform.instance = originalPathProvider;
      await db.close();
      if (tempDir.existsSync()) {
        await tempDir.delete(recursive: true);
      }
    });

    final secureStorage = SecureStorageService();
    final settingsDao = SettingsDao(db);
    final settingsRepository = SettingsRepositoryImpl(
      dao: settingsDao,
      secureStorageService: secureStorage,
    );
    final themeRepository = ThemeSettingsRepositoryImpl(
      dao: settingsDao,
      secureStorageService: secureStorage,
    );
    final backupRepository = BackupRepositoryImpl(
      db: db,
      backupDao: BackupDao(db),
      settingsRepository: settingsRepository,
      themeSettingsRepository: themeRepository,
      storage: BackupStorage(baseDir: tempDir),
    );
    final exportDataUseCase = ExportDataUseCase(
      learningItemRepository: LearningItemRepositoryImpl(LearningItemDao(db)),
      learningSubtaskRepository: LearningSubtaskRepositoryImpl(
        dao: LearningSubtaskDao(db),
      ),
      reviewTaskRepository: ReviewTaskRepositoryImpl(dao: ReviewTaskDao(db)),
    );
    final exportBackupUseCase = ExportBackupUseCase(
      repository: backupRepository,
    );
    final importBackupUseCase = ImportBackupUseCase(
      repository: backupRepository,
    );

    final container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
    addTearDown(container.dispose);

    await TestDataFactory.createLearningItemWithPlan(
      container,
      title: '备份链路内容',
      description: '用于验证备份恢复一致性',
      tags: const <String>['备份', '恢复'],
    );

    final exportResult = await exportDataUseCase.execute(
      const ExportParams(
        format: ExportFormat.json,
        includeItems: true,
        includeTasks: true,
      ),
    );
    final exportJson = jsonDecode(
      await exportResult.file.readAsString(),
    ) as Map<String, dynamic>;
    expect(
      (exportJson['items'] as List<dynamic>).cast<Map<String, dynamic>>().any(
        (item) => item['title'] == '备份链路内容',
      ),
      isTrue,
    );
    expect((exportJson['tasks'] as List<dynamic>).length, greaterThanOrEqualTo(1));

    final backupSummary = await exportBackupUseCase.execute(
      cancelToken: BackupCancelToken(),
    );
    expect(await backupSummary.file.exists(), isTrue);
    expect((await backupRepository.getBackupList()).length, 1);

    await TestDataFactory.createLearningItemWithPlan(
      container,
      title: '恢复前新增内容',
      description: '用于验证覆盖恢复会清理非备份数据',
    );
    expect(
      (await container.read(learningItemRepositoryProvider).getAll()).length,
      2,
    );

    final preview = await importBackupUseCase.preview(
      file: backupSummary.file,
      cancelToken: BackupCancelToken(),
    );
    await importBackupUseCase.execute(
      preview: preview,
      strategy: BackupImportStrategy.overwrite,
      cancelToken: BackupCancelToken(),
    );

    final restoredItems = await container.read(learningItemRepositoryProvider).getAll();
    final restoredTasks = await container.read(reviewTaskRepositoryProvider).getAllTasks();
    expect(restoredItems.map((item) => item.title), <String>['备份链路内容']);
    expect(restoredTasks.length, 10);

    final duplicatePreview = await importBackupUseCase.preview(
      file: backupSummary.file,
      cancelToken: BackupCancelToken(),
    );
    expect(duplicatePreview.isDuplicateBackupId, isTrue);
    expect(duplicatePreview.isDuplicateChecksum, isTrue);

    await importBackupUseCase.execute(
      preview: duplicatePreview,
      strategy: BackupImportStrategy.merge,
      cancelToken: BackupCancelToken(),
    );

    final dedupedItems = await container.read(learningItemRepositoryProvider).getAll();
    final dedupedTasks = await container.read(reviewTaskRepositoryProvider).getAllTasks();
    expect(dedupedItems.length, 1);
    expect(dedupedTasks.length, 10);
  });
}
