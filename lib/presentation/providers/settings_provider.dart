/// 文件用途：设置状态管理（Riverpod StateNotifier），用于读取/保存通知与免打扰等配置。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../di/providers.dart';
import '../../domain/entities/app_settings.dart';
import '../../domain/repositories/settings_repository.dart';

/// 设置页状态。
class SettingsState {
  const SettingsState({
    required this.isLoading,
    required this.settings,
    this.errorMessage,
  });

  final bool isLoading;
  final AppSettingsEntity settings;
  final String? errorMessage;

  factory SettingsState.initial() =>
      SettingsState(isLoading: true, settings: AppSettingsEntity.defaults);

  SettingsState copyWith({
    bool? isLoading,
    AppSettingsEntity? settings,
    String? errorMessage,
  }) {
    return SettingsState(
      isLoading: isLoading ?? this.isLoading,
      settings: settings ?? this.settings,
      errorMessage: errorMessage,
    );
  }
}

/// 设置 Notifier。
class SettingsNotifier extends StateNotifier<SettingsState> {
  SettingsNotifier(this._repository) : super(SettingsState.initial());

  final SettingsRepository _repository;

  /// 加载设置。
  Future<void> load() async {
    if (!mounted) return;
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final settings = await _repository.getSettings();
      if (!mounted) return;
      state = state.copyWith(isLoading: false, settings: settings);
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }

  /// 保存设置（整体覆盖）。
  Future<void> save(AppSettingsEntity settings) async {
    if (!mounted) return;
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      await _repository.saveSettings(settings);
      if (!mounted) return;
      state = state.copyWith(isLoading: false, settings: settings);
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(isLoading: false, errorMessage: e.toString());
    }
  }
}

/// 设置 Provider。
final settingsProvider = StateNotifierProvider<SettingsNotifier, SettingsState>(
  (ref) {
    final repo = ref.read(settingsRepositoryProvider);
    final notifier = SettingsNotifier(repo);
    notifier.load();
    return notifier;
  },
);
