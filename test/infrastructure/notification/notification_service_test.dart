// 文件用途：NotificationService 服务测试，覆盖 Windows 分支的初始化、即时通知、定时提醒与取消逻辑。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:fake_async/fake_async.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/notification/notification_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const localNotificationsChannel = MethodChannel(
    'dexterous.com/flutter/local_notifications',
  );
  const localNotifierChannel = MethodChannel('local_notifier');
  const timezoneChannel = MethodChannel('flutter_timezone');

  final pluginCalls = <String>[];
  final notifierCalls = <String>[];
  final notifiedBodies = <Map<dynamic, dynamic>>[];

  setUp(() {
    pluginCalls.clear();
    notifierCalls.clear();
    notifiedBodies.clear();

    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(localNotificationsChannel, (call) async {
      pluginCalls.add(call.method);
      switch (call.method) {
        case 'initialize':
        case 'cancelAll':
          return true;
        case 'pendingNotificationRequests':
          return <Map<String, Object?>>[];
        default:
          return null;
      }
    });
    messenger.setMockMethodCallHandler(localNotifierChannel, (call) async {
      notifierCalls.add(call.method);
      if (call.method == 'notify') {
        notifiedBodies.add(call.arguments as Map<dynamic, dynamic>);
      }
      return true;
    });
    messenger.setMockMethodCallHandler(timezoneChannel, (call) async {
      if (call.method == 'getLocalTimezone') {
        return 'Asia/Shanghai';
      }
      return null;
    });
  });

  tearDown(() async {
    await NotificationService.instance.cancelAll();
    final messenger =
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
    messenger.setMockMethodCallHandler(localNotificationsChannel, null);
    messenger.setMockMethodCallHandler(localNotifierChannel, null);
    messenger.setMockMethodCallHandler(timezoneChannel, null);
  });

  group('NotificationService', () {
    test('initialize 会初始化插件与 Windows 通知器', () async {
      await NotificationService.instance.initialize();

      expect(pluginCalls, contains('initialize'));
      expect(notifierCalls, contains('setup'));
    });

    test('showReviewNotification 会通过 Windows 通知器发送即时提醒', () async {
      await NotificationService.instance.showReviewNotification(
        id: 21,
        title: '任务逾期提醒',
        body: '请尽快处理逾期任务',
        payloadRoute: '/home?tab=today&focus=overdue',
      );

      expect(notifierCalls, containsAll(<String>['setup', 'notify']));
      expect(notifiedBodies, hasLength(1));
      expect(notifiedBodies.single['identifier'], 'review_21');
      expect(notifiedBodies.single['title'], '任务逾期提醒');
      expect(notifiedBodies.single['body'], '请尽快处理逾期任务');
    });

    test('scheduleDailyReviewReminder 在 Windows 下会创建前台定时提醒', () {
      fakeAsync((async) {
        final now = DateTime.now();
        final nextMinute = now.add(const Duration(minutes: 1));

        NotificationService.instance.scheduleDailyReviewReminder(
          id: 1,
          time: TimeOfDay(hour: nextMinute.hour, minute: nextMinute.minute),
          title: '今日复习提醒',
          body: '打开忆刻开始复习吧',
          payloadRoute: '/home',
        );

        async.flushMicrotasks();
        final delay = Duration(
          hours: nextMinute.hour - now.hour,
          minutes: nextMinute.minute - now.minute,
        );
        async.elapse(delay + const Duration(seconds: 1));
        async.flushMicrotasks();

        expect(notifierCalls.where((item) => item == 'notify'), isNotEmpty);
      });
    });

    test('hasPendingNotification 与 cancelAll 在 Windows 分支可正常返回', () async {
      final hasPending = await NotificationService.instance.hasPendingNotification(
        9,
      );
      expect(hasPending, isFalse);

      await NotificationService.instance.cancelAll();
      expect(pluginCalls, contains('cancelAll'));
    });
  });
}
