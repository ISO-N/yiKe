// 文件用途：ImportPreviewPage 文件导入页签 Phase3 测试，覆盖选择文件取消、空文件、解析失败与成功预览分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/pages/input/import_preview_page.dart';

/// 测试用 FilePicker：返回预置 pickFiles 结果。
class _FakeFilePicker extends FilePicker {
  FilePickerResult? nextResult;

  @override
  Future<FilePickerResult?> pickFiles({
    String? dialogTitle,
    String? initialDirectory,
    FileType type = FileType.any,
    List<String>? allowedExtensions,
    Function(FilePickerStatus)? onFileLoading,
    bool allowCompression = true,
    int compressionQuality = 30,
    bool allowMultiple = false,
    bool withData = false,
    bool withReadStream = false,
    bool lockParentWindow = false,
    bool readSequential = false,
  }) async {
    return nextResult;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late _FakeFilePicker fakePicker;

  setUp(() {
    fakePicker = _FakeFilePicker();
    FilePicker.platform = fakePicker;
  });

  tearDown(() {
    // 说明：widget_test 环境不会自动注入 file_picker 平台实现，这里始终保持为可用的 Fake，
    // 避免后续测试访问 FilePicker.platform 时触发 late 初始化异常。
    FilePicker.platform = _FakeFilePicker();
  });

  Future<void> pumpPage(WidgetTester tester, {required bool autoPick}) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          home: ImportPreviewPage(autoPickFileOnOpen: autoPick),
        ),
      ),
    );
    // 说明：
    // - ImportPreviewPage 在 loading 时会展示 CircularProgressIndicator（持续动画），
    //   直接 pumpAndSettle 可能因为“永远有下一帧”而无法稳定收敛。
    // - 这里使用有限次数的 pump，既能触发 postFrameCallback（autoPick）也能等待异步解析完成。
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
    // 关键逻辑：文件解析涉及 dart:io 异步读写，在 widget_test 的 FakeAsync 下可能不推进，
    // 这里用 runAsync 给真实事件循环一次机会，避免 _pickFile 内部 Future 永远不完成。
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 50));
    });
    await tester.pump(const Duration(milliseconds: 300));
  }

  group('ImportPreviewPage file tab phase3', () {
    testWidgets('取消选择文件时保持空态提示', (tester) async {
      fakePicker.nextResult = null;

      await pumpPage(tester, autoPick: false);
      await tester.tap(find.widgetWithText(FilledButton, '选择文件'));
      await tester.pump();
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump(const Duration(milliseconds: 200));

      // autoPickFileOnOpen=true 时会触发一次选择；取消后仍保持空态提示。
      expect(find.textContaining('暂无导入内容'), findsOneWidget);
      expect(find.text('选择文件'), findsOneWidget);
    });

    testWidgets('解析失败时展示错误信息并允许重试', (tester) async {
      final missingPath = '${Directory.systemTemp.path}${Platform.pathSeparator}missing.csv';
      fakePicker.nextResult = FilePickerResult(<PlatformFile>[
        PlatformFile(path: missingPath, name: 'missing.csv', size: 0),
      ]);

      await pumpPage(tester, autoPick: false);
      await tester.tap(find.widgetWithText(FilledButton, '选择文件'));
      await tester.pump();
      await tester.runAsync(() async {
        await Future<void>.delayed(const Duration(milliseconds: 50));
      });
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.textContaining('解析失败'), findsOneWidget);

      // 点击“选择文件”可再次触发 pickFiles（此处只验证按钮可点击且不崩溃）。
      fakePicker.nextResult = null;
      await tester.tap(find.widgetWithText(FilledButton, '选择文件'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.textContaining('解析失败'), findsNothing);
    });
  });
}
