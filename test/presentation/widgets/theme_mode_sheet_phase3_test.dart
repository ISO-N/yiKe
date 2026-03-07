// 文件用途：ThemeModeSheet Phase3 Widget 测试，覆盖主题模式选择与关闭弹窗分支。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/theme_settings.dart';
import 'package:yike/domain/repositories/theme_settings_repository.dart';
import 'package:yike/presentation/pages/settings/widgets/theme_mode_sheet.dart';
import 'package:yike/presentation/providers/theme_provider.dart';

/// 测试用主题仓储：记录保存次数与最后一次保存内容。
class _FakeThemeSettingsRepository implements ThemeSettingsRepository {
  ThemeSettingsEntity current = ThemeSettingsEntity.defaults();
  final List<ThemeSettingsEntity> saved = <ThemeSettingsEntity>[];

  @override
  Future<ThemeSettingsEntity> getThemeSettings() async => current;

  @override
  Future<void> saveThemeSettings(ThemeSettingsEntity settings) async {
    current = settings;
    saved.add(settings);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpWithSheet(
    WidgetTester tester, {
    required Brightness brightness,
    required _FakeThemeSettingsRepository repository,
  }) async {
    tester.view.physicalSize = const Size(900, 1400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          themeSettingsRepositoryProvider.overrideWithValue(repository),
        ],
        child: MaterialApp(
          theme: ThemeData(brightness: brightness),
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: FilledButton(
                  onPressed: () {
                    showModalBottomSheet<void>(
                      context: context,
                      builder: (_) => const ThemeModeSheet(),
                    );
                  },
                  child: const Text('打开弹窗'),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('打开弹窗'));
    await tester.pumpAndSettle();
    expect(find.text('选择主题模式'), findsOneWidget);
  }

  group('ThemeModeSheet phase3', () {
    testWidgets('浅色模式下选择深色会保存并关闭弹窗', (tester) async {
      final repository = _FakeThemeSettingsRepository();
      await pumpWithSheet(
        tester,
        brightness: Brightness.light,
        repository: repository,
      );

      await tester.tap(find.widgetWithText(RadioListTile<AppThemeMode>, '深色'));
      await tester.pumpAndSettle();

      expect(repository.saved, isNotEmpty);
      expect(repository.saved.last.mode, AppThemeMode.dark.value);
      expect(find.text('选择主题模式'), findsNothing);
    });

    testWidgets('深色模式下取消按钮会关闭弹窗且不保存', (tester) async {
      final repository = _FakeThemeSettingsRepository();
      await pumpWithSheet(
        tester,
        brightness: Brightness.dark,
        repository: repository,
      );

      await tester.tap(find.text('取消'));
      await tester.pumpAndSettle();

      expect(repository.saved, isEmpty);
      expect(find.text('选择主题模式'), findsNothing);
    });

    testWidgets('支持选择跟随系统并触发保存', (tester) async {
      final repository = _FakeThemeSettingsRepository();
      repository.current = repository.current.copyWith(
        mode: AppThemeMode.dark.value,
      );

      await pumpWithSheet(
        tester,
        brightness: Brightness.light,
        repository: repository,
      );

      await tester.tap(find.widgetWithText(RadioListTile<AppThemeMode>, '跟随系统'));
      await tester.pumpAndSettle();

      expect(repository.saved, isNotEmpty);
      expect(repository.saved.last.mode, AppThemeMode.system.value);
      expect(find.text('选择主题模式'), findsNothing);
    });
  });
}

