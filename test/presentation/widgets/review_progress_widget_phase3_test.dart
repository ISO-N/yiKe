// 文件用途：ReviewProgressWidget Phase3 Widget 测试，覆盖 loading/error/data 与展开统计分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/today_progress_provider.dart';
import 'package:yike/presentation/widgets/review_progress.dart';

/// 测试用统计 Notifier：避免真实 UseCase 依赖，只提供可控的状态。
class _FakeStatisticsNotifier extends StateNotifier<StatisticsState>
    implements StatisticsNotifier {
  _FakeStatisticsNotifier(super.state);

  @override
  Future<void> load() async {}

  void setState(StatisticsState next) => state = next;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpWidget(
    WidgetTester tester,
    ProviderContainer container,
  ) async {
    tester.view.physicalSize = const Size(1200, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(
          home: Scaffold(body: ReviewProgressWidget()),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
  }

  group('ReviewProgressWidget phase3', () {
    testWidgets('loading 状态会展示加载指示器', (tester) async {
      final completer = Completer<(int, int)>();
      final fakeStats = _FakeStatisticsNotifier(StatisticsState.initial());

      final container = ProviderContainer(
        overrides: <Override>[
          todayProgressProvider.overrideWith((ref) async {
            return completer.future;
          }),
          statisticsProvider.overrideWith((ref) => fakeStats),
        ],
      );
      addTearDown(container.dispose);

      await pumpWidget(tester, container);

      // Future 未完成时走 loading 分支：展示加载指示器。
      expect(find.byType(CircularProgressIndicator), findsOneWidget);

      completer.complete((0, 0));
      await tester.pumpAndSettle();
      expect(find.text('未开始'), findsOneWidget);
    });

    testWidgets('loading -> data 会渲染进度并支持展开详情', (tester) async {
      final progressStateProvider = StateProvider<(int, int)>(
        (ref) => (0, 0),
      );
      final fakeStats = _FakeStatisticsNotifier(StatisticsState.initial());

      final container = ProviderContainer(
        overrides: <Override>[
          todayProgressProvider.overrideWith(
            (ref) async => ref.watch(progressStateProvider),
          ),
          statisticsProvider.overrideWith((ref) => fakeStats),
        ],
      );
      addTearDown(container.dispose);

      await pumpWidget(tester, container);

      expect(find.text('今日复习进度'), findsOneWidget);

      // 切换为有数据：触发 didUpdateWidget 动画/状态路径。
      container.read(progressStateProvider.notifier).state = (1, 3);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.text('1 / 3'), findsOneWidget);
      expect(find.text('进行中'), findsOneWidget);

      // 展开详情：首次 stats 仍为 loading，会展示“加载中…”。
      await tester.tap(find.byType(ReviewProgressWidget));
      await tester.pumpAndSettle();
      expect(find.text('本周'), findsOneWidget);
      expect(find.text('加载中…'), findsWidgets);

      // 更新统计状态，覆盖 ratioText 与 streak 分支。
      fakeStats.setState(
        fakeStats.state.copyWith(
          isLoading: false,
          weekCompleted: 2,
          weekTotal: 5,
          monthCompleted: 10,
          monthTotal: 12,
          consecutiveCompletedDays: 7,
        ),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 50));
      expect(find.text('2/5 (40%)'), findsOneWidget);
      expect(find.text('10/12 (83%)'), findsOneWidget);
      expect(find.text('7 天'), findsOneWidget);

      // 覆盖“已完成/超额完成”状态文本路径。
      container.read(progressStateProvider.notifier).state = (3, 3);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.text('已完成'), findsOneWidget);

      container.read(progressStateProvider.notifier).state = (4, 3);
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));
      expect(find.text('已完成'), findsOneWidget);
    });

    testWidgets('error 状态会展示错误提示', (tester) async {
      final fakeStats = _FakeStatisticsNotifier(StatisticsState.initial());
      final container = ProviderContainer(
        overrides: <Override>[
          todayProgressProvider.overrideWith(
            (ref) async => throw StateError('模拟错误'),
          ),
          statisticsProvider.overrideWith((ref) => fakeStats),
        ],
      );
      addTearDown(container.dispose);

      await pumpWidget(tester, container);
      await tester.pumpAndSettle();

      expect(find.textContaining('加载失败'), findsOneWidget);
      expect(find.textContaining('模拟错误'), findsOneWidget);
    });
  });
}
