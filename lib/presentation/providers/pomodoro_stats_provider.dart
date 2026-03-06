/// 文件用途：番茄钟统计状态管理（Riverpod StateNotifier）。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../domain/entities/pomodoro_stats.dart';

/// 番茄钟统计状态。
class PomodoroStatsState {
  /// 构造函数。
  const PomodoroStatsState({
    required this.isLoading,
    required this.stats,
    this.errorMessage,
  });

  /// 是否正在加载。
  final bool isLoading;

  /// 统计数据。
  final PomodoroStatsEntity stats;

  /// 错误信息。
  final String? errorMessage;

  /// 初始状态。
  factory PomodoroStatsState.initial() =>
      const PomodoroStatsState(isLoading: true, stats: PomodoroStatsEntity.empty);

  /// 复制状态。
  PomodoroStatsState copyWith({
    bool? isLoading,
    PomodoroStatsEntity? stats,
    String? errorMessage,
  }) {
    return PomodoroStatsState(
      isLoading: isLoading ?? this.isLoading,
      stats: stats ?? this.stats,
      errorMessage: errorMessage,
    );
  }
}

/// 番茄钟统计 Notifier。
class PomodoroStatsNotifier extends StateNotifier<PomodoroStatsState> {
  /// 构造函数。
  PomodoroStatsNotifier(this._ref) : super(PomodoroStatsState.initial()) {
    load();
  }

  final Ref _ref;

  /// 读取番茄钟统计数据。
  Future<void> load() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final repo = _ref.read(pomodoroRepositoryProvider);
      final stats = await repo.getStats();
      state = state.copyWith(isLoading: false, stats: stats);
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }
}

/// 番茄钟统计 Provider。
final pomodoroStatsProvider =
    StateNotifierProvider<PomodoroStatsNotifier, PomodoroStatsState>((ref) {
      return PomodoroStatsNotifier(ref);
    });
