/// 文件用途：复习计划预览面板（ReviewPreviewPanel），支持展开/收起、间隔调整与启用开关（F1.5）。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/ebbinghaus_utils.dart';
import '../../domain/entities/review_interval_config.dart';
import '../providers/review_intervals_provider.dart';
import 'glass_card.dart';

class ReviewPreviewPanel extends ConsumerStatefulWidget {
  /// 复习计划预览面板。
  ///
  /// 参数：
  /// - [learningDate] 学习日期（用于计算预览日期，按年月日）
  /// - [onUnsavedChangesChanged] 未保存状态变化回调（用于父页面拦截返回）
  const ReviewPreviewPanel({
    super.key,
    required this.learningDate,
    this.onUnsavedChangesChanged,
  });

  final DateTime learningDate;
  final ValueChanged<bool>? onUnsavedChangesChanged;

  @override
  ConsumerState<ReviewPreviewPanel> createState() => _ReviewPreviewPanelState();
}

class _ReviewPreviewPanelState extends ConsumerState<ReviewPreviewPanel> {
  static const int _maxRounds = 10;
  static const int _maxIntervalDays = 180;

  bool _expanded = false;
  bool _hasUnsavedChanges = false;
  List<ReviewIntervalConfigEntity> _draft = const [];

  /// 当前配置中的最大轮次。
  int get _maxRound =>
      _draft.fold<int>(0, (prev, e) => e.round > prev ? e.round : prev);

  /// 是否允许增加轮次（最大 10 轮）。
  bool get _canAddRound => _maxRound < _maxRounds;

  /// 是否允许减少轮次（至少保留 1 轮启用）。
  bool get _canRemoveRound => _draft.where((e) => e.enabled).length > 1;

  /// 校验草稿配置是否符合基本规则。
  ///
  /// 返回值：
  /// - `null` 表示合法
  /// - 非空字符串表示错误提示（用于 UI 展示/保存前拦截）
  String? _validateDraft() {
    if (_draft.isEmpty) return '复习配置为空，请点击“恢复默认”后再保存';

    // 保护：至少保留一轮复习（仅检查 enabled=true）。
    final enabled = _draft.where((e) => e.enabled).toList()
      ..sort((a, b) => a.round.compareTo(b.round));
    if (enabled.isEmpty) return '至少保留一轮复习';

    // 规则：后一次复习需要比前一次更晚（间隔天数需递增）。
    for (var i = 1; i < enabled.length; i++) {
      final prev = enabled[i - 1];
      final curr = enabled[i];
      if (curr.intervalDays <= prev.intervalDays) {
        return '第${curr.round}轮复习需晚于第${prev.round}轮（间隔天数需递增）';
      }
    }

    return null;
  }

  /// 更新草稿并按需标记未保存状态。
  ///
  /// 参数：
  /// - [next] 下一版草稿列表
  /// - [markUnsaved] 是否将状态标记为“未保存”
  void _setDraft(
    List<ReviewIntervalConfigEntity> next, {
    required bool markUnsaved,
  }) {
    final shouldNotify = markUnsaved && !_hasUnsavedChanges;
    setState(() {
      _draft = next;
      if (markUnsaved) _hasUnsavedChanges = true;
    });
    if (shouldNotify) widget.onUnsavedChangesChanged?.call(true);
  }

  /// 构建默认配置草稿（艾宾浩斯）。
  ///
  /// 返回值：按 round 递增排列的配置列表（最多 10 轮）。
  List<ReviewIntervalConfigEntity> _buildDefaultDraft() {
    final defaults = EbbinghausUtils.defaultIntervalsDays.take(_maxRounds);
    var round = 0;
    return defaults
        .map((days) {
          round++;
          return ReviewIntervalConfigEntity(
            round: round,
            intervalDays: days,
            enabled: true,
          );
        })
        .toList();
  }

  /// 增加一轮复习（末尾追加，间隔约为前一轮的 2 倍）。
  void _addRound() {
    if (!_canAddRound) return;
    if (_draft.isEmpty) return;

    final maxRound = _maxRound;
    final last =
        _draft.firstWhere((e) => e.round == maxRound, orElse: () => _draft.last);
    final nextRound = maxRound + 1;

    // 约 2 倍：与需求保持一致，同时上限收敛到 Slider 的可展示范围。
    final nextIntervalDays =
        (last.intervalDays * 2).clamp(1, _maxIntervalDays).toInt();

    _setDraft(
      [
        ..._draft,
        ReviewIntervalConfigEntity(
          round: nextRound,
          intervalDays: nextIntervalDays,
          enabled: true,
        ),
      ]..sort((a, b) => a.round.compareTo(b.round)),
      markUnsaved: true,
    );
  }

  /// 减少一轮复习：移除最后一个启用的轮次，并保持轮次编号连续。
  void _removeRound() {
    if (!_canRemoveRound) return;

    final lastEnabledIndex = _draft.lastIndexWhere((e) => e.enabled);
    if (lastEnabledIndex < 0) return;

    final next = <ReviewIntervalConfigEntity>[];
    for (var i = 0; i < _draft.length; i++) {
      if (i == lastEnabledIndex) continue;
      next.add(_draft[i]);
    }

    // 重新编号：避免出现 round 缺口，确保 UI 与保存数据一致。
    final normalized = <ReviewIntervalConfigEntity>[];
    for (var i = 0; i < next.length; i++) {
      normalized.add(next[i].copyWith(round: i + 1));
    }

    _setDraft(normalized, markUnsaved: true);
  }

  /// 显式保存用户修改的草稿配置。
  ///
  /// 说明：仅在存在未保存更改时触发持久化，避免频繁刷新。
  Future<void> _saveChanges(ReviewIntervalsNotifier notifier) async {
    if (!_hasUnsavedChanges) return;

    final validationMessage = _validateDraft();
    if (validationMessage != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(validationMessage)),
      );
      return;
    }

    try {
      await notifier.save(_draft);
      if (!mounted) return;
      setState(() => _hasUnsavedChanges = false);
      widget.onUnsavedChangesChanged?.call(false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('复习配置已保存'),
          duration: Duration(seconds: 2),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('保存失败：$e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(reviewIntervalsProvider);
    final notifier = ref.read(reviewIntervalsProvider.notifier);

    if (!state.isLoading && _draft.isEmpty && state.configs.isNotEmpty) {
      // 首次同步：避免每次 build 覆盖用户正在拖动的本地草稿。
      _draft = [...state.configs];
    }

    Widget header() {
      return InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => setState(() => _expanded = !_expanded),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Row(
            children: [
              Icon(
                _expanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(child: Text('复习计划预览', style: AppTypography.h2(context))),
              Text('默认复习间隔', style: AppTypography.bodySecondary(context)),
            ],
          ),
        ),
      );
    }

    if (state.isLoading) {
      return GlassCard(
        child: Column(
          children: [
            header(),
            if (_expanded)
              const Padding(
                padding: EdgeInsets.only(bottom: AppSpacing.lg),
                child: Center(child: CircularProgressIndicator()),
              ),
          ],
        ),
      );
    }

    final enabledCount = _draft.where((e) => e.enabled).length;
    final validationMessage = _validateDraft();

    return GlassCard(
      child: Column(
        children: [
          header(),
          AnimatedCrossFade(
            crossFadeState: _expanded
                ? CrossFadeState.showSecond
                : CrossFadeState.showFirst,
            duration: const Duration(milliseconds: 200),
            firstChild: const SizedBox.shrink(),
            secondChild: Padding(
              padding: const EdgeInsets.fromLTRB(
                AppSpacing.lg,
                0,
                AppSpacing.lg,
                AppSpacing.lg,
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '已启用 $enabledCount / ${_draft.length} 轮',
                    style: AppTypography.bodySecondary(context),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  Wrap(
                    spacing: AppSpacing.sm,
                    runSpacing: AppSpacing.sm,
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      OutlinedButton.icon(
                        onPressed: _canRemoveRound ? _removeRound : null,
                        icon: const Icon(Icons.remove, size: 18),
                        label: const Text('减少轮次'),
                      ),
                      OutlinedButton.icon(
                        onPressed: _canAddRound ? _addRound : null,
                        icon: const Icon(Icons.add, size: 18),
                        label: const Text('增加轮次'),
                      ),
                      if (_hasUnsavedChanges)
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.warning.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(999),
                          ),
                          child: Text(
                            '有未保存更改',
                            style: AppTypography.bodySecondary(
                              context,
                            ).copyWith(color: AppColors.warning),
                          ),
                        ),
                    ],
                  ),
                  if (!_canRemoveRound)
                    Padding(
                      padding: const EdgeInsets.only(top: AppSpacing.sm),
                      child: Text(
                        '至少保留一轮复习',
                        style: AppTypography.bodySecondary(
                          context,
                        ).copyWith(color: AppColors.textSecondary),
                      ),
                    ),
                  if (validationMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(top: AppSpacing.sm),
                      child: Text(
                        validationMessage,
                        style: AppTypography.bodySecondary(
                          context,
                        ).copyWith(color: AppColors.error),
                      ),
                    ),
                  const SizedBox(height: AppSpacing.md),
                  ..._draft.map(
                    (c) => _RoundTile(
                      config: c,
                      learningDate: widget.learningDate,
                      onToggle: (v) {
                        final next = _draft
                            .map(
                              (e) => e.round == c.round
                                  ? e.copyWith(enabled: v)
                                  : e,
                            )
                            .toList();
                        if (!next.any((e) => e.enabled)) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            const SnackBar(content: Text('至少保留一轮复习')),
                          );
                          return;
                        }
                        _setDraft(next, markUnsaved: true);
                      },
                      onIntervalChanged: (value) {
                        final next = _draft
                            .map(
                              (e) => e.round == c.round
                                  ? e.copyWith(intervalDays: value)
                                  : e,
                            )
                            .toList();
                        _setDraft(next, markUnsaved: true);
                      },
                    ),
                  ),
                  const SizedBox(height: AppSpacing.md),
                  if (state.errorMessage != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                      child: Text(
                        '保存失败：${state.errorMessage}',
                        style: AppTypography.bodySecondary(
                          context,
                        ).copyWith(color: AppColors.error),
                      ),
                    ),
                  Row(
                    children: [
                      TextButton(
                        onPressed: () {
                          _setDraft(_buildDefaultDraft(), markUnsaved: true);
                        },
                        child: const Text('恢复默认'),
                      ),
                      const SizedBox(width: AppSpacing.sm),
                      TextButton(
                        onPressed: () {
                          final next = _draft
                              .map((c) => c.copyWith(enabled: true))
                              .toList();
                          _setDraft(next, markUnsaved: true);
                        },
                        child: const Text('启用全部'),
                      ),
                      const Spacer(),
                      FilledButton(
                        onPressed:
                            _hasUnsavedChanges &&
                                    validationMessage == null &&
                                    !state.isLoading
                                ? () => _saveChanges(notifier)
                                : null,
                        child: const Text('确认保存'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _RoundTile extends StatelessWidget {
  const _RoundTile({
    required this.config,
    required this.learningDate,
    required this.onToggle,
    required this.onIntervalChanged,
  });

  final ReviewIntervalConfigEntity config;
  final DateTime learningDate;
  final ValueChanged<bool> onToggle;
  final ValueChanged<int> onIntervalChanged;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final borderColor = isDark
        ? AppColors.darkGlassBorder
        : AppColors.glassBorder;

    final date = DateTime(
      learningDate.year,
      learningDate.month,
      learningDate.day,
    ).add(Duration(days: config.intervalDays));
    final dateText =
        '${date.month.toString().padLeft(2, '0')}-${date.day.toString().padLeft(2, '0')}';

    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Container(
        decoration: BoxDecoration(
          border: Border.all(color: borderColor),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      '第${config.round}次复习  +${config.intervalDays}天  $dateText',
                      style: AppTypography.body(context),
                    ),
                  ),
                  Switch(value: config.enabled, onChanged: onToggle),
                ],
              ),
              Slider(
                min: 1,
                max: 180,
                divisions: 179,
                value: config.intervalDays.toDouble(),
                label: '${config.intervalDays}',
                onChanged: config.enabled
                    ? (v) => onIntervalChanged(v.round())
                    : null,
              ),
              if (config.intervalDays > 30)
                Text(
                  '间隔过长可能导致遗忘',
                  style: AppTypography.bodySecondary(
                    context,
                  ).copyWith(color: AppColors.warning),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
