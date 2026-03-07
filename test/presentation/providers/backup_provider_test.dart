// 文件用途：BackupProvider 状态流测试，覆盖加载、导出、预览、导入与删除主路径。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/backup_file.dart';
import 'package:yike/domain/entities/backup_summary.dart';
import 'package:yike/domain/repositories/backup_repository.dart';
import 'package:yike/domain/usecases/export_backup_usecase.dart';
import 'package:yike/domain/usecases/get_backup_list_usecase.dart';
import 'package:yike/domain/usecases/import_backup_usecase.dart';
import 'package:yike/presentation/providers/backup_provider.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/settings_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';

import '../../helpers/test_database.dart';

class _FakeBackupRepository implements BackupRepository {
  _FakeBackupRepository({
    required this.backups,
    required this.snapshot,
    required this.exportedSummary,
    required this.previewBackup,
  });

  final List<BackupSummaryEntity> backups;
  BackupSummaryEntity? snapshot;
  final BackupSummaryEntity exportedSummary;
  final BackupFileEntity previewBackup;

  bool cancelExport = false;
  bool cancelPreview = false;
  bool cancelImport = false;
  bool throwDelete = false;
  bool importCalled = false;

  @override
  Future<BackupSummaryEntity> exportBackup({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.writingFile,
        message: '写入备份文件…',
      ),
    );
    if (cancelExport) {
      throw BackupCanceledException();
    }
    backups.insert(0, exportedSummary);
    return exportedSummary;
  }

  @override
  Future<List<BackupSummaryEntity>> getBackupList() async {
    return List<BackupSummaryEntity>.from(backups);
  }

  @override
  Future<void> deleteBackup(File file) async {
    if (throwDelete) {
      throw StateError('删除失败');
    }
    backups.removeWhere((item) => item.file.path == file.path);
  }

  @override
  Future<BackupFileEntity> readBackupFile({
    required File file,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.parsingFile,
        message: '解析备份文件…',
      ),
    );
    if (cancelPreview) {
      throw BackupCanceledException();
    }
    return previewBackup;
  }

  @override
  Future<BackupSummaryEntity?> getLatestSnapshot() async => snapshot;

  @override
  Future<BackupStatsEntity> getCurrentUserDataStats() async {
    return const BackupStatsEntity(
      learningItems: 1,
      reviewTasks: 1,
      reviewRecords: 0,
      payloadSize: 64,
    );
  }

  @override
  Future<BackupSummaryEntity> createImportSnapshot({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    return exportedSummary;
  }

  @override
  Future<bool> hasImportedBackupId(String backupId) async => false;

  @override
  Future<bool> hasImportedChecksum(String checksum) async => false;

  @override
  Future<void> markBackupImported({
    required String backupId,
    required String checksum,
    required String importedAtUtc,
  }) async {}

  @override
  Future<void> importBackup({
    required BackupFileEntity backup,
    required bool overwrite,
    required bool createSnapshotBeforeOverwrite,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.importingDatabase,
        message: '写入数据库…',
      ),
    );
    if (cancelImport) {
      throw BackupCanceledException();
    }
    importCalled = true;
  }
}

void main() {
  late AppDatabase db;
  late ProviderContainer container;
  late _FakeBackupRepository fakeRepository;
  late BackupSummaryEntity existingSummary;
  late BackupSummaryEntity snapshotSummary;
  late BackupSummaryEntity exportedSummary;
  late BackupFileEntity previewBackup;

  /// 等待 Provider 状态稳定，避免读取到初始化或联动刷新中的中间态。
  Future<void> waitFor(
    bool Function() predicate, {
    int maxAttempts = 50,
  }) async {
    for (var attempt = 0; attempt < maxAttempts; attempt++) {
      if (predicate()) {
        return;
      }
      await Future<void>.delayed(Duration.zero);
    }
    fail('等待 BackupProvider 状态稳定超时');
  }

  BackupSummaryEntity buildSummary({
    required String name,
    required bool isSnapshot,
  }) {
    return BackupSummaryEntity(
      file: File(name),
      fileName: name,
      bytes: 128,
      schemaVersion: '1.1',
      appVersion: '1.0.0',
      backupId: '$name-id',
      createdAt: '2026-03-06T10:00:00+08:00',
      createdAtUtc: '2026-03-06T02:00:00Z',
      checksum: 'sha256:$name',
      stats: const BackupStatsEntity(
        learningItems: 1,
        reviewTasks: 1,
        reviewRecords: 0,
        payloadSize: 64,
      ),
      isSnapshot: isSnapshot,
    );
  }

  Future<BackupFileEntity> buildPreviewBackup() async {
    const data = BackupDataEntity(
      learningItems: <BackupLearningItemEntity>[
        BackupLearningItemEntity(
          uuid: 'item-1',
          title: '测试内容',
          description: '描述',
          note: null,
          tags: <String>['测试'],
          learningDate: '2026-03-06',
          createdAt: '2026-03-06T10:00:00+08:00',
          updatedAt: '2026-03-06T10:00:00+08:00',
          isDeleted: false,
          deletedAt: null,
        ),
      ],
      learningSubtasks: <BackupLearningSubtaskEntity>[],
      reviewTasks: <BackupReviewTaskEntity>[
        BackupReviewTaskEntity(
          uuid: 'task-1',
          learningItemUuid: 'item-1',
          reviewRound: 1,
          scheduledDate: '2026-03-07',
          status: 'pending',
          completedAt: null,
          skippedAt: null,
          createdAt: '2026-03-06T10:00:00+08:00',
          updatedAt: '2026-03-06T10:00:00+08:00',
        ),
      ],
      reviewRecords: <BackupReviewRecordEntity>[],
      settings: <String, dynamic>{'notifyEnabled': true},
    );

    final checksum = await BackupUtils.computeChecksumForDataInIsolate(
      data.toJson(),
    );
    return BackupFileEntity(
      schemaVersion: '1.1',
      appVersion: '1.0.0',
      dbSchemaVersion: 9,
      backupId: 'preview-backup-id',
      createdAt: '2026-03-06T10:00:00+08:00',
      createdAtUtc: '2026-03-06T02:00:00Z',
      checksum: checksum.checksum,
      stats: BackupStatsEntity(
        learningItems: 1,
        reviewTasks: 1,
        reviewRecords: 0,
        payloadSize: checksum.payloadSize,
      ),
      data: data,
      platform: 'windows',
      deviceModel: 'test-device',
    );
  }

  setUp(() async {
    db = createInMemoryDatabase();
    existingSummary = buildSummary(name: 'backup-1.json', isSnapshot: false);
    snapshotSummary = buildSummary(name: 'snapshot-1.json', isSnapshot: true);
    exportedSummary = buildSummary(name: 'backup-2.json', isSnapshot: false);
    previewBackup = await buildPreviewBackup();
    fakeRepository = _FakeBackupRepository(
      backups: <BackupSummaryEntity>[existingSummary],
      snapshot: snapshotSummary,
      exportedSummary: exportedSummary,
      previewBackup: previewBackup,
    );
    container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        backupRepositoryProvider.overrideWithValue(fakeRepository),
        getBackupListUseCaseProvider.overrideWithValue(
          GetBackupListUseCase(repository: fakeRepository),
        ),
        exportBackupUseCaseProvider.overrideWithValue(
          ExportBackupUseCase(repository: fakeRepository),
        ),
        importBackupUseCaseProvider.overrideWithValue(
          ImportBackupUseCase(repository: fakeRepository),
        ),
      ],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('BackupProvider', () {
    test('加载、导出与删除会刷新备份历史和提示文案', () async {
      final notifier = container.read(backupProvider.notifier);
      await waitFor(() {
        final state = container.read(backupProvider);
        return state.backups.length == 1 && state.snapshot != null;
      });

      var state = container.read(backupProvider);
      expect(state.backups.single.fileName, 'backup-1.json');
      expect(state.snapshot?.fileName, 'snapshot-1.json');

      final exported = await notifier.exportBackup();
      expect(exported?.fileName, 'backup-2.json');

      state = container.read(backupProvider);
      expect(state.isRunning, isFalse);
      expect(state.message, '备份成功');
      expect(state.lastExported?.fileName, 'backup-2.json');
      expect(state.progress?.stage, BackupProgressStage.completed);
      expect(state.backups.first.fileName, 'backup-2.json');

      await notifier.deleteBackup(exportedSummary);

      state = container.read(backupProvider);
      expect(state.message, '已删除');
      expect(state.backups.map((item) => item.fileName), <String>['backup-1.json']);
    });

    test('预览导入与成功导入会清理进度并刷新联动 Provider', () async {
      final homeTasksSub = container.listen<HomeTasksState>(
        homeTasksProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final calendarSub = container.listen<CalendarState>(
        calendarProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final statisticsSub = container.listen<StatisticsState>(
        statisticsProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      final settingsSub = container.listen<SettingsState>(
        settingsProvider,
        (previous, next) {},
        fireImmediately: true,
      );
      addTearDown(homeTasksSub.close);
      addTearDown(calendarSub.close);
      addTearDown(statisticsSub.close);
      addTearDown(settingsSub.close);

      final notifier = container.read(backupProvider.notifier);
      await waitFor(() => container.read(backupProvider).backups.isNotEmpty);
      await waitFor(() => !container.read(homeTasksProvider).isLoading);
      await waitFor(() => !container.read(calendarProvider).isLoadingMonth);
      await waitFor(() => !container.read(statisticsProvider).isLoading);
      await waitFor(() => !container.read(settingsProvider).isLoading);

      final preview = await notifier.previewImport(File('preview.json'));
      expect(preview, isNotNull);
      expect(preview?.backup.backupId, 'preview-backup-id');
      expect(container.read(backupProvider).progress, isNull);

      final imported = await notifier.importBackup(
        preview: preview!,
        strategy: BackupImportStrategy.merge,
      );
      expect(imported, isTrue);
      expect(fakeRepository.importCalled, isTrue);

      final state = container.read(backupProvider);
      expect(state.isRunning, isFalse);
      expect(state.progress, isNull);
      expect(state.message, '导入成功');
      expect(state.errorMessage, isNull);
    });

    test('导出取消会回写取消提示，预览取消会清理运行态', () async {
      final notifier = container.read(backupProvider.notifier);
      await waitFor(() => container.read(backupProvider).backups.isNotEmpty);

      fakeRepository.cancelExport = true;
      final exported = await notifier.exportBackup();
      expect(exported, isNull);

      var state = container.read(backupProvider);
      expect(state.isRunning, isFalse);
      expect(state.message, '已取消');
      expect(state.progress, isNull);

      fakeRepository.cancelExport = false;
      fakeRepository.cancelPreview = true;
      final preview = await notifier.previewImport(File('preview.json'));
      expect(preview, isNull);

      state = container.read(backupProvider);
      expect(state.isRunning, isFalse);
      expect(state.message, '已取消');
      expect(state.progress, isNull);
    });
  });
}
