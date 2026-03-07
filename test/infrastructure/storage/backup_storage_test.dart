// 文件用途：BackupStorage 单元测试，覆盖目录管理、原子写入、枚举、清理与删除保护。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:path/path.dart' as p;
import 'package:yike/infrastructure/storage/backup_storage.dart';

void main() {
  late Directory tempDir;
  late BackupStorage storage;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('yike_backup_storage_test_');
    storage = BackupStorage(baseDir: tempDir);
  });

  tearDown(() async {
    if (await tempDir.exists()) {
      await tempDir.delete(recursive: true);
    }
  });

  group('BackupStorage', () {
    test('会创建备份目录与快照目录，并支持读取快照文件', () async {
      final backupsDir = await storage.getBackupsDir();
      final snapshotsDir = await storage.getSnapshotsDir();
      final snapshot = await storage.writeSnapshotFile(
        fileName: 'snapshot.yikebackup',
        content: '{"snapshot":true}',
      );

      expect(await backupsDir.exists(), isTrue);
      expect(await snapshotsDir.exists(), isTrue);
      expect(
        await storage.getSnapshotFile('snapshot.yikebackup'),
        isNotNull,
      );
      expect(await snapshot.readAsString(), '{"snapshot":true}');
    });

    test('写入、枚举、清理临时文件与删除保护会按目录边界工作', () async {
      final first = await storage.writeBackupFile(
        fileName: 'a.yikebackup',
        content: '{"id":"a"}',
      );
      await Future<void>.delayed(const Duration(milliseconds: 20));
      final second = await storage.writeBackupFile(
        fileName: 'b.yikebackup',
        content: '{"id":"b"}',
      );

      // 人工制造残留 tmp，验证 cleanup 行为。
      final backupsDir = await storage.getBackupsDir();
      final snapshotsDir = await storage.getSnapshotsDir();
      await File(p.join(backupsDir.path, 'stale.tmp')).writeAsString('tmp');
      await File(p.join(snapshotsDir.path, 'stale.tmp')).writeAsString('tmp');

      final listed = await storage.listBackupFiles();
      expect(listed, hasLength(2));
      expect(
        listed.map((file) => p.basename(file.path)),
        containsAll(<String>['a.yikebackup', 'b.yikebackup']),
      );

      await storage.cleanupTempFiles();
      expect(await File(p.join(backupsDir.path, 'stale.tmp')).exists(), isFalse);
      expect(await File(p.join(snapshotsDir.path, 'stale.tmp')).exists(), isFalse);

      await storage.deleteBackupFile(second);
      expect(await second.exists(), isFalse);
      expect(await first.exists(), isTrue);

      final outside = File(p.join(tempDir.path, 'outside.yikebackup'))
        ..writeAsStringSync('x');
      await expectLater(
        () => storage.deleteBackupFile(outside),
        throwsArgumentError,
      );
    });
  });
}
