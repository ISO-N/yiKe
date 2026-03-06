/// 文件用途：番茄钟配置状态管理（Riverpod StateNotifier）。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../domain/entities/pomodoro_settings.dart';
import '../../domain/repositories/pomodoro_settings_repository.dart';

/// 番茄钟配置页面状态。
class PomodoroSettingsState {
  /// 构造函数。
  const PomodoroSettingsState({
    required this.isLoading,
    required this.settings,
    this.errorMessage,
  });

  /// 是否正在加载或保存。
  final bool isLoading;

  /// 当前番茄钟配置。
  final PomodoroSettingsEntity settings;

  /// 错误提示。
  final String? errorMessage;

  /// 初始状态。
  factory PomodoroSettingsState.initial() => const PomodoroSettingsState(
    isLoading: true,
    settings: PomodoroSettingsEntity.defaults,
  );

  /// 拷贝状态。
  PomodoroSettingsState copyWith({
    bool? isLoading,
    PomodoroSettingsEntity? settings,
    String? errorMessage,
  }) {
    return PomodoroSettingsState(
      isLoading: isLoading ?? this.isLoading,
      settings: settings ?? this.settings,
      errorMessage: errorMessage,
    );
  }
}

/// 番茄钟配置 Notifier。
class PomodoroSettingsNotifier extends StateNotifier<PomodoroSettingsState> {
  /// 构造函数。
  PomodoroSettingsNotifier(this._repository)
    : super(PomodoroSettingsState.initial());

  final PomodoroSettingsRepository _repository;

  /// 加载配置。
  Future<void> load() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final settings = await _repository.getSettings();
      state = state.copyWith(isLoading: false, settings: settings);
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 保存配置。
  ///
  /// 参数：
  /// - [settings] 目标配置。
  Future<void> save(PomodoroSettingsEntity settings) async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      await _repository.saveSettings(settings);
      final reloaded = await _repository.getSettings();
      state = state.copyWith(isLoading: false, settings: reloaded);
    } catch (e) {
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }
}

/// 番茄钟配置 Provider。
final pomodoroSettingsProvider =
    StateNotifierProvider<PomodoroSettingsNotifier, PomodoroSettingsState>((
      ref,
    ) {
      final repo = ref.read(pomodoroSettingsRepositoryProvider);
      final notifier = PomodoroSettingsNotifier(repo);
      notifier.load();
      return notifier;
    });
