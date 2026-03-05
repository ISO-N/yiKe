/// 文件用途：复习间隔配置状态管理（Riverpod StateNotifier），用于复习预览与新内容生成（F1.5）。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/utils/ebbinghaus_utils.dart';
import '../../di/providers.dart';
import '../../domain/entities/review_interval_config.dart';
import '../../domain/repositories/settings_repository.dart';

/// 复习间隔配置状态。
class ReviewIntervalsState {
  const ReviewIntervalsState({
    required this.isLoading,
    required this.configs,
    this.errorMessage,
  });

  final bool isLoading;
  final List<ReviewIntervalConfigEntity> configs;
  final String? errorMessage;

  factory ReviewIntervalsState.initial() =>
      const ReviewIntervalsState(isLoading: true, configs: []);

  ReviewIntervalsState copyWith({
    bool? isLoading,
    List<ReviewIntervalConfigEntity>? configs,
    String? errorMessage,
  }) {
    return ReviewIntervalsState(
      isLoading: isLoading ?? this.isLoading,
      configs: configs ?? this.configs,
      errorMessage: errorMessage,
    );
  }
}

/// 复习间隔配置 Notifier。
class ReviewIntervalsNotifier extends StateNotifier<ReviewIntervalsState> {
  ReviewIntervalsNotifier(this._repository)
    : super(ReviewIntervalsState.initial());

  final SettingsRepository _repository;

  /// 校验配置合法性。
  ///
  /// 参数：
  /// - [sortedConfigs] 已按 round 升序排序的配置列表
  ///
  /// 异常：
  /// - 不满足规则时抛出 [ArgumentError]，用于阻止持久化错误配置
  void _validateConfigs(List<ReviewIntervalConfigEntity> sortedConfigs) {
    if (sortedConfigs.isEmpty) {
      throw ArgumentError('复习配置不能为空');
    }

    // 规则 1：round 必须从 1 开始连续，且不可重复。
    for (var i = 0; i < sortedConfigs.length; i++) {
      final expectedRound = i + 1;
      final actualRound = sortedConfigs[i].round;
      if (actualRound != expectedRound) {
        throw ArgumentError('轮次编号必须从 1 开始连续（当前发现第 $expectedRound 轮缺失或重复）');
      }
    }

    // 规则 2：至少保留一轮启用。
    final enabled = sortedConfigs.where((e) => e.enabled).toList();
    if (enabled.isEmpty) {
      throw ArgumentError('至少保留一轮复习');
    }

    // 规则 3：后一次复习必须更晚（以学习日为基准的 intervalDays 需递增）。
    ReviewIntervalConfigEntity? prevEnabled;
    for (final c in sortedConfigs) {
      if (!c.enabled) continue;
      final prev = prevEnabled;
      if (prev != null && c.intervalDays <= prev.intervalDays) {
        throw ArgumentError(
          '第${c.round}轮复习需晚于第${prev.round}轮（间隔天数需递增）',
        );
      }
      prevEnabled = c;
    }
  }

  /// 加载配置。
  Future<void> load() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final configs = await _repository.getReviewIntervalConfigs();
      state = state.copyWith(isLoading: false, configs: configs);
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 保存配置（并持久化到设置表）。
  Future<void> save(List<ReviewIntervalConfigEntity> configs) async {
    final next = [...configs]..sort((a, b) => a.round.compareTo(b.round));
    _validateConfigs(next);

    state = state.copyWith(
      isLoading: true,
      errorMessage: null,
      configs: next,
    );
    try {
      await _repository.saveReviewIntervalConfigs(next);
      state = state.copyWith(isLoading: false, configs: next);
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
      rethrow;
    }
  }

  /// 更新指定轮次配置并保存。
  Future<void> updateRound(
    int round, {
    int? intervalDays,
    bool? enabled,
  }) async {
    final next = state.configs.map((c) {
      if (c.round != round) return c;
      return c.copyWith(
        intervalDays: intervalDays ?? c.intervalDays,
        enabled: enabled ?? c.enabled,
      );
    }).toList();

    // 保护：至少保留一轮复习。
    if (!next.any((e) => e.enabled)) {
      throw ArgumentError('至少保留一轮复习');
    }

    await save(next);
  }

  /// 恢复默认（艾宾浩斯）。
  Future<void> resetDefault() async {
    final defaults = EbbinghausUtils.defaultIntervalsDays;
    final next = List<ReviewIntervalConfigEntity>.generate(
      defaults.length,
      (index) => ReviewIntervalConfigEntity(
        round: index + 1,
        intervalDays: defaults[index],
        enabled: true,
      ),
    );
    await save(next);
  }

  /// 启用全部轮次。
  Future<void> enableAll() async {
    final next = state.configs.map((c) => c.copyWith(enabled: true)).toList();
    await save(next);
  }
}

/// 复习间隔配置 Provider。
final reviewIntervalsProvider =
    StateNotifierProvider<ReviewIntervalsNotifier, ReviewIntervalsState>((ref) {
      final repo = ref.read(settingsRepositoryProvider);
      final notifier = ReviewIntervalsNotifier(repo);
      notifier.load();
      return notifier;
    });
