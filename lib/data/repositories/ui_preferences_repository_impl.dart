/// 文件用途：UI 本地偏好仓储实现（UiPreferencesRepositoryImpl），用于保存“性能相关的 UI 开关”等本机偏好。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'dart:convert';

import '../../domain/repositories/ui_preferences_repository.dart';
import '../../infrastructure/storage/secure_storage_service.dart';
import '../../infrastructure/storage/settings_crypto.dart';
import '../database/daos/settings_dao.dart';

/// UI 本地偏好仓储实现。
///
/// 说明：
/// - 复用 SettingsDao（key-value 表）与 SettingsCrypto（加密策略），避免引入新的持久化方案
/// - 不写入 SyncLog，因此不会通过 F12 同步传播（符合“按设备本地设置”的约束）
class UiPreferencesRepositoryImpl implements UiPreferencesRepository {
  /// 构造函数。
  ///
  /// 参数：
  /// - [dao] SettingsDao
  /// - [secureStorageService] 安全存储服务（用于管理加密密钥）
  UiPreferencesRepositoryImpl({
    required this.dao,
    required SecureStorageService secureStorageService,
  }) : _crypto = SettingsCrypto(secureStorageService: secureStorageService);

  final SettingsDao dao;
  final SettingsCrypto _crypto;

  // 约定：UI 本地偏好使用 ui_ 前缀，避免与业务设置 key 混淆。
  static const String _keyTaskListBlurEnabled = 'ui_task_list_blur_enabled';

  @override
  Future<bool> getTaskListBlurEnabled() async {
    try {
      final stored = await dao.getValue(_keyTaskListBlurEnabled);
      if (stored == null) return true;
      final decrypted = await _crypto.decrypt(stored);
      final decoded = jsonDecode(decrypted);
      return decoded is bool ? decoded : true;
    } catch (_) {
      // 读取失败时兜底开启：保持原有视觉默认值，避免因加密/解析异常导致 UI 退化。
      return true;
    }
  }

  @override
  Future<void> setTaskListBlurEnabled(bool enabled) async {
    await dao.upsertValue(
      _keyTaskListBlurEnabled,
      await _crypto.encrypt(jsonEncode(enabled)),
    );
  }
}

