// 文件用途：ImportPreviewPage 粘贴导入测试（Markdown、纯文本、空内容、无效内容）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/pages/input/draft_learning_item.dart';
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

  Future<List<DraftLearningItem>?> pumpImportRoute(
    WidgetTester tester,
    Future<void> Function() act,
  ) async {
    List<DraftLearningItem>? imported;

    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          home: Builder(
            builder: (context) {
              return Scaffold(
                body: Center(
                  child: TextButton(
                    onPressed: () async {
                      imported = await Navigator.of(context).push<List<DraftLearningItem>>(
                        MaterialPageRoute<List<DraftLearningItem>>(
                          builder: (_) => const ImportPreviewPage(),
                        ),
                      );
                    },
                    child: const Text('打开导入页'),
                  ),
                ),
              );
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('打开导入页'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('粘贴导入'));
    await tester.pumpAndSettle();

    await act();
    return imported;
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

  testWidgets('粘贴导入支持编辑与删除预览条目', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '# 初始标题\n初始描述\n- 子任务一\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('编辑'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, '标题（必填）'), '编辑后的标题');
    await tester.enterText(find.widgetWithText(TextField, '描述（选填）'), '编辑后的描述');
    await tester.enterText(
      find.widgetWithText(TextField, '子任务（选填，每行一条）'),
      '子任务甲\n子任务乙',
    );
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    expect(find.text('编辑后的标题'), findsOneWidget);
    expect(find.text('编辑后的描述'), findsOneWidget);

    await tester.tap(find.byTooltip('删除'));
    await tester.pumpAndSettle();
    expect(find.text('已选 0 条 / 共 0 条'), findsOneWidget);
  });

  testWidgets('重复标题导入支持跳过重复项', (tester) async {
    final imported = await pumpImportRoute(tester, () async {
      await tester.enterText(
        find.byKey(const Key('paste_import_text_field')),
        '重复标题\n重复标题\n唯一标题\n',
      );
      await tester.tap(find.byKey(const Key('paste_import_parse_button')));
      await tester.pumpAndSettle();

      await tester.tap(find.text('导入'));
      await tester.pumpAndSettle();
      expect(find.text('检测到重复标题'), findsOneWidget);
      await tester.tap(find.text('跳过重复'));
      await tester.pumpAndSettle();
    });

    expect(imported, isNotNull);
    expect(imported!.map((item) => item.title), <String>['重复标题', '唯一标题']);
  });

  testWidgets('重复标题导入支持覆盖重复项', (tester) async {
    final imported = await pumpImportRoute(tester, () async {
      await tester.enterText(
        find.byKey(const Key('paste_import_text_field')),
        '# 重复标题\n第一次描述\n\n# 重复标题\n第二次描述\n',
      );
      await tester.tap(find.byKey(const Key('paste_import_parse_button')));
      await tester.pumpAndSettle();

      await tester.tap(find.text('导入'));
      await tester.pumpAndSettle();
      expect(find.text('检测到重复标题'), findsOneWidget);
      await tester.tap(find.text('覆盖重复'));
      await tester.pumpAndSettle();
    });

    expect(imported, isNotNull);
    expect(imported, hasLength(1));
    expect(imported!.single.title, '重复标题');
    expect(imported.single.description, '第二次描述');
  });

  testWidgets('导入前取消所有有效内容时会提示至少选择一条', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '第一条\n第二条\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(Checkbox).at(0));
    await tester.pumpAndSettle();
    await tester.tap(find.byType(Checkbox).at(1));
    await tester.pumpAndSettle();
    expect(find.text('已选 0 条 / 共 2 条'), findsOneWidget);

    await tester.tap(find.text('导入'));
    await tester.pumpAndSettle();

    expect(find.text('请至少选择一条有效内容'), findsOneWidget);
  });

  testWidgets('编辑为空标题后会标记错误并禁止勾选', (tester) async {
    await pumpPage(tester);

    await tester.enterText(
      find.byKey(const Key('paste_import_text_field')),
      '# 原始标题\n原始描述\n',
    );
    await tester.tap(find.byKey(const Key('paste_import_parse_button')));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('编辑'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, '标题（必填）'), '');
    await tester.tap(find.text('保存'));
    await tester.pumpAndSettle();

    expect(find.text('标题为空'), findsOneWidget);
    final checkbox = tester.widget<Checkbox>(find.byType(Checkbox));
    expect(checkbox.value, isFalse);
    expect(checkbox.onChanged, isNull);
  });
}
