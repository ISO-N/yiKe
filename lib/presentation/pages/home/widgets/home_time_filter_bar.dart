/// 文件用途：首页“全部”标签页时间筛选栏组件（全部/今天前/今天后）。
/// 作者：Codex
/// 创建日期：2026-03-05
library;

import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_spacing.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../providers/home_time_filter_provider.dart';
import '../../../widgets/glass_card.dart';

/// 首页时间筛选栏。
class HomeTimeFilterBar extends StatelessWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [filter] 当前时间筛选值
  /// - [onChanged] 切换筛选回调
  const HomeTimeFilterBar({
    super.key,
    required this.filter,
    required this.onChanged,
  });

  final HomeTimeFilter filter;
  final ValueChanged<HomeTimeFilter> onChanged;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '时间筛选',
              style: AppTypography.h2(context).copyWith(fontSize: 16),
            ),
            const SizedBox(height: AppSpacing.sm),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _Chip(
                    label: '全部',
                    icon: Icons.all_inclusive_rounded,
                    selected: filter == HomeTimeFilter.all,
                    selectedColor: primary,
                    onTap: () => onChanged(HomeTimeFilter.all),
                  ),
                  const SizedBox(width: 10),
                  _Chip(
                    label: '今天前',
                    icon: Icons.history_rounded,
                    selected: filter == HomeTimeFilter.beforeToday,
                    selectedColor: primary,
                    onTap: () => onChanged(HomeTimeFilter.beforeToday),
                  ),
                  const SizedBox(width: 10),
                  _Chip(
                    label: '今天后',
                    icon: Icons.upcoming_rounded,
                    selected: filter == HomeTimeFilter.afterToday,
                    selectedColor: primary,
                    onTap: () => onChanged(HomeTimeFilter.afterToday),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Chip extends StatelessWidget {
  const _Chip({
    required this.label,
    required this.icon,
    required this.selected,
    required this.selectedColor,
    required this.onTap,
  });

  final String label;
  final IconData icon;
  final bool selected;
  final Color selectedColor;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final bg = selected ? selectedColor.withAlpha(28) : Colors.transparent;
    final border = selected
        ? selectedColor.withAlpha(110)
        : Theme.of(context).dividerColor;
    final textColor = selected
        ? selectedColor
        : (Theme.of(context).textTheme.bodyMedium?.color ?? Colors.black);

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(999),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: border),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 18, color: textColor),
            const SizedBox(width: 8),
            Text(
              label,
              style: AppTypography.body(context).copyWith(
                fontSize: 13,
                fontWeight: FontWeight.w700,
                color: textColor,
              ),
            ),
          ],
        ),
      ),
    );
  }
}
