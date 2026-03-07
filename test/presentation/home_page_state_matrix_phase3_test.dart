// 文件用途：HomePage Phase 3 状态矩阵测试，通过直接注入 Provider 状态覆盖加载/空态/高负载/选择模式等分支，快速提升高风险页面覆盖率。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:yike/presentation/widgets/error_card.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/presentation/pages/home/widgets/home_tab_switcher.dart';
import 'package:yike/presentation/providers/home_task_filter_provider.dart';
import 'package:yike/presentation/providers/home_task_tab_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';
import 'package:yike/presentation/providers/task_filter_provider.dart';

import '../helpers/app_harness.dart';

/// 用于 HomePage 状态矩阵的稳定 Notifier：
/// - 不触发异步 load
/// - 初始直接置为非 loading，避免 Skeleton 动画导致 pumpAndSettle 永不结束
class _StableHomeTasksNotifier extends HomeTasksNotifier {
  _StableHomeTasksNotifier(super.ref) {
    state = HomeTasksState.initial().copyWith(isLoading: false);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues(const <String, Object>{});
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  ReviewTaskViewEntity buildTask({
    required int taskId,
    required int learningItemId,
    required String title,
    required DateTime scheduledDate,
    required ReviewTaskStatus status,
    int reviewRound = 1,
  }) {
    return ReviewTaskViewEntity(
      taskId: taskId,
      learningItemId: learningItemId,
      title: title,
      description: '描述-$title',
      note: null,
      subtaskCount: 0,
      tags: const <String>['标签A', '标签B'],
      reviewRound: reviewRound,
      scheduledDate: scheduledDate,
      status: status,
      completedAt: status == ReviewTaskStatus.done ? scheduledDate : null,
      skippedAt: status == ReviewTaskStatus.skipped ? scheduledDate : null,
      isDeleted: false,
      deletedAt: null,
    );
  }

  Future<AppHarness> pumpHomeHarness(
    WidgetTester tester, {
    Size size = const Size(390, 844),
  }) async {
    return pumpYiKeApp(
      tester,
      initialLocation: '/home',
      size: size,
      overrides: <Override>[
        // 说明：HomeTasksProvider 默认会在创建时触发异步 load；
        // 在“状态矩阵测试”中希望由测试主动注入状态，避免异步回写导致断言抖动。
        homeTasksProvider.overrideWith((ref) => _StableHomeTasksNotifier(ref)),
      ],
    );
  }

  group('HomePage Phase3 state matrix', () {
    testWidgets('加载态与错误态会渲染 Skeleton/错误卡片', (tester) async {
      // 说明：HomePage 采用 CustomScrollView + SliverList 懒构建；
      // 提高视口高度，确保错误卡片位于首屏范围内，避免因未构建导致 finder 失败。
      final harness = await pumpHomeHarness(tester, size: const Size(1440, 1800));
      final notifier = harness.container.read(homeTasksProvider.notifier);

      notifier.state = HomeTasksState.initial().copyWith(
        isLoading: true,
        errorMessage: null,
      );
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsWidgets);

      notifier.state = notifier.state.copyWith(
        isLoading: false,
        errorMessage: '加载失败：网络不可用',
      );
      await tester.pumpAndSettle();
      expect(find.byType(ErrorCard), findsWidgets);
      expect(find.textContaining('网络不可用'), findsWidgets);
    });

    testWidgets('冷启动空态会展示今日无任务提示', (tester) async {
      final harness = await pumpHomeHarness(tester, size: const Size(1440, 1800));
      final notifier = harness.container.read(homeTasksProvider.notifier);

      notifier.state = HomeTasksState.initial().copyWith(
        isLoading: false,
        learningItemCount: 0,
        todayPending: const [],
        overduePending: const [],
        todayCompleted: const [],
        todaySkipped: const [],
        completedCount: 0,
        totalCount: 0,
      );
      await tester.pumpAndSettle();

      // 说明：冷启动（无任何学习内容）空态使用专用文案与 CTA，引导用户去录入。
      expect(find.text('还没有任何学习内容'), findsWidgets);
    });

    testWidgets('高负载 + 选择模式 + 多分区数据会渲染批量操作栏与统计指标', (tester) async {
      final now = DateTime.now();
      final harness = await pumpHomeHarness(tester, size: const Size(1440, 900));
      final notifier = harness.container.read(homeTasksProvider.notifier);

      // 覆盖：切到今日 tab，且筛选为 pending 时允许选择模式。
      harness.container.read(homeTaskTabProvider.notifier).state = HomeTaskTab.today;
      harness.container.read(homeTaskFilterProvider.notifier).state =
          ReviewTaskFilter.pending;

      final todayPending = List<ReviewTaskViewEntity>.generate(
        12,
        (i) => buildTask(
          taskId: 1000 + i,
          learningItemId: 200 + (i % 3),
          title: '今日待复习 $i',
          scheduledDate: now,
          status: ReviewTaskStatus.pending,
          reviewRound: 1 + (i % 3),
        ),
      );
      final overduePending = List<ReviewTaskViewEntity>.generate(
        5,
        (i) => buildTask(
          taskId: 2000 + i,
          learningItemId: 300 + (i % 2),
          title: '逾期待复习 $i',
          scheduledDate: now.subtract(const Duration(days: 2)),
          status: ReviewTaskStatus.pending,
          reviewRound: 1,
        ),
      );
      final todayCompleted = List<ReviewTaskViewEntity>.generate(
        3,
        (i) => buildTask(
          taskId: 3000 + i,
          learningItemId: 400 + i,
          title: '已完成 $i',
          scheduledDate: now,
          status: ReviewTaskStatus.done,
          reviewRound: 1,
        ),
      );
      final todaySkipped = List<ReviewTaskViewEntity>.generate(
        2,
        (i) => buildTask(
          taskId: 4000 + i,
          learningItemId: 500 + i,
          title: '已跳过 $i',
          scheduledDate: now,
          status: ReviewTaskStatus.skipped,
          reviewRound: 1,
        ),
      );

      notifier.state = notifier.state.copyWith(
        isLoading: false,
        todayPending: todayPending,
        overduePending: overduePending,
        todayCompleted: todayCompleted,
        todaySkipped: todaySkipped,
        completedCount: todayCompleted.length,
        totalCount: todayPending.length + overduePending.length + todayCompleted.length,
        isSelectionMode: true,
        selectedTaskIds: <int>{todayPending.first.taskId, todayPending.last.taskId},
        expandedTaskIds: <int>{todayPending.first.taskId},
        lastDoneOrSkippedRoundByLearningItemId: <int, int>{
          todayCompleted.first.learningItemId: 1,
          todaySkipped.first.learningItemId: 1,
        },
        nextReviewScheduledDateByLearningItemId: <int, DateTime?>{
          todayCompleted.first.learningItemId: now.add(const Duration(days: 1)),
          todaySkipped.first.learningItemId: null,
        },
        nextReviewPreviewDisabledLearningItemIds: const <int>{},
      );
      await tester.pumpAndSettle();

      // 断言：渲染任务分区标题与批量操作栏（选择模式）。
      expect(find.text('逾期任务'), findsWidgets);
      expect(find.textContaining('已选择'), findsWidgets);
      expect(find.text('完成所选'), findsWidgets);
      expect(find.text('跳过所选'), findsWidgets);

      // 覆盖：all-tab 下会挂载 taskHubProvider（避免“未监听时 loadMore 不触发”）。
      harness.container.read(homeTaskTabProvider.notifier).state = HomeTaskTab.all;
      // 手动触发 TaskHubProvider 首次加载，避免 HomePage 中 hubState/hubNotifier 分支被短路。
      // ignore: unused_result
      harness.container.read(taskHubProvider);
      await tester.pumpAndSettle();
      expect(find.text('今天前'), findsWidgets);
    });
  });
}
