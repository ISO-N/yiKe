// 文件用途：InputPage 与 ReviewPreviewPanel 的 Phase 1 集成测试，覆盖录入、模板与复习配置主链路。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:permission_handler_platform_interface/permission_handler_platform_interface.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/pages/input/input_page.dart';
import 'package:yike/presentation/providers/review_intervals_provider.dart';
import 'package:yike/presentation/providers/templates_provider.dart';

import '../helpers/test_database.dart';
import '../helpers/test_uuid.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;
  late PermissionHandlerPlatform originalPermissionPlatform;

  Future<void> pumpPage(WidgetTester tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    tester.view.physicalSize = const Size(1440, 2600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
    addTearDown(container.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: InputPage()),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
  }

  setUp(() {
    db = createInMemoryDatabase();
    originalPermissionPlatform = PermissionHandlerPlatform.instance;
  });

  tearDown(() async {
    PermissionHandlerPlatform.instance = originalPermissionPlatform;
    await db.close();
  });

  group('InputPage phase1', () {
    testWidgets('支持多条录入、主题关联与保存', (tester) async {
      final seedContainer = ProviderContainer(
        overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
      );
      addTearDown(seedContainer.dispose);
      final topic = await seedContainer.read(manageTopicUseCaseProvider).create(
        const TopicParams(name: '编程主题', description: '测试主题'),
      );

      await pumpPage(tester);

      expect(find.text('今天学了什么？'), findsOneWidget);
      expect(find.text('条目 1'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）'),
        'Riverpod 状态管理',
      );
      await tester.enterText(
        find.widgetWithText(TextField, '描述（选填）'),
        '记录 Provider 与状态流转',
      );
      await tester.tap(find.widgetWithText(OutlinedButton, '新增子任务'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '输入子任务内容'),
        '补齐 provider 测试',
      );
      await tester.enterText(
        find.widgetWithText(TextFormField, '标签（选填，用逗号分隔）'),
        'Flutter, Riverpod',
      );

      await tester.tap(find.text('添加到主题'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('编程主题'));
      await tester.pumpAndSettle();
      expect(find.text('编程主题'), findsOneWidget);

      await tester.tap(find.text('再添加一条'));
      await tester.pumpAndSettle();
      expect(find.text('条目 2'), findsOneWidget);
      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）').last,
        'GoRouter 深链',
      );

      await tester.tap(find.text('保存'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 800));

      final items = await container.read(learningItemRepositoryProvider).getAll();
      expect(items.map((item) => item.title), containsAll(<String>[
        'Riverpod 状态管理',
        'GoRouter 深链',
      ]));

      final reloadedTopic = await container.read(manageTopicUseCaseProvider).getById(
            topic.id!,
          );
      expect(reloadedTopic?.itemIds.length, 1);
      final tasks = await container.read(reviewTaskRepositoryProvider).getAllTasks();
      expect(tasks.length, greaterThanOrEqualTo(20));
    });

    testWidgets('支持保存为模板并通过模板回填当前条目', (tester) async {
      await pumpPage(tester);

      final titleField = find.widgetWithText(TextField, '标题（必填）');
      final descriptionField = find.widgetWithText(TextField, '描述（选填）');
      await tester.enterText(titleField, '模板化录入');
      await tester.enterText(descriptionField, '用于保存模板');
      await tester.tap(find.widgetWithText(OutlinedButton, '新增子任务'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '输入子任务内容'),
        '整理模板内容',
      );

      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      expect(find.text('选择模板'), findsOneWidget);

      await tester.tap(find.text('保存为模板'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, '默认模板');
      await tester.tap(find.text('保存').last);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      expect(container.read(templatesProvider).templates, hasLength(1));

      await tester.enterText(titleField, '');
      await tester.enterText(descriptionField, '');
      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('默认模板'));
      await tester.pumpAndSettle();

      expect(find.text('模板化录入'), findsOneWidget);
      expect(find.textContaining('整理模板内容'), findsWidgets);
    });

    testWidgets('快捷标签支持切换，且可通过更多页面选择全部标签', (tester) async {
      final seedContainer = ProviderContainer(
        overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
      );
      addTearDown(seedContainer.dispose);
      final repo = seedContainer.read(learningItemRepositoryProvider);
      await repo.create(
        LearningItemEntity(
          uuid: testUuid(1),
          title: '标签种子 1',
          description: null,
          note: null,
          tags: const <String>['数学', '英语'],
          learningDate: DateTime(2026, 3, 7),
          createdAt: DateTime(2026, 3, 7, 10),
          updatedAt: DateTime(2026, 3, 7, 10),
          isDeleted: false,
          deletedAt: null,
        ),
      );
      await repo.create(
        LearningItemEntity(
          uuid: testUuid(2),
          title: '标签种子 2',
          description: null,
          note: null,
          tags: const <String>['数学'],
          learningDate: DateTime(2026, 3, 6),
          createdAt: DateTime(2026, 3, 6, 9),
          updatedAt: DateTime(2026, 3, 6, 9),
          isDeleted: false,
          deletedAt: null,
        ),
      );
      await repo.create(
        LearningItemEntity(
          uuid: testUuid(3),
          title: '标签种子 3',
          description: null,
          note: null,
          tags: const <String>['物理'],
          learningDate: DateTime(2026, 3, 5),
          createdAt: DateTime(2026, 3, 5, 9),
          updatedAt: DateTime(2026, 3, 5, 9),
          isDeleted: false,
          deletedAt: null,
        ),
      );

      await pumpPage(tester);

      expect(find.text('快捷标签'), findsOneWidget);
      expect(find.text('更多'), findsOneWidget);
      final quickMathTag = find.widgetWithText(FilterChip, '数学');
      expect(quickMathTag, findsOneWidget);

      await tester.tap(quickMathTag);
      await tester.pumpAndSettle();
      var tagField = tester.widget<TextFormField>(
        find.widgetWithText(TextFormField, '标签（选填，用逗号分隔）'),
      );
      expect(tagField.controller?.text, contains('数学'));

      await tester.tap(quickMathTag);
      await tester.pumpAndSettle();
      tagField = tester.widget<TextFormField>(
        find.widgetWithText(TextFormField, '标签（选填，用逗号分隔）'),
      );
      expect(tagField.controller?.text, isNot(contains('数学')));

      await tester.tap(find.text('更多'));
      await tester.pumpAndSettle();
      expect(find.text('全部标签'), findsOneWidget);

      await tester.tap(find.widgetWithText(CheckboxListTile, '英语'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('完成'));
      await tester.pumpAndSettle();

      tagField = tester.widget<TextFormField>(
        find.widgetWithText(TextFormField, '标签（选填，用逗号分隔）'),
      );
      expect(tagField.controller?.text, contains('英语'));
    });

    testWidgets('支持调整复习计划并保存配置', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.text('复习计划预览'));
      await tester.pumpAndSettle();
      expect(find.text('减少轮次'), findsOneWidget);
      expect(find.text('确认保存'), findsOneWidget);

      await tester.tap(find.text('减少轮次'));
      await tester.pumpAndSettle();
      expect(find.textContaining('有未保存更改'), findsOneWidget);

      await tester.tap(find.text('恢复默认'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('确认保存'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      final state = container.read(reviewIntervalsProvider);
      expect(state.configs.length, 10);
      expect(state.errorMessage, isNull);
    });

    testWidgets('支持新建主题并删除额外条目', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.text('再添加一条'));
      await tester.pumpAndSettle();
      expect(find.text('条目 2'), findsOneWidget);

      await tester.tap(find.text('添加到主题').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('新建主题'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '主题名称（必填）'),
        '输入页新主题',
      );
      await tester.enterText(
        find.widgetWithText(TextField, '主题描述（选填）'),
        '来自输入页测试',
      );
      await tester.tap(find.text('创建'));
      await tester.pumpAndSettle();

      final topics = await container.read(manageTopicUseCaseProvider).getAll();
      expect(topics.map((topic) => topic.name), contains('输入页新主题'));

      await tester.tap(find.byTooltip('删除条目').last);
      await tester.pumpAndSettle();
      expect(find.text('条目 2'), findsNothing);
    });

    testWidgets('支持空标题模板提示与同名模板覆盖保存', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存为模板'));
      await tester.pumpAndSettle();
      expect(find.text('当前条目标题为空，无法保存为模板'), findsOneWidget);

      final titleField = find.widgetWithText(TextField, '标题（必填）');
      final descriptionField = find.widgetWithText(TextField, '描述（选填）');
      await tester.enterText(titleField, '覆盖模板标题');
      await tester.enterText(descriptionField, '第一次描述');
      await tester.tap(find.widgetWithText(OutlinedButton, '新增子任务'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '输入子任务内容'),
        '第一次子任务',
      );

      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存为模板'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, '同名模板');
      await tester.tap(find.text('保存').last);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      expect(container.read(templatesProvider).templates, hasLength(1));

      await tester.enterText(descriptionField, '覆盖后的描述');
      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存为模板'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, '同名模板');
      await tester.tap(find.text('保存').last);
      await tester.pumpAndSettle();
      expect(find.text('模板名称已存在'), findsOneWidget);
      await tester.tap(find.text('覆盖'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      final savedTemplate = container.read(templatesProvider).templates.single;
      expect(savedTemplate.name, '同名模板');
      expect(savedTemplate.notePattern, contains('覆盖后的描述'));
    });

    testWidgets('支持标签快捷追加、取消主题选择与空模板名提示', (tester) async {
      final seedContainer = ProviderContainer(
        overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
      );
      addTearDown(seedContainer.dispose);

      await seedContainer.read(createLearningItemUseCaseProvider).execute(
            CreateLearningItemParams(
              title: '标签种子',
              tags: const <String>['已有标签'],
              reviewIntervals: <ReviewIntervalConfigEntity>[
                ReviewIntervalConfigEntity(
                  round: 1,
                  intervalDays: 1,
                  enabled: true,
                ),
              ],
            ),
          );
      await seedContainer.read(manageTopicUseCaseProvider).create(
            const TopicParams(name: '可取消主题', description: '用于取消关联'),
          );

      await pumpPage(tester);

      await tester.tap(find.text('已有标签'));
      await tester.pumpAndSettle();
      final tagField = tester.widget<TextFormField>(
        find.widgetWithText(TextFormField, '标签（选填，用逗号分隔）'),
      );
      expect(tagField.controller?.text, '已有标签');

      await tester.tap(find.text('添加到主题'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('可取消主题'));
      await tester.pumpAndSettle();
      expect(find.text('可取消主题'), findsOneWidget);

      await tester.tap(find.text('添加到主题'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('不选择主题'));
      await tester.pumpAndSettle();
      expect(find.text('可取消主题'), findsNothing);
      expect(find.text('不选择主题'), findsOneWidget);

      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）'),
        '空模板名称测试',
      );
      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('保存为模板'));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField).last, '   ');
      await tester.tap(find.text('保存').last);
      await tester.pumpAndSettle();

      expect(find.text('模板名称不能为空'), findsOneWidget);
    });

    testWidgets('OCR 拍照权限被拒绝时支持前往系统设置', (tester) async {
      final fakePermissionPlatform = _FakePermissionHandlerPlatform();
      PermissionHandlerPlatform.instance = fakePermissionPlatform;

      await pumpPage(tester);

      await tester.tap(find.text('OCR'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('拍照识别'));
      await tester.pumpAndSettle();

      expect(find.text('需要相机权限'), findsOneWidget);
      await tester.tap(find.text('去设置'));
      await tester.pumpAndSettle();

      expect(fakePermissionPlatform.requestedPermissions, contains(Permission.camera));
      expect(fakePermissionPlatform.openSettingsCalled, isTrue);
    });

    testWidgets('支持从模板面板进入模板管理页', (tester) async {
      await pumpPage(tester);

      await tester.tap(find.text('模板'));
      await tester.pumpAndSettle();
      expect(find.text('还没有模板，点击下方“管理模板”创建'), findsOneWidget);

      await tester.tap(find.text('管理模板'));
      await tester.pumpAndSettle();

      expect(find.text('模板管理'), findsOneWidget);
      expect(find.text('还没有模板\n点击右上角 + 新建一个吧'), findsOneWidget);
    });
  });
}

class _FakePermissionHandlerPlatform extends PermissionHandlerPlatform {
  bool openSettingsCalled = false;
  final List<Permission> requestedPermissions = <Permission>[];

  @override
  Future<PermissionStatus> checkPermissionStatus(Permission permission) async {
    return PermissionStatus.denied;
  }

  @override
  Future<ServiceStatus> checkServiceStatus(Permission permission) async {
    return ServiceStatus.disabled;
  }

  @override
  Future<bool> openAppSettings() async {
    openSettingsCalled = true;
    return true;
  }

  @override
  Future<Map<Permission, PermissionStatus>> requestPermissions(
    List<Permission> permissions,
  ) async {
    requestedPermissions.addAll(permissions);
    return <Permission, PermissionStatus>{
      for (final permission in permissions) permission: PermissionStatus.denied,
    };
  }

  @override
  Future<bool> shouldShowRequestPermissionRationale(
    Permission permission,
  ) async {
    return false;
  }
}
