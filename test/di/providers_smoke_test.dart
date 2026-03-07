// 文件用途：DI Provider 覆盖率烟囱测试，确保 providers.dart 中的仓储/用例 Provider 工厂被执行，减少“仅声明未触发”带来的覆盖率缺口。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/infrastructure/storage/backup_storage.dart';

import '../helpers/test_database.dart';

void main() {
  test('Provider 工厂可在测试环境创建并返回实例', () async {
    final db = createInMemoryDatabase();
    addTearDown(() async => db.close());

    final tempDir = await Directory.systemTemp.createTemp('yike_provider_smoke');
    addTearDown(() async {
      if (await tempDir.exists()) {
        await tempDir.delete(recursive: true);
      }
    });

    final container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        // 说明：BackupStorage 默认使用 path_provider，测试环境下直接注入临时目录即可。
        backupStorageProvider.overrideWithValue(BackupStorage(baseDir: tempDir)),
      ],
    );
    addTearDown(container.dispose);

    // DAO Provider
    expect(container.read(learningItemDaoProvider), isNotNull);
    expect(container.read(reviewTaskDaoProvider), isNotNull);
    expect(container.read(settingsDaoProvider), isNotNull);

    // Repository Provider（不触发网络/平台插件）
    expect(container.read(learningItemRepositoryProvider), isNotNull);
    expect(container.read(learningSubtaskRepositoryProvider), isNotNull);
    expect(container.read(reviewTaskRepositoryProvider), isNotNull);
    expect(container.read(settingsRepositoryProvider), isNotNull);
    expect(container.read(themeSettingsRepositoryProvider), isNotNull);
    expect(container.read(uiPreferencesRepositoryProvider), isNotNull);
    expect(container.read(backupRepositoryProvider), isNotNull);

    // UseCase Provider
    expect(container.read(createLearningItemUseCaseProvider), isNotNull);
    expect(container.read(importLearningItemsUseCaseProvider), isNotNull);
    expect(container.read(getHomeTasksUseCaseProvider), isNotNull);
    expect(container.read(getCalendarTasksUseCaseProvider), isNotNull);
    expect(container.read(getTasksByTimeUseCaseProvider), isNotNull);
    expect(container.read(completeReviewTaskUseCaseProvider), isNotNull);
    expect(container.read(skipReviewTaskUseCaseProvider), isNotNull);
    expect(container.read(undoTaskStatusUseCaseProvider), isNotNull);

    // Backup / Export 用例（依赖 DB + storage）
    expect(container.read(exportBackupUseCaseProvider), isNotNull);
    expect(container.read(getBackupListUseCaseProvider), isNotNull);
    expect(container.read(importBackupUseCaseProvider), isNotNull);
    expect(container.read(exportDataUseCaseProvider), isNotNull);
    expect(container.read(exportStatisticsCsvUseCaseProvider), isNotNull);
  });
}

