// 文件用途：SkeletonLoader / SkeletonShimmer Phase 3 Widget 测试，覆盖 auto/on/off 策略与延迟显示逻辑，提升骨架屏组件覆盖率并验证计时器释放。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/presentation/widgets/skeleton_loader.dart';

class _SkeletonHost extends StatefulWidget {
  const _SkeletonHost();

  @override
  State<_SkeletonHost> createState() => _SkeletonHostState();
}

class _SkeletonHostState extends State<_SkeletonHost> {
  bool isLoading = true;
  String strategy = 'auto';

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Column(
          children: [
            // 控制按钮始终存在，避免 skeleton 覆盖 child 后无法切换状态。
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                FilledButton(
                  onPressed: () => setState(() => isLoading = !isLoading),
                  child: const Text('toggle-loading'),
                ),
                FilledButton(
                  onPressed: () => setState(() => strategy = 'on'),
                  child: const Text('set-on'),
                ),
                FilledButton(
                  onPressed: () => setState(() => strategy = 'off'),
                  child: const Text('set-off'),
                ),
                FilledButton(
                  onPressed: () => setState(() => strategy = 'auto'),
                  child: const Text('set-auto'),
                ),
              ],
            ),
            const SizedBox(height: 12),
            Expanded(
              child: Center(
                child: SkeletonLoader(
                  isLoading: isLoading,
                  strategy: strategy,
                  delay: const Duration(milliseconds: 200),
                  skeleton: const Text('skeleton'),
                  child: const Text('child'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('SkeletonLoader Phase3', () {
    testWidgets('strategy=off 时始终展示 child', (tester) async {
      await tester.pumpWidget(const _SkeletonHost());
      expect(find.text('child'), findsOneWidget);

      await tester.tap(find.text('set-off'));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(find.text('skeleton'), findsNothing);

      // 即使 isLoading=true，off 仍不展示 skeleton。
      await tester.tap(find.text('toggle-loading'));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(find.text('skeleton'), findsNothing);
    });

    testWidgets('strategy=on 时加载中立即展示 skeleton', (tester) async {
      await tester.pumpWidget(const _SkeletonHost());

      await tester.tap(find.text('set-on'));
      await tester.pump();

      expect(find.text('skeleton'), findsOneWidget);
      expect(find.text('child'), findsNothing);

      // 结束加载后回到 child。
      await tester.tap(find.text('toggle-loading'));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(find.text('skeleton'), findsNothing);
    });

    testWidgets('strategy=auto 会延迟到阈值后展示 skeleton', (tester) async {
      await tester.pumpWidget(const _SkeletonHost());

      // 初始 strategy=auto 且 isLoading=true：<200ms 不展示 skeleton。
      expect(find.text('child'), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 199));
      expect(find.text('skeleton'), findsNothing);

      // 超过阈值后展示 skeleton。
      await tester.pump(const Duration(milliseconds: 2));
      expect(find.text('skeleton'), findsOneWidget);

      // 切换为非 loading 应取消计时器并显示 child。
      await tester.tap(find.text('toggle-loading'));
      await tester.pump();
      expect(find.text('child'), findsOneWidget);
      expect(find.text('skeleton'), findsNothing);
    });
  });

  group('SkeletonShimmer Phase3', () {
    testWidgets('会构建 ShaderMask 并保持 child', (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: SkeletonShimmer(child: Text('shimmer-child')),
          ),
        ),
      );

      expect(find.byType(ShaderMask), findsOneWidget);
      expect(find.text('shimmer-child'), findsOneWidget);
    });
  });
}
