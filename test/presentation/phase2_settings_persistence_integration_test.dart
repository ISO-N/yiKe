// 文件用途：Phase 2 设置持久化集成测试，覆盖主题、学习配置与番茄钟设置重建恢复。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/pomodoro_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/presentation/providers/pomodoro_settings_provider.dart';
import 'package:yike/presentation/providers/review_intervals_provider.dart';
import 'package:yike/presentation/providers/theme_provider.dart';

/// 创建文件数据库，确保重建 ProviderContainer 后仍可读取持久化结果。
AppDatabase createFileBackedDatabase(File file) {
  return AppDatabase(NativeDatabase(file));
}

void main() {
  /// 等待主题、复习配置与番茄钟配置全部加载完成。
  Future<void> waitUntilLoaded(ProviderContainer container) async {
    for (var attempt = 0; attempt < 80; attempt++) {
      final intervalsLoaded = !container.read(reviewIntervalsProvider).isLoading;
      final pomodoroLoaded = !container.read(pomodoroSettingsProvider).isLoading;
      final themeLoaded = container.read(themeSettingsProvider).seedColorHex.isNotEmpty;
      if (intervalsLoaded && pomodoroLoaded && themeLoaded) {
        return;
      }
      await Future<void>.delayed(Duration.zero);
    }
    fail('等待设置 Provider 加载超时');
  }

  test('4.5 主题、学习配置与番茄钟设置会在重建后恢复', () async {
    final tempDir = await Directory.systemTemp.createTemp(
      'yike_phase2_settings_',
    );
    final dbFile = File(
      '${tempDir.path}${Platform.pathSeparator}phase2_settings.sqlite',
    );

    addTearDown(() async {
      if (tempDir.existsSync()) {
        await tempDir.delete(recursive: true);
      }
    });

    var db = createFileBackedDatabase(dbFile);
    var container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );

    await waitUntilLoaded(container);

    await container.read(themeSettingsProvider.notifier).save(
      container.read(themeSettingsProvider).copyWith(
        seedColorHex: '#4CAF50',
        amoled: true,
      ),
    );
    await container.read(reviewIntervalsProvider.notifier).save(
      <ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
        ReviewIntervalConfigEntity(round: 3, intervalDays: 6, enabled: true),
        ReviewIntervalConfigEntity(round: 4, intervalDays: 10, enabled: true),
        ReviewIntervalConfigEntity(round: 5, intervalDays: 16, enabled: true),
        ReviewIntervalConfigEntity(round: 6, intervalDays: 24, enabled: true),
        ReviewIntervalConfigEntity(round: 7, intervalDays: 36, enabled: true),
        ReviewIntervalConfigEntity(round: 8, intervalDays: 54, enabled: true),
        ReviewIntervalConfigEntity(round: 9, intervalDays: 90, enabled: true),
      ],
    );
    await container.read(pomodoroSettingsProvider.notifier).save(
      const PomodoroSettingsEntity(
        workMinutes: 45,
        shortBreakMinutes: 8,
        longBreakMinutes: 20,
        longBreakInterval: 3,
        autoStartBreak: false,
        autoStartWork: true,
      ),
    );

    container.dispose();
    await db.close();

    db = createFileBackedDatabase(dbFile);
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
    addTearDown(() async {
      container.dispose();
      await db.close();
    });

    await waitUntilLoaded(container);

    final themeSettings = container.read(themeSettingsProvider);
    final intervalSettings = container.read(reviewIntervalsProvider);
    final pomodoroSettings = container.read(pomodoroSettingsProvider).settings;

    expect(themeSettings.seedColorHex, '#4CAF50');
    expect(themeSettings.amoled, isTrue);
    expect(intervalSettings.configs.length, 9);
    expect(
      intervalSettings.configs.map((config) => config.intervalDays).toList(),
      <int>[1, 3, 6, 10, 16, 24, 36, 54, 90],
    );
    expect(pomodoroSettings.workMinutes, 45);
    expect(pomodoroSettings.shortBreakMinutes, 8);
    expect(pomodoroSettings.longBreakMinutes, 20);
    expect(pomodoroSettings.longBreakInterval, 3);
    expect(pomodoroSettings.autoStartBreak, isFalse);
    expect(pomodoroSettings.autoStartWork, isTrue);
  });
}
