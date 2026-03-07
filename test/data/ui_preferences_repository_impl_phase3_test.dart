// 文件用途：UiPreferencesRepositoryImpl Phase3 单测，覆盖解密失败/解析失败/默认值兜底等分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/ui_preferences_repository_impl.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';
import 'package:yike/infrastructure/storage/settings_crypto.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late SettingsDao dao;
  late SecureStorageService secureStorage;
  late SettingsCrypto crypto;
  late UiPreferencesRepositoryImpl repo;

  setUp(() {
    db = createInMemoryDatabase();
    dao = SettingsDao(db);
    secureStorage = SecureStorageService();
    crypto = SettingsCrypto(secureStorageService: secureStorage);
    repo = UiPreferencesRepositoryImpl(
      dao: dao,
      secureStorageService: secureStorage,
    );
  });

  tearDown(() async {
    await db.close();
  });

  group('UiPreferencesRepositoryImpl', () {
    test('getTaskListBlurEnabled：无值时默认 true', () async {
      final enabled = await repo.getTaskListBlurEnabled();
      expect(enabled, isTrue);
    });

    test('getTaskListBlurEnabled：正常读写 bool', () async {
      await repo.setTaskListBlurEnabled(false);
      expect(await repo.getTaskListBlurEnabled(), isFalse);

      await repo.setTaskListBlurEnabled(true);
      expect(await repo.getTaskListBlurEnabled(), isTrue);
    });

    test('getTaskListBlurEnabled：解密/解析异常时兜底 true', () async {
      // 1) 解密异常：伪造带前缀但非法的密文。
      await dao.upsertValue('ui_task_list_blur_enabled', 'enc:v1:@@invalid@@');
      expect(await repo.getTaskListBlurEnabled(), isTrue);

      // 2) JSON 非 bool：解密后返回 string，仍兜底 true。
      final encryptedString = await crypto.encrypt(jsonEncode('not-bool'));
      await dao.upsertValue('ui_task_list_blur_enabled', encryptedString);
      expect(await repo.getTaskListBlurEnabled(), isTrue);
    });

    test('getUndoSnackbarEnabled / getHapticFeedbackEnabled：非法值会回退 default', () async {
      // 写入一个“解密成功，但 JSON 非 bool”的值。
      final encryptedString = await crypto.encrypt(jsonEncode('invalid'));
      await dao.upsertValue('ui_undo_snackbar', encryptedString);
      await dao.upsertValue('ui_haptic_feedback', encryptedString);

      expect(await repo.getUndoSnackbarEnabled(), isTrue);
      expect(await repo.getHapticFeedbackEnabled(), isTrue);

      // 再写入一个“解密失败”的值，仍应回退 default。
      await dao.upsertValue('ui_undo_snackbar', 'enc:v1:broken');
      await dao.upsertValue('ui_haptic_feedback', 'enc:v1:broken');
      expect(await repo.getUndoSnackbarEnabled(), isTrue);
      expect(await repo.getHapticFeedbackEnabled(), isTrue);
    });

    test('getSkeletonStrategy：支持 on/off/auto，并对非法值回退 auto', () async {
      expect(await repo.getSkeletonStrategy(), 'auto');

      await repo.setSkeletonStrategy('on');
      expect(await repo.getSkeletonStrategy(), 'on');

      await repo.setSkeletonStrategy('off');
      expect(await repo.getSkeletonStrategy(), 'off');

      // setSkeletonStrategy 会归一化非法输入为 auto。
      await repo.setSkeletonStrategy('whatever');
      expect(await repo.getSkeletonStrategy(), 'auto');

      // 手动写入非法 JSON/解密失败，也应回退 auto。
      await dao.upsertValue('ui_skeleton_strategy', 'enc:v1:bad');
      expect(await repo.getSkeletonStrategy(), 'auto');

      final encryptedWeird = await crypto.encrypt(jsonEncode(123));
      await dao.upsertValue('ui_skeleton_strategy', encryptedWeird);
      expect(await repo.getSkeletonStrategy(), 'auto');
    });
  });
}

