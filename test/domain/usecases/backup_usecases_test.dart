// 文件用途：备份相关用例测试，覆盖导出与历史列表查询的透传行为。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/domain/entities/backup_file.dart';
import 'package:yike/domain/entities/backup_summary.dart';
import 'package:yike/domain/repositories/backup_repository.dart';
import 'package:yike/domain/usecases/export_backup_usecase.dart';
import 'package:yike/domain/usecases/get_backup_list_usecase.dart';

class _RecordingBackupRepository implements BackupRepository {
  BackupCancelToken? lastExportCancelToken;
  List<BackupSummaryEntity> listResult = <BackupSummaryEntity>[];
  BackupSummaryEntity? exportResult;
  int exportCallCount = 0;
  int listCallCount = 0;

  @override
  Future<BackupSummaryEntity> exportBackup({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    exportCallCount++;
    lastExportCancelToken = cancelToken;
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.writingFile,
        message: '写入备份文件…',
      ),
    );
    return exportResult!;
  }

  @override
  Future<List<BackupSummaryEntity>> getBackupList() async {
    listCallCount++;
    return listResult;
  }

  @override
  Future<void> deleteBackup(File file) {
    throw UnimplementedError();
  }

  @override
  Future<BackupFileEntity> readBackupFile({
    required File file,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<BackupSummaryEntity?> getLatestSnapshot() {
    throw UnimplementedError();
  }

  @override
  Future<BackupStatsEntity> getCurrentUserDataStats() {
    throw UnimplementedError();
  }

  @override
  Future<BackupSummaryEntity> createImportSnapshot({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<bool> hasImportedBackupId(String backupId) {
    throw UnimplementedError();
  }

  @override
  Future<bool> hasImportedChecksum(String checksum) {
    throw UnimplementedError();
  }

  @override
  Future<void> markBackupImported({
    required String backupId,
    required String checksum,
    required String importedAtUtc,
  }) {
    throw UnimplementedError();
  }

  @override
  Future<void> importBackup({
    required BackupFileEntity backup,
    required bool overwrite,
    required bool createSnapshotBeforeOverwrite,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) {
    throw UnimplementedError();
  }
}

void main() {
  BackupSummaryEntity buildSummary(String name) {
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
      isSnapshot: false,
    );
  }

  group('备份相关用例', () {
    test('ExportBackupUseCase 会透传取消令牌并上报进度', () async {
      final repository = _RecordingBackupRepository()
        ..exportResult = buildSummary('backup.json');
      final useCase = ExportBackupUseCase(repository: repository);
      final cancelToken = BackupCancelToken();
      final progress = <BackupProgress>[];

      final result = await useCase.execute(
        cancelToken: cancelToken,
        onProgress: progress.add,
      );

      expect(repository.exportCallCount, 1);
      expect(repository.lastExportCancelToken, same(cancelToken));
      expect(result.fileName, 'backup.json');
      expect(progress.single.stage, BackupProgressStage.writingFile);
    });

    test('GetBackupListUseCase 会原样返回仓储中的备份历史', () async {
      final repository = _RecordingBackupRepository()
        ..listResult = <BackupSummaryEntity>[
          buildSummary('backup-a.json'),
          buildSummary('backup-b.json'),
        ];
      final useCase = GetBackupListUseCase(repository: repository);

      final result = await useCase.execute();

      expect(repository.listCallCount, 1);
      expect(result.map((item) => item.fileName), <String>[
        'backup-a.json',
        'backup-b.json',
      ]);
    });
  });
}
