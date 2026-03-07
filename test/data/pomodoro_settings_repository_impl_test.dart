// 文件用途：PomodoroSettingsRepositoryImpl 单元测试（默认值、保存与裁剪）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/settings_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/pomodoro_settings_repository_impl.dart';
import 'package:yike/domain/entities/pomodoro_settings.dart';
import 'package:yike/infrastructure/storage/secure_storage_service.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late PomodoroSettingsRepositoryImpl repository;

  setUp(() {
    db = createInMemoryDatabase();
    repository = PomodoroSettingsRepositoryImpl(
      dao: SettingsDao(db),
      secureStorageService: SecureStorageService(),
    );
  });

  tearDown(() async {
    await db.close();
  });

  test('无持久化数据时返回默认番茄钟配置', () async {
    final settings = await repository.getSettings();
    expect(settings.workMinutes, PomodoroSettingsEntity.defaults.workMinutes);
    expect(
      settings.shortBreakMinutes,
      PomodoroSettingsEntity.defaults.shortBreakMinutes,
    );
    expect(
      settings.longBreakMinutes,
      PomodoroSettingsEntity.defaults.longBreakMinutes,
    );
    expect(
      settings.longBreakInterval,
      PomodoroSettingsEntity.defaults.longBreakInterval,
    );
  });

  test('saveSettings 后可读取，且会对边界值做裁剪', () async {
    await repository.saveSettings(
      const PomodoroSettingsEntity(
        workMinutes: 999,
        shortBreakMinutes: 0,
        longBreakMinutes: 200,
        longBreakInterval: -1,
        autoStartBreak: false,
        autoStartWork: false,
      ),
    );

    final settings = await repository.getSettings();
    expect(settings.workMinutes, 180);
    expect(settings.shortBreakMinutes, 1);
    expect(settings.longBreakMinutes, 120);
    expect(settings.longBreakInterval, 1);
    expect(settings.autoStartBreak, isFalse);
    expect(settings.autoStartWork, isFalse);
  });
}
