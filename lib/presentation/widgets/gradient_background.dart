/// 文件用途：页面渐变背景组件，支持深色模式动态适配。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_visual_tokens.dart';

/// 渐变背景容器。
///
/// 说明：
/// - 浅色模式沿用 v2.0 的柔和渐变
/// - 深色模式使用低饱和度渐变（OLED Optimized）
class GradientBackground extends StatelessWidget {
  /// 构造函数。
  const GradientBackground({super.key, required this.child});

  /// 子组件。
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return DecoratedBox(
      decoration: BoxDecoration(
        gradient: isDark
            ? const LinearGradient(
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
                colors: [
                  AppColors.darkBackground,
                  AppColors.darkSurface,
                  AppColors.darkBackground,
                ],
              )
            : const LinearGradient(
                begin: Alignment.topCenter,
                end: Alignment.bottomCenter,
                colors: [
                  Color(0xFFF4FFFD),
                  AppColors.background,
                  AppColors.backgroundMuted,
                ],
              ),
      ),
      child: Stack(
        fit: StackFit.expand,
        children: [
          IgnorePointer(
            child: Padding(
              padding: const EdgeInsets.all(AppLayoutTokens.pagePadding),
              child: DecoratedBox(
                decoration: BoxDecoration(
                  gradient: RadialGradient(
                    center: Alignment.topLeft,
                    radius: 1.1,
                    colors: isDark
                        ? [
                            AppColors.primaryLight.withValues(alpha: 0.10),
                            Colors.transparent,
                          ]
                        : [
                            AppColors.primary.withValues(alpha: 0.08),
                            Colors.transparent,
                          ],
                  ),
                ),
              ),
            ),
          ),
          child,
        ],
      ),
    );
  }
}
