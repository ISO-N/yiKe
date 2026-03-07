// 文件用途：Phase 1 高价值页面测试，覆盖模板编辑、主题页、OCR 结果页与设置页的关键交互。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/services/ocr_service.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/domain/usecases/ocr_recognition_usecase.dart';
import 'package:yike/presentation/pages/input/ocr_result_page.dart';
import 'package:yike/presentation/pages/input/template_edit_page.dart';
import 'package:yike/presentation/pages/settings/goal_settings_page.dart';
import 'package:yike/presentation/pages/settings/pomodoro_settings_page.dart';
import 'package:yike/presentation/pages/settings/theme_settings_page.dart';
import 'package:yike/presentation/pages/topics/topic_detail_page.dart';
import 'package:yike/presentation/pages/topics/topics_page.dart';
import 'package:yike/presentation/providers/goal_provider.dart';
import 'package:yike/presentation/providers/templates_provider.dart';
import 'package:yike/presentation/providers/theme_provider.dart';

import '../helpers/test_data_factory.dart';
import '../helpers/test_database.dart';

class _FakeOcrService implements OcrService {
  _FakeOcrService(this.results);

  final Map<String, OcrResult> results;

  @override
  Future<OcrResult> recognizeText(String imagePath) async {
    final result = results[imagePath];
    if (result == null) {
      throw StateError('未找到 OCR 结果：$imagePath');
    }
    return result;
  }
}

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  Future<void> pumpPage(
    WidgetTester tester,
    Widget page, {
    List<Override> overrides = const <Override>[],
  }) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    tester.view.physicalSize = const Size(1440, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        ...overrides,
      ],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(home: page),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 300));
  }

  setUp(() {
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    await db.close();
  });

  group('Phase1 pages', () {
    testWidgets('TemplateEditPage 支持预览、子任务编辑与保存模板', (tester) async {
      await pumpPage(tester, const TemplateEditPage());

      expect(find.text('新建模板'), findsOneWidget);
      expect(find.text('替换预览'), findsOneWidget);
      expect(find.text('（无）'), findsOneWidget);

      await tester.enterText(
        find.byType(TextFormField).at(0),
        '每日复盘模板',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        '复盘 {date}',
      );
      await tester.enterText(
        find.byType(TextFormField).at(2),
        '今天复盘重点',
      );
      await tester.tap(find.text('新增'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '输入子任务内容（支持占位符）'),
        '整理错题',
      );
      await tester.enterText(
        find.byType(TextFormField).last,
        '复盘, 模板',
      );

      expect(find.textContaining('复盘 2026-02-26'), findsOneWidget);
      expect(find.textContaining('整理错题'), findsWidgets);

      await tester.tap(find.text('保存'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      final state = container.read(templatesProvider);
      expect(state.templates, hasLength(1));
      expect(state.templates.first.name, '每日复盘模板');
      expect(state.templates.first.titlePattern, '复盘 {date}');
      expect(state.templates.first.notePattern, contains('整理错题'));
      expect(state.templates.first.tags, <String>['复盘', '模板']);
    });

    testWidgets('TopicsPage 支持查看说明、创建主题与删除主题', (tester) async {
      await pumpPage(tester, const TopicsPage());

      await tester.pumpAndSettle();
      expect(find.text('主题功能说明'), findsOneWidget);
      await tester.tap(find.text('不再提示'));
      await tester.pumpAndSettle();

      expect(find.textContaining('还没有主题'), findsOneWidget);

      await tester.tap(find.byTooltip('新建'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).at(0), '英语学习');
      await tester.enterText(find.byType(TextField).at(1), '单词和语法');
      await tester.tap(find.text('保存'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('英语学习'), findsOneWidget);
      expect(find.textContaining('0 条内容'), findsOneWidget);

      await tester.tap(find.text('删除'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('删除').last);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('英语学习'), findsNothing);
      expect(find.textContaining('还没有主题'), findsOneWidget);
    });

    testWidgets('TopicDetailPage 支持加载主题、添加关联与移除关联', (tester) async {
      final seedContainer = ProviderContainer(
        overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
      );
      addTearDown(seedContainer.dispose);
      final firstItemId = await TestDataFactory.createLearningItemWithPlan(
        seedContainer,
        title: '单词 A',
      );
      final secondItemId = await TestDataFactory.createLearningItemWithPlan(
        seedContainer,
        title: '单词 B',
      );
      final topic = await seedContainer.read(manageTopicUseCaseProvider).create(
        const TopicParams(name: '英语主题', description: '主题说明'),
      );
      await seedContainer.read(manageTopicUseCaseProvider).addItemToTopic(
            topic.id!,
            firstItemId,
          );

      await pumpPage(
        tester,
        TopicDetailPage(topicId: topic.id!),
      );

      expect(find.text('英语主题'), findsWidgets);
      expect(find.text('单词 A'), findsOneWidget);
      expect(find.textContaining('主题说明'), findsOneWidget);

      await tester.tap(find.text('+ 添加关联内容'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('单词 B'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('添加'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('单词 B'), findsOneWidget);

      await tester.drag(find.text('单词 A'), const Offset(-600, 0));
      await tester.pumpAndSettle();
      await tester.tap(find.text('移除'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      expect(find.text('单词 A'), findsNothing);
      expect(find.text('单词 B'), findsOneWidget);

      final reloaded = await container.read(manageTopicUseCaseProvider).getById(
            topic.id!,
          );
      expect(reloaded?.itemIds, <int>[secondItemId]);
    });

    testWidgets('OcrResultPage 支持识别、移除草稿与标题校验', (tester) async {
      final pngBytes = base64Decode(
        'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+ncZkAAAAASUVORK5CYII=',
      );
      final imageA = File(
        '${Directory.systemTemp.path}${Platform.pathSeparator}ocr_a.png',
      )..writeAsBytesSync(pngBytes);
      final imageB = File(
        '${Directory.systemTemp.path}${Platform.pathSeparator}ocr_b.png',
      )..writeAsBytesSync(pngBytes);
      addTearDown(() async {
        if (imageA.existsSync()) await imageA.delete();
        if (imageB.existsSync()) await imageB.delete();
      });

      final fakeService = _FakeOcrService(<String, OcrResult>{
        imageA.path: const OcrResult(
          text: '英语笔记\n重点内容\n- 子任务一\n- 子任务二',
          confidence: 0.92,
        ),
        imageB.path: const OcrResult(
          text: '数学错题\n- 整理公式',
          confidence: 0.88,
        ),
      });

      await pumpPage(
        tester,
        OcrResultPage(imagePaths: <String>[imageA.path, imageB.path]),
        overrides: <Override>[
          ocrServiceProvider.overrideWithValue(fakeService),
          ocrRecognitionUseCaseProvider.overrideWithValue(
            OcrRecognitionUseCase(ocrService: fakeService),
          ),
        ],
      );

      expect(find.text('OCR 批量识别'), findsOneWidget);
      expect(find.text('英语笔记'), findsOneWidget);
      expect(find.text('数学错题'), findsOneWidget);
      expect(find.textContaining('置信度：92%'), findsOneWidget);

      await tester.tap(find.byTooltip('移除').first);
      await tester.pumpAndSettle();
      expect(find.text('英语笔记'), findsNothing);

      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）'),
        '',
      );
      await tester.tap(find.text('全部添加到录入'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(find.textContaining('标题为空'), findsOneWidget);
    });

    testWidgets('ThemeSettingsPage 支持选择主题色并确认保存', (tester) async {
      await pumpPage(tester, const ThemeSettingsPage());

      expect(find.text('主题设置'), findsOneWidget);
      await tester.tap(find.text('绿色'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('AMOLED 深色模式'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确认'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));

      final settings = container.read(themeSettingsProvider);
      expect(settings.seedColorHex, '#4CAF50');
      expect(settings.amoled, isTrue);
    });

    testWidgets('GoalSettingsPage 支持启用每日目标', (tester) async {
      await pumpPage(tester, const GoalSettingsPage());

      expect(find.text('学习目标'), findsOneWidget);
      await tester.tap(find.text('每日完成目标'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
      expect(container.read(goalSettingsProvider).settings.dailyTarget, 10);
    });

    testWidgets('PomodoroSettingsPage 会展示配置说明与核心字段', (tester) async {
      await pumpPage(tester, const PomodoroSettingsPage());

      expect(find.text('番茄钟设置'), findsOneWidget);
      expect(find.text('配置说明'), findsOneWidget);
      expect(find.text('工作时长'), findsOneWidget);
      expect(find.text('短休息时长'), findsOneWidget);
      expect(find.text('长休息时长'), findsOneWidget);
      expect(find.text('长休息间隔轮数'), findsOneWidget);
    });

  });
}
