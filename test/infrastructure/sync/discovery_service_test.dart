// 文件用途：DiscoveryService 集成测试，覆盖 UDP 发现、忽略自身与资源释放。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/sync/discovery_service.dart';

void main() {
  Future<void> waitFor(
    bool Function() predicate, {
    Duration step = const Duration(milliseconds: 50),
    int maxAttempts = 40,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      if (predicate()) return;
      await Future<void>.delayed(step);
    }
    fail('等待发现结果超时');
  }

  Future<void> sendDatagram(Map<String, Object?> payload) async {
    final sender = await RawDatagramSocket.bind(
      InternetAddress.loopbackIPv4,
      0,
    );
    addTearDown(sender.close);
    sender.send(
      utf8.encode(jsonEncode(payload)),
      InternetAddress.loopbackIPv4,
      DiscoveryService.discoveryPort,
    );
    await Future<void>.delayed(const Duration(milliseconds: 100));
  }

  test('DiscoveryService 可接收远端广播并忽略无效/自身报文', () async {
    final service = DiscoveryService(localDeviceId: 'local-device')
      ..configure(deviceName: '本机', deviceType: 'windows', isMaster: true);
    addTearDown(service.dispose);

    await service.start();

    await sendDatagram(<String, Object?>{
      'type': 'invalid',
      'device_id': 'device-invalid',
      'device_name': '无效设备',
      'device_type': 'android',
      'is_master': false,
      'ts': DateTime.now().millisecondsSinceEpoch,
    });
    expect(service.devices, isEmpty);

    await sendDatagram(<String, Object?>{
      'type': 'yike_discovery',
      'device_id': 'local-device',
      'device_name': '自己',
      'device_type': 'windows',
      'is_master': true,
      'ts': DateTime.now().millisecondsSinceEpoch,
    });
    expect(service.devices, isEmpty);

    await sendDatagram(<String, Object?>{
      'type': 'yike_discovery',
      'device_id': 'device-1',
      'device_name': '书桌平板',
      'device_type': 'android',
      'is_master': false,
      'ts': DateTime.now().millisecondsSinceEpoch,
    });
    await waitFor(() => service.devices.length == 1);

    final device = service.devices.single;
    expect(device.deviceId, 'device-1');
    expect(device.deviceName, '书桌平板');
    expect(device.deviceType, 'android');
    expect(device.isMaster, isFalse);
    expect(device.ipAddress, isNotEmpty);

    await sendDatagram(<String, Object?>{
      'type': 'yike_discovery',
      'device_id': 'device-1',
      'device_name': '',
      'device_type': '',
      'is_master': true,
      'ts': DateTime.now().millisecondsSinceEpoch,
    });
    await waitFor(() => service.devices.single.isMaster);

    final updated = service.devices.single;
    expect(updated.deviceName, '未知设备');
    expect(updated.deviceType, 'unknown');
    expect(updated.isMaster, isTrue);
  });

  test('DiscoveryService stop 与 dispose 会清空设备并关闭流', () async {
    final service = DiscoveryService(localDeviceId: 'local-device');
    await service.start();

    await sendDatagram(<String, Object?>{
      'type': 'yike_discovery',
      'device_id': 'device-2',
      'device_name': '会议室电脑',
      'device_type': 'windows',
      'is_master': true,
      'ts': DateTime.now().millisecondsSinceEpoch,
    });
    await waitFor(() => service.devices.isNotEmpty);

    await service.stop();
    expect(service.devices, isEmpty);

    await service.dispose();
    expect(() => service.devicesStream.listen((_) {}), returnsNormally);
  });
}
