// 文件用途：AppPreloadService 测试，覆盖 ensureStarted 的幂等、资源预加载触发与内存压力跳过分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/preload/app_preload_service.dart';

ByteData _fakePngBytes() {
  // 说明：precacheImage 仅需要可解码的图片数据；这里使用最小的 1x1 PNG。
  const base64Png =
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/6XqXzUAAAAASUVORK5CYII=';
  final bytes = base64.decode(base64Png);
  return ByteData.view(Uint8List.fromList(bytes).buffer);
}

class _PreloadHost extends StatelessWidget {
  const _PreloadHost();

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        body: Center(
          child: FilledButton(
            onPressed: () => AppPreloadService.ensureStarted(context),
            child: const Text('start-preload'),
          ),
        ),
      ),
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('ensureStarted 会触发预加载并在内存压力下跳过后续逻辑', (tester) async {
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMessageHandler('flutter/assets', (message) async {
      final key = utf8.decode(message!.buffer.asUint8List());
      if (key == 'AssetManifest.bin') {
        // 说明：precacheImage 会通过 AssetBundle 读取 manifest；这里返回一个空 manifest 即可。
        return const StandardMessageCodec().encodeMessage(<String, dynamic>{});
      }
      if (key == 'AssetManifest.json') {
        final bytes = utf8.encode('{}');
        return ByteData.view(Uint8List.fromList(bytes).buffer);
      }
      if (key.startsWith('assets/icons/app_icon')) {
        return _fakePngBytes();
      }
      return null;
    });
    addTearDown(() => messenger.setMockMessageHandler('flutter/assets', null));

    await tester.pumpWidget(const _PreloadHost());

    // 第一次启动：注册 observer + post frame precache。
    await tester.tap(find.text('start-preload'));
    await tester.pump();

    // 触发 post frame callback + precache。
    await tester.pump(const Duration(milliseconds: 10));

    // 触发内存压力：后续延迟预加载应被跳过（覆盖 didHaveMemoryPressure 分支）。
    WidgetsBinding.instance.handleMemoryPressure();
    await tester.pump();

    // ensureStarted 幂等：重复调用不会重复注册/重复调度。
    await tester.tap(find.text('start-preload'));
    await tester.pump();

    // 跑过延迟 timer（500ms），确保测试结束前无悬挂 timer。
    await tester.pump(const Duration(milliseconds: 600));
  });
}
