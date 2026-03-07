// 文件用途：ReviewPreviewPanel Phase3 边界分支测试，覆盖增加轮次、校验错误与保存失败提示。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/repositories/settings_repository.dart';
import 'package:yike/presentation/widgets/review_preview_panel.dart';

/// 可控 SettingsRepository：用于复习间隔配置读取与保存。
class _FakeSettingsRepository implements SettingsRepository {
  _FakeSettingsRepository(this._configs);

  List<ReviewIntervalConfigEntity> _configs;
  bool shouldFailOnSave = false;
  final List<List<ReviewIntervalConfigEntity>> saved = <List<ReviewIntervalConfigEntity>>[];

  @override
  Future<AppSettingsEntity> getSettings() async => AppSettingsEntity.defaults;

  @override
  Future<List<ReviewIntervalConfigEntity>> getReviewIntervalConfigs() async {
    return List<ReviewIntervalConfigEntity>.from(_configs);
  }

  @override
  Future<void> saveReviewIntervalConfigs(
    List<ReviewIntervalConfigEntity> configs,
  ) async {
    if (shouldFailOnSave) {
      throw StateError('模拟保存失败');
    }
    _configs = List<ReviewIntervalConfigEntity>.from(configs);
    saved.add(List<ReviewIntervalConfigEntity>.from(configs));
  }

  @override
  Future<void> saveSettings(AppSettingsEntity settings) async {}
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  Future<void> pumpPanel(
    WidgetTester tester,
    _FakeSettingsRepository repository,
  ) async {
    tester.view.physicalSize = const Size(1200, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      ProviderScope(
        overrides: <Override>[
          settingsRepositoryProvider.overrideWithValue(repository),
        ],
        child: MaterialApp(
          home: Scaffold(
            body: ReviewPreviewPanel(learningDate: DateTime(2026, 3, 7)),
          ),
        ),
      ),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 80));
  }

  group('ReviewPreviewPanel phase3 edges', () {
    testWidgets('支持增加轮次并展示长间隔警告', (tester) async {
      final repository = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
        ReviewIntervalConfigEntity(round: 3, intervalDays: 60, enabled: true),
        ReviewIntervalConfigEntity(round: 4, intervalDays: 90, enabled: true),
        ReviewIntervalConfigEntity(round: 5, intervalDays: 120, enabled: true),
        ReviewIntervalConfigEntity(round: 6, intervalDays: 150, enabled: true),
        ReviewIntervalConfigEntity(round: 7, intervalDays: 160, enabled: true),
        ReviewIntervalConfigEntity(round: 8, intervalDays: 170, enabled: true),
        ReviewIntervalConfigEntity(round: 9, intervalDays: 175, enabled: true),
      ]);

      await pumpPanel(tester, repository);

      await tester.tap(find.text('复习计划预览'));
      await tester.pumpAndSettle();

      // intervalDays > 30 时展示警告文案。
      expect(find.text('间隔过长可能导致遗忘'), findsWidgets);

      // 追加到第 10 轮（覆盖 _addRound 分支，且间隔会被 clamp 到 180 内）。
      await tester.tap(find.text('增加轮次'));
      await tester.pumpAndSettle();
      expect(find.textContaining('第10次复习'), findsOneWidget);

      final addButton = tester.widget<OutlinedButton>(
        find.widgetWithText(OutlinedButton, '增加轮次'),
      );
      expect(addButton.onPressed, isNull);
    });

    testWidgets('配置不递增时展示校验错误并禁止保存', (tester) async {
      final repository = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 7, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
        ReviewIntervalConfigEntity(round: 3, intervalDays: 10, enabled: true),
      ]);

      await pumpPanel(tester, repository);
      await tester.tap(find.text('复习计划预览'));
      await tester.pumpAndSettle();

      expect(find.textContaining('间隔天数需递增'), findsOneWidget);
      final saveButton = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, '确认保存'),
      );
      expect(saveButton.onPressed, isNull);
    });

    testWidgets('保存失败时会展示保存失败错误提示', (tester) async {
      final repository = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
        ReviewIntervalConfigEntity(round: 3, intervalDays: 7, enabled: true),
      ])..shouldFailOnSave = true;

      await pumpPanel(tester, repository);
      await tester.tap(find.text('复习计划预览'));
      await tester.pumpAndSettle();

      // 触发未保存状态：减少轮次会修改草稿配置。
      await tester.tap(find.text('减少轮次'));
      await tester.pumpAndSettle();
      expect(find.text('有未保存更改'), findsOneWidget);

      await tester.tap(find.text('确认保存'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 200));

      expect(find.textContaining('保存失败'), findsWidgets);
      expect(find.textContaining('模拟保存失败'), findsWidgets);
    });
  });
}
