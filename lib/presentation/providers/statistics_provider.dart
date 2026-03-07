/// 文件用途：学习统计状态管理（F7，Riverpod StateNotifier）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';

/// 统计页面状态。
class StatisticsState {
  /// 构造函数。
  const StatisticsState({
    required this.isLoading,
    required this.consecutiveCompletedDays,
    required this.weekCompleted,
    required this.weekTotal,
    required this.monthCompleted,
    required this.monthTotal,
    required this.tagDistribution,
    this.errorMessage,
  });

  final bool isLoading;
  final int consecutiveCompletedDays;
  final int weekCompleted;
  final int weekTotal;
  final int monthCompleted;
  final int monthTotal;
  final Map<String, int> tagDistribution;
  final String? errorMessage;

  factory StatisticsState.initial() => const StatisticsState(
    isLoading: true,
    consecutiveCompletedDays: 0,
    weekCompleted: 0,
    weekTotal: 0,
    monthCompleted: 0,
    monthTotal: 0,
    tagDistribution: {},
  );

  StatisticsState copyWith({
    bool? isLoading,
    int? consecutiveCompletedDays,
    int? weekCompleted,
    int? weekTotal,
    int? monthCompleted,
    int? monthTotal,
    Map<String, int>? tagDistribution,
    String? errorMessage,
  }) {
    return StatisticsState(
      isLoading: isLoading ?? this.isLoading,
      consecutiveCompletedDays:
          consecutiveCompletedDays ?? this.consecutiveCompletedDays,
      weekCompleted: weekCompleted ?? this.weekCompleted,
      weekTotal: weekTotal ?? this.weekTotal,
      monthCompleted: monthCompleted ?? this.monthCompleted,
      monthTotal: monthTotal ?? this.monthTotal,
      tagDistribution: tagDistribution ?? this.tagDistribution,
      errorMessage: errorMessage,
    );
  }

  double get weekCompletionRate =>
      weekTotal == 0 ? 0 : weekCompleted / weekTotal;

  double get monthCompletionRate =>
      monthTotal == 0 ? 0 : monthCompleted / monthTotal;
}

/// 统计页面 Notifier。
class StatisticsNotifier extends StateNotifier<StatisticsState> {
  StatisticsNotifier(this._ref) : super(StatisticsState.initial()) {
    load();
  }

  final Ref _ref;

  /// 加载统计数据。
  ///
  /// 异常：异常会捕获并写入 [StatisticsState.errorMessage]。
  Future<void> load() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final useCase = _ref.read(getStatisticsUseCaseProvider);
      final result = await useCase.execute();
      // Provider 可能在 await 期间被刷新销毁；此时不再回写旧实例状态。
      if (!mounted) return;
      state = state.copyWith(
        isLoading: false,
        consecutiveCompletedDays: result.consecutiveCompletedDays,
        weekCompleted: result.weekCompleted,
        weekTotal: result.weekTotal,
        monthCompleted: result.monthCompleted,
        monthTotal: result.monthTotal,
        tagDistribution: result.tagDistribution,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }
}

/// 统计页面 Provider。
final statisticsProvider =
    StateNotifierProvider<StatisticsNotifier, StatisticsState>((ref) {
      return StatisticsNotifier(ref);
    });
