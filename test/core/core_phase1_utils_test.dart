// 文件用途：Phase 1 的 core 工具补充测试，覆盖颜色、CSV 导出与 BuildContext 扩展。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/core/extensions/context_extensions.dart';
import 'package:yike/core/utils/color_utils.dart';
import 'package:yike/core/utils/csv_export_utils.dart';
import 'package:yike/core/utils/haptic_utils.dart';
import 'package:yike/domain/entities/task_day_stats.dart';

void main() {
  group('ColorUtils', () {
    test('支持解析 6 位与 8 位十六进制颜色，并拒绝非法输入', () {
      expect(ColorUtils.tryParseHex('#336699'), const Color(0xFF336699));
      expect(ColorUtils.tryParseHex('80336699'), const Color(0x80336699));
      expect(ColorUtils.tryParseHex('  '), isNull);
      expect(ColorUtils.tryParseHex('#12345'), isNull);
      expect(ColorUtils.tryParseHex('#GG6699'), isNull);
    });

    test('toHexRgb 会输出规范化的大写 RGB 文本', () {
      expect(ColorUtils.toHexRgb(const Color(0xFF336699)), '#336699');
      expect(ColorUtils.toHexRgb(const Color(0x80123456)), '#123456');
    });
  });

  group('CsvExportUtils', () {
    test('会按日期区间生成统计 CSV，并输出 UTF-8 字节', () {
      final csv = CsvExportUtils.buildDailyStatisticsCsv(
        start: DateTime(2026, 3, 6),
        end: DateTime(2026, 3, 8),
        statsByDay: <DateTime, TaskDayStats>{
          DateTime(2026, 3, 6): const TaskDayStats(
            pendingCount: 2,
            doneCount: 3,
            skippedCount: 1,
          ),
        },
      );

      final lines = const LineSplitter().convert(csv.trimRight());
      expect(lines.first, 'date,completed,skipped,pending,completion_rate');
      expect(lines[1], '2026-03-06,3,1,2,60.00');
      expect(lines[2], '2026-03-07,0,0,0,0.00');
      expect(utf8.decode(CsvExportUtils.toUtf8Bytes(csv)), csv);
    });
  });

  testWidgets('ContextExtensions 可读取主题、配色与屏幕尺寸', (tester) async {
    late ThemeData theme;
    late ColorScheme colors;
    late Size screenSize;

    await tester.pumpWidget(
      MaterialApp(
        theme: ThemeData(
          colorScheme: ColorScheme.fromSeed(seedColor: Colors.teal),
        ),
        home: Builder(
          builder: (context) {
            theme = context.theme;
            colors = context.colors;
            screenSize = context.screenSize;
            return const Scaffold(body: Text('context'));
          },
        ),
      ),
    );

    expect(find.text('context'), findsOneWidget);
    expect(colors, same(theme.colorScheme));
    expect(screenSize, tester.view.physicalSize / tester.view.devicePixelRatio);
  });

  testWidgets('HapticUtils 在用户关闭触觉反馈时会直接短路', (tester) async {
    late BuildContext capturedContext;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            capturedContext = context;
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    await HapticUtils.lightImpact(capturedContext, enabledByUser: false);
    await HapticUtils.mediumImpact(capturedContext, enabledByUser: false);
    await HapticUtils.heavyImpact(capturedContext, enabledByUser: false);
  });
}
