// 文件用途：Phase 2 剩余 core/domain 基础测试，覆盖文件解析补充场景与简单实体。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/file_parser.dart';
import 'package:yike/domain/entities/backup_file.dart';
import 'package:yike/domain/entities/pomodoro_record.dart';
import 'package:yike/domain/entities/pomodoro_settings.dart';
import 'package:yike/domain/entities/theme_settings.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('yike_phase2_core_test_');
  });

  tearDown(() async {
    try {
      await tempDir.delete(recursive: true);
    } catch (_) {}
  });

  test('FileParser 支持新 CSV 列、标签行 Markdown 与公开解析入口', () async {
    final csvFile = File('${tempDir.path}${Platform.pathSeparator}items.csv');
    await csvFile.writeAsString(
      'title,description,subtasks,tags\n'
      'T1,结构化描述,"- 子任务甲\n- 子任务乙","英语, 词汇"\n',
    );

    final csvItems = await FileParser.parseFile(csvFile.path);
    expect(csvItems.single.title, 'T1');
    expect(csvItems.single.description, '结构化描述');
    expect(csvItems.single.subtasks, <String>['子任务甲', '子任务乙']);
    expect(csvItems.single.tags, <String>['英语', '词汇']);

    final markdownItems = FileParser.parseMarkdownContent(
      '# 标题一\n'
      '描述一\n'
      '标签: 复盘, 数学\n'
      '* 子任务甲\n'
      '补充子任务\n',
    );
    expect(markdownItems.single.description, '描述一');
    expect(markdownItems.single.tags, <String>['复盘', '数学']);
    expect(markdownItems.single.subtasks, <String>['子任务甲', '补充子任务']);

    final txtItems = FileParser.parseTxtContent('A\nB\n');
    expect(txtItems.map((item) => item.title).toList(), <String>['A', 'B']);
  });

  test('BackupFileEntity 与简单实体支持默认值、序列化与 copyWith', () {
    final backup = BackupFileEntity.fromJson(<String, dynamic>{
      'stats': <String, dynamic>{'learningItems': '2', 'payloadSize': '128'},
      'data': <String, dynamic>{
        'learningItems': <Object?>[
          <String, dynamic>{
            'uuid': ' item-1 ',
            'title': ' 标题 ',
            'tags': <Object?>[' 英语 ', '', 1],
            'learningDate': '2026-03-06',
            'createdAt': '2026-03-06T00:00:00.000',
          },
        ],
      },
    });

    expect(backup.schemaVersion, '1.0');
    expect(backup.stats.learningItems, 2);
    expect(backup.stats.payloadSize, 128);
    expect(backup.data.learningItems.single.uuid, 'item-1');
    expect(backup.data.learningItems.single.title, '标题');
    expect(backup.data.learningItems.single.tags, <String>['英语']);
    expect(backup.toJson()['schemaVersion'], '1.0');

    final theme = ThemeSettingsEntity.defaults().copyWith(
      mode: 'dark',
      seedColorHex: '#4CAF50',
      amoled: true,
    );
    expect(theme.toJson(), <String, dynamic>{
      'mode': 'dark',
      'seed_color': '#4CAF50',
      'amoled': true,
    });
    expect(
      ThemeSettingsEntity.fromJson(theme.toJson()).seedColorHex,
      '#4CAF50',
    );

    final record = PomodoroRecordEntity(
      id: 1,
      startTime: DateTime(2026, 3, 6, 8),
      durationMinutes: 25,
      phaseType: 'work',
      completed: false,
    ).copyWith(completed: true, phaseType: 'shortBreak');
    expect(record.completed, isTrue);
    expect(record.phaseType, 'shortBreak');

    final settings = PomodoroSettingsEntity.defaults.copyWith(
      workMinutes: 30,
      longBreakInterval: 5,
    );
    expect(settings.workMinutes, 30);
    expect(settings.longBreakInterval, 5);
  });
}
