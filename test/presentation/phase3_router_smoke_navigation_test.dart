// 文件用途：Phase 3 路由烟囱测试（desktop + mobile），覆盖 GoRouter 多路由构建、桌面弹层分支与关键页面 build 路径。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';

import '../helpers/app_harness.dart';
import '../helpers/test_data_factory.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const secureStorageChannel = MethodChannel(
    'plugins.it_nomads.com/flutter_secure_storage',
  );

  setUpAll(() {
    // 说明：部分页面会读取加密设置；在测试环境下通过 mock channel 避免 MissingPluginException。
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secureStorageChannel, (call) async {
      // 简化策略：统一返回 null，触发 SecureStorageService 内存兜底/默认值路径。
      return null;
    });
  });

  tearDownAll(() {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(secureStorageChannel, null);
  });

  setUp(() {
    // 说明：VisibilityDetector 默认会启动定时器；在 widget test 中易产生“未完成 timer”噪音。
    VisibilityDetectorController.instance.updateInterval = Duration.zero;
  });

  group('Phase3 router smoke', () {
    testWidgets('桌面端可导航至关键路由并构建复杂页面', (tester) async {
      late final int itemId;
      late final int topicId;

      final harness = await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(1440, 900),
        seed: (container) async {
          itemId = await TestDataFactory.createLearningItemWithPlan(
            container,
            title: '路由烟囱条目',
            description: '用于覆盖 LearningItemDetail/TaskDetailSheet 的内容',
          );

          final topicUseCase = container.read(manageTopicUseCaseProvider);
          final topic = await topicUseCase.create(
            const TopicParams(name: '路由烟囱主题', description: '用于 Topics 路由覆盖'),
          );
          topicId = topic.id!;
          await topicUseCase.addItemToTopic(topicId, itemId);
        },
      );

      // 1) 任务中心
      await goToRoute(tester, harness, '/tasks');
      expect(find.text('任务中心'), findsOneWidget);

      // 2) 任务详情（edit=1：自动弹出编辑基本信息 Sheet）
      await goToRoute(tester, harness, '/tasks/detail/$itemId?edit=1');
      // 说明：数据存在时 Sheet 顶部标题为学习内容标题，而非固定“任务详情”。
      expect(find.text('路由烟囱条目'), findsWidgets);
      await tester.pumpAndSettle();
      if (find.text('编辑基本信息').evaluate().isNotEmpty) {
        expect(find.text('编辑基本信息'), findsOneWidget);
        // 关闭自动弹出的编辑 Sheet，避免影响后续路由。
        await tester.tap(find.text('取消').first);
        await tester.pumpAndSettle();
      }

      // 3) 录入（桌面端 dialog 路由分支）
      await goToRoute(tester, harness, '/input');
      expect(find.text('今天学了什么？'), findsOneWidget);

      await goToRoute(tester, harness, '/input/templates');
      expect(find.text('模板管理'), findsOneWidget);

      // 4) 学习内容详情
      await goToRoute(tester, harness, '/items/$itemId');
      expect(find.text('路由烟囱条目'), findsWidgets);

      // 5) 主题页（首次进入可能弹出“主题功能说明”引导）
      await goToRoute(tester, harness, '/topics');
      await tester.pumpAndSettle();
      if (find.text('主题功能说明').evaluate().isNotEmpty) {
        await tester.tap(find.text('我知道了'));
        await tester.pumpAndSettle();
      }
      expect(find.textContaining('主题'), findsWidgets);

      await goToRoute(tester, harness, '/topics/$topicId');
      expect(find.text('路由烟囱主题'), findsWidgets);

      // 6) Shell 内主 Tab
      await goToRoute(tester, harness, '/calendar');
      expect(harness.router.routeInformationProvider.value.uri.path, '/calendar');

      await goToRoute(tester, harness, '/statistics');
      expect(find.text('学习统计'), findsOneWidget);

      await goToRoute(tester, harness, '/settings');
      expect(harness.router.routeInformationProvider.value.uri.path, '/settings');

      await goToRoute(tester, harness, '/pomodoro');
      expect(harness.router.routeInformationProvider.value.uri.path, '/pomodoro');
    });

    testWidgets('移动端可覆盖 dialog fallback 分支', (tester) async {
      late final int itemId;
      final harness = await pumpYiKeApp(
        tester,
        initialLocation: '/home',
        size: const Size(390, 844),
        seed: (container) async {
          itemId = await TestDataFactory.createLearningItemWithPlan(
            container,
            title: '移动端路由条目',
          );
        },
      );

      // 覆盖：/input 在移动端为 fullscreenDialog fallback。
      await goToRoute(tester, harness, '/input');
      expect(find.text('今天学了什么？'), findsOneWidget);

      // 覆盖：任务详情移动端为 FractionallySizedBox 底部 Sheet fallback。
      await goToRoute(tester, harness, '/tasks/detail/$itemId');
      expect(find.text('移动端路由条目'), findsWidgets);
    });
  });
}
