// 文件用途：ExportPage Widget 测试，覆盖预览切换、导出分享与统计导出主链路。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:share_plus_platform_interface/share_plus_platform_interface.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/repositories/learning_subtask_repository.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/export_data_usecase.dart';
import 'package:yike/domain/usecases/export_statistics_csv_usecase.dart';
import 'package:yike/presentation/pages/settings/export_page.dart';

class _DummyLearningItemRepository implements LearningItemRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _DummyLearningSubtaskRepository implements LearningSubtaskRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _DummyReviewTaskRepository implements ReviewTaskRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FakeExportDataUseCase extends ExportDataUseCase {
  _FakeExportDataUseCase()
    : super(
        learningItemRepository: _DummyLearningItemRepository(),
        learningSubtaskRepository: _DummyLearningSubtaskRepository(),
        reviewTaskRepository: _DummyReviewTaskRepository(),
      );

  final List<ExportParams> previewCalls = <ExportParams>[];
  final List<ExportParams> executeCalls = <ExportParams>[];

  bool previewShouldFail = false;
  bool executeShouldFail = false;

  @override
  Future<ExportPreview> preview(ExportParams params) async {
    previewCalls.add(params);
    if (previewShouldFail || (!params.includeItems && !params.includeTasks)) {
      throw const ExportException('请至少选择一种导出内容');
    }
    return ExportPreview(
      itemCount: params.includeItems ? 3 : 0,
      taskCount: params.includeTasks ? 5 : 0,
    );
  }

  @override
  Future<ExportResult> execute(ExportParams params) async {
    executeCalls.add(params);
    if (executeShouldFail) {
      throw const ExportException('导出失败');
    }

    final dir = await Directory.systemTemp.createTemp('export_page_test');
    final file = File(
      '${dir.path}${Platform.pathSeparator}yike_export_test.${params.format == ExportFormat.json ? 'json' : 'csv'}',
    );
    await file.writeAsString('exported');
    return ExportResult(
      file: file,
      itemCount: params.includeItems ? 3 : 0,
      taskCount: params.includeTasks ? 5 : 0,
      exportedAt: DateTime(2026, 3, 6, 12),
      bytes: await file.length(),
    );
  }
}

class _FakeExportStatisticsCsvUseCase extends ExportStatisticsCsvUseCase {
  _FakeExportStatisticsCsvUseCase()
    : super(reviewTaskRepository: _DummyReviewTaskRepository());

  final List<int> years = <int>[];

  @override
  Future<ExportStatisticsCsvResult> execute({
    required int year,
    String? outputPath,
  }) async {
    years.add(year);
    // 说明：当调用方已传入 outputPath（桌面端保存对话框路径）时，直接写入即可，
    // 避免在部分 Windows 环境下 createTemp 偶发卡死影响测试稳定性。
    final file = outputPath == null
        ? File(
            '${Directory.systemTemp.path}${Platform.pathSeparator}yike_statistics_$year.csv',
          )
        : File(outputPath);
    await file.writeAsString('date,done\n2026-03-06,1\n');
    return ExportStatisticsCsvResult(
      file: file,
      fileName: 'yike_statistics_$year.csv',
      bytes: await file.length(),
      start: DateTime(year, 1, 1),
      end: DateTime(year + 1, 1, 1),
    );
  }
}

class _FakeSharePlatform extends SharePlatform {
  final List<List<XFile>> sharedFileCalls = <List<XFile>>[];

  @override
  Future<ShareResult> shareXFiles(
    List<XFile> files, {
    String? subject,
    String? text,
    Rect? sharePositionOrigin,
  }) async {
    sharedFileCalls.add(List<XFile>.from(files));
    return const ShareResult('success', ShareResultStatus.success);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeExportDataUseCase exportUseCase;
  late _FakeExportStatisticsCsvUseCase statisticsUseCase;
  late _FakeSharePlatform sharePlatform;
  late SharePlatform previousSharePlatform;

  Future<void> pumpPage(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1440, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          exportDataUseCaseProvider.overrideWithValue(exportUseCase),
          exportStatisticsCsvUseCaseProvider.overrideWithValue(statisticsUseCase),
        ],
        child: const MaterialApp(home: ExportPage()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 200));
    SharePlatform.instance = sharePlatform;
  }

  setUp(() {
    exportUseCase = _FakeExportDataUseCase();
    statisticsUseCase = _FakeExportStatisticsCsvUseCase();
    sharePlatform = _FakeSharePlatform();
    previousSharePlatform = SharePlatform.instance;
    SharePlatform.instance = sharePlatform;
  });

  tearDown(() {
    SharePlatform.instance = previousSharePlatform;
  });

  group('ExportPage', () {
    testWidgets(
      '会加载预览并响应格式与内容切换',
      (tester) async {
        await pumpPage(tester);

        expect(find.text('数据预览'), findsOneWidget);
        expect(find.text('3'), findsOneWidget);
        expect(find.text('5'), findsOneWidget);
        expect(exportUseCase.previewCalls, isNotEmpty);
        expect(exportUseCase.previewCalls.last.format, ExportFormat.json);

        await tester.tap(find.text('CSV'));
        await tester.pumpAndSettle();
        expect(exportUseCase.previewCalls.last.format, ExportFormat.csv);

        await tester.tap(find.widgetWithText(CheckboxListTile, '复习任务'));
        await tester.pumpAndSettle();
        expect(exportUseCase.previewCalls.last.includeTasks, isFalse);

        await tester.tap(find.widgetWithText(CheckboxListTile, '学习内容'));
        await tester.pumpAndSettle();
        expect(find.textContaining('预览失败'), findsOneWidget);
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.android,
      }),
    );

    testWidgets(
      '支持导出并分享以及统计 CSV 导出',
      (tester) async {
        await pumpPage(tester);

        await tester.tap(find.text('导出并分享'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 300));

        expect(exportUseCase.executeCalls, hasLength(1));
        expect(find.textContaining('导出失败'), findsNothing);

        await tester.tap(find.text('导出'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 300));

        expect(statisticsUseCase.years, hasLength(1));
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.android,
      }),
    );

    testWidgets(
      '导出失败时会展示错误提示并允许继续操作',
      (tester) async {
        exportUseCase.executeShouldFail = true;
        await pumpPage(tester);

        await tester.tap(find.text('导出并分享'));
        await tester.pump();
        await tester.pump(const Duration(milliseconds: 300));

        expect(exportUseCase.executeCalls, hasLength(1));
        expect(find.textContaining('导出失败'), findsWidgets);

        // 失败后仍允许重新勾选导出选项，覆盖状态恢复逻辑。
        await tester.tap(find.widgetWithText(CheckboxListTile, '学习内容'));
        await tester.pumpAndSettle();
        expect(exportUseCase.previewCalls, isNotEmpty);
      },
      variant: const TargetPlatformVariant(<TargetPlatform>{
        TargetPlatform.android,
      }),
    );
  });
}
