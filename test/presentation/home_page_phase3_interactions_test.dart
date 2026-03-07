// 文件用途：HomePage Phase3 交互回归测试，覆盖批量操作、撤销 Snackbar、滑动失败回滚与 all-tab 分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_timeline.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/presentation/pages/home/home_page.dart';
import 'package:yike/presentation/pages/input/input_page.dart';
import 'package:yike/presentation/pages/topics/topics_page.dart';
import 'package:yike/presentation/providers/home_task_filter_provider.dart';
import 'package:yike/presentation/providers/home_task_tab_provider.dart';
import 'package:yike/presentation/providers/home_time_filter_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/notification_permission_provider.dart';
import 'package:yike/presentation/providers/sync_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';
import 'package:yike/presentation/pages/home/widgets/home_tab_switcher.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_database.dart';

/// 用于覆盖 HomePage 的可控 HomeTasksNotifier。
///
/// 说明：
/// - 通过 override `homeTasksProvider` 注入，使 UI 行为可预测且无需依赖数据库。
/// - 仅实现本文件测试会触发的操作方法（完成/跳过/撤销/批量/选择）。
class _TestHomeTasksNotifier extends HomeTasksNotifier {
  _TestHomeTasksNotifier(super.ref, {required HomeTasksState initial}) {
    state = initial;
  }

  /// 是否在“完成”操作时抛出异常（用于覆盖 HomePage.runAction 的失败提示分支）。
  bool throwOnComplete = false;

  /// 是否在“跳过”操作时抛出异常（用于覆盖 HomePage.runAction 的失败提示分支）。
  bool throwOnSkip = false;

  int completeSelectedCalls = 0;
  int skipSelectedCalls = 0;
  int undoCalls = 0;

  @override
  void toggleSelectionMode() {
    state = state.copyWith(
      isSelectionMode: !state.isSelectionMode,
      selectedTaskIds: const <int>{},
    );
  }

  @override
  void toggleSelected(int taskId) {
    final next = Set<int>.from(state.selectedTaskIds);
    if (next.contains(taskId)) {
      next.remove(taskId);
    } else {
      next.add(taskId);
    }
    state = state.copyWith(selectedTaskIds: next);
  }

  @override
  Future<void> completeTask(int taskId) async {
    if (throwOnComplete) throw StateError('故意失败：completeTask');
  }

  @override
  Future<void> completeTaskWithoutReload(int taskId) async {
    if (throwOnComplete) throw StateError('故意失败：completeTaskWithoutReload');
  }

  @override
  Future<void> skipTask(int taskId) async {
    if (throwOnSkip) throw StateError('故意失败：skipTask');
  }

  @override
  Future<void> skipTaskWithoutReload(int taskId) async {
    if (throwOnSkip) throw StateError('故意失败：skipTaskWithoutReload');
  }

  @override
  Future<void> completeSelected() async {
    completeSelectedCalls++;
    // 说明：仅更新选择模式与选中集合即可覆盖 UI 的“成功回收”分支。
    state = state.copyWith(isSelectionMode: false, selectedTaskIds: const {});
  }

  @override
  Future<void> skipSelected() async {
    skipSelectedCalls++;
    state = state.copyWith(isSelectionMode: false, selectedTaskIds: const {});
  }

  @override
  Future<void> undoTaskStatus(int taskId) async {
    undoCalls++;
  }

  @override
  Future<void> load() async {
    // 说明：本测试通过 initial state 控制 UI，不需要触发真实加载。
  }
}

/// 用于覆盖 all-tab 分支的可控 TaskHubNotifier。
class _TestTaskHubNotifier extends TaskHubNotifier {
  _TestTaskHubNotifier(super.ref, {required TaskHubState initial}) {
    state = initial;
  }

  int loadMoreCalls = 0;
  int setTimeFilterCalls = 0;

  @override
  Future<void> loadInitial() async {
    // 说明：避免构造时触发真实 usecase 调用。
  }

  @override
  Future<void> loadMore() async {
    loadMoreCalls++;
  }

  @override
  Future<void> setTimeFilter(HomeTimeFilter next) async {
    setTimeFilterCalls++;
    state = state.copyWith(timeFilter: next);
  }
}

/// 用于覆盖 HomePage 顶部同步图标与 tooltip 的静态 SyncController。
class _StaticSyncController extends SyncController {
  _StaticSyncController(super.ref, {required SyncUiState value}) {
    state = value;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const appSettingsChannel = MethodChannel('app_settings');

  setUp(() {
    // 说明：HomePage/SettingsPage 会调用 app_settings 插件打开系统设置页，测试环境需兜底。
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(appSettingsChannel, (call) async {
      // 兼容 app_settings 的不同 method 命名，不做行为断言，仅保证不抛 MissingPluginException。
      if (call.method.contains('open')) return true;
      return null;
    });
  });

  tearDown(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(appSettingsChannel, null);
  });

  group('HomePage Phase3 interactions', () {
    testWidgets('冷启动空态会展示入口按钮并可导航到录入/专题', (tester) async {
      late _TestHomeTasksNotifier notifier;

      // 说明：使用 override 的方式注入，不依赖真实 DB。
      final harness = await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 1600),
        overrides: [
          notificationPermissionProvider.overrideWith(
            (ref) async => NotificationPermissionState.unknown,
          ),
          homeTasksProvider.overrideWith((ref) {
            notifier = _TestHomeTasksNotifier(
              ref,
              initial: HomeTasksState.initial().copyWith(
                isLoading: false,
                learningItemCount: 0,
                todayPending: const [],
                overduePending: const [],
                todayCompleted: const [],
                todaySkipped: const [],
              ),
            );
            return notifier;
          }),
        ],
      );

      expect(find.text('还没有任何学习内容'), findsOneWidget);
      expect(find.text('＋添加学习内容'), findsOneWidget);
      expect(find.text('或创建学习专题'), findsOneWidget);

      await tester.tap(find.widgetWithText(FilledButton, '＋添加学习内容'));
      await tester.pumpAndSettle();
      expect(find.byType(InputPage), findsOneWidget);

      await goToRoute(tester, harness, '/home');
      await tester.tap(find.widgetWithText(TextButton, '或创建学习专题'));
      await tester.pumpAndSettle();
      expect(find.byType(TopicsPage), findsOneWidget);
    });

    testWidgets('批量模式会展示底部操作栏，并在完成/跳过后退出选择模式', (tester) async {
      final task1 = ReviewTaskViewEntity(
        taskId: 1,
        learningItemId: 101,
        title: '待复习任务 A',
        description: '第一行\n第二行\n第三行\n第四行\n第五行\n第六行',
        note: '旧备注',
        subtaskCount: 2,
        tags: ['语文', '必背', '阶段1', '多余标签'],
        reviewRound: 1,
        scheduledDate: DateTime(2026, 3, 7),
        status: ReviewTaskStatus.pending,
        completedAt: null,
        skippedAt: null,
        isDeleted: false,
        deletedAt: null,
      );

      late _TestHomeTasksNotifier notifier;

      // 说明：该用例需要点击 HomePage 自身的 bottomNavigationBar（批量操作栏）。
      // 若直接使用生产 YiKeApp + ShellRoute，页面内 Scaffold 的 bottomNavigationBar
      // 可能被外层底部导航遮挡，导致 hitTest 不稳定。因此这里用最小 GoRouter 单页装配。
      final AppDatabase db = createInMemoryDatabase();
      addTearDown(() async => db.close());

      final container = ProviderContainer(
        overrides: [
          appDatabaseProvider.overrideWithValue(db),
          notificationPermissionProvider.overrideWith(
            (ref) async => NotificationPermissionState.disabled,
          ),
          homeTaskFilterProvider.overrideWith((ref) => ReviewTaskFilter.pending),
          homeTasksProvider.overrideWith((ref) {
            notifier = _TestHomeTasksNotifier(
              ref,
              initial: HomeTasksState.initial().copyWith(
                isLoading: false,
                learningItemCount: 1,
                todayPending: [task1],
                overduePending: const [],
                todayCompleted: const [],
                todaySkipped: const [],
                isSelectionMode: false,
                selectedTaskIds: const <int>{},
              ),
            );
            return notifier;
          }),
          syncControllerProvider.overrideWith(
            (ref) => _StaticSyncController(
              ref,
              value: SyncUiState.initial().copyWith(state: SyncState.error),
            ),
          ),
        ],
      );
      addTearDown(container.dispose);

      tester.view.physicalSize = const Size(390, 1400);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final router = GoRouter(
        initialLocation: '/home',
        routes: [
          GoRoute(path: '/home', builder: (context, state) => const HomePage()),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.pumpAndSettle();

      // 通知权限 disabled 时，首页可能弹出引导弹窗；先关闭弹窗再进行后续交互。
      if (find.text('开启通知，确保及时复习').evaluate().isNotEmpty) {
        await tester.tap(find.text('稍后'));
        await tester.pumpAndSettle();
      }

      // 进入批量模式（AppBar 右侧 “批量” 文案）。
      await tester.tap(find.widgetWithText(TextButton, '批量'));
      await tester.pumpAndSettle();

      // 选中一条任务：selectionMode 下会显示 Checkbox。
      await tester.tap(find.byType(Checkbox).first);
      await tester.pumpAndSettle();

      expect(find.textContaining('已选择 1 项'), findsOneWidget);
      expect(find.text('完成所选'), findsOneWidget);
      expect(find.text('跳过所选'), findsOneWidget);

      await tester.tap(find.widgetWithText(FilledButton, '完成所选'));
      await tester.pumpAndSettle();
      expect(notifier.completeSelectedCalls, 1);
      // 退出选择模式后底部栏应消失。
      expect(find.textContaining('已选择'), findsNothing);

      // 再次进入批量模式，覆盖“跳过所选”分支。
      await tester.tap(find.widgetWithText(TextButton, '批量'));
      await tester.pumpAndSettle();
      await tester.tap(find.byType(Checkbox).first);
      await tester.pumpAndSettle();
      expect(find.textContaining('已选择 1 项'), findsOneWidget);
      await tester.ensureVisible(find.widgetWithText(OutlinedButton, '跳过所选'));
      await tester.tap(find.widgetWithText(OutlinedButton, '跳过所选'));
      await tester.pumpAndSettle();
      expect(notifier.skipSelectedCalls, 1);
      expect(find.textContaining('已选择'), findsNothing);
    });

    testWidgets('滑动完成失败时会回弹并提示“操作失败，请重试”', (tester) async {
      final task1 = ReviewTaskViewEntity(
        taskId: 2,
        learningItemId: 102,
        title: '可滑动任务',
        description: 'desc',
        note: null,
        subtaskCount: 0,
        tags: ['测试'],
        reviewRound: 1,
        scheduledDate: DateTime(2026, 3, 7),
        status: ReviewTaskStatus.pending,
        completedAt: null,
        skippedAt: null,
        isDeleted: false,
        deletedAt: null,
      );

      late _TestHomeTasksNotifier notifier;

      await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 1400),
        overrides: [
          notificationPermissionProvider.overrideWith(
            (ref) async => NotificationPermissionState.unknown,
          ),
          homeTaskFilterProvider.overrideWith((ref) => ReviewTaskFilter.pending),
          homeTasksProvider.overrideWith((ref) {
            notifier = _TestHomeTasksNotifier(
              ref,
              initial: HomeTasksState.initial().copyWith(
                isLoading: false,
                learningItemCount: 1,
                todayPending: [task1],
                overduePending: const [],
                todayCompleted: const [],
                todaySkipped: const [],
              ),
            )..throwOnComplete = true;
            return notifier;
          }),
        ],
      );

      // 触发 Dismissible：向右滑动（完成）。
      final scrollable = find.byType(Scrollable).first;
      await tester.scrollUntilVisible(
        find.text('可滑动任务（第1次）'),
        240,
        scrollable: scrollable,
      );
      final dismissible = find.byKey(const ValueKey('task_2'));
      expect(dismissible, findsOneWidget);
      await tester.drag(dismissible, const Offset(420, 0));
      await tester.pumpAndSettle();

      // 失败时 confirmDismiss 返回 false，卡片应仍然存在。
      expect(dismissible, findsOneWidget);
      expect(find.text('操作失败，请重试'), findsOneWidget);
    });

    testWidgets('all-tab 会渲染时间线并在接近底部时触发 loadMore', (tester) async {
      final tasks = List<ReviewTaskTimelineItemEntity>.generate(32, (i) {
        final task = ReviewTaskViewEntity(
          taskId: 1000 + i,
          learningItemId: 2000 + i,
          title: '时间线任务 $i',
          description: null,
          note: null,
          subtaskCount: 0,
          tags: const [],
          reviewRound: 1,
          scheduledDate: DateTime(2026, 3, 7),
          status: ReviewTaskStatus.done,
          completedAt: DateTime(2026, 3, 7, 9, 0),
          skippedAt: null,
          isDeleted: false,
          deletedAt: null,
        );
        return ReviewTaskTimelineItemEntity(
          task: task,
          occurredAt: DateTime(2026, 3, 7, 9, 0),
        );
      });

      final timelineRows = <TaskHubTimelineRow>[
        TaskHubTimelineHeaderRow(day: DateTime(2026, 3, 7)),
        ...tasks.map(
          (e) => TaskHubTimelineTaskRow.fromEntity(
            e,
            isLastInGroup: false,
          ),
        ),
      ];

      late _TestTaskHubNotifier hubNotifier;

      await pumpYiKeApp(
        tester,
        initialLocation: '/home?tab=all',
        overrides: [
          homeTaskTabProvider.overrideWith((ref) => HomeTaskTab.all),
          taskHubProvider.overrideWith((ref) {
            hubNotifier = _TestTaskHubNotifier(
              ref,
              initial: TaskHubState.initial().copyWith(
                isLoading: false,
                items: tasks,
                timelineRows: timelineRows,
                counts: const TaskStatusCounts(
                  all: 32,
                  pending: 0,
                  done: 32,
                  skipped: 0,
                ),
                nextCursor: TaskTimelineCursorEntity(
                  occurredAt: DateTime(2026, 3, 7, 9, 0),
                  taskId: 1031,
                ),
              ),
            );
            return hubNotifier;
          }),
          // 确保 homeTasksProvider 不触发真实加载（today-tab 逻辑不会使用，但为了减噪做兜底）。
          homeTasksProvider.overrideWith(
            (ref) => _TestHomeTasksNotifier(
              ref,
              initial: HomeTasksState.initial().copyWith(isLoading: false),
            ),
          ),
        ],
      );

      // 拉到底部，触发 _onAllTasksScroll 的“接近底部 loadMore”逻辑。
      await tester.drag(find.byType(CustomScrollView), const Offset(0, -2400));
      await tester.pumpAndSettle();

      expect(hubNotifier.loadMoreCalls, greaterThanOrEqualTo(1));
    });
  });
}
