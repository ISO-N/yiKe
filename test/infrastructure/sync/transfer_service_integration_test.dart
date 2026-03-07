// 文件用途：TransferService 集成测试，覆盖真实 HTTP 配对、鉴权交换与在线探测路径。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/sync/sync_models.dart';
import 'package:yike/infrastructure/sync/transfer_service.dart';

void main() {
  late TransferService service;

  setUp(() {
    service = TransferService();
  });

  tearDown(() async {
    await service.dispose();
  });

  group('TransferService', () {
    test('支持真实 HTTP 配对请求、配对确认与在线探测', () async {
      InternetAddress? lastPeer;
      service.onPairRequest = (request, peer) async {
        lastPeer = peer;
        expect(request.clientDeviceId, 'client-a');
        expect(request.clientDeviceName, '测试客户端');
        expect(request.clientDeviceType, 'windows');
        return PairRequestResponse(sessionId: 'session-1', expiresAtMs: 123456);
      };
      service.onPairConfirm = (request, peer) async {
        lastPeer = peer;
        expect(request.sessionId, 'session-1');
        expect(request.pairingCode, '654321');
        return PairConfirmResponse(authToken: 'token-1');
      };

      await service.startServer();

      expect(await service.ping(ipAddress: '127.0.0.1'), isTrue);

      final pairResponse = await service.requestPairing(
        ipAddress: '127.0.0.1',
        request: PairRequest(
          clientDeviceId: 'client-a',
          clientDeviceName: '测试客户端',
          clientDeviceType: 'windows',
        ),
      );
      expect(pairResponse.sessionId, 'session-1');
      expect(pairResponse.expiresAtMs, 123456);

      final confirmResponse = await service.confirmPairing(
        ipAddress: '127.0.0.1',
        request: PairConfirmRequest(sessionId: 'session-1', pairingCode: '654321'),
      );
      expect(confirmResponse.authToken, 'token-1');
      expect(lastPeer?.address, anyOf('127.0.0.1', '::1'));
    });

    test('同步交换会校验 Bearer Token，并在通过后返回对端事件', () async {
      var validatorCallCount = 0;
      var exchangeCallCount = 0;
      service.validateToken = (fromDeviceId, token) async {
        validatorCallCount++;
        expect(fromDeviceId, 'client-a');
        return token == 'good-token';
      };
      service.onSyncExchange = (request, peer) async {
        exchangeCallCount++;
        expect(peer.address, anyOf('127.0.0.1', '::1'));
        expect(request.sinceMs, 10);
        expect(request.events.single.entityType, 'learning_item');
        return SyncExchangeResponse(
          serverNowMs: 999,
          events: <SyncEvent>[
            SyncEvent(
              deviceId: 'server-a',
              entityType: 'review_task',
              entityId: 7,
              operation: SyncOperation.update,
              data: <String, dynamic>{'status': 'done'},
              timestampMs: 1000,
            ),
          ],
        );
      };

      await service.startServer();

      final request = SyncExchangeRequest(
        fromDeviceId: 'client-a',
        sinceMs: 10,
        events: <SyncEvent>[
          SyncEvent(
            deviceId: 'client-a',
            entityType: 'learning_item',
            entityId: 1,
            operation: SyncOperation.create,
            data: <String, dynamic>{'title': '需要同步'},
            timestampMs: 11,
          ),
        ],
      );

      await expectLater(
        service.exchange(
          ipAddress: '127.0.0.1',
          token: 'bad-token',
          request: request,
        ),
        throwsA(isA<HttpException>()),
      );
      expect(validatorCallCount, 1);
      expect(exchangeCallCount, 0);

      final response = await service.exchange(
        ipAddress: '127.0.0.1',
        token: 'good-token',
        request: request,
      );
      expect(validatorCallCount, 2);
      expect(exchangeCallCount, 1);
      expect(response.serverNowMs, 999);
      expect(response.events.single.entityType, 'review_task');
      expect(response.events.single.data['status'], 'done');
    });
  });
}
