// 文件用途：ReviewIntervalsNotifier Phase 3 单元测试，覆盖配置校验异常、updateRound 保护逻辑与 reset/enableAll 主路径。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/repositories/settings_repository.dart';
import 'package:yike/presentation/providers/review_intervals_provider.dart';

class _FakeSettingsRepository implements SettingsRepository {
  _FakeSettingsRepository(this._configs);

  List<ReviewIntervalConfigEntity> _configs;
  int saveCalls = 0;

  @override
  Future<List<ReviewIntervalConfigEntity>> getReviewIntervalConfigs() async {
    return _configs;
  }

  @override
  Future<void> saveReviewIntervalConfigs(
    List<ReviewIntervalConfigEntity> configs,
  ) async {
    saveCalls += 1;
    _configs = configs;
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

void main() {
  group('ReviewIntervalsNotifier Phase3', () {
    test('save 会校验空配置/轮次不连续/无启用/间隔不递增', () async {
      final repo = _FakeSettingsRepository(const <ReviewIntervalConfigEntity>[]);
      final notifier = ReviewIntervalsNotifier(repo);

      expect(
        () => notifier.save(const <ReviewIntervalConfigEntity>[]),
        throwsA(isA<ArgumentError>()),
      );

      final nonContiguous = <ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 3, intervalDays: 3, enabled: true),
      ];
      expect(
        () => notifier.save(nonContiguous),
        throwsA(isA<ArgumentError>()),
      );

      final noneEnabled = <ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: false),
      ];
      expect(
        () => notifier.save(noneEnabled),
        throwsA(isA<ArgumentError>()),
      );

      final notIncreasing = <ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 2, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 1, enabled: true),
      ];
      expect(
        () => notifier.save(notIncreasing),
        throwsA(isA<ArgumentError>()),
      );
    });

    test('updateRound 禁止关闭最后一轮启用，并可更新后持久化', () async {
      final repo = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 2, enabled: false),
      ]);
      final notifier = ReviewIntervalsNotifier(repo);

      // 初始化 state，模拟已加载完成。
      notifier.state = notifier.state.copyWith(isLoading: false, configs: await repo.getReviewIntervalConfigs());

      await expectLater(
        () => notifier.updateRound(1, enabled: false),
        throwsA(isA<ArgumentError>()),
      );

      await notifier.updateRound(2, enabled: true, intervalDays: 3);
      expect(repo.saveCalls, 1);
      expect(notifier.state.configs.last.enabled, isTrue);
      expect(notifier.state.configs.last.intervalDays, 3);
    });

    test('resetDefault 与 enableAll 会保存并刷新 state', () async {
      final repo = _FakeSettingsRepository(<ReviewIntervalConfigEntity>[
        ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
        ReviewIntervalConfigEntity(round: 2, intervalDays: 2, enabled: false),
      ]);
      final notifier = ReviewIntervalsNotifier(repo);
      notifier.state = notifier.state.copyWith(isLoading: false, configs: await repo.getReviewIntervalConfigs());

      await notifier.enableAll();
      expect(notifier.state.configs.every((c) => c.enabled), isTrue);

      await notifier.resetDefault();
      expect(notifier.state.configs.length, greaterThanOrEqualTo(5));
      expect(notifier.state.configs.first.round, 1);
    });
  });
}
