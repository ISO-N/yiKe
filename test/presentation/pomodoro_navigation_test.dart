// 文件用途：专注页导航冒烟测试（底部 Tab 与页面渲染）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';

import '../helpers/app_harness.dart';

void main() {
  testWidgets('点击底部导航“专注”后可进入番茄钟页面', (tester) async {
    await pumpYiKeApp(tester);

    expect(find.text('专注'), findsOneWidget);
    await tester.tap(find.text('专注'));
    await tester.pumpAndSettle();

    expect(find.text('专注计时'), findsOneWidget);
    expect(find.text('专注统计'), findsOneWidget);
  });
}
