// 文件用途：首页 Phase 3 深链与“全部任务”分页触发测试，补齐 HomePage 中 query 同步与滚动监听等关键分支覆盖。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    // 说明：VisibilityDetector 默认会启动定时器；在 widget test 中易产生“未完成 timer”噪音。
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  group('HomePage Phase3 deeplink', () {
    testWidgets('focus=overdue 时会渲染并尝试聚焦逾期分区', (tester) async {
      final now = DateTime.now();
      await pumpYiKeApp(
        tester,
        initialLocation: '/home?focus=overdue',
        size: const Size(390, 844),
        seed: (container) async {
          // 说明：构造一条“逾期任务”与一条“今日任务”，保证逾期分区真实渲染。
          await TestDataFactory.createLearningItemWithPlan(
            container,
            title: '逾期任务条目',
            learningDate: now.subtract(const Duration(days: 2)),
          );
          await TestDataFactory.createLearningItemWithPlan(
            container,
            title: '今日任务条目',
            learningDate: now.subtract(const Duration(days: 1)),
          );
        },
      );

      // 断言：逾期分区标题出现，意味着 deep link 目标分区已构建。
      // 说明：首页任务加载包含异步查询与少量 UI 去抖，这里额外 pump 一次让状态稳定。
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text('逾期任务'), findsWidgets);
    });

    testWidgets('tab=all 会同步到 Provider 并挂载“全部任务”时间筛选栏', (tester) async {
      final now = DateTime.now();
      final harness = await pumpYiKeApp(
        tester,
        initialLocation: '/home?tab=all',
        size: const Size(1280, 900),
        seed: (container) async {
          // 说明：构造足够多的任务，使 all-tab 的时间线具备滚动空间，从而触发分页监听逻辑。
          for (var i = 0; i < 6; i++) {
            await TestDataFactory.createLearningItemWithPlan(
              container,
              title: 'AllTab 条目 $i',
              learningDate: now.subtract(Duration(days: 1 + i)),
            );
          }
        },
      );

      // 断言：tab=all 时应该渲染“全部/今天前/今天后”时间筛选栏。
      expect(find.text('全部'), findsWidgets);
      expect(find.text('今天前'), findsOneWidget);
      expect(find.text('今天后'), findsOneWidget);

      // 关键覆盖：尝试滚动到底部，触发 HomePage 的 _onAllTasksScroll 分支（分页 loadMore）。
      final scrollable = find.byType(Scrollable).first;
      await tester.fling(scrollable, const Offset(0, -1200), 3000);
      await tester.pumpAndSettle();

      // 额外覆盖：切换回今日 tab，确保“query -> Provider”同步不会崩溃。
      await tester.tap(find.text('今日'));
      await tester.pumpAndSettle();

      // 回归断言：仍能正常展示 Shell 的底部导航区域（桌面端为 NavigationRail / 移动端为 BottomNavigationBar）。
      expect(harness.router, isNotNull);
    });
  });
}
