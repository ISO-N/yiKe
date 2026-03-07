// 文件用途：Core Phase3 覆盖率补齐用例，覆盖 file_parser/app_theme/time_utils/haptic_utils 等遗漏分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/theme/app_theme.dart';
import 'package:yike/core/utils/backup_utils.dart';
import 'package:yike/core/utils/file_parser.dart';
import 'package:yike/core/utils/haptic_utils.dart';
import 'package:yike/core/utils/time_utils.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Core Phase3 coverage edges', () {
    test('ParsedItem.copyWith 可覆盖 description/subtasks/tags', () {
      final item = ParsedItem(
        title: '标题',
        description: '旧描述',
        subtasks: const ['旧子任务'],
        tags: const ['旧标签'],
      );

      final next = item.copyWith(
        title: '新标题',
        description: '新描述',
        subtasks: const ['新子任务1', '新子任务2'],
        tags: const ['新标签'],
      );

      expect(next.title, '新标题');
      expect(next.description, '新描述');
      expect(next.subtasks, const ['新子任务1', '新子任务2']);
      expect(next.tags, const ['新标签']);
    });

    test('ParsedItem.copyWith 在参数为空时保持原值', () {
      final item = ParsedItem(
        title: '原始标题',
        description: '原始描述',
        subtasks: ['子任务1'],
        tags: ['标签1'],
      );

      final next = item.copyWith();
      expect(next.title, '原始标题');
      expect(next.description, '原始描述');
      expect(next.subtasks, ['子任务1']);
      expect(next.tags, ['标签1']);
    });

    test('FileParser.parseFile: CSV 无表头时仍可按旧格式解析（desc/subtasks 索引为 -1）', () async {
      final dir = await Directory.systemTemp.createTemp('yike_csv_no_header');
      addTearDown(() async => dir.delete(recursive: true));

      final file = File('${dir.path}${Platform.pathSeparator}items.csv');
      // 说明：首行首列不为“标题/title”，因此不会被识别为表头。
      // 该路径会走 headers == null 的分支，从而覆盖 descIndex/subtasksIndex 的 -1 赋值逻辑。
      await file.writeAsString('内容1,旧备注,标签1 标签2\\n');

      final parsed = await FileParser.parseFile(file.path);
      expect(parsed, hasLength(1));
      expect(parsed.single.title, '内容1');
      expect(parsed.single.isValid, isTrue);
    });

    test('FileParser.parseFile: 非法 UTF-8 且非法 GBK 时回退 allowMalformed 解码', () async {
      final dir = await Directory.systemTemp.createTemp('yike_csv_malformed');
      addTearDown(() async => dir.delete(recursive: true));

      final file = File('${dir.path}${Platform.pathSeparator}items.csv');
      // 说明：
      // - 0x80 对于 UTF-8（allowMalformed=false）会触发解码异常
      // - 同时在 GBK 中也属于非法单字节
      // - 最终应回退到 allowMalformed=true 的 UTF-8 解码路径
      // 该用例主要用于覆盖 FileParser._decodeUtf8OrGbk 的兜底分支。
      final bytes = Uint8List.fromList(<int>[
        0x80,
        ...'A,B\n'.codeUnits,
      ]);
      await file.writeAsBytes(bytes);

      final parsed = await FileParser.parseFile(file.path);
      expect(parsed, hasLength(1));
      expect(parsed.single.title, contains('A'));
      expect(parsed.single.isValid, isTrue);
    });

    test('TimeUtils.parseHHmm 在越界值时抛 FormatException', () {
      expect(
        () => TimeUtils.parseHHmm('25:00'),
        throwsA(isA<FormatException>()),
      );
    });

    test('AppTheme navigationBarTheme 在 selected 状态下使用主色', () {
      final theme = AppTheme.light(seedColor: Colors.teal);
      final selected = <WidgetState>{WidgetState.selected};

      final labelStyle = theme.navigationBarTheme.labelTextStyle?.resolve(
        selected,
      );
      final iconTheme = theme.navigationBarTheme.iconTheme?.resolve(selected);

      expect(labelStyle, isNotNull);
      expect(labelStyle?.color, theme.colorScheme.primary);
      expect(iconTheme, isNotNull);
      expect(iconTheme?.color, theme.colorScheme.primary);
    });

    test('AppTheme dark navigationBarTheme 在 selected 状态下使用主色', () {
      final theme = AppTheme.dark(seedColor: Colors.orange);
      final selected = <WidgetState>{WidgetState.selected};

      final labelStyle = theme.navigationBarTheme.labelTextStyle?.resolve(
        selected,
      );
      final iconTheme = theme.navigationBarTheme.iconTheme?.resolve(selected);

      expect(labelStyle, isNotNull);
      expect(labelStyle?.color, theme.colorScheme.primary);
      expect(iconTheme, isNotNull);
      expect(iconTheme?.color, theme.colorScheme.primary);
    });

    testWidgets('HapticUtils.mediumImpact 在移动端启用时会触发平台调用', (tester) async {
      final calls = <MethodCall>[];
      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      final originalPlatform = debugDefaultTargetPlatformOverride;
      messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        calls.add(call);
        return null;
      });
      try {
        debugDefaultTargetPlatformOverride = TargetPlatform.android;

        await tester.pumpWidget(
          const MaterialApp(home: Scaffold(body: SizedBox(key: Key('root')))),
        );

        final context = tester.element(find.byKey(const Key('root')));
        await HapticUtils.mediumImpact(context, enabledByUser: true);

        expect(calls, isNotEmpty);
        expect(
          calls.any((c) => c.method.contains('HapticFeedback')),
          isTrue,
        );

        // 保护：必须在测试结束前恢复，否则会触发 Flutter invariants。
      } finally {
        debugDefaultTargetPlatformOverride = originalPlatform;
        messenger.setMockMethodCallHandler(SystemChannels.platform, null);
      }
    });

    test('BackupProgress 可正常构造并保留字段', () {
      final progress = BackupProgress(
        stage: BackupProgressStage.preparing,
        message: '导出中',
        percent: 0.25,
      );
      expect(progress.stage, BackupProgressStage.preparing);
      expect(progress.message, '导出中');
      expect(progress.percent, 0.25);
    });
  });
}
