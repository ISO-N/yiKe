/// 文件用途：通知去重持久化存储（基于 settings 表），用于“多通知类型 24h 去重/里程碑只提醒一次”等逻辑。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'dart:convert';

import '../../infrastructure/storage/secure_storage_service.dart';
import '../../infrastructure/storage/settings_crypto.dart';
import '../../data/database/daos/settings_dao.dart';

/// 通知去重存储。
///
/// 设计说明：
/// - 复用现有 settings 表（key-value）与 SettingsCrypto 加密策略
/// - 该类仅用于“内部状态”（如上次发送时间），不应写入 SyncLog，也不参与 F12 同步
class NotificationDedupStore {
  /// 构造函数。
  ///
  /// 参数：
  /// - [dao] SettingsDao
  /// - [secureStorageService] 安全存储服务（用于解密/加密）
  NotificationDedupStore({
    required SettingsDao dao,
    required SecureStorageService secureStorageService,
  }) : _dao = dao,
       _crypto = SettingsCrypto(secureStorageService: secureStorageService);

  final SettingsDao _dao;
  final SettingsCrypto _crypto;

  static const String _keyLastSentPrefix = 'notification_last_sent:';
  static const String _keyStreakMilestone = 'notification_streak_milestone';

  /// 判断某类型通知是否可发送（24h 去重）。
  ///
  /// 参数：
  /// - [type] 类型标识（如 "review" / "overdue" / "goal" / "streak"）
  /// - [ttl] 去重时间窗（默认 24h）
  /// - [now] 当前时间（用于测试注入）
  ///
  /// 返回值：true=可发送，false=应去重。
  Future<bool> shouldSend(
    String type, {
    Duration ttl = const Duration(hours: 24),
    DateTime? now,
  }) async {
    final current = now ?? DateTime.now();
    final last = await getLastSent(type);
    if (last == null) return true;
    return current.difference(last) >= ttl;
  }

  /// 获取某类型通知上次发送时间（本机）。
  Future<DateTime?> getLastSent(String type) async {
    final raw = await _dao.getValue('$_keyLastSentPrefix$type');
    if (raw == null) return null;
    try {
      final decrypted = await _crypto.decrypt(raw);
      final decoded = jsonDecode(decrypted);
      final ms = decoded is int ? decoded : int.tryParse(decoded.toString());
      if (ms == null) return null;
      return DateTime.fromMillisecondsSinceEpoch(ms);
    } catch (_) {
      return null;
    }
  }

  /// 设置某类型通知上次发送时间（本机）。
  Future<void> setLastSent(String type, DateTime time) async {
    await _dao.upsertValue(
      '$_keyLastSentPrefix$type',
      await _crypto.encrypt(jsonEncode(time.millisecondsSinceEpoch)),
    );
  }

  /// 读取“连续打卡里程碑”去重信息。
  ///
  /// 返回值：
  /// - milestone: 已提醒的最大里程碑（如 7/30/100）；未提醒为 0
  /// - sentAt: 上次提醒时间（用于 24h 去重）
  Future<({int milestone, DateTime? sentAt})> getStreakMilestoneState() async {
    final raw = await _dao.getValue(_keyStreakMilestone);
    if (raw == null) return (milestone: 0, sentAt: null);
    try {
      final decrypted = await _crypto.decrypt(raw);
      final decoded = jsonDecode(decrypted);
      if (decoded is! Map) return (milestone: 0, sentAt: null);
      final mRaw = decoded['milestone'];
      final tRaw = decoded['sent_at_ms'];
      final m = mRaw is int ? mRaw : int.tryParse(mRaw?.toString() ?? '') ?? 0;
      final ms =
          tRaw is int ? tRaw : int.tryParse(tRaw?.toString() ?? '') ?? 0;
      return (
        milestone: m,
        sentAt: ms <= 0 ? null : DateTime.fromMillisecondsSinceEpoch(ms),
      );
    } catch (_) {
      return (milestone: 0, sentAt: null);
    }
  }

  /// 写入“连续打卡里程碑”去重信息。
  Future<void> setStreakMilestoneState({
    required int milestone,
    required DateTime sentAt,
  }) async {
    final payload = <String, dynamic>{
      'milestone': milestone,
      'sent_at_ms': sentAt.millisecondsSinceEpoch,
    };
    await _dao.upsertValue(
      _keyStreakMilestone,
      await _crypto.encrypt(jsonEncode(payload)),
    );
  }
}

