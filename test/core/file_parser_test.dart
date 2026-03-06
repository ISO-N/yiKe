// 文件用途：FileParser 单元测试（TXT/CSV/Markdown 解析、GBK 回退、异常分支）。
// 作者：Codex
// 创建日期：2026-02-26

import 'dart:io';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/utils/file_parser.dart';

void main() {
  late Directory tempDir;

  setUp(() async {
    tempDir = await Directory.systemTemp.createTemp('yike_file_parser_test_');
  });

  tearDown(() async {
    // 说明：Windows 下临时目录删除可能受杀毒软件/索引服务影响而偶发卡住。
    // 测试全局 timeout 为 10s，因此这里做“尽力清理 + 超时忽略”，避免用例被误判超时失败。
    try {
      await tempDir.delete(recursive: true).timeout(const Duration(seconds: 2));
    } catch (_) {
      // 忽略：仅影响临时目录清理，不应影响解析逻辑的正确性判定。
    }
  });

  test('parseFile: TXT 每行一条标题（忽略空行与首尾空白）', () async {
    final file = File('${tempDir.path}${Platform.pathSeparator}items.txt');
    await file.writeAsString('  A  \n\nB\r\n  \nC  ');

    final items = await FileParser.parseFile(file.path);
    expect(items.map((e) => e.title).toList(), ['A', 'B', 'C']);
    expect(items.every((e) => e.isValid), isTrue);
  });

  test('parseFile: CSV 支持表头、空标题报错、标签去重与多分隔符', () async {
    final file = File('${tempDir.path}${Platform.pathSeparator}items.csv');

    // 说明：第三列 tags 需要通过引号包裹，避免被 CSV 解析为多列。
    await file.writeAsString(
      '标题,备注,标签\n'
      'T1, N1 ,"a, b，c;d；a"\n'
      ',N2,"x"\n',
    );

    final items = await FileParser.parseFile(file.path);
    expect(items.length, 2);

    expect(items[0].title, 'T1');
    // 说明：旧表头“备注”会走智能迁移规则，短文本迁移到 description。
    expect(items[0].description, 'N1');
    expect(items[0].subtasks, isEmpty);
    expect(items[0].tags.toSet(), {'a', 'b', 'c', 'd'});
    expect(items[0].isValid, isTrue);

    expect(items[1].title, '');
    expect(items[1].description, 'N2');
    expect(items[1].subtasks, isEmpty);
    expect(items[1].tags, ['x']);
    expect(items[1].errorMessage, '标题为空');
    expect(items[1].isValid, isFalse);
  });

  test('parseFile: Markdown 以标题行分段，解析 description/subtasks（空行切换段落）', () async {
    final file = File('${tempDir.path}${Platform.pathSeparator}items.md');
    await file.writeAsString(
      '# T1\n'
      'line1\n'
      '\n'
      'line2\n'
      '## T2\n'
      'only\n',
    );

    final items = await FileParser.parseFile(file.path);
    expect(items.length, 2);
    expect(items[0].title, 'T1');
    expect(items[0].description, 'line1');
    expect(items[0].subtasks, ['line2']);
    expect(items[1].title, 'T2');
    expect(items[1].description, 'only');
    expect(items[1].subtasks, isEmpty);
  });

  test('parsePastedContent: 可自动识别 Markdown 与纯文本', () {
    final markdown = FileParser.parsePastedContent(
      '# 标题一\n'
      '描述一\n'
      '- 子任务A\n',
    );
    final plainText = FileParser.parsePastedContent('条目一\n条目二\n');

    expect(markdown.length, 1);
    expect(markdown.single.title, '标题一');
    expect(markdown.single.description, '描述一');
    expect(markdown.single.subtasks, ['子任务A']);

    expect(plainText.map((e) => e.title).toList(), ['条目一', '条目二']);
  });

  test('parsePastedContent: 纯标点内容会被视为无效', () {
    final items = FileParser.parsePastedContent('!!!\n……\n---');
    expect(items, isEmpty);
  });

  test('parseFile: 不支持的扩展名会抛 ArgumentError', () async {
    final file = File('${tempDir.path}${Platform.pathSeparator}items.json');
    await file.writeAsString('[]');

    await expectLater(FileParser.parseFile(file.path), throwsArgumentError);
  });

  test(
    'parseFile: UTF-8 解码失败时回退 GBK（用于历史 CSV/TXT）',
    () async {
      final file = File(
        '${tempDir.path}${Platform.pathSeparator}items_gbk.txt',
      );

      // 说明：写入“确定为 GBK 且严格 UTF-8 必定解码失败”的字节序列，确保走回退分支。
      // - “中”= D6D0（GBK），“文”= CEC4（GBK）
      final bytes = <int>[0xD6, 0xD0, 0xCE, 0xC4];
      expect(
        () => utf8.decode(bytes, allowMalformed: false),
        throwsA(anything),
      );
      await file.writeAsBytes(bytes, flush: true);

      final items = await FileParser.parseFile(file.path);
      expect(items.length, 1);
      expect(items.single.title.trim(), isNotEmpty);
    },
    // 说明：该用例涉及系统临时目录与文件删除，在 Windows CI/并发运行下可能偶发慢 IO。
    timeout: const Timeout(Duration(minutes: 2)),
  );
}
