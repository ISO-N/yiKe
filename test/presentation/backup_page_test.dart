// 文件用途：BackupPage Widget 测试，覆盖导出、恢复快照与删除备份的主交互链路。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:share_plus_platform_interface/share_plus_platform_interface.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/backup_file.dart';
import 'package:yike/domain/entities/backup_summary.dart';
import 'package:yike/domain/repositories/backup_repository.dart';
import 'package:yike/domain/usecases/export_backup_usecase.dart';
import 'package:yike/domain/usecases/get_backup_list_usecase.dart';
import 'package:yike/domain/usecases/import_backup_usecase.dart';
import 'package:yike/presentation/pages/settings/backup_page.dart';

import '../helpers/test_database.dart';

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

  int loadCount = 0;
  bool importCalled = false;
  int importFailuresRemaining = 0;
  bool lastOverwrite = false;
  bool lastCreateSnapshotBeforeOverwrite = false;
  int importCallCount = 0;
  final List<({bool overwrite, bool createSnapshotBeforeOverwrite})>
  importHistory = <({bool overwrite, bool createSnapshotBeforeOverwrite})>[];

  @override
  Future<BackupSummaryEntity> exportBackup({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    backups.insert(0, exportedSummary);
    return exportedSummary;
  }

  @override
  Future<List<BackupSummaryEntity>> getBackupList() async {
    loadCount++;
    return List<BackupSummaryEntity>.from(backups);
  }

  @override
  Future<void> deleteBackup(File file) async {
    backups.removeWhere((item) => item.file.path == file.path);
  }

  @override
  Future<BackupFileEntity> readBackupFile({
    required File file,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
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
    importCallCount++;
    importCalled = true;
    lastOverwrite = overwrite;
    lastCreateSnapshotBeforeOverwrite = createSnapshotBeforeOverwrite;
    importHistory.add((
      overwrite: overwrite,
      createSnapshotBeforeOverwrite: createSnapshotBeforeOverwrite,
    ));
    if (importFailuresRemaining > 0) {
      importFailuresRemaining--;
      throw ImportBackupException('模拟导入失败');
    }
  }
}

class _FastImportBackupUseCase extends ImportBackupUseCase {
  _FastImportBackupUseCase({required super.repository})
    : _repository = repository;

  final BackupRepository _repository;

  @override
  Future<BackupImportPreviewEntity> preview({
    required File file,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    // 说明：Widget 测试不需要覆盖 checksum isolate 计算，这里直接复用仓储解析结果，
    // 避免在部分环境下 isolate + 文件系统操作导致的超时波动。
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.parsingFile,
        message: '解析备份文件…',
      ),
    );
    final backup = await _repository.readBackupFile(
      file: file,
      cancelToken: cancelToken,
      onProgress: onProgress,
    );
    return BackupImportPreviewEntity(
      file: file,
      backup: backup,
      isDuplicateBackupId: false,
      isDuplicateChecksum: false,
      canonicalPayloadSize: backup.stats.payloadSize,
      computedChecksum: backup.checksum,
    );
  }
}

class _FakeSharePlatform extends SharePlatform {
  final List<List<XFile>> shareCalls = <List<XFile>>[];

  @override
  Future<ShareResult> shareXFiles(
    List<XFile> files, {
    String? subject,
    String? text,
    Rect? sharePositionOrigin,
  }) async {
    shareCalls.add(List<XFile>.from(files));
    return const ShareResult('ok', ShareResultStatus.success);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const localNotificationsChannel = MethodChannel(
    'dexterous.com/flutter/local_notifications',
  );

  late AppDatabase db;
  late ProviderContainer container;
  late _FakeBackupRepository fakeRepository;
  late _FakeSharePlatform sharePlatform;
  late SharePlatform previousSharePlatform;

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
      platform: 'android',
    );
  }

  Future<void> pumpPage(WidgetTester tester) async {
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: BackupPage()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  }

  /// 等待弹窗/页面完成异步刷新，避免 pumpAndSettle 被持续动画阻塞。
  Future<void> pumpUntilVisible(
    WidgetTester tester,
    Finder finder, {
    int maxAttempts = 200,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      await tester.pump(const Duration(milliseconds: 50));
      if (finder.evaluate().isNotEmpty) return;
    }
    fail('等待界面状态稳定超时');
  }

  setUp(() async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(localNotificationsChannel, (call) async {
      // 说明：导入流程成功后会调用 NotificationService.cancelAll，避免这里因 MissingPlugin 阻塞。
      if (call.method == 'cancelAll' || call.method == 'initialize') {
        return true;
      }
      return null;
    });

    db = createInMemoryDatabase();
    sharePlatform = _FakeSharePlatform();
    previousSharePlatform = SharePlatform.instance;
    SharePlatform.instance = sharePlatform;
    fakeRepository = _FakeBackupRepository(
      backups: <BackupSummaryEntity>[
        buildSummary(name: 'backup-1.json', isSnapshot: false),
      ],
      snapshot: buildSummary(name: 'snapshot-1.json', isSnapshot: true),
      exportedSummary: buildSummary(name: 'backup-2.json', isSnapshot: false),
      previewBackup: await buildPreviewBackup(),
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
          _FastImportBackupUseCase(repository: fakeRepository),
        ),
      ],
    );
  });

  tearDown(() async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(localNotificationsChannel, null);

    SharePlatform.instance = previousSharePlatform;
    container.dispose();
    await db.close();
  });

  group('BackupPage', () {
    testWidgets('初始渲染会显示快照卡片与备份历史，并支持刷新', (tester) async {
      await pumpPage(tester);

      expect(find.text('备份与恢复'), findsOneWidget);
      expect(find.text('[导入前快照]'), findsOneWidget);
      expect(find.text('备份历史'), findsOneWidget);
      expect(find.text('backup-1.json'), findsNothing);
      expect(find.textContaining('2026-03-06'), findsWidgets);

      final loadCountBefore = fakeRepository.loadCount;
      await tester.tap(find.byTooltip('刷新'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(fakeRepository.loadCount, greaterThan(loadCountBefore));
    });

    testWidgets('支持导出备份与删除备份历史', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.text('导出备份'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('继续'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.textContaining('备份成功：backup-2.json'), findsOneWidget);
      expect(fakeRepository.backups.first.fileName, 'backup-2.json');

      await tester.tap(find.text('删除').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('删除').last);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(fakeRepository.backups.map((item) => item.fileName), <String>[
        'backup-1.json',
      ]);
      expect(find.text('已删除'), findsOneWidget);
    });

    testWidgets('支持打开快照恢复确认弹窗并使用分享面板分享备份', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.widgetWithText(FilledButton, '恢复'));
      await tester.pumpAndSettle();
      expect(find.text('恢复快照'), findsOneWidget);
      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();

      await tester.tap(find.widgetWithText(TextButton, '分享').first);
      await tester.pumpAndSettle();
      expect(find.text('分享/另存为'), findsOneWidget);
      await tester.tap(find.widgetWithIcon(ListTile, Icons.share));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(sharePlatform.shareCalls, hasLength(1));
      expect(sharePlatform.shareCalls.single.single.path, contains('backup-1.json'));
    });

    testWidgets('备份历史可进入导入预览并按覆盖策略导入', (tester) async {
      await pumpPage(tester);

      // 说明：备份历史条目中的“恢复”入口复用导入预览对话框。
      // SnapshotCard 与 BackupTile 都有“恢复”按钮，这里选择备份历史条目（通常在后面）。
      await tester.tap(find.widgetWithText(TextButton, '恢复').last);
      await pumpUntilVisible(tester, find.text('导入预览'));

      expect(find.text('导入预览'), findsOneWidget);
      expect(find.text('导入策略'), findsOneWidget);
      expect(find.text('合并（推荐）'), findsOneWidget);
      expect(find.text('覆盖'), findsOneWidget);

      await tester.tap(find.text('覆盖'));
      await tester.pump(const Duration(milliseconds: 200));
      await tester.tap(find.text('确认导入'));
      await pumpUntilVisible(tester, find.text('确认覆盖导入'));

      // 覆盖导入需要二次确认。
      expect(find.text('确认覆盖导入'), findsOneWidget);
      await tester.tap(find.text('继续'));
      await pumpUntilVisible(tester, find.text('导入成功'));

      expect(fakeRepository.importCalled, isTrue);
      expect(fakeRepository.lastOverwrite, isTrue);
      expect(fakeRepository.lastCreateSnapshotBeforeOverwrite, isTrue);
      expect(find.text('导入成功'), findsOneWidget);
    });

  });
}
