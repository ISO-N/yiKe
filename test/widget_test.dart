// 文件用途：基础 Widget 冒烟测试，确保 App 能成功渲染并进入首页路由。
// 作者：Codex
// 创建日期：2026-02-25

import 'package:flutter_test/flutter_test.dart';

import 'helpers/app_harness.dart';

void main() {
  testWidgets('App 可以渲染并显示首页标题', (WidgetTester tester) async {
    await pumpYiKeApp(tester);

    // “今日复习”在首页标题与底部导航中都会出现，因此断言至少出现一次即可。
    expect(find.text('今日复习'), findsAtLeastNWidgets(1));
  });
}
