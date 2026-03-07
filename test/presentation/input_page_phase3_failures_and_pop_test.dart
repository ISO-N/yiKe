// 文件用途：InputPage Phase3 边界场景测试，覆盖部分保存失败弹窗、主题关联失败、保存遮罩与未保存复习配置拦截返回分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/pages/input/input_page.dart';

import '../helpers/test_database.dart';

/// 测试用 CreateLearningItemUseCase：支持对首条保存做延迟，并对指定标题注入失败。
class _DelayingCreateLearningItemUseCase implements CreateLearningItemUseCase {
  _DelayingCreateLearningItemUseCase({
    required CreateLearningItemUseCase delegate,
    required this.gate,
  }) : _delegate = delegate;

  final CreateLearningItemUseCase _delegate;
  final Completer<void> gate;
  bool _delayedOnce = false;

  @override
  Future<CreateLearningItemResult> execute(CreateLearningItemParams params) async {
    // 首次保存做延迟：覆盖 InputPage 的 saving 遮罩。
    if (!_delayedOnce) {
      _delayedOnce = true;
      await gate.future;
    }
    if (params.title.contains('失败')) {
      throw StateError('模拟保存失败');
    }
    return _delegate.execute(params);
  }
}

/// 测试用 ManageTopicUseCase：仅对 addItemToTopic 注入失败，用于覆盖“保存成功但关联主题失败”提示。
class _FailingAddItemToTopicUseCase extends ManageTopicUseCase {
  const _FailingAddItemToTopicUseCase({required super.repository});

  @override
  Future<void> addItemToTopic(int topicId, int learningItemId) async {
    throw StateError('模拟关联失败');
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  late AppDatabase db;
  late ProviderContainer container;

  setUp(() {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    db = createInMemoryDatabase();
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  Future<void> pumpPage(
    WidgetTester tester, {
    required Completer<void> saveGate,
  }) async {
    tester.view.physicalSize = const Size(1440, 2600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    container = ProviderContainer(
      overrides: <Override>[
        appDatabaseProvider.overrideWithValue(db),
        manageTopicUseCaseProvider.overrideWith(
          (ref) => _FailingAddItemToTopicUseCase(
            repository: ref.read(learningTopicRepositoryProvider),
          ),
        ),
        createLearningItemUseCaseProvider.overrideWith((ref) {
          final delegate = CreateLearningItemUseCase(
            learningItemRepository: ref.read(learningItemRepositoryProvider),
            learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
            reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
          );
          return _DelayingCreateLearningItemUseCase(
            delegate: delegate,
            gate: saveGate,
          );
        }),
      ],
    );

    // 预置一个主题，供“添加到主题”选择。
    await container.read(manageTopicUseCaseProvider).create(
          const TopicParams(name: '测试主题', description: '用于触发 addItemToTopic'),
        );

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp(
          home: Builder(
            builder: (context) {
              // 说明：将 InputPage 作为二级路由打开，便于使用 pageBack 触发 PopScope 拦截逻辑。
              WidgetsBinding.instance.addPostFrameCallback((_) {
                Navigator.of(context).push<void>(
                  MaterialPageRoute<void>(
                    builder: (_) => const InputPage(),
                  ),
                );
              });
              return const Scaffold(body: Text('根页面'));
            },
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pumpAndSettle();
  }

  group('InputPage phase3 edges', () {
    testWidgets('部分保存失败会展示保存结果弹窗，并在保存中显示遮罩', (tester) async {
      final gate = Completer<void>();
      await pumpPage(tester, saveGate: gate);

      // 第一条：可保存 + 选择主题（但关联主题会失败，进入 errors 列表）。
      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）'),
        '成功条目',
      );
      await tester.tap(find.text('添加到主题'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('测试主题'));
      await tester.pumpAndSettle();

      // 第二条：注入失败。
      await tester.tap(find.text('再添加一条'));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.widgetWithText(TextField, '标题（必填）').last,
        '失败条目',
      );

      // 点击保存后应进入 saving 状态，展示遮罩。
      await tester.tap(find.text('保存'));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsWidgets);

      // 放行首条保存延迟。
      gate.complete();
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 800));

      expect(find.text('保存结果'), findsOneWidget);
      expect(find.textContaining('成功：1 条'), findsOneWidget);
      expect(find.textContaining('失败：1 条'), findsOneWidget);
      expect(find.textContaining('关联主题失败'), findsOneWidget);
      expect(find.textContaining('保存失败'), findsOneWidget);

      await tester.tap(find.text('知道了'));
      await tester.pumpAndSettle();
      expect(find.text('保存结果'), findsNothing);
    });

    testWidgets('复习配置有未保存更改时返回会弹窗确认', (tester) async {
      final gate = Completer<void>()..complete();
      await pumpPage(tester, saveGate: gate);

      // 打开复习计划预览并触发未保存状态。
      await tester.tap(find.text('复习计划预览'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('减少轮次'));
      await tester.pumpAndSettle();
      expect(find.text('有未保存更改'), findsOneWidget);

      // 触发系统返回：应弹出确认对话框。
      await tester.pageBack();
      await tester.pumpAndSettle();
      expect(find.text('未保存的更改'), findsOneWidget);

      // 取消离开。
      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();
      expect(find.text('未保存的更改'), findsNothing);

      // 确认离开：清除标记并 pop。
      await tester.pageBack();
      await tester.pumpAndSettle();
      await tester.tap(find.text('离开'));
      await tester.pumpAndSettle();
      expect(find.text('根页面'), findsOneWidget);
    });
  });
}
