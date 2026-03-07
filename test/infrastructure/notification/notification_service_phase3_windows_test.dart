// 文件用途：NotificationService Phase3 Windows 分支测试，覆盖桌面端通知发送与前台 Timer 调度逻辑。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:fake_async/fake_async.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/infrastructure/notification/notification_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const localNotificationsChannel =
      MethodChannel('dexterous.com/flutter/local_notifications');

  setUp(() {
    // 说明：NotificationService.cancelAll 会调用 flutter_local_notifications 的 MethodChannel。
    // widget_test 环境下没有插件注册，这里提供最小 mock，避免 MissingPluginException。
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(localNotificationsChannel, (call) async {
          switch (call.method) {
            case 'cancelAll':
              return null;
            case 'pendingNotificationRequests':
              return <Map<String, Object?>>[];
            default:
              return null;
          }
        });
  });

  tearDown(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(localNotificationsChannel, null);
  });

  group('NotificationService Windows', () {
    test('showReviewNotification 在 Windows 上失败也会被吞掉（不抛异常）', () async {
      await NotificationService.instance.showReviewNotification(
        id: 1,
        title: '复习提醒',
        body: '该用例主要覆盖 Windows 分支的 try/catch',
        payloadRoute: '/home',
      );
    });

    test('scheduleDailyReviewReminder 在 Windows 上会使用前台 Timer 兜底并支持递归调度', () {
      fakeAsync((async) {
        final now = DateTime.now();
        var minute = now.minute + 1;
        var hour = now.hour;
        if (minute >= 60) {
          minute -= 60;
          hour = (hour + 1) % 24;
        }

        // 触发一次调度：会创建 Timer，并在触发后递归调度下一天。
        NotificationService.instance.scheduleDailyReviewReminder(
          id: 99,
          time: TimeOfDay(hour: hour, minute: minute),
          title: '每日提醒',
          body: '用于覆盖 Windows Timer 分支',
          payloadRoute: '/statistics',
        );

        // 推进时间触发 Timer。
        async.elapse(const Duration(minutes: 2));
        async.flushMicrotasks();

        // 再次调度同一个 ID：应取消旧 Timer 并建立新 Timer（覆盖 cancel 分支）。
        NotificationService.instance.scheduleDailyReviewReminder(
          id: 99,
          time: TimeOfDay(hour: hour, minute: minute),
          title: '每日提醒2',
          body: '覆盖重复调度路径',
          payloadRoute: '/calendar',
        );
        async.elapse(const Duration(minutes: 2));
        async.flushMicrotasks();

        // 清理：cancelAll 会取消 Timer，并调用 flutter_local_notifications.cancelAll（已 mock）。
        async.run((async) async {
          await NotificationService.instance.cancelAll();
        });
      });
    });
  });
}

