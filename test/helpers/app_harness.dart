// 文件用途：应用级测试 harness，复用生产路由、真实 Provider 装配与内存数据库启动 YiKeApp。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:yike/app.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/infrastructure/router/app_router.dart';

import 'test_database.dart';

typedef TestDatabaseSeeder = Future<void> Function(ProviderContainer container);

class AppHarness {
  AppHarness({required this.container, required this.router});

  final ProviderContainer container;
  final GoRouter router;
}

Future<AppHarness> pumpYiKeApp(
  WidgetTester tester, {
  String initialLocation = '/home',
  Size size = const Size(390, 844),
  Map<String, Object> sharedPreferencesValues = const {},
  List<Override> overrides = const [],
  TestDatabaseSeeder? seed,
}) async {
  SharedPreferences.setMockInitialValues(sharedPreferencesValues);
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  final db = createInMemoryDatabase();
  addTearDown(() async => db.close());

  final router = createAppRouter(initialLocation: initialLocation);
  final container = ProviderContainer(
    overrides: [
      appDatabaseProvider.overrideWithValue(db),
      appRouterProvider.overrideWithValue(router),
      ...overrides,
    ],
  );
  addTearDown(container.dispose);

  if (seed != null) {
    await seed(container);
  }

  await tester.pumpWidget(
    UncontrolledProviderScope(container: container, child: const YiKeApp()),
  );
  await tester.pumpAndSettle();

  return AppHarness(container: container, router: router);
}

Future<void> goToRoute(
  WidgetTester tester,
  AppHarness harness,
  String location,
) async {
  harness.router.go(location);
  await tester.pumpAndSettle();
}
