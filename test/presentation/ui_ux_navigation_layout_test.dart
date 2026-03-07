// 文件用途：UI/UX 改造后的主路径导航与断点布局回归测试。
// 文件用途：UI/UX 改造后的主路径导航与断点布局回归测试。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/widgets/semantic_panels.dart';

import '../helpers/app_harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpApp(WidgetTester tester, {required Size size}) async {
    await pumpYiKeApp(tester, size: size);
  }

  testWidgets('主路径导航与统计次级入口可访问', (tester) async {
    await pumpApp(tester, size: const Size(390, 844));

    expect(find.text('今日'), findsOneWidget);
    expect(find.text('计划'), findsOneWidget);
    expect(find.text('专注'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('复习计划'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsOneWidget);

    await tester.tap(find.text('专注'));
    await tester.pumpAndSettle();
    expect(find.text('专注计时'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsOneWidget);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.text('管理提醒、外观和学习偏好'), findsOneWidget);
    expect(find.byType(FloatingActionButton), findsNothing);

    await tester.ensureVisible(find.text('查看统计'));
    await tester.tap(find.text('查看统计'));
    await tester.pumpAndSettle();
    expect(find.text('学习统计'), findsOneWidget);
    expect(find.text('统计'), findsAtLeastNWidgets(1));
  });

  testWidgets('375 宽度下保持移动端单轴布局', (tester) async {
    await pumpApp(tester, size: const Size(375, 812));

    expect(find.text('今日复习总览'), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('当天安排'), findsNothing);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('768 宽度下保持非桌面布局', (tester) async {
    await pumpApp(tester, size: const Size(768, 1024));

    expect(find.text('今日复习总览'), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('当天安排'), findsNothing);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('1024 宽度下仍遵循当前桌面断点策略', (tester) async {
    await pumpApp(tester, size: const Size(1024, 900));

    expect(find.text('今日复习总览'), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('当天安排'), findsNothing);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsNothing);
  });

  testWidgets('1200 宽度下切换为桌面骨架布局', (tester) async {
    await pumpApp(tester, size: const Size(1200, 900));

    expect(find.text('今日复习总览'), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('当天安排'), findsOneWidget);
    expect(find.text('选择一天开始查看计划详情'), findsOneWidget);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.byType(NavigationRail), findsOneWidget);
  });

  testWidgets('核心页面使用统一语义区块与 CTA 编排', (tester) async {
    await pumpApp(tester, size: const Size(1200, 900));

    expect(find.byType(HeroCard), findsOneWidget);
    expect(find.byType(SummaryStrip), findsAtLeastNWidgets(1));
    expect(find.text('开始复习'), findsOneWidget);
    expect(find.text('查看全部任务'), findsOneWidget);

    await tester.tap(find.text('计划'));
    await tester.pumpAndSettle();
    expect(find.text('复习计划'), findsOneWidget);
    expect(find.text('当天安排'), findsOneWidget);

    await tester.tap(find.text('我的'));
    await tester.pumpAndSettle();
    expect(find.byType(HeroCard), findsOneWidget);
    expect(find.text('查看统计'), findsOneWidget);
    expect(find.text('学习统计'), findsAtLeastNWidgets(1));
    expect(find.text('同步设置'), findsAtLeastNWidgets(1));

    await tester.tap(find.text('查看统计'));
    await tester.pumpAndSettle();
    expect(find.byType(HeroCard), findsOneWidget);
    expect(find.text('学习统计'), findsOneWidget);
  });
}
