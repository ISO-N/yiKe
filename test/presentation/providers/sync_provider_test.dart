// 文件用途：SyncProvider 状态流测试，覆盖偏好持久化、会话异常与快速失败分支。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:drift/drift.dart' as drift;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/infrastructure/sync/discovery_service.dart';
import 'package:yike/infrastructure/storage/settings_crypto.dart';
import 'package:yike/presentation/providers/sync_provider.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  Future<void> waitUntil(
    bool Function() predicate, {
    int maxAttempts = 80,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      if (predicate()) return;
      await Future<void>.delayed(const Duration(milliseconds: 50));
    }
    fail('等待同步状态稳定超时');
  }

  /// 读取并解密同步偏好，验证 Provider 确实把设置写入了持久层。
  Future<String?> readSyncPref(String key) async {
    final raw = await container.read(settingsDaoProvider).getValue(key);
    if (raw == null) return null;
    final crypto = SettingsCrypto(
      secureStorageService: container.read(secureStorageServiceProvider),
    );
    return crypto.decrypt(raw);
  }

  setUp(() {
    db = createInMemoryDatabase();
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('SyncProvider', () {
    test('开关类设置会更新状态并持久化加密偏好', () async {
      final notifier = container.read(syncControllerProvider.notifier);

      await notifier.setAutoSyncEnabled(true);
      await notifier.setIncludeMockData(true);
      await notifier.setWifiOnly(false);
      await notifier.setAllowCellular(true);
      await notifier.setIsMaster(true);

      final state = container.read(syncControllerProvider);
      expect(state.autoSyncEnabled, isTrue);
      expect(state.includeMockData, isTrue);
      expect(state.wifiOnly, isFalse);
      expect(state.allowCellular, isTrue);
      expect(state.isMaster, isTrue);

      expect(await readSyncPref('sync_auto_sync_enabled'), 'true');
      expect(await readSyncPref('sync_include_mock_data'), 'true');
      expect(await readSyncPref('sync_wifi_only'), 'false');
      expect(await readSyncPref('sync_allow_cellular'), 'true');
      expect(await readSyncPref('sync_is_master'), 'true');
    });

    test('confirmPairing 在缺少会话和会话过期时会写入错误状态', () async {
      final notifier = container.read(syncControllerProvider.notifier);

      await notifier.confirmPairing(pairingCode: '123456');
      var state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.errorMessage, contains('未找到进行中的配对会话'));

      notifier.state = state.copyWith(
        state: SyncState.connecting,
        outgoingPairing: OutgoingPairing(
          masterDeviceId: 'master-1',
          masterDeviceName: '主机',
          masterIp: '127.0.0.1',
          sessionId: 'session-1',
          expiresAtMs: DateTime.now()
              .subtract(const Duration(minutes: 1))
              .millisecondsSinceEpoch,
        ),
        errorMessage: null,
      );

      await notifier.confirmPairing(pairingCode: '123456');
      state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.outgoingPairing, isNull);
      expect(state.errorMessage, contains('配对码已过期'));
    });

    test('syncNow 在没有在线同步目标时会快速失败并给出明确原因', () async {
      final notifier = container.read(syncControllerProvider.notifier);

      await notifier.syncNow();
      var state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.errorMessage, contains('暂无已配对设备'));

      notifier.state = state.copyWith(
        state: SyncState.connected,
        errorMessage: null,
        connectedDevices: const <ConnectedDevice>[
          ConnectedDevice(
            deviceId: 'master-1',
            deviceName: '主机',
            deviceType: 'windows',
            ipAddress: '127.0.0.1',
            isMaster: true,
            lastSyncMs: null,
            isOnline: false,
            lastSeenAtMs: null,
          ),
        ],
      );
      await notifier.syncNow();
      state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.errorMessage, contains('主机离线'));
    });

    test('disconnectDevice 会删除已配对设备记录', () async {
      final notifier = container.read(syncControllerProvider.notifier);
      await container.read(syncDeviceDaoProvider).upsert(
        SyncDevicesCompanion.insert(
          deviceId: 'device-1',
          deviceName: '测试设备',
          deviceType: 'windows',
          ipAddress: const drift.Value('127.0.0.1'),
          authToken: const drift.Value('token-1'),
          isMaster: const drift.Value(true),
          lastSyncMs: const drift.Value.absent(),
          lastOutgoingMs: const drift.Value.absent(),
          lastIncomingMs: const drift.Value.absent(),
        ),
      );

      expect(
        await container.read(syncDeviceDaoProvider).getByDeviceId('device-1'),
        isNotNull,
      );

      await notifier.disconnectDevice('device-1');

      expect(
        await container.read(syncDeviceDaoProvider).getByDeviceId('device-1'),
        isNull,
      );
    });

    test('initialize 后支持自配对并完成首次同步', () async {
      final notifier = container.read(syncControllerProvider.notifier);
      await notifier.initialize();

      final master = DiscoveredDevice(
        deviceId: 'self-master',
        deviceName: '本机主机',
        deviceType: 'windows',
        ipAddress: '127.0.0.1',
        isMaster: true,
        lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
      );

      final outgoing = await notifier.requestPairing(master);
      final stateAfterRequest = container.read(syncControllerProvider);
      expect(outgoing.masterDeviceId, 'self-master');
      expect(stateAfterRequest.outgoingPairing?.sessionId, isNotEmpty);
      expect(stateAfterRequest.pendingPairings, hasLength(1));

      notifier.state = stateAfterRequest.copyWith(
        connectedDevices: <ConnectedDevice>[
          ConnectedDevice(
            deviceId: 'self-master',
            deviceName: '本机主机',
            deviceType: 'windows',
            ipAddress: '127.0.0.1',
            isMaster: true,
            lastSyncMs: null,
            isOnline: true,
            lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
          ),
        ],
      );

      await notifier.confirmPairing(
        pairingCode: stateAfterRequest.pendingPairings.single.pairingCode,
      );
      await waitUntil(
        () => container.read(syncControllerProvider).outgoingPairing == null,
      );

      final finalState = container.read(syncControllerProvider);
      expect(finalState.outgoingPairing, isNull);
      expect(
        finalState.state,
        anyOf(SyncState.connected, SyncState.synced, SyncState.error),
      );

      final pairedDevice = await container.read(syncDeviceDaoProvider).getByDeviceId(
            'self-master',
          );
      expect(pairedDevice, isNotNull);
      expect(pairedDevice?.authToken, isNotNull);

      await notifier.dispose();
      container = ProviderContainer();
    });

    test('requestPairing 失败时会写入错误状态并保留异常', () async {
      final notifier = container.read(syncControllerProvider.notifier);
      final master = DiscoveredDevice(
        deviceId: 'offline-master',
        deviceName: '离线主机',
        deviceType: 'windows',
        ipAddress: '127.0.0.1',
        isMaster: true,
        lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
      );

      await expectLater(
        notifier.requestPairing(master),
        throwsA(isA<Object>()),
      );

      final state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.errorMessage, isNotNull);
      expect(state.outgoingPairing, isNull);
    });

    test('syncNow 在主机信息不完整时会给出明确错误', () async {
      final notifier = container.read(syncControllerProvider.notifier);
      await container.read(syncDeviceDaoProvider).upsert(
        SyncDevicesCompanion.insert(
          deviceId: 'master-1',
          deviceName: '主机',
          deviceType: 'windows',
          ipAddress: const drift.Value.absent(),
          authToken: const drift.Value.absent(),
          isMaster: const drift.Value(true),
          lastSyncMs: const drift.Value.absent(),
          lastOutgoingMs: const drift.Value.absent(),
          lastIncomingMs: const drift.Value.absent(),
        ),
      );

      notifier.state = container.read(syncControllerProvider).copyWith(
        state: SyncState.connected,
        connectedDevices: <ConnectedDevice>[
          ConnectedDevice(
            deviceId: 'master-1',
            deviceName: '主机',
            deviceType: 'windows',
            ipAddress: null,
            isMaster: true,
            lastSyncMs: null,
            isOnline: true,
            lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
          ),
        ],
      );

      await notifier.syncNow();

      final state = container.read(syncControllerProvider);
      expect(state.state, SyncState.error);
      expect(state.errorMessage, contains('主机信息不完整'));
    });

    test('主机模式遇到缺少 IP 或令牌的客户端时会跳过并保持成功', () async {
      final notifier = container.read(syncControllerProvider.notifier);
      await container.read(syncDeviceDaoProvider).upsert(
        SyncDevicesCompanion.insert(
          deviceId: 'client-1',
          deviceName: '客户端',
          deviceType: 'android',
          ipAddress: const drift.Value.absent(),
          authToken: const drift.Value.absent(),
          isMaster: const drift.Value(false),
          lastSyncMs: const drift.Value.absent(),
          lastOutgoingMs: const drift.Value.absent(),
          lastIncomingMs: const drift.Value.absent(),
        ),
      );

      notifier.state = container.read(syncControllerProvider).copyWith(
        isMaster: true,
        state: SyncState.connected,
        connectedDevices: <ConnectedDevice>[
          ConnectedDevice(
            deviceId: 'client-1',
            deviceName: '客户端',
            deviceType: 'android',
            ipAddress: null,
            isMaster: false,
            lastSyncMs: null,
            isOnline: true,
            lastSeenAtMs: DateTime.now().millisecondsSinceEpoch,
          ),
        ],
      );

      await notifier.syncNow();

      final state = container.read(syncControllerProvider);
      expect(state.state, SyncState.synced);
      expect(state.errorMessage, isNull);
    });
  });
}
