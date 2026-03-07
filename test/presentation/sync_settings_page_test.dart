// 文件用途：SyncSettingsPage Widget 测试，覆盖同步状态展示、开关交互、配对弹窗与设备操作。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/sync/discovery_service.dart';
import 'package:yike/presentation/pages/settings/sync_settings_page.dart';
import 'package:yike/presentation/providers/sync_provider.dart';

class _FakeSyncController extends SyncController {
  _FakeSyncController(super.ref, SyncUiState initialState) : super() {
    state = initialState;
  }

  int refreshCount = 0;
  int syncCount = 0;
  String? requestedDeviceId;
  String? confirmedPairingCode;
  final List<String> disconnectedDeviceIds = <String>[];

  @override
  void refreshDiscovery() {
    refreshCount++;
  }

  @override
  Future<void> setIsMaster(bool value) async {
    state = state.copyWith(isMaster: value);
  }

  @override
  Future<void> setAutoSyncEnabled(bool enabled) async {
    state = state.copyWith(autoSyncEnabled: enabled);
  }

  @override
  Future<void> setIncludeMockData(bool enabled) async {
    state = state.copyWith(includeMockData: enabled);
  }

  @override
  Future<void> setWifiOnly(bool value) async {
    state = state.copyWith(wifiOnly: value);
  }

  @override
  Future<void> setAllowCellular(bool value) async {
    state = state.copyWith(allowCellular: value);
  }

  @override
  Future<OutgoingPairing> requestPairing(DiscoveredDevice master) async {
    requestedDeviceId = master.deviceId;
    final outgoing = OutgoingPairing(
      masterDeviceId: master.deviceId,
      masterDeviceName: master.deviceName,
      masterIp: master.ipAddress,
      sessionId: 'session-1',
      expiresAtMs: DateTime.now()
          .add(const Duration(minutes: 5))
          .millisecondsSinceEpoch,
    );
    state = state.copyWith(outgoingPairing: outgoing);
    return outgoing;
  }

  @override
  Future<void> confirmPairing({required String pairingCode}) async {
    confirmedPairingCode = pairingCode;
    state = state.copyWith(
      outgoingPairing: null,
      state: SyncState.connected,
      errorMessage: null,
    );
  }

  @override
  Future<void> syncNow() async {
    syncCount++;
    state = state.copyWith(state: SyncState.synced, errorMessage: null);
  }

  @override
  Future<void> disconnectDevice(String deviceId) async {
    disconnectedDeviceIds.add(deviceId);
    state = state.copyWith(
      connectedDevices: state.connectedDevices
          .where((device) => device.deviceId != deviceId)
          .toList(),
    );
  }
}

void main() {
  Future<_FakeSyncController> pumpPage(
    WidgetTester tester, {
    required SyncUiState initialState,
  }) async {
    late _FakeSyncController controller;
    tester.view.physicalSize = const Size(1440, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          syncControllerProvider.overrideWith((ref) {
            controller = _FakeSyncController(ref, initialState);
            return controller;
          }),
        ],
        child: const MaterialApp(home: SyncSettingsPage()),
      ),
    );
    await tester.pumpAndSettle();
    return controller;
  }

  group('SyncSettingsPage', () {
    testWidgets('客户端视角支持刷新、切换开关、发起配对与手动同步', (tester) async {
      final controller = await pumpPage(
        tester,
        initialState: SyncUiState(
          state: SyncState.connected,
          isMaster: false,
          autoSyncEnabled: true,
          includeMockData: false,
          wifiOnly: false,
          allowCellular: true,
          discoveredDevices: <DiscoveredDevice>[
            DiscoveredDevice(
              deviceId: 'master-1',
              deviceName: '主机设备',
              deviceType: 'windows',
              ipAddress: '192.168.1.10',
              isMaster: true,
              lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
            ),
          ],
          connectedDevices: const <ConnectedDevice>[
            ConnectedDevice(
              deviceId: 'master-1',
              deviceName: '主机设备',
              deviceType: 'windows',
              ipAddress: '192.168.1.10',
              isMaster: true,
              lastSyncMs: 1700000000000,
              isOnline: true,
              lastSeenAtMs: 1700000000000,
            ),
          ],
          pendingPairings: const <PendingPairing>[],
          outgoingPairing: null,
        ),
      );

      expect(find.text('同步设置'), findsOneWidget);
      expect(find.textContaining('已配对'), findsWidgets);
      expect(find.text('主机设备'), findsWidgets);
      expect(find.text('附近设备'), findsOneWidget);
      expect(find.text('已配对的设备'), findsOneWidget);

      await tester.tap(find.byTooltip('刷新发现'));
      await tester.pumpAndSettle();
      expect(controller.refreshCount, 1);

      await tester.tap(find.text('将本机设为主机'));
      await tester.pumpAndSettle();
      expect(controller.state.isMaster, isTrue);

      await tester.tap(find.text('允许移动网络同步'));
      await tester.pumpAndSettle();
      expect(controller.state.allowCellular, isFalse);

      await tester.tap(find.text('开启自动同步'));
      await tester.pumpAndSettle();
      expect(controller.state.autoSyncEnabled, isFalse);

      await tester.tap(find.text('将本机设为主机'));
      await tester.pumpAndSettle();
      expect(controller.state.isMaster, isFalse);

      await tester.tap(find.text('配对'));
      await tester.pumpAndSettle();
      expect(controller.requestedDeviceId, 'master-1');
      expect(find.text('输入主机配对码'), findsOneWidget);

      await tester.enterText(find.byType(TextField), '123456');
      await tester.tap(find.text('确认'));
      await tester.pumpAndSettle();
      expect(controller.confirmedPairingCode, '123456');

      await tester.tap(find.text('立即同步'));
      await tester.pumpAndSettle();
      expect(controller.syncCount, 1);
      expect(find.textContaining('同步完成'), findsWidgets);

      await tester.tap(find.text('断开'));
      await tester.pumpAndSettle();
      expect(controller.disconnectedDeviceIds, <String>['master-1']);
      expect(find.text('暂无已配对设备'), findsOneWidget);
    });

    testWidgets('主机视角会展示待配对请求、离线状态与错误信息', (tester) async {
      final nowMs = DateTime.now().millisecondsSinceEpoch;

      await pumpPage(
        tester,
        initialState: SyncUiState(
          state: SyncState.error,
          isMaster: true,
          autoSyncEnabled: false,
          includeMockData: true,
          wifiOnly: true,
          allowCellular: false,
          discoveredDevices: <DiscoveredDevice>[
            DiscoveredDevice(
              deviceId: 'client-1',
              deviceName: '客户端设备',
              deviceType: 'android',
              ipAddress: '192.168.1.20',
              isMaster: false,
              lastSeenAtMs: nowMs,
            ),
          ],
          connectedDevices: const <ConnectedDevice>[
            ConnectedDevice(
              deviceId: 'client-1',
              deviceName: '客户端设备',
              deviceType: 'android',
              ipAddress: '192.168.1.20',
              isMaster: false,
              lastSyncMs: null,
              isOnline: false,
              lastSeenAtMs: null,
            ),
          ],
          pendingPairings: <PendingPairing>[
            PendingPairing(
              sessionId: 'session-1',
              clientDeviceId: 'client-1',
              clientDeviceName: '客户端设备',
              clientDeviceType: 'android',
              clientIp: '192.168.1.20',
              pairingCode: '654321',
              expiresAtMs: nowMs + const Duration(minutes: 4).inMilliseconds,
            ),
          ],
          outgoingPairing: null,
          errorMessage: '同步失败：测试错误',
        ),
      );

      expect(find.text('待配对请求'), findsOneWidget);
      expect(find.text('654321'), findsOneWidget);
      expect(find.textContaining('同步失败：测试错误'), findsOneWidget);
      expect(find.text('客户端设备'), findsWidgets);
      expect(find.textContaining('离线'), findsOneWidget);
      expect(find.text('同步模拟数据（仅调试）'), findsOneWidget);
      expect(find.text('配对'), findsNothing);
    });
  });
}
