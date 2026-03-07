// 文件用途：TemplateEditPage Phase 3 Widget 测试，覆盖校验提示、子任务增删、同名覆盖弹窗与保存失败提示等分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/learning_template.dart';
import 'package:yike/domain/repositories/learning_template_repository.dart';
import 'package:yike/domain/usecases/manage_template_usecase.dart';
import 'package:yike/presentation/pages/input/template_edit_page.dart';
import 'package:yike/presentation/providers/templates_provider.dart';

import '../helpers/test_database.dart';

class _DummyLearningTemplateRepository implements LearningTemplateRepository {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

class _FailingManageTemplateUseCase extends ManageTemplateUseCase {
  _FailingManageTemplateUseCase({required this.templates})
    : super(repository: _DummyLearningTemplateRepository());

  final List<LearningTemplateEntity> templates;

  @override
  Future<List<LearningTemplateEntity>> getAll() async => templates;

  @override
  Future<LearningTemplateEntity> create(TemplateParams params) async {
    throw StateError('模板名称已存在');
  }

  @override
  Future<LearningTemplateEntity> update(
    int id,
    TemplateParams params, {
    required DateTime createdAt,
    required String uuid,
  }) async {
    throw StateError('模拟覆盖失败');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;
  late ProviderContainer container;

  Future<void> pumpPage(
    WidgetTester tester,
    Widget page, {
    ProviderContainer? testContainer,
  }) async {
    SharedPreferences.setMockInitialValues(const <String, Object>{});
    tester.view.physicalSize = const Size(1200, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: testContainer ?? container,
        child: MaterialApp(home: page),
      ),
    );
    await tester.pumpAndSettle();
  }

  setUp(() {
    db = createInMemoryDatabase();
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('TemplateEditPage Phase3', () {
    testWidgets('空字段保存会触发表单校验，并支持新增/删除子任务', (tester) async {
      await pumpPage(tester, const TemplateEditPage());

      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();
      expect(find.text('请输入模板名称'), findsOneWidget);
      expect(find.text('请输入标题模板'), findsOneWidget);

      await tester.enterText(
        find.byType(TextFormField).at(0),
        '模板A',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        '标题 {date}',
      );
      await tester.enterText(
        find.byType(TextFormField).at(3),
        '英语, 单词',
      );

      // 新增一个子任务并删除。
      await tester.tap(find.widgetWithText(OutlinedButton, '新增'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byType(TextField).last,
        '子任务1',
      );
      expect(find.text('子任务1'), findsOneWidget);
      await tester.tap(find.byIcon(Icons.delete_outline).last);
      await tester.pumpAndSettle();
      expect(find.text('（无）'), findsOneWidget);
    });

    testWidgets('同名模板保存会弹出覆盖对话框并完成覆盖保存', (tester) async {
      final notifier = container.read(templatesProvider.notifier);
      await notifier.create(
        const TemplateParams(
          name: '重复模板',
          titlePattern: '旧标题',
          notePattern: '旧描述',
          tags: <String>['旧'],
        ),
      );
      await tester.pumpAndSettle();

      await pumpPage(tester, const TemplateEditPage());

      await tester.enterText(
        find.byType(TextFormField).at(0),
        '重复模板',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        '新标题 {date}',
      );
      await tester.enterText(
        find.byType(TextFormField).at(3),
        '新, 标签',
      );

      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();

      // 同名时会弹出对话框，允许覆盖或重命名。
      expect(find.text('模板名称已存在'), findsOneWidget);
      await tester.tap(find.text('覆盖'));
      await tester.pumpAndSettle();

      // 页面应关闭（pop），且模板被覆盖更新。
      final templates = container.read(templatesProvider).templates;
      final updated = templates.singleWhere((t) => t.name == '重复模板');
      expect(updated.titlePattern, contains('新标题'));
      expect(updated.tags, containsAll(<String>['新', '标签']));
    });

    testWidgets('覆盖失败时会给出 SnackBar 提示', (tester) async {
      final existing = LearningTemplateEntity(
        id: 1,
        uuid: 'dup-uuid',
        name: '重复模板',
        titlePattern: '旧标题',
        notePattern: '旧描述',
        tags: const <String>[],
        sortOrder: 0,
        createdAt: DateTime(2026, 3, 7),
        updatedAt: DateTime(2026, 3, 7),
      );

      final failingUseCase = _FailingManageTemplateUseCase(
        templates: <LearningTemplateEntity>[existing],
      );

      final localDb = createInMemoryDatabase();
      addTearDown(() async => localDb.close());

      final localContainer = ProviderContainer(
        overrides: <Override>[
          appDatabaseProvider.overrideWithValue(localDb),
          manageTemplateUseCaseProvider.overrideWithValue(failingUseCase),
        ],
      );
      addTearDown(localContainer.dispose);

      await pumpPage(
        tester,
        const TemplateEditPage(),
        testContainer: localContainer,
      );

      await tester.enterText(
        find.byType(TextFormField).at(0),
        '重复模板',
      );
      await tester.enterText(
        find.byType(TextFormField).at(1),
        '新标题',
      );

      await tester.tap(find.text('保存'));
      await tester.pumpAndSettle();

      expect(find.text('模板名称已存在'), findsOneWidget);
      await tester.tap(find.text('覆盖'));
      await tester.pumpAndSettle();

      // update 抛异常后应提示覆盖失败。
      expect(find.textContaining('覆盖失败'), findsOneWidget);
    });
  });
}
