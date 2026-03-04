// 文件用途：SettingsRepositoryImpl 单元测试（默认值、加密存储与读取回放）。
// 作者：Codex
// 创建日期：2026-02-25

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/settings_repository_impl.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late SettingsRepositoryImpl repo;

  setUp(() {
    db = createInMemoryDatabase();
    repo = SettingsRepositoryImpl(
      dao: SettingsDao(db),
      secureStorageService: SecureStorageService(),
    );
  });

  tearDown(() async {
    await db.close();
  });

  test('getSettings 在无数据时返回 defaults', () async {
    final s = await repo.getSettings();
    expect(s.reminderTime, AppSettingsEntity.defaults.reminderTime);
    expect(
      s.notificationsEnabled,
      AppSettingsEntity.defaults.notificationsEnabled,
    );
  });

  test('saveSettings 后可正常读取（含 lastNotifiedDate）', () async {
    final input = AppSettingsEntity.defaults.copyWith(
      reminderTime: '10:30',
      doNotDisturbStart: '23:00',
      notificationsEnabled: false,
      lastNotifiedDate: '2026-02-25',
    );
    await repo.saveSettings(input);
    final out = await repo.getSettings();
    expect(out.reminderTime, '10:30');
    expect(out.doNotDisturbStart, '23:00');
    expect(out.notificationsEnabled, false);
    expect(out.lastNotifiedDate, '2026-02-25');
  });

  test('getReviewIntervalConfigs：无数据时返回默认 10 轮配置（全启用）', () async {
    final configs = await repo.getReviewIntervalConfigs();
    expect(configs.length, 10);
    expect(configs.map((e) => e.round).toList(), [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
    expect(
      configs.map((e) => e.intervalDays).toList(),
      [1, 2, 4, 7, 15, 30, 60, 90, 120, 180],
    );
    expect(configs.every((e) => e.enabled), isTrue);
  });

  test('saveReviewIntervalConfigs：全禁用会抛 ArgumentError', () async {
    final configs = [
      ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: false),
      ReviewIntervalConfigEntity(round: 2, intervalDays: 2, enabled: false),
    ];
    expect(() => repo.saveReviewIntervalConfigs(configs), throwsArgumentError);
  });

  test('saveReviewIntervalConfigs：保存后可读取且按 round 升序', () async {
    final input = [
      ReviewIntervalConfigEntity(round: 3, intervalDays: 10, enabled: false),
      ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
      ReviewIntervalConfigEntity(round: 2, intervalDays: 2, enabled: true),
    ];

    await repo.saveReviewIntervalConfigs(input);
    final out = await repo.getReviewIntervalConfigs();
    expect(out.map((e) => e.round).toList(), [1, 2, 3]);
    expect(out.map((e) => e.intervalDays).toList(), [1, 2, 10]);
    expect(out.map((e) => e.enabled).toList(), [true, true, false]);
  });

  test('getReviewIntervalConfigs：脏数据会被过滤，若无启用轮次则回退默认', () async {
    final dao = SettingsDao(db);

    // 说明：直接写入明文 JSON（不带加密前缀），用于触发解析与过滤分支。
    await dao.upsertValue(
      SettingsRepositoryImpl.keyReviewIntervals,
      '[{"round":0,"interval":1,"enabled":true},'
      '{"round":1,"interval":0,"enabled":true},'
      '{"round":2,"interval":2,"enabled":false}]',
    );

    final out = await repo.getReviewIntervalConfigs();
    // round=2 唯一有效但被禁用，因此整体回退默认配置。
    expect(out.length, 10);
    expect(out.every((e) => e.enabled), isTrue);
  });

  test('getSettings：当存储值类型不匹配时会回退默认/或转为字符串', () async {
    final dao = SettingsDao(db);

    // reminder_time：存储为数字，_getString 应转为字符串。
    await dao.upsertValue(SettingsRepositoryImpl.keyNotificationTime, '123');

    // notifications_enabled：存储为字符串，_getBool 应返回 null 并回退默认值。
    await dao.upsertValue(
      SettingsRepositoryImpl.keyNotificationEnabled,
      '"true"',
    );

    final out = await repo.getSettings();
    expect(out.reminderTime, '123');
    expect(
      out.notificationsEnabled,
      AppSettingsEntity.defaults.notificationsEnabled,
    );
  });
}
