// 文件用途：backup_file.dart 领域实体测试，覆盖默认值兜底、嵌套解析与序列化回放。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/backup_file.dart';

void main() {
  group('BackupFileEntity', () {
    test('fromJson 会过滤脏字段并为缺失值提供默认兜底', () {
      final file = BackupFileEntity.fromJson(<String, dynamic>{
        'schemaVersion': ' ',
        'appVersion': ' 1.0.0 ',
        'dbSchemaVersion': '9',
        'backupId': ' backup-id ',
        'createdAt': ' 2026-03-07T08:00:00+08:00 ',
        'createdAtUtc': ' 2026-03-07T00:00:00.000Z ',
        'checksum': ' sha256:abc ',
        'platform': ' desktop ',
        'deviceModel': ' ThinkPad ',
        'stats': <String, dynamic>{
          'learningItems': '2',
          'reviewTasks': 3,
          'reviewRecords': '4',
          'payloadSize': '512',
        },
        'data': <String, dynamic>{
          'learningItems': <dynamic>[
            <String, dynamic>{
              'uuid': ' item-1 ',
              'title': ' 标题 ',
              'description': ' 描述 ',
              'note': '备注',
              'tags': <dynamic>[' 英语 ', '', 1, '单词'],
              'learningDate': ' 2026-03-07 ',
              'createdAt': ' 2026-03-07T08:00:00 ',
              'updatedAt': ' 2026-03-07T09:00:00 ',
              'isDeleted': true,
              'deletedAt': ' 2026-03-08T09:00:00 ',
            },
          ],
          'learningSubtasks': <dynamic>[
            <String, dynamic>{
              'uuid': ' subtask-1 ',
              'learningItemUuid': ' item-1 ',
              'content': ' 子任务 ',
              'sortOrder': '2',
              'createdAt': ' 2026-03-07T08:30:00 ',
              'updatedAt': ' 2026-03-07T09:30:00 ',
            },
          ],
          'reviewTasks': <dynamic>[
            <String, dynamic>{
              'uuid': ' task-1 ',
              'learningItemUuid': ' item-1 ',
              'reviewRound': '3',
              'scheduledDate': ' 2026-03-09 ',
              'status': ' done ',
              'completedAt': ' 2026-03-09T09:00:00 ',
              'skippedAt': null,
              'createdAt': ' 2026-03-07T08:00:00 ',
              'updatedAt': ' 2026-03-09T09:00:00 ',
            },
          ],
          'reviewRecords': <dynamic>[
            <String, dynamic>{
              'uuid': ' record-1 ',
              'reviewTaskUuid': ' task-1 ',
              'action': ' done ',
              'occurredAt': ' 2026-03-09T09:00:00 ',
              'createdAt': ' 2026-03-09T09:00:00 ',
            },
          ],
          'settings': <String, dynamic>{'theme_mode': 'dark'},
        },
      });

      expect(file.schemaVersion, '');
      expect(file.appVersion, '1.0.0');
      expect(file.dbSchemaVersion, 9);
      expect(file.backupId, 'backup-id');
      expect(file.checksum, 'sha256:abc');
      expect(file.platform, 'desktop');
      expect(file.deviceModel, 'ThinkPad');
      expect(file.stats.learningItems, 2);
      expect(file.stats.reviewTasks, 3);
      expect(file.stats.reviewRecords, 4);
      expect(file.stats.payloadSize, 512);
      expect(file.data.learningItems.single.tags, <String>['英语', '单词']);
      expect(file.data.learningItems.single.title, '标题');
      expect(file.data.learningSubtasks.single.sortOrder, 2);
      expect(file.data.reviewTasks.single.reviewRound, 3);
      expect(file.data.reviewTasks.single.status, 'done');
      expect(file.data.reviewRecords.single.action, 'done');
      expect(file.data.settings['theme_mode'], 'dark');
    });

    test('toJson 可保持可选字段与嵌套数据完整回放', () {
      const entity = BackupFileEntity(
        schemaVersion: '1.1',
        appVersion: '1.2.0',
        dbSchemaVersion: 9,
        backupId: 'backup-1',
        createdAt: '2026-03-07T08:00:00+08:00',
        createdAtUtc: '2026-03-07T00:00:00.000Z',
        checksum: 'sha256:test',
        platform: 'desktop',
        deviceModel: 'surface',
        stats: BackupStatsEntity(
          learningItems: 1,
          reviewTasks: 1,
          reviewRecords: 1,
          payloadSize: 128,
        ),
        data: BackupDataEntity(
          learningItems: <BackupLearningItemEntity>[
            BackupLearningItemEntity(
              uuid: 'item-1',
              title: '标题',
              description: '描述',
              note: null,
              tags: <String>['a'],
              learningDate: '2026-03-07',
              createdAt: '2026-03-07T08:00:00',
              updatedAt: null,
              isDeleted: false,
              deletedAt: null,
            ),
          ],
          learningSubtasks: <BackupLearningSubtaskEntity>[
            BackupLearningSubtaskEntity(
              uuid: 'subtask-1',
              learningItemUuid: 'item-1',
              content: '子任务',
              sortOrder: 0,
              createdAt: '2026-03-07T08:10:00',
              updatedAt: null,
            ),
          ],
          reviewTasks: <BackupReviewTaskEntity>[
            BackupReviewTaskEntity(
              uuid: 'task-1',
              learningItemUuid: 'item-1',
              reviewRound: 1,
              scheduledDate: '2026-03-08',
              status: 'pending',
              completedAt: null,
              skippedAt: null,
              createdAt: '2026-03-07T08:00:00',
              updatedAt: null,
            ),
          ],
          reviewRecords: <BackupReviewRecordEntity>[
            BackupReviewRecordEntity(
              uuid: 'record-1',
              reviewTaskUuid: 'task-1',
              action: 'done',
              occurredAt: '2026-03-08T08:00:00',
              createdAt: '2026-03-08T08:00:00',
            ),
          ],
          settings: <String, dynamic>{'language': 'zh-CN'},
        ),
      );

      final json = entity.toJson();
      final reparsed = BackupFileEntity.fromJson(json);

      expect(reparsed.schemaVersion, '1.1');
      expect(reparsed.platform, 'desktop');
      expect(reparsed.deviceModel, 'surface');
      expect(reparsed.data.learningItems.single.uuid, 'item-1');
      expect(reparsed.data.learningSubtasks.single.learningItemUuid, 'item-1');
      expect(reparsed.data.reviewTasks.single.uuid, 'task-1');
      expect(reparsed.data.reviewRecords.single.reviewTaskUuid, 'task-1');
      expect(reparsed.data.settings['language'], 'zh-CN');
    });
  });
}
