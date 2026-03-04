/// 文件用途：时区初始化服务（timezone + flutter_timezone），用于精准定时通知。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'package:flutter/foundation.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:timezone/data/latest.dart' as tz_data;
import 'package:timezone/timezone.dart' as tz;

/// 时区初始化服务。
///
/// 说明：
/// - flutter_local_notifications 的 zonedSchedule 依赖 tz.local 正确设置
/// - 该服务在应用启动/通知初始化时调用一次即可
class TimezoneService {
  TimezoneService._();

  static bool _initialized = false;

  /// 确保时区已初始化。
  ///
  /// 返回值：Future（无返回值）。
  /// 异常：初始化失败时会吞掉异常并降级为 tz.UTC（避免启动崩溃）。
  static Future<void> ensureInitialized() async {
    if (_initialized) return;
    try {
      tz_data.initializeTimeZones();

      // Web/桌面端：不强制设置 IANA 时区（无需精准定时通知）。
      if (kIsWeb) {
        _initialized = true;
        return;
      }

      final info = await FlutterTimezone.getLocalTimezone();
      final location = tz.getLocation(info.identifier);
      tz.setLocalLocation(location);
      _initialized = true;
    } catch (_) {
      // 降级：使用 UTC，避免因时区初始化失败导致通知功能不可用。
      try {
        tz_data.initializeTimeZones();
        tz.setLocalLocation(tz.UTC);
      } catch (_) {}
      _initialized = true;
    }
  }
}
