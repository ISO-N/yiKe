// 文件用途：TaskDetailSheet Phase3 Widget 测试，覆盖错误提示、编辑弹窗、主题选择与子任务失败提示等高风险路径。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/learning_subtask.dart';
import 'package:yike/domain/entities/learning_topic.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/pages/tasks/task_detail_sheet.dart';
import 'package:yike/presentation/providers/task_detail_provider.dart';

/// 测试用 LearningItemRepository：仅实现 getAllTags，其余走 noSuchMethod。
class _FakeLearningItemRepository implements LearningItemRepository {
  _FakeLearningItemRepository({required this.tags});

  final List<String> tags;

  @override
  Future<List<String>> getAllTags() async => tags;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// 测试用 ManageTopicUseCase：仅实现 getAll，其余走 noSuchMethod。
class _FakeManageTopicUseCase implements ManageTopicUseCase {
  _FakeManageTopicUseCase({required this.topics});

  final List<LearningTopicEntity> topics;

  @override
  Future<List<LearningTopicEntity>> getAll() async => topics;

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// 测试用 TaskDetailNotifier：跳过真实 load，并允许注入失败分支。
class _TestTaskDetailNotifier extends TaskDetailNotifier {
  _TestTaskDetailNotifier(
    super.ref,
    super.learningItemId, {
    required TaskDetailState initial,
  }) {
    state = initial;
  }

  bool throwOnCreateSubtask = false;

  @override
  Future<void> load() async {
    // 说明：测试通过 initial state 控制 UI，避免依赖真实数据库与用例链路。
  }

  @override
  Future<void> updateBasicInfo({
    required String title,
    required List<String> tags,
    required Set<int> topicIds,
  }) async {
    final current = state.item;
    if (current == null) return;
    final nextTopics =
        state.topics
            .where((t) => t.id != null && topicIds.contains(t.id))
            .toList();
    state = state.copyWith(
      item: current.copyWith(title: title, tags: tags),
      topics: nextTopics,
    );
  }

  @override
  Future<void> createSubtask(String content) async {
    if (throwOnCreateSubtask) throw StateError('故意失败：createSubtask');
  }
}

/// 泵起 TaskDetailSheet（使用最小 ProviderContainer 装配）。
Future<ProviderContainer> _pumpSheet(
  WidgetTester tester, {
  required TaskDetailState state,
  required List<String> availableTags,
  required List<LearningTopicEntity> allTopics,
  bool openEditOnLoad = false,
  int learningItemId = 1,
  bool throwOnCreateSubtask = false,
}) async {
  tester.view.physicalSize = const Size(900, 1600);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  late _TestTaskDetailNotifier notifier;
  final container = ProviderContainer(
    overrides: [
      learningItemRepositoryProvider.overrideWithValue(
        _FakeLearningItemRepository(tags: availableTags),
      ),
      manageTopicUseCaseProvider.overrideWithValue(
        _FakeManageTopicUseCase(topics: allTopics),
      ),
      taskDetailProvider.overrideWith((ref, id) {
        notifier = _TestTaskDetailNotifier(ref, id, initial: state)
          ..throwOnCreateSubtask = throwOnCreateSubtask;
        return notifier;
      }),
    ],
  );
  addTearDown(container.dispose);

  await tester.pumpWidget(
    UncontrolledProviderScope(
      container: container,
      child: MaterialApp(
        home: TaskDetailSheet(
          learningItemId: learningItemId,
          openEditOnLoad: openEditOnLoad,
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();

  return container;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('TaskDetailSheet Phase3 widget paths', () {
    testWidgets('展示错误卡片、空计划文案，并可打开基本信息编辑与主题筛选搜索', (tester) async {
      final item = LearningItemEntity(
        uuid: 'uuid-1',
        id: 1,
        title: '测试学习内容',
        description: null,
        note: '旧备注',
        tags: const ['旧标签'],
        learningDate: DateTime(2026, 3, 7),
        createdAt: DateTime(2026, 3, 7),
        updatedAt: DateTime(2026, 3, 7),
      );

      final topics = <LearningTopicEntity>[
        LearningTopicEntity(
          uuid: 't1',
          id: 1,
          name: '主题A',
          createdAt: DateTime(2026, 3, 7),
          itemIds: const [1],
        ),
        LearningTopicEntity(
          uuid: 't2',
          id: 2,
          name: '主题B',
          createdAt: DateTime(2026, 3, 7),
          itemIds: const [1],
        ),
        LearningTopicEntity(
          uuid: 't3',
          id: 3,
          name: '主题C',
          createdAt: DateTime(2026, 3, 7),
          itemIds: const [1],
        ),
      ];

      final state = TaskDetailState(
        isLoading: false,
        item: item,
        // 计划为空：覆盖“暂无复习任务”分支。
        plan: const [],
        subtasks: <LearningSubtaskEntity>[
          LearningSubtaskEntity(
            uuid: 's1',
            id: 11,
            learningItemId: 1,
            content: '子任务1',
            sortOrder: 0,
            createdAt: DateTime(2026, 3, 7),
          ),
        ],
        topics: topics,
        errorMessage: '测试错误提示',
      );

      await _pumpSheet(
        tester,
        state: state,
        availableTags: const ['TagX', 'TagY'],
        allTopics: topics,
      );

      expect(find.text('测试错误提示'), findsOneWidget);
      expect(find.text('暂无复习任务'), findsOneWidget);

      // 打开“编辑基本信息”Sheet。
      final infoCard = find.ancestor(
        of: find.text('基本信息'),
        matching: find.byType(Card),
      );
      expect(infoCard, findsOneWidget);
      await tester.tap(
        find.descendant(of: infoCard, matching: find.widgetWithText(OutlinedButton, '编辑')),
      );
      await tester.pumpAndSettle();
      expect(find.text('编辑基本信息'), findsOneWidget);

      // 主题摘要：3 个主题时会走 “A，B 等 3 个” 的摘要分支。
      expect(find.textContaining('等 3 个'), findsOneWidget);

      // 触发标签 ActionChip，覆盖 _appendTag 分支（会更新 tags controller）。
      await tester.tap(find.widgetWithText(ActionChip, 'TagX'));
      await tester.pumpAndSettle();

      // 打开主题选择对话框，输入搜索关键字以覆盖过滤分支。
      await tester.tap(find.widgetWithText(ListTile, '主题'));
      await tester.pumpAndSettle();
      expect(find.text('选择主题（可多选）'), findsOneWidget);
      await tester.enterText(find.byType(TextField).first, '主题B');
      await tester.pumpAndSettle();
      expect(find.text('主题B'), findsWidgets);

      // 取消关闭对话框（覆盖 pop(null/false) 分支）。
      await tester.tap(find.text('取消').last);
      await tester.pumpAndSettle();
      expect(find.text('选择主题（可多选）'), findsNothing);
    });

    testWidgets('新增子任务失败会通过 SnackBar 提示“操作失败”', (tester) async {
      final item = LearningItemEntity(
        uuid: 'uuid-2',
        id: 2,
        title: '测试学习内容2',
        description: '描述',
        note: null,
        tags: const [],
        learningDate: DateTime(2026, 3, 7),
        createdAt: DateTime(2026, 3, 7),
      );

      final state = TaskDetailState(
        isLoading: false,
        item: item,
        plan: const [],
        subtasks: const [],
        topics: const [],
      );

      await _pumpSheet(
        tester,
        state: state,
        availableTags: const [],
        allTopics: const [],
        throwOnCreateSubtask: true,
        learningItemId: 2,
      );

      // SubtasksCard 右上角“新增”按钮。
      await tester.tap(find.widgetWithText(OutlinedButton, '新增'));
      await tester.pumpAndSettle();

      expect(find.text('新增子任务'), findsWidgets);
      await tester.enterText(find.byType(TextField).first, '新子任务');
      await tester.tap(find.text('保存').last);
      await tester.pumpAndSettle();

      expect(find.textContaining('操作失败'), findsOneWidget);
    });
  });
}
