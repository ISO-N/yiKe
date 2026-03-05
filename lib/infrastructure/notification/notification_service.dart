/// 文件用途：本地通知服务（flutter_local_notifications），用于发送复习提醒。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:local_notifier/local_notifier.dart';
import 'package:timezone/timezone.dart' as tz;

import 'timezone_service.dart';

class NotificationService {
  /// 单例实例。
  static final NotificationService instance = NotificationService._internal();
  NotificationService._internal();

  /// Android 通知渠道 ID（用于“复习提醒”相关通知）。
  static const String _androidChannelId = 'review_reminder';

  /// Android 通知渠道名称。
  static const String _androidChannelName = '复习提醒';

  /// Android 通知渠道描述。
  static const String _androidChannelDescription = '复习任务提醒通知';

  /// Windows 桌面端通知使用的 appName（用于创建/匹配通知快捷方式）。
  ///
  /// 说明：
  /// - local_notifier 在 Windows 上会基于 appName 生成/校验快捷方式与 AUMI
  /// - 使用纯 ASCII 可降低因字符集/路径兼容导致 setup 失败的概率
  static const String _windowsNotifierAppName = 'YiKe';

  final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();

  // 通知点击事件流（payload=路由字符串），由 UI 层订阅并执行导航。
  final StreamController<String> _tapRouteController =
      StreamController<String>.broadcast();

  // Windows 前台定时提醒：当 Workmanager 不支持时使用 in-app Timer 兜底。
  Timer? _windowsDailyTimer;

  // 初始化状态：避免重复初始化导致的竞态/重复弹窗。
  bool _isInitialized = false;
  Future<void>? _initializing;

  /// 通知点击路由流。
  ///
  /// 说明：payload 约定为应用内路由（如 `/home`、`/statistics`）。
  Stream<String> get onTapRouteStream => _tapRouteController.stream;

  /// 确保 Android 通知渠道已创建。
  ///
  /// 说明：
  /// - 渠道是 Android 8+ 必需概念；未创建渠道时，部分机型/系统版本可能直接不展示通知
  /// - 创建渠道是幂等操作：重复创建不会影响现有设置（但无法覆盖用户手动修改的配置）
  Future<void> _ensureAndroidChannelCreated() async {
    if (kIsWeb || !Platform.isAndroid) return;
    final android = _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    if (android == null) return;

    const channel = AndroidNotificationChannel(
      _androidChannelId,
      _androidChannelName,
      description: _androidChannelDescription,
      importance: Importance.high,
    );
    await android.createNotificationChannel(channel);
  }

  /// 确保 Windows 桌面端通知已完成 setup。
  ///
  /// 说明：
  /// - local_notifier 在 Windows 上必须先 setup，否则 notify 会抛出异常
  /// - setup 可能因为快捷方式创建失败等原因返回“未初始化”，因此这里做一次兜底重试
  Future<void> _ensureWindowsNotifierSetup() async {
    if (kIsWeb || !Platform.isWindows) return;
    await localNotifier.setup(
      appName: _windowsNotifierAppName,
      shortcutPolicy: ShortcutPolicy.requireCreate,
    );
  }

  /// 解析 Android 的调度模式（exact → inexact 降级）。
  ///
  /// 说明：
  /// - Android 12+ 的“精确闹钟”权限可能导致 exact 调度失败或不触发
  /// - 为避免“用户已开启通知但完全收不到”的问题，优先尝试 exact，失败则降级为 inexact
  Future<AndroidScheduleMode> _resolveAndroidScheduleMode() async {
    if (kIsWeb || !Platform.isAndroid) return AndroidScheduleMode.exactAllowWhileIdle;
    final android = _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >();
    if (android == null) return AndroidScheduleMode.exactAllowWhileIdle;

    try {
      final canExact = await android.canScheduleExactNotifications();
      if (canExact == true) return AndroidScheduleMode.exactAllowWhileIdle;

      // 尝试引导系统弹窗/设置页授权（Android 14+ 可能需要用户显式同意）。
      await android.requestExactAlarmsPermission();
      final canExactAfter = await android.canScheduleExactNotifications();
      if (canExactAfter == true) return AndroidScheduleMode.exactAllowWhileIdle;
    } catch (_) {
      // 权限 API 在部分设备/ROM 上可能异常，直接走降级策略，避免影响主流程。
    }

    return AndroidScheduleMode.inexactAllowWhileIdle;
  }

  /// 初始化通知服务（请求权限、初始化通道等）。
  ///
  /// 返回值：Future（无返回值）。
  /// 异常：插件初始化失败时可能抛出异常。
  Future<void> initialize() async {
    if (_isInitialized) return;
    if (_initializing != null) return _initializing!;

    final future = _doInitialize();
    _initializing = future;
    return future;
  }

  Future<void> _doInitialize() async {
    try {
      // 精准通知依赖时区初始化（Android/iOS）。
      await TimezoneService.ensureInitialized();

      const androidSettings = AndroidInitializationSettings(
        '@mipmap/ic_launcher',
      );
      const darwinSettings = DarwinInitializationSettings();
      const initSettings = InitializationSettings(
        android: androidSettings,
        iOS: darwinSettings,
        macOS: darwinSettings,
      );

      await _plugin.initialize(
        initSettings,
        onDidReceiveNotificationResponse: (response) {
          final payload = response.payload?.trim();
          if (payload == null || payload.isEmpty) return;
          _tapRouteController.add(payload);
        },
      );

      // Android 13+ 通知权限请求（若不可用则忽略）。
      await _plugin
          .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin
          >()
          ?.requestNotificationsPermission();
      // Android：确保渠道存在（避免“计划通知触发但系统不展示”的边缘情况）。
      await _ensureAndroidChannelCreated();

      // iOS/macOS 权限请求（若平台不支持则忽略）。
      await _plugin
          .resolvePlatformSpecificImplementation<
            IOSFlutterLocalNotificationsPlugin
          >()
          ?.requestPermissions(alert: true, badge: true, sound: true);
      await _plugin
          .resolvePlatformSpecificImplementation<
            MacOSFlutterLocalNotificationsPlugin
          >()
          ?.requestPermissions(alert: true, badge: true, sound: true);

      // Windows 桌面端通知：local_notifier 需要显式 setup。
      await _ensureWindowsNotifierSetup();

      _isInitialized = true;
    } finally {
      // 说明：无论成功失败都释放“初始化中”的 Future，避免后续调用被永久阻塞。
      _initializing = null;
    }
  }

  /// 查询通知是否启用（仅 Android 支持，其他平台返回 null）。
  ///
  /// 返回值：true/false/null（未知）。
  /// 异常：查询失败时可能抛出异常。
  Future<bool?> areNotificationsEnabled() async {
    return _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.areNotificationsEnabled();
  }

  /// 重新请求通知权限（Android 13+ / iOS）。
  ///
  /// 返回值：是否同意（若平台不支持则返回 null）。
  Future<bool?> requestPermission() async {
    final android = await _plugin
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.requestNotificationsPermission();
    if (android != null) return android;

    final ios = await _plugin
        .resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(alert: true, badge: true, sound: true);
    if (ios != null) return ios;

    return _plugin
        .resolvePlatformSpecificImplementation<MacOSFlutterLocalNotificationsPlugin>()
        ?.requestPermissions(alert: true, badge: true, sound: true);
  }

  /// 发送复习提醒通知。
  ///
  /// 参数：
  /// - [id] 通知 ID
  /// - [title] 标题
  /// - [body] 内容
  /// 返回值：Future（无返回值）。
  /// 异常：发送失败时可能抛出异常。
  Future<void> showReviewNotification({
    required int id,
    required String title,
    required String body,
    String? payloadRoute,
  }) async {
    // Windows：使用系统通知（local_notifier）。
    if (!kIsWeb && Platform.isWindows) {
      try {
        await _ensureWindowsNotifierSetup();
        await localNotifier.notify(
          LocalNotification(
            identifier: 'review_$id',
            title: title,
            body: body,
          ),
        );
      } catch (e, st) {
        // 说明：Windows 通知失败时不应中断主流程（例如后台任务、设置保存）。
        debugPrint('Windows 通知发送失败：$e\n$st');
      }
      return;
    }

    // 兜底：防止调用方忘记 initialize（例如未来新增入口/深链路）。
    await initialize();

    // Android：再次确保渠道存在（initialize 被跳过/失败时仍可自愈）。
    await _ensureAndroidChannelCreated();

    const androidDetails = AndroidNotificationDetails(
      _androidChannelId,
      _androidChannelName,
      channelDescription: _androidChannelDescription,
      importance: Importance.high,
      priority: Priority.high,
    );
    const darwinDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentSound: true,
      presentBadge: true,
    );

    await _plugin.show(
      id,
      title,
      body,
      const NotificationDetails(android: androidDetails, iOS: darwinDetails),
      payload: payloadRoute,
    );
  }

  /// 精准定时：按本地时区调度每日复习提醒（zonedSchedule）。
  ///
  /// 说明：
  /// - 仅 Android/iOS/macOS 有意义；Windows 使用后台检查或到时再提示（本函数会静默跳过）。
  /// - 调度策略：每天固定时间触发
  ///
  /// 参数：
  /// - [id] 通知 ID
  /// - [time] 提醒时间（本地时间）
  /// - [title] 标题
  /// - [body] 内容
  Future<void> scheduleDailyReviewReminder({
    required int id,
    required TimeOfDay time,
    required String title,
    required String body,
    String? payloadRoute,
  }) async {
    // Windows：使用前台 Timer 兜底（不依赖系统级计划任务）。
    if (!kIsWeb && Platform.isWindows) {
      _windowsDailyTimer?.cancel();
      _windowsDailyTimer = null;

      final now = DateTime.now();
      var scheduled = DateTime(
        now.year,
        now.month,
        now.day,
        time.hour,
        time.minute,
      );
      if (!scheduled.isAfter(now)) {
        scheduled = scheduled.add(const Duration(days: 1));
      }

      final delay = scheduled.difference(now);
      _windowsDailyTimer = Timer(delay, () async {
        try {
          await showReviewNotification(
            id: id,
            title: title,
            body: body,
            payloadRoute: payloadRoute,
          );
        } catch (_) {}

        // 递归调度下一天（保持在前台运行时的稳定性）。
        try {
          await scheduleDailyReviewReminder(
            id: id,
            time: time,
            title: title,
            body: body,
            payloadRoute: payloadRoute,
          );
        } catch (_) {}
      });
      return;
    }

    await initialize();
    await TimezoneService.ensureInitialized();

    final now = tz.TZDateTime.now(tz.local);
    var scheduled = tz.TZDateTime(
      tz.local,
      now.year,
      now.month,
      now.day,
      time.hour,
      time.minute,
    );
    if (scheduled.isBefore(now)) {
      scheduled = scheduled.add(const Duration(days: 1));
    }

    const androidDetails = AndroidNotificationDetails(
      _androidChannelId,
      _androidChannelName,
      channelDescription: _androidChannelDescription,
      importance: Importance.high,
      priority: Priority.high,
    );
    const darwinDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentSound: true,
      presentBadge: true,
    );

    // Android：确保渠道存在 + 解析调度模式（精确权限不足则降级）。
    await _ensureAndroidChannelCreated();
    final scheduleMode = await _resolveAndroidScheduleMode();

    await _plugin.zonedSchedule(
      id,
      title,
      body,
      scheduled,
      const NotificationDetails(android: androidDetails, iOS: darwinDetails),
      androidScheduleMode: scheduleMode,
      uiLocalNotificationDateInterpretation:
          UILocalNotificationDateInterpretation.absoluteTime,
      matchDateTimeComponents: DateTimeComponents.time,
      payload: payloadRoute,
    );
  }

  /// 判断指定通知 ID 是否存在待触发的计划通知。
  ///
  /// 说明：用于避免“后台定时检查 + 精准定时”同时启用导致重复通知。
  Future<bool> hasPendingNotification(int id) async {
    if (!kIsWeb && Platform.isWindows) return false;
    final list = await _plugin.pendingNotificationRequests();
    return list.any((e) => e.id == id);
  }

  /// 取消全部通知。
  ///
  /// 说明：用于“导入恢复后”清理旧通知残留（避免通知 ID/内容与新数据不一致）。
  /// 返回值：Future（无返回值）。
  /// 异常：取消失败时可能抛出异常。
  Future<void> cancelAll() async {
    _windowsDailyTimer?.cancel();
    _windowsDailyTimer = null;
    await _plugin.cancelAll();
  }
}
