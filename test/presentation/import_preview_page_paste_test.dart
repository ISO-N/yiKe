// 文件用途：ImportPreviewPage 粘贴导入测试（Markdown、纯文本、空内容、无效内容）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/pages/input/import_preview_page.dart';

void main() {
  Future<void> pumpPage(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: ImportPreviewPage(autoPickFileOnOpen: false),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('粘贴导入'));
    await tester.pumpAndSettle();
  }

  testWidgets('粘贴 Markdown 后可解析并展示预览', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '# Markdown 标题\n描述内容\n- 子任务1\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    expect(find.text('Markdown 标题'), findsOneWidget);
    expect(find.text('描述内容'), findsOneWidget);
  });

  testWidgets('粘贴纯文本后按每行生成条目', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '第一条\n第二条\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    expect(find.text('第一条'), findsOneWidget);
    expect(find.text('已选 2 条 / 共 2 条'), findsOneWidget);
  });

  testWidgets('粘贴空内容时提示请输入内容', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '   \n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    expect(find.text('请输入内容'), findsOneWidget);
  });

  testWidgets('粘贴无效内容时提示未识别到有效内容', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '!!!\n……\n---',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    expect(find.text('未识别到有效内容'), findsOneWidget);
  });
}
