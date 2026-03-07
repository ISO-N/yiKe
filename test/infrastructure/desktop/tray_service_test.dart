// 文件用途：TrayService 服务测试，覆盖初始化、状态切换、窗口控制、托盘菜单与资源释放。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:io';
import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:yike/infrastructure/desktop/tray_service.dart';

class _FakePathProviderPlatform extends PathProviderPlatform {
  @override
  Future<String?> getTemporaryPath() async => Directory.systemTemp.path;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const trayChannel = MethodChannel('tray_manager');
  const windowChannel = MethodChannel('window_manager');

  final trayCalls = <MethodCall>[];
  final windowCalls = <String>[];

  ByteData assetBytes() {
    final bytes = Uint8List.fromList(<int>[1, 2, 3, 4]);
    return ByteData.view(bytes.buffer);
  }

  setUp(() {
    trayCalls.clear();
    windowCalls.clear();
    PathProviderPlatform.instance = _FakePathProviderPlatform();

    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(trayChannel, (call) async {
      trayCalls.add(call);
      return null;
    });
    messenger.setMockMethodCallHandler(windowChannel, (call) async {
      windowCalls.add(call.method);
      if (call.method.startsWith('is')) {
        return false;
      }
      return null;
    });
    messenger.setMockMessageHandler('flutter/assets', (message) async {
      final key = utf8.decode(message!.buffer.asUint8List());
      if (key.contains('tray_icon_')) {
        return assetBytes();
      }
      return null;
    });
  });

  tearDown(() async {
    await TrayService.instance.dispose();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(trayChannel, null);
    messenger.setMockMethodCallHandler(windowChannel, null);
    messenger.setMockMessageHandler('flutter/assets', null);
  });

  group('TrayService', () {
    test('init 与 updateStatus 会更新图标、提示与上下文菜单', () async {
      await TrayService.instance.init();

      expect(trayCalls.map((call) => call.method), containsAll(<String>[
        'setIcon',
        'setToolTip',
        'setContextMenu',
      ]));

      await TrayService.instance.updateStatus(TrayStatus.syncing);
      final syncingMenuCall = trayCalls.lastWhere(
        (call) => call.method == 'setContextMenu',
      );
      final syncingMenu = syncingMenuCall.arguments as Map<dynamic, dynamic>;
      final syncingItems =
          (syncingMenu['menu']['items'] as List<dynamic>).cast<Map<dynamic, dynamic>>();
      expect(syncingItems.first['label'], '状态：同步中');

      await TrayService.instance.updateStatus(TrayStatus.offline);
      final offlineMenuCall = trayCalls.lastWhere(
        (call) => call.method == 'setContextMenu',
      );
      final offlineMenu = offlineMenuCall.arguments as Map<dynamic, dynamic>;
      final offlineItems =
          (offlineMenu['menu']['items'] as List<dynamic>).cast<Map<dynamic, dynamic>>();
      expect(offlineItems.first['label'], '状态：离线');
    });

    test('窗口控制与托盘菜单事件会调用 window_manager', () async {
      await TrayService.instance.init();

      await TrayService.instance.minimizeToTray();
      await TrayService.instance.showMainWindow();
      TrayService.instance.onTrayIconRightMouseDown();

      expect(windowCalls, containsAll(<String>[
        'setSkipTaskbar',
        'hide',
        'show',
        'focus',
      ]));
      expect(
        trayCalls.map((call) => call.method),
        contains('popUpContextMenu'),
      );
    });

    test('dispose 会销毁托盘资源', () async {
      await TrayService.instance.init();
      await TrayService.instance.dispose();

      expect(
        trayCalls.map((call) => call.method),
        contains('destroy'),
      );
    });
  });
}
