/// 文件用途：备份仓储实现（BackupRepositoryImpl）- 导出备份、读取备份历史与解析备份文件。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import 'dart:convert';
import 'dart:io';
import 'dart:isolate';

import 'package:drift/drift.dart';
import 'package:intl/intl.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:uuid/uuid.dart';

import '../../domain/entities/review_interval_config.dart';
import '../../domain/entities/theme_settings.dart';
import '../../infrastructure/storage/secure_storage_service.dart';
import '../../infrastructure/storage/settings_crypto.dart';

import '../../core/utils/backup_utils.dart';
import '../../core/utils/note_migration_parser.dart';
import '../../domain/entities/backup_file.dart';
import '../../domain/entities/backup_summary.dart';
import '../../domain/repositories/backup_repository.dart';
import '../../domain/repositories/settings_repository.dart';
import '../../domain/repositories/theme_settings_repository.dart';
import '../../infrastructure/storage/backup_storage.dart';
import '../database/daos/settings_dao.dart';
import '../database/daos/backup_dao.dart';
import '../database/database.dart';

/// 备份仓储实现。
class BackupRepositoryImpl implements BackupRepository {
  /// 构造函数。
  ///
  /// 参数：
  /// - [db] 数据库实例
  /// - [backupDao] 备份 DAO（负责导出读取）
  /// - [settingsRepository] 设置仓储（用于导出/导入 settings）
  /// - [themeSettingsRepository] 主题设置仓储（用于导出/导入 theme_mode）
  /// - [storage] 备份存储（文件系统）
  BackupRepositoryImpl({
    required AppDatabase db,
    required BackupDao backupDao,
    required SettingsRepository settingsRepository,
    required ThemeSettingsRepository themeSettingsRepository,
    required BackupStorage storage,
  }) : _db = db,
       _backupDao = backupDao,
       _settingsRepository = settingsRepository,
       _themeSettingsRepository = themeSettingsRepository,
       _storage = storage,
       _settingsDao = SettingsDao(db),
       _settingsCrypto = SettingsCrypto(
         secureStorageService: SecureStorageService(),
       );

  final AppDatabase _db;
  final BackupDao _backupDao;
  final SettingsRepository _settingsRepository;
  final ThemeSettingsRepository _themeSettingsRepository;
  final BackupStorage _storage;
  final SettingsDao _settingsDao;
  final SettingsCrypto _settingsCrypto;

  // v2.6：任务结构升级后，备份 data 增加 description 与 learningSubtasks。
  static const String _schemaVersion = '1.1';
  static const String _backupFileExt = '.yikebackup';
  static const String _snapshotFileName =
      'yike_snapshot_import_before$_backupFileExt';

  // 重复导入检测（本机记录）。
  static const String _importedBackupIdPrefix = 'backup_imported_id:';
  static const String _importedChecksumPrefix = 'backup_imported_checksum:';

  @override
  Future<BackupSummaryEntity> exportBackup({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.preparing,
        message: '准备导出…',
      ),
    );
    cancelToken.throwIfCanceled();

    // 1) 读取数据库数据（按 spec：items/tasks/records/settings）。
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.readingDatabase,
        message: '读取数据库…',
      ),
    );
    final items = await _backupDao.getLearningItemsForBackup();
    cancelToken.throwIfCanceled();
    final subtasks = await _backupDao.getLearningSubtasksForBackup();
    cancelToken.throwIfCanceled();
    final tasks = await _backupDao.getReviewTasksForBackup();
    cancelToken.throwIfCanceled();
    final records = await _backupDao.getReviewRecordsForBackup();
    cancelToken.throwIfCanceled();

    // 2) 读取设置（导出为明文，导入时由本机密钥重加密写入）。
    final appSettings = await _settingsRepository.getSettings();
    final intervals = await _settingsRepository.getReviewIntervalConfigs();
    final theme = await _themeSettingsRepository.getThemeSettings();
    cancelToken.throwIfCanceled();

    final settings = <String, dynamic>{
      'reminder_time': appSettings.reminderTime,
      'do_not_disturb_start': appSettings.doNotDisturbStart,
      'do_not_disturb_end': appSettings.doNotDisturbEnd,
      'notifications_enabled': appSettings.notificationsEnabled,
      'notification_permission_guide_dismissed':
          appSettings.notificationPermissionGuideDismissed,
      'review_intervals': intervals
          .map(
            (e) => {
              'round': e.round,
              'interval': e.intervalDays,
              'enabled': e.enabled,
            },
          )
          .toList(),
      'theme_mode': theme.mode,
    };

    // 3) 组装 data，并计算规范化 JSON 与 checksum。
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.encodingJson,
        message: '生成备份文件…',
      ),
    );
    cancelToken.throwIfCanceled();

    final data = BackupDataEntity(
      learningItems: items,
      learningSubtasks: subtasks,
      reviewTasks: tasks,
      reviewRecords: records,
      settings: settings,
    );

    final checksumResult = await BackupUtils.computeChecksumForDataInIsolate(
      data.toJson().cast<String, dynamic>(),
    );
    final checksum = checksumResult.checksum;
    final payloadSize = checksumResult.payloadSize;

    final now = DateTime.now();
    final createdAt = BackupUtils.formatLocalIsoWithOffset(now);
    final createdAtUtc = now.toUtc().toIso8601String();
    final backupId = const Uuid().v4();

    final appVersion = await _resolveAppVersion();
    final stats = BackupStatsEntity(
      learningItems: items.length,
      reviewTasks: tasks.length,
      reviewRecords: records.length,
      payloadSize: payloadSize,
    );

    final fileEntity = BackupFileEntity(
      schemaVersion: _schemaVersion,
      appVersion: appVersion,
      dbSchemaVersion: _db.schemaVersion,
      backupId: backupId,
      createdAt: createdAt,
      createdAtUtc: createdAtUtc,
      checksum: checksum,
      stats: stats,
      data: data,
      platform: _platformName(),
    );

    // 4) 写入应用托管目录（原子写入），并写入 meta（用于列表快速展示）。
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.writingFile,
        message: '写入文件…',
      ),
    );
    cancelToken.throwIfCanceled();

    await _storage.cleanupTempFiles();
    final fileName = _buildBackupFileName(now);
    final fileJson = fileEntity.toJson();
    final encoded = await Isolate.run(() => jsonEncode(fileJson));
    final file = await _storage.writeBackupFile(
      fileName: fileName,
      content: encoded,
    );
    await _writeMetaFileForBackup(file: file, entity: fileEntity);

    final bytes = await file.length();
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.completed,
        message: '导出完成',
      ),
    );

    return BackupSummaryEntity(
      file: file,
      fileName: fileName,
      bytes: bytes,
      schemaVersion: fileEntity.schemaVersion,
      appVersion: fileEntity.appVersion,
      backupId: fileEntity.backupId,
      createdAt: fileEntity.createdAt,
      createdAtUtc: fileEntity.createdAtUtc,
      checksum: fileEntity.checksum,
      stats: fileEntity.stats,
      isSnapshot: false,
    );
  }

  @override
  Future<List<BackupSummaryEntity>> getBackupList() async {
    await _storage.cleanupTempFiles();
    final files = await _storage.listBackupFiles();
    final list = <BackupSummaryEntity>[];
    for (final file in files) {
      final summary = await _readSummaryFromMetaOrFile(file, isSnapshot: false);
      if (summary != null) list.add(summary);
    }
    return list;
  }

  @override
  Future<void> deleteBackup(File file) async {
    await _storage.deleteBackupFile(file);
    await _deleteMetaFileIfExists(file);
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
    cancelToken.throwIfCanceled();

    final raw = await file.readAsString();
    cancelToken.throwIfCanceled();

    final decoded = await Isolate.run(() => jsonDecode(raw));
    if (decoded is! Map) {
      throw const FormatException('备份文件格式无效');
    }
    final parsed = BackupFileEntity.fromJson(decoded.cast<String, dynamic>());
    return _normalizeParsedBackup(parsed);
  }

  @override
  Future<BackupSummaryEntity?> getLatestSnapshot() async {
    await _storage.cleanupTempFiles();
    final files = await _storage.listSnapshotFiles();
    if (files.isEmpty) return null;

    // v1：仅保留 1 份快照；若目录中出现多份，取最新。
    final latest = files.first;
    return _readSummaryFromMetaOrFile(latest, isSnapshot: true);
  }

  @override
  Future<BackupStatsEntity> getCurrentUserDataStats() async {
    // 说明：统计范围与备份范围一致，并排除 Mock 数据。
    final itemCount = await _db
        .customSelect(
          'SELECT COUNT(*) AS c FROM learning_items WHERE is_mock_data = 0',
        )
        .getSingle()
        .then((r) => r.read<int>('c'));

    final taskCount = await _db
        .customSelect(
          'SELECT COUNT(*) AS c FROM review_tasks WHERE is_mock_data = 0',
        )
        .getSingle()
        .then((r) => r.read<int>('c'));

    final recordCount = await _db
        .customSelect('''
SELECT COUNT(*) AS c
FROM review_records rr
JOIN review_tasks rt ON rt.id = rr.review_task_id
WHERE rt.is_mock_data = 0
''')
        .getSingle()
        .then((r) => r.read<int>('c'));

    return BackupStatsEntity(
      learningItems: itemCount,
      reviewTasks: taskCount,
      reviewRecords: recordCount,
      payloadSize: 0,
    );
  }

  @override
  Future<BackupSummaryEntity> createImportSnapshot({
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.preparing,
        message: '创建导入前快照…',
      ),
    );
    cancelToken.throwIfCanceled();

    // 复用导出读取逻辑，但写入 snapshots/ 并使用固定文件名（仅保留 1 份）。
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.readingDatabase,
        message: '读取数据库…',
      ),
    );
    final items = await _backupDao.getLearningItemsForBackup();
    cancelToken.throwIfCanceled();
    final subtasks = await _backupDao.getLearningSubtasksForBackup();
    cancelToken.throwIfCanceled();
    final tasks = await _backupDao.getReviewTasksForBackup();
    cancelToken.throwIfCanceled();
    final records = await _backupDao.getReviewRecordsForBackup();
    cancelToken.throwIfCanceled();

    final appSettings = await _settingsRepository.getSettings();
    final intervals = await _settingsRepository.getReviewIntervalConfigs();
    final theme = await _themeSettingsRepository.getThemeSettings();
    cancelToken.throwIfCanceled();

    final settings = <String, dynamic>{
      'reminder_time': appSettings.reminderTime,
      'do_not_disturb_start': appSettings.doNotDisturbStart,
      'do_not_disturb_end': appSettings.doNotDisturbEnd,
      'notifications_enabled': appSettings.notificationsEnabled,
      'notification_permission_guide_dismissed':
          appSettings.notificationPermissionGuideDismissed,
      'review_intervals': intervals
          .map(
            (e) => {
              'round': e.round,
              'interval': e.intervalDays,
              'enabled': e.enabled,
            },
          )
          .toList(),
      'theme_mode': theme.mode,
    };

    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.encodingJson,
        message: '生成快照文件…',
      ),
    );
    cancelToken.throwIfCanceled();

    final data = BackupDataEntity(
      learningItems: items,
      learningSubtasks: subtasks,
      reviewTasks: tasks,
      reviewRecords: records,
      settings: settings,
    );

    final checksumResult = await BackupUtils.computeChecksumForDataInIsolate(
      data.toJson().cast<String, dynamic>(),
    );
    final checksum = checksumResult.checksum;
    final payloadSize = checksumResult.payloadSize;

    final now = DateTime.now();
    final createdAt = BackupUtils.formatLocalIsoWithOffset(now);
    final createdAtUtc = now.toUtc().toIso8601String();
    final backupId = const Uuid().v4();

    final appVersion = await _resolveAppVersion();
    final stats = BackupStatsEntity(
      learningItems: items.length,
      reviewTasks: tasks.length,
      reviewRecords: records.length,
      payloadSize: payloadSize,
    );

    final entity = BackupFileEntity(
      schemaVersion: _schemaVersion,
      appVersion: appVersion,
      dbSchemaVersion: _db.schemaVersion,
      backupId: backupId,
      createdAt: createdAt,
      createdAtUtc: createdAtUtc,
      checksum: checksum,
      stats: stats,
      data: data,
      platform: _platformName(),
    );

    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.writingFile,
        message: '写入快照…',
      ),
    );
    cancelToken.throwIfCanceled();

    await _storage.cleanupTempFiles();
    final snapshotJson = entity.toJson();
    final encoded = await Isolate.run(() => jsonEncode(snapshotJson));
    final file = await _storage.writeSnapshotFile(
      fileName: _snapshotFileName,
      content: encoded,
    );
    await _writeMetaFileForBackup(file: file, entity: entity);

    final bytes = await file.length();
    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.completed,
        message: '快照已创建',
      ),
    );

    return BackupSummaryEntity(
      file: file,
      fileName: _snapshotFileName,
      bytes: bytes,
      schemaVersion: entity.schemaVersion,
      appVersion: entity.appVersion,
      backupId: entity.backupId,
      createdAt: entity.createdAt,
      createdAtUtc: entity.createdAtUtc,
      checksum: entity.checksum,
      stats: entity.stats,
      isSnapshot: true,
    );
  }

  @override
  Future<bool> hasImportedBackupId(String backupId) async {
    final id = backupId.trim();
    if (id.isEmpty) return false;
    final key = '$_importedBackupIdPrefix$id';
    final value = await _settingsDao.getValue(key);
    return value != null && value.isNotEmpty;
  }

  @override
  Future<bool> hasImportedChecksum(String checksum) async {
    final c = checksum.trim();
    if (c.isEmpty) return false;
    final key = '$_importedChecksumPrefix$c';
    final value = await _settingsDao.getValue(key);
    return value != null && value.isNotEmpty;
  }

  @override
  Future<void> markBackupImported({
    required String backupId,
    required String checksum,
    required String importedAtUtc,
  }) async {
    final ts = importedAtUtc.trim().isEmpty
        ? DateTime.now().toUtc().toIso8601String()
        : importedAtUtc.trim();

    if (backupId.trim().isNotEmpty) {
      await _settingsDao.upsertValue(
        '$_importedBackupIdPrefix${backupId.trim()}',
        ts,
      );
    }
    if (checksum.trim().isNotEmpty) {
      await _settingsDao.upsertValue(
        '$_importedChecksumPrefix${checksum.trim()}',
        ts,
      );
    }
  }

  @override
  Future<void> importBackup({
    required BackupFileEntity backup,
    required bool overwrite,
    required bool createSnapshotBeforeOverwrite,
    required BackupCancelToken cancelToken,
    void Function(BackupProgress progress)? onProgress,
  }) async {
    cancelToken.throwIfCanceled();

    if (overwrite && createSnapshotBeforeOverwrite) {
      await createImportSnapshot(
        cancelToken: cancelToken,
        onProgress: onProgress,
      );
      cancelToken.throwIfCanceled();
    }

    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.importingDatabase,
        message: '写入数据库…',
      ),
    );

    final normalized = _normalizeParsedBackup(backup);
    _validateBackupData(normalized);

    await _db.transaction(() async {
      cancelToken.throwIfCanceled();

      if (overwrite) {
        await _clearUserData();
        await _clearUserSettingsKeys();
      }

      // 1) learning_items
      final (itemUuidToId, migratedSubtasks) = await _upsertLearningItems(
        normalized.data.learningItems,
        hasSubtasksFromBackup: normalized.data.learningSubtasks
            .map((e) => e.learningItemUuid)
            .toSet(),
      );
      cancelToken.throwIfCanceled();

      // 1.1) learning_subtasks（含旧备份 note 迁移生成的子任务）
      final mergedSubtasks = <BackupLearningSubtaskEntity>[
        ...normalized.data.learningSubtasks,
        ...migratedSubtasks,
      ];
      await _upsertLearningSubtasks(
        mergedSubtasks,
        itemUuidToId: itemUuidToId,
      );
      cancelToken.throwIfCanceled();

      // 2) review_tasks
      final taskUuidToId = await _upsertReviewTasks(
        normalized.data.reviewTasks,
        itemUuidToId: itemUuidToId,
      );
      cancelToken.throwIfCanceled();

      // 3) review_records
      await _insertReviewRecords(
        normalized.data.reviewRecords,
        taskUuidToId: taskUuidToId,
      );
      cancelToken.throwIfCanceled();

      // 4) settings（主题/语言覆盖；通知类不导入）
      await _importSettings(normalized.data.settings);
    });

    onProgress?.call(
      const BackupProgress(
        stage: BackupProgressStage.completed,
        message: '导入完成',
      ),
    );
  }

  String _buildBackupFileName(DateTime now) {
    final ts = DateFormat('yyyyMMdd_HHmmss').format(now);
    return 'yike_backup_$ts$_backupFileExt';
  }

  String? _platformName() {
    if (Platform.isAndroid) return 'android';
    if (Platform.isIOS) return 'ios';
    if (Platform.isWindows || Platform.isMacOS || Platform.isLinux) {
      return 'desktop';
    }
    return null;
  }

  Future<String> _resolveAppVersion() async {
    try {
      final info = await PackageInfo.fromPlatform();
      final version = info.version.trim();
      if (version.isEmpty) return 'unknown';
      // 说明：不拼 buildNumber，保持 UI 展示简洁（如需诊断可后续扩展）。
      return version;
    } catch (_) {
      // 测试环境/平台插件不可用时兜底。
      return 'unknown';
    }
  }

  Future<File> _metaFileOf(File backupFile) async {
    return File('${backupFile.path}.meta.json');
  }

  Future<void> _writeMetaFileForBackup({
    required File file,
    required BackupFileEntity entity,
  }) async {
    final meta = await _metaFileOf(file);
    final payload = <String, dynamic>{
      'schemaVersion': entity.schemaVersion,
      'appVersion': entity.appVersion,
      'dbSchemaVersion': entity.dbSchemaVersion,
      'backupId': entity.backupId,
      'createdAt': entity.createdAt,
      'createdAtUtc': entity.createdAtUtc,
      'checksum': entity.checksum,
      'stats': entity.stats.toJson(),
      if (entity.platform != null) 'platform': entity.platform,
      if (entity.deviceModel != null) 'deviceModel': entity.deviceModel,
    };
    await meta.writeAsString(jsonEncode(payload), flush: true);
  }

  Future<void> _deleteMetaFileIfExists(File backupFile) async {
    final meta = await _metaFileOf(backupFile);
    if (await meta.exists()) {
      try {
        await meta.delete();
      } catch (_) {
        // 删除失败不阻塞主流程。
      }
    }
  }

  Future<BackupSummaryEntity?> _readSummaryFromMetaOrFile(
    File file, {
    required bool isSnapshot,
  }) async {
    try {
      final meta = await _metaFileOf(file);
      BackupFileEntity entity;
      if (await meta.exists()) {
        final raw = await meta.readAsString();
        final decoded = jsonDecode(raw);
        if (decoded is Map) {
          // meta 只包含 header + stats，补齐 data 的空对象以复用解析器。
          final enriched = <String, dynamic>{
            ...decoded.cast<String, dynamic>(),
            'data': const <String, dynamic>{
              'learningItems': <dynamic>[],
              'reviewTasks': <dynamic>[],
              'reviewRecords': <dynamic>[],
              'settings': <String, dynamic>{},
            },
          };
          entity = BackupFileEntity.fromJson(enriched);
        } else {
          entity = await readBackupFile(
            file: file,
            cancelToken: BackupCancelToken(),
          );
        }
      } else {
        entity = await readBackupFile(
          file: file,
          cancelToken: BackupCancelToken(),
        );
      }

      final bytes = await file.length();
      final fileName = file.uri.pathSegments.isEmpty
          ? file.path
          : file.uri.pathSegments.last;

      return BackupSummaryEntity(
        file: file,
        fileName: fileName,
        bytes: bytes,
        schemaVersion: entity.schemaVersion,
        appVersion: entity.appVersion,
        backupId: entity.backupId,
        createdAt: entity.createdAt,
        createdAtUtc: entity.createdAtUtc,
        checksum: entity.checksum,
        stats: entity.stats,
        isSnapshot: isSnapshot,
      );
    } catch (_) {
      return null;
    }
  }

  BackupFileEntity _normalizeParsedBackup(BackupFileEntity parsed) {
    final schema = parsed.schemaVersion.trim().isEmpty
        ? _schemaVersion
        : parsed.schemaVersion.trim();
    final id = parsed.backupId.trim().isEmpty
        ? const Uuid().v4()
        : parsed.backupId.trim();
    final createdAtUtc = parsed.createdAtUtc.trim().isEmpty
        ? DateTime.now().toUtc().toIso8601String()
        : parsed.createdAtUtc.trim();
    final createdAt = parsed.createdAt.trim().isEmpty
        ? BackupUtils.formatLocalIsoWithOffset(DateTime.now())
        : parsed.createdAt.trim();

    if (schema == parsed.schemaVersion.trim() &&
        id == parsed.backupId.trim() &&
        createdAtUtc == parsed.createdAtUtc.trim() &&
        createdAt == parsed.createdAt.trim()) {
      return parsed;
    }

    return BackupFileEntity(
      schemaVersion: schema,
      appVersion: parsed.appVersion,
      dbSchemaVersion: parsed.dbSchemaVersion,
      backupId: id,
      createdAt: createdAt,
      createdAtUtc: createdAtUtc,
      checksum: parsed.checksum,
      stats: parsed.stats,
      data: parsed.data,
      platform: parsed.platform,
      deviceModel: parsed.deviceModel,
    );
  }

  void _validateBackupData(BackupFileEntity backup) {
    // 基础字段校验（缺字段默认值已在 fromJson 层处理，这里只做“阻断型错误”）。
    if (backup.checksum.trim().isEmpty) {
      throw const FormatException('备份文件格式无效');
    }
    if (backup.data.learningItems.any(
      (e) => e.uuid.isEmpty || e.title.isEmpty,
    )) {
      throw const FormatException('备份文件已损坏');
    }
    if (backup.data.reviewTasks.any(
      (e) => e.uuid.isEmpty || e.learningItemUuid.isEmpty,
    )) {
      throw const FormatException('备份文件已损坏');
    }
    if (backup.data.reviewRecords.any(
      (e) => e.uuid.isEmpty || e.reviewTaskUuid.isEmpty,
    )) {
      throw const FormatException('备份文件已损坏');
    }
    if (backup.data.learningSubtasks.any(
      (e) => e.uuid.isEmpty || e.learningItemUuid.isEmpty,
    )) {
      throw const FormatException('备份文件已损坏');
    }

    final itemUuids = backup.data.learningItems.map((e) => e.uuid).toSet();
    final hasMissingItemRef = backup.data.reviewTasks.any(
      (t) => !itemUuids.contains(t.learningItemUuid),
    );
    if (hasMissingItemRef) {
      throw const FormatException('备份文件格式无效');
    }

    final hasMissingItemRefForSubtasks = backup.data.learningSubtasks.any(
      (s) => !itemUuids.contains(s.learningItemUuid),
    );
    if (hasMissingItemRefForSubtasks) {
      throw const FormatException('备份文件格式无效');
    }

    final taskUuids = backup.data.reviewTasks.map((e) => e.uuid).toSet();
    final hasMissingTaskRef = backup.data.reviewRecords.any(
      (r) => !taskUuids.contains(r.reviewTaskUuid),
    );
    if (hasMissingTaskRef) {
      throw const FormatException('备份文件格式无效');
    }
  }

  Future<void> _clearUserData() async {
    // 顺序：records → tasks → items（避免外键约束/级联差异带来的脏数据残留）。
    final taskIdsQuery = _db.selectOnly(_db.reviewTasks)
      ..addColumns([_db.reviewTasks.id])
      ..where(_db.reviewTasks.isMockData.equals(false));

    await (_db.delete(
      _db.reviewRecords,
    )..where((t) => t.reviewTaskId.isInQuery(taskIdsQuery))).go();
    await (_db.delete(
      _db.reviewTasks,
    )..where((t) => t.isMockData.equals(false))).go();
    await (_db.delete(
      _db.learningItems,
    )..where((t) => t.isMockData.equals(false))).go();
  }

  Future<void> _clearUserSettingsKeys() async {
    // 说明：覆盖导入仅清理“用户设置键”，避免误删同步/调试等内部键。
    const keys = <String>[
      'reminder_time',
      'do_not_disturb_start',
      'do_not_disturb_end',
      'notifications_enabled',
      'notification_permission_guide_dismissed',
      'last_notified_date',
      'review_intervals',
      'theme_mode',
      'language',
    ];

    for (final k in keys) {
      await (_db.delete(
        _db.appSettingsTable,
      )..where((t) => t.key.equals(k))).go();
    }
  }

  /// Upsert 学习内容，并对旧备份（仅 note、无 description/learningSubtasks）做一次性迁移。
  ///
  /// 返回值：
  /// - itemUuid → localId 映射（用于外键修复）
  /// - 由 note 迁移生成的子任务列表（用于后续写入 learning_subtasks）
  Future<(Map<String, int>, List<BackupLearningSubtaskEntity>)> _upsertLearningItems(
    List<BackupLearningItemEntity> items, {
    required Set<String> hasSubtasksFromBackup,
  }) async {
    final existing = await _loadExistingLearningItemsByUuid(
      items.map((e) => e.uuid).toList(),
    );

    final map = <String, int>{};
    final migratedSubtasks = <BackupLearningSubtaskEntity>[];
    final uuidGen = const Uuid();
    for (final item in items) {
      final createdAt = _parseDateTime(item.createdAt) ?? DateTime.now();
      final updatedAt = _parseDateTime(item.updatedAt);
      final learningDate = _parseDateTime(item.learningDate) ?? DateTime.now();
      final deletedAt = _parseDateTime(item.deletedAt);
      final tagsJson = jsonEncode(item.tags);

      // 旧备份兼容：若无 description 且备份也未提供 learningSubtasks，则按迁移规则从 note 拆解。
      final hasNewStructure =
          (item.description?.trim().isNotEmpty ?? false) ||
          hasSubtasksFromBackup.contains(item.uuid);
      final legacyNote = (item.note ?? '').trim();
      final migration = (!hasNewStructure && legacyNote.isNotEmpty)
          ? NoteMigrationParser.parse(legacyNote)
          : null;

      final effectiveDescription =
          (item.description?.trim().isNotEmpty ?? false)
              ? item.description?.trim()
              : migration?.description?.trim();
      final effectiveNote = migration == null ? item.note : null;

      if (migration != null && migration.subtasks.isNotEmpty) {
        for (var i = 0; i < migration.subtasks.length; i++) {
          migratedSubtasks.add(
            BackupLearningSubtaskEntity(
              uuid: uuidGen.v4(),
              learningItemUuid: item.uuid,
              content: migration.subtasks[i],
              sortOrder: i,
              createdAt: createdAt.toIso8601String(),
              updatedAt: updatedAt?.toIso8601String(),
            ),
          );
        }
      }

      final row = existing[item.uuid];
      if (row != null) {
        // 更新：不覆盖 id、created_at。
        await (_db.update(
          _db.learningItems,
        )..where((t) => t.id.equals(row.id))).write(
          LearningItemsCompanion(
            title: Value(item.title),
            description: Value(effectiveDescription),
            note: Value(effectiveNote),
            tags: Value(tagsJson),
            learningDate: Value(learningDate),
            isDeleted: Value(item.isDeleted),
            deletedAt: deletedAt != null
                ? Value(deletedAt)
                : const Value.absent(),
            updatedAt: updatedAt != null
                ? Value(updatedAt)
                : const Value.absent(),
            // 强制标记为非 Mock（避免外部文件写入 mock 造成清理/同步口径异常）。
            isMockData: const Value(false),
          ),
        );
        map[item.uuid] = row.id;
      } else {
        final id = await _db
            .into(_db.learningItems)
            .insert(
              LearningItemsCompanion.insert(
                uuid: Value(item.uuid),
                title: item.title,
                description: Value(effectiveDescription),
                note: Value(effectiveNote),
                tags: Value(tagsJson),
                learningDate: learningDate,
                createdAt: Value(createdAt),
                updatedAt: updatedAt != null
                    ? Value(updatedAt)
                    : const Value.absent(),
                isDeleted: Value(item.isDeleted),
                deletedAt: deletedAt != null
                    ? Value(deletedAt)
                    : const Value.absent(),
                isMockData: const Value(false),
              ),
            );
        map[item.uuid] = id;
      }
    }
    return (map, migratedSubtasks);
  }

  Future<void> _upsertLearningSubtasks(
    List<BackupLearningSubtaskEntity> subtasks, {
    required Map<String, int> itemUuidToId,
  }) async {
    if (subtasks.isEmpty) return;

    final existing = await _loadExistingLearningSubtasksByUuid(
      subtasks.map((e) => e.uuid).toList(),
    );

    for (final subtask in subtasks) {
      final createdAt = _parseDateTime(subtask.createdAt) ?? DateTime.now();
      final updatedAt = _parseDateTime(subtask.updatedAt);

      final row = existing[subtask.uuid];
      if (row != null) {
        // learningItemUuid 不一致说明 uuid 冲突：中止导入。
        if (row.learningItemUuid != subtask.learningItemUuid) {
          throw const FormatException('备份文件数据异常，导入中止');
        }

        // 更新：不覆盖 id、learning_item_id、created_at。
        await (_db.update(
          _db.learningSubtasks,
        )..where((t) => t.id.equals(row.id))).write(
          LearningSubtasksCompanion(
            content: Value(subtask.content),
            sortOrder: Value(subtask.sortOrder),
            updatedAt: updatedAt != null ? Value(updatedAt) : const Value.absent(),
            isMockData: const Value(false),
          ),
        );
      } else {
        final itemId = itemUuidToId[subtask.learningItemUuid];
        if (itemId == null) {
          throw const FormatException('备份文件格式无效');
        }

        await _db.into(_db.learningSubtasks).insert(
          LearningSubtasksCompanion.insert(
            uuid: Value(subtask.uuid),
            learningItemId: itemId,
            content: subtask.content,
            sortOrder: Value(subtask.sortOrder),
            createdAt: createdAt,
            updatedAt: updatedAt != null ? Value(updatedAt) : const Value.absent(),
            isMockData: const Value(false),
          ),
        );
      }
    }
  }

  Future<Map<String, int>> _upsertReviewTasks(
    List<BackupReviewTaskEntity> tasks, {
    required Map<String, int> itemUuidToId,
  }) async {
    final existing = await _loadExistingReviewTasksByUuid(
      tasks.map((e) => e.uuid).toList(),
    );

    final map = <String, int>{};
    for (final task in tasks) {
      final createdAt = _parseDateTime(task.createdAt) ?? DateTime.now();
      final updatedAt = _parseDateTime(task.updatedAt);
      final scheduled = _parseDateTime(task.scheduledDate) ?? DateTime.now();
      final completedAt = _parseDateTime(task.completedAt);
      final skippedAt = _parseDateTime(task.skippedAt);
      // 性能优化（v10）：occurredAt 落地列用于任务中心时间线排序与游标分页。
      // 备份文件不强制携带该字段，这里基于当前状态与时间戳回推口径。
      final occurredAt = switch (task.status) {
        'pending' => scheduled,
        'done' => completedAt ?? scheduled,
        'skipped' => skippedAt ?? scheduled,
        _ => scheduled,
      };

      final row = existing[task.uuid];
      if (row != null) {
        // learningItemUuid 不一致说明 uuid 冲突：中止导入。
        if (row.learningItemUuid != task.learningItemUuid) {
          throw const FormatException('备份文件数据异常，导入中止');
        }

        // 更新：不覆盖 id、learning_item_id、created_at。
        await (_db.update(
          _db.reviewTasks,
        )..where((t) => t.id.equals(row.id))).write(
          ReviewTasksCompanion(
            reviewRound: Value(task.reviewRound),
            scheduledDate: Value(scheduled),
            occurredAt: Value(occurredAt),
            status: Value(task.status),
            completedAt: completedAt != null
                ? Value(completedAt)
                : const Value.absent(),
            skippedAt: skippedAt != null
                ? Value(skippedAt)
                : const Value.absent(),
            updatedAt: updatedAt != null
                ? Value(updatedAt)
                : const Value.absent(),
            isMockData: const Value(false),
          ),
        );
        map[task.uuid] = row.id;
      } else {
        final itemId = itemUuidToId[task.learningItemUuid];
        if (itemId == null) {
          throw const FormatException('备份文件格式无效');
        }

        final id = await _db
            .into(_db.reviewTasks)
            .insert(
              ReviewTasksCompanion.insert(
                uuid: Value(task.uuid),
                learningItemId: itemId,
                reviewRound: task.reviewRound,
                scheduledDate: scheduled,
                occurredAt: Value(occurredAt),
                status: Value(task.status),
                completedAt: completedAt != null
                    ? Value(completedAt)
                    : const Value.absent(),
                skippedAt: skippedAt != null
                    ? Value(skippedAt)
                    : const Value.absent(),
                createdAt: Value(createdAt),
                updatedAt: updatedAt != null
                    ? Value(updatedAt)
                    : const Value.absent(),
                isMockData: const Value(false),
              ),
            );
        map[task.uuid] = id;
      }
    }
    return map;
  }

  Future<void> _insertReviewRecords(
    List<BackupReviewRecordEntity> records, {
    required Map<String, int> taskUuidToId,
  }) async {
    final existing = await _loadExistingReviewRecordsByUuid(
      records.map((e) => e.uuid).toList(),
    );

    for (final record in records) {
      final occurredAt = _parseDateTime(record.occurredAt) ?? DateTime.now();
      final createdAt = _parseDateTime(record.createdAt) ?? occurredAt;

      final row = existing[record.uuid];
      if (row != null) {
        // records 不可变：同 uuid 不同内容视为异常并中止导入。
        if (row.reviewTaskUuid != record.reviewTaskUuid ||
            row.action != record.action ||
            row.occurredAt.toIso8601String() != occurredAt.toIso8601String()) {
          throw const FormatException('备份文件数据异常，导入中止');
        }
        continue;
      }

      final taskId = taskUuidToId[record.reviewTaskUuid];
      if (taskId == null) {
        throw const FormatException('备份文件格式无效');
      }

      await _db
          .into(_db.reviewRecords)
          .insert(
            ReviewRecordsCompanion.insert(
              uuid: Value(record.uuid),
              reviewTaskId: taskId,
              action: record.action,
              occurredAt: occurredAt,
              createdAt: Value(createdAt),
            ),
          );
    }
  }

  Future<void> _importSettings(Map<String, dynamic> settings) async {
    // 主题覆盖（v1 必选）。
    final themeRaw = settings['theme_mode'];
    if (themeRaw is String && themeRaw.trim().isNotEmpty) {
      await _themeSettingsRepository.saveThemeSettings(
        ThemeSettingsEntity(mode: themeRaw.trim()),
      );
    }

    // 语言覆盖（若存在；当前版本未启用语言设置，仍按 spec 预留）。
    final languageRaw = settings['language'];
    if (languageRaw is String && languageRaw.trim().isNotEmpty) {
      final encrypted = await _settingsCrypto.encrypt(
        jsonEncode(languageRaw.trim()),
      );
      await _settingsDao.upsertValue('language', encrypted);
    }

    // 复习间隔配置（导入）。
    final intervalsRaw = settings['review_intervals'];
    final intervals = _parseReviewIntervals(intervalsRaw);
    if (intervals != null) {
      await _settingsRepository.saveReviewIntervalConfigs(intervals);
    }

    // 通知类设置不导入（按 spec）：
    // reminder_time / do_not_disturb_* / notifications_enabled / notification_permission_guide_dismissed / last_notified_date
  }

  List<ReviewIntervalConfigEntity>? _parseReviewIntervals(Object? raw) {
    if (raw is! List) return null;
    final result = <ReviewIntervalConfigEntity>[];
    for (final item in raw) {
      if (item is! Map) continue;
      final round = item['round'];
      final interval = item['interval'];
      final enabled = item['enabled'];
      final r = round is int ? round : int.tryParse(round?.toString() ?? '');
      final i = interval is int
          ? interval
          : int.tryParse(interval?.toString() ?? '');
      final e = enabled is bool ? enabled : null;
      if (r == null || i == null || e == null) continue;
      try {
        result.add(
          ReviewIntervalConfigEntity(round: r, intervalDays: i, enabled: e),
        );
      } catch (_) {
        // 跳过脏数据。
      }
    }
    if (result.isEmpty) return null;

    final hasEnabled = result.any((e) => e.enabled);
    if (!hasEnabled) return null;

    result.sort((a, b) => a.round.compareTo(b.round));
    return result;
  }

  DateTime? _parseDateTime(String? raw) {
    if (raw == null) return null;
    final s = raw.trim();
    if (s.isEmpty) return null;
    try {
      return DateTime.parse(s);
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, LearningItem>> _loadExistingLearningItemsByUuid(
    List<String> uuids,
  ) async {
    final map = <String, LearningItem>{};
    final filtered = uuids.where((e) => e.trim().isNotEmpty).toList();
    for (final chunk in _chunk(filtered, size: 300)) {
      final rows = await (_db.select(
        _db.learningItems,
      )..where((t) => t.uuid.isIn(chunk))).get();
      for (final row in rows) {
        map[row.uuid] = row;
      }
    }
    return map;
  }

  Future<Map<String, _ExistingLearningSubtaskRow>>
  _loadExistingLearningSubtasksByUuid(List<String> uuids) async {
    final map = <String, _ExistingLearningSubtaskRow>{};
    final filtered = uuids.where((e) => e.trim().isNotEmpty).toList();
    for (final chunk in _chunk(filtered, size: 300)) {
      final query = _db.select(_db.learningSubtasks).join([
        innerJoin(
          _db.learningItems,
          _db.learningItems.id.equalsExp(_db.learningSubtasks.learningItemId),
        ),
      ]);
      query.where(_db.learningSubtasks.uuid.isIn(chunk));

      final rows = await query.get();
      for (final row in rows) {
        final subtask = row.readTable(_db.learningSubtasks);
        final item = row.readTable(_db.learningItems);
        map[subtask.uuid] = _ExistingLearningSubtaskRow(
          id: subtask.id,
          learningItemUuid: item.uuid,
        );
      }
    }
    return map;
  }

  Future<Map<String, _ExistingTaskRow>> _loadExistingReviewTasksByUuid(
    List<String> uuids,
  ) async {
    final map = <String, _ExistingTaskRow>{};
    final filtered = uuids.where((e) => e.trim().isNotEmpty).toList();
    for (final chunk in _chunk(filtered, size: 300)) {
      final query = _db.select(_db.reviewTasks).join([
        innerJoin(
          _db.learningItems,
          _db.learningItems.id.equalsExp(_db.reviewTasks.learningItemId),
        ),
      ]);
      query.where(_db.reviewTasks.uuid.isIn(chunk));

      final rows = await query.get();
      for (final row in rows) {
        final task = row.readTable(_db.reviewTasks);
        final item = row.readTable(_db.learningItems);
        map[task.uuid] = _ExistingTaskRow(
          id: task.id,
          learningItemUuid: item.uuid,
        );
      }
    }
    return map;
  }

  Future<Map<String, _ExistingRecordRow>> _loadExistingReviewRecordsByUuid(
    List<String> uuids,
  ) async {
    final map = <String, _ExistingRecordRow>{};
    final filtered = uuids.where((e) => e.trim().isNotEmpty).toList();
    for (final chunk in _chunk(filtered, size: 300)) {
      final query = _db.select(_db.reviewRecords).join([
        innerJoin(
          _db.reviewTasks,
          _db.reviewTasks.id.equalsExp(_db.reviewRecords.reviewTaskId),
        ),
      ]);
      query.where(_db.reviewRecords.uuid.isIn(chunk));
      final rows = await query.get();
      for (final row in rows) {
        final record = row.readTable(_db.reviewRecords);
        final task = row.readTable(_db.reviewTasks);
        map[record.uuid] = _ExistingRecordRow(
          reviewTaskUuid: task.uuid,
          action: record.action,
          occurredAt: record.occurredAt,
        );
      }
    }
    return map;
  }

  Iterable<List<T>> _chunk<T>(List<T> items, {required int size}) sync* {
    if (items.isEmpty) return;
    for (var i = 0; i < items.length; i += size) {
      final end = (i + size) > items.length ? items.length : (i + size);
      yield items.sublist(i, end);
    }
  }
}

class _ExistingLearningSubtaskRow {
  const _ExistingLearningSubtaskRow({
    required this.id,
    required this.learningItemUuid,
  });

  final int id;
  final String learningItemUuid;
}

class _ExistingTaskRow {
  const _ExistingTaskRow({required this.id, required this.learningItemUuid});
  final int id;
  final String learningItemUuid;
}

class _ExistingRecordRow {
  const _ExistingRecordRow({
    required this.reviewTaskUuid,
    required this.action,
    required this.occurredAt,
  });

  final String reviewTaskUuid;
  final String action;
  final DateTime occurredAt;
}
