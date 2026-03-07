// 文件用途：HelpPage Phase 3 补充测试，覆盖资产加载失败分支、目录锚点滚动与非 Windows 语义输出分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/material.dart';
import 'package:yike/presentation/pages/help/help_page.dart';

const _sampleMarkdown = '''
# 忆刻学习指南

## 原理一：艾宾浩斯遗忘曲线
一些说明文字

### 小节：间隔复习
更多说明

## 原理二：主动回忆
内容
''';

ByteData _utf8Bytes(String value) {
  return ByteData.view(Uint8List.fromList(utf8.encode(value)).buffer);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const assetChannel = 'flutter/assets';

  Future<void> pumpHelp(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1200, 1800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(const MaterialApp(home: HelpPage()));
    await tester.pump();
  }

  testWidgets('目录点击可触发锚点滚动（非 Windows 分支）', (tester) async {
    debugDefaultTargetPlatformOverride = TargetPlatform.android;
    try {
      // 说明：rootBundle 为 CachingAssetBundle，会跨测试缓存 asset；主动清缓存避免影响后续“错误态”测试。
      rootBundle.evict('assets/markdown/learning_guide.md');
      rootBundle.evict('AssetManifest.bin');
      rootBundle.evict('AssetManifest.json');

      final messenger =
          TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMessageHandler(assetChannel, (message) async {
        final key = utf8.decode(message!.buffer.asUint8List());
        if (key == 'AssetManifest.bin') {
          return const StandardMessageCodec().encodeMessage(<String, dynamic>{});
        }
        if (key == 'AssetManifest.json') {
          return _utf8Bytes('{}');
        }
        if (key == 'assets/markdown/learning_guide.md') {
          return _utf8Bytes(_sampleMarkdown);
        }
        return null;
      });
      addTearDown(() => messenger.setMockMessageHandler(assetChannel, null));

      await pumpHelp(tester);
      await tester.pumpAndSettle();

      // 目录卡片应展示二级/三级标题。
      expect(find.text('目录'), findsOneWidget);
      // ExpansionTile 默认折叠，需要先展开才能看到条目。
      await tester.tap(find.text('目录'));
      await tester.pumpAndSettle();
      expect(find.text('原理一：艾宾浩斯遗忘曲线'), findsWidgets);
      expect(find.text('小节：间隔复习'), findsWidgets);

      // 点击目录项：触发 ensureVisible 滚动（覆盖 _scrollToAnchor 分支）。
      await tester.tap(find.text('原理二：主动回忆'));
      await tester.pumpAndSettle();
      expect(find.text('原理二：主动回忆'), findsWidgets);
    } finally {
      // Flutter test invariants 要求用例结束前复位该全局变量。
      debugDefaultTargetPlatformOverride = null;
    }
  });

  testWidgets('资产加载失败时会展示错误提示', (tester) async {
    // 清理缓存，确保 loadString 会命中 mock handler 并触发错误分支。
    rootBundle.evict('assets/markdown/learning_guide.md');
    rootBundle.evict('AssetManifest.bin');
    rootBundle.evict('AssetManifest.json');

    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMessageHandler(assetChannel, (message) async {
      final key = utf8.decode(message!.buffer.asUint8List());
      if (key == 'AssetManifest.bin') {
        return const StandardMessageCodec().encodeMessage(<String, dynamic>{});
      }
      if (key == 'AssetManifest.json') {
        return _utf8Bytes('{}');
      }
      if (key == 'assets/markdown/learning_guide.md') {
        // 说明：显式抛异常，覆盖 HelpPage 的 snapshot.hasError 分支。
        throw PlatformException(code: '404', message: 'asset missing');
      }
      return null;
    });
    addTearDown(() => messenger.setMockMessageHandler(assetChannel, null));

    await pumpHelp(tester);
    // 说明：FutureBuilder 错误态依赖异步 loadString 抛异常，这里用短轮询等待文本出现。
    for (var i = 0; i < 30; i++) {
      await tester.pump(const Duration(milliseconds: 50));
      if (find.textContaining('学习指南加载失败').evaluate().isNotEmpty) break;
    }

    expect(find.textContaining('学习指南加载失败'), findsOneWidget);
  });
}
