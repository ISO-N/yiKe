/// 文件用途：通用毛玻璃卡片组件（Glassmorphism），用于承载内容区块。
/// 作者：Codex
/// 创建日期：2026-02-25
/// 最后更新：2026-02-26（支持深色模式动态适配）
library;

import 'dart:ui';

import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_visual_tokens.dart';

/// 毛玻璃卡片语义样式。
enum GlassCardStyle {
  /// 默认样式。
  base,

  /// Hero 区块样式。
  hero,

  /// 主要模块样式。
  section,

  /// 平面正文样式。
  plain,

  /// 轻摘要条样式。
  strip,
}

class GlassCard extends StatelessWidget {
  /// 毛玻璃卡片。
  ///
  /// 参数：
  /// - [child] 卡片内容。
  /// - [borderRadius] 圆角。
  /// - [blurSigma] 背景模糊程度。
  /// 返回值：Widget。
  /// 异常：无。
  const GlassCard({
    super.key,
    required this.child,
    this.borderRadius = AppRadii.card,
    this.blurSigma,
    this.style = GlassCardStyle.base,
    this.backgroundColor,
    this.borderColor,
  });

  final Widget child;
  final double borderRadius;
  final double? blurSigma;
  final GlassCardStyle style;
  final Color? backgroundColor;
  final Color? borderColor;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final resolvedBlurSigma = blurSigma ?? _resolvedBlurSigma();
    final resolvedBackground =
        backgroundColor ?? _resolvedBackgroundColor(isDark);
    final resolvedBorder = borderColor ?? _resolvedBorderColor(isDark);

    final decorated = DecoratedBox(
      decoration: BoxDecoration(
        // 关键逻辑：不同语义区块共享同一底层容器，但在视觉重量上分层。
        color: resolvedBackground,
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: resolvedBorder),
        boxShadow: _resolvedShadow(isDark),
      ),
      child: child,
    );

    // 关键逻辑：当 blurSigma <= 0 时不创建 BackdropFilter，避免在大面积内容场景下
    // 引入不必要的离屏渲染与合成开销（桌面端滚动时更明显）。
    if (resolvedBlurSigma <= 0) {
      return ClipRRect(
        borderRadius: BorderRadius.circular(borderRadius),
        child: decorated,
      );
    }

    return ClipRRect(
      borderRadius: BorderRadius.circular(borderRadius),
      child: BackdropFilter(
        filter: ImageFilter.blur(
          sigmaX: resolvedBlurSigma,
          sigmaY: resolvedBlurSigma,
        ),
        child: decorated,
      ),
    );
  }

  double _resolvedBlurSigma() {
    return switch (style) {
      GlassCardStyle.hero => AppBlur.hero,
      GlassCardStyle.section => AppBlur.section,
      GlassCardStyle.plain => AppBlur.none,
      GlassCardStyle.strip => AppBlur.strip,
      GlassCardStyle.base => AppBlur.card,
    };
  }

  Color _resolvedBackgroundColor(bool isDark) {
    return switch (style) {
      GlassCardStyle.hero =>
        isDark
            ? AppColors.darkHeroSurface.withValues(alpha: 0.90)
            : AppColors.heroSurface.withValues(alpha: 0.92),
      GlassCardStyle.section =>
        isDark ? AppColors.darkGlassSurface : AppColors.glassSurface,
      GlassCardStyle.plain =>
        isDark ? AppColors.darkPlainSurface : AppColors.plainSurface,
      GlassCardStyle.strip =>
        isDark
            ? AppColors.darkSurfaceMuted.withValues(alpha: 0.88)
            : AppColors.backgroundElevated.withValues(alpha: 0.86),
      GlassCardStyle.base =>
        isDark ? AppColors.darkGlassSurface : AppColors.glassSurface,
    };
  }

  Color _resolvedBorderColor(bool isDark) {
    return switch (style) {
      GlassCardStyle.hero =>
        isDark
            ? AppColors.primaryLight.withValues(alpha: 0.24)
            : AppColors.primary.withValues(alpha: 0.16),
      GlassCardStyle.section =>
        isDark ? AppColors.darkGlassBorder : AppColors.glassBorder,
      GlassCardStyle.plain =>
        isDark ? AppColors.darkDivider : AppColors.divider,
      GlassCardStyle.strip =>
        isDark
            ? AppColors.darkDivider.withValues(alpha: 0.80)
            : AppColors.divider.withValues(alpha: 0.80),
      GlassCardStyle.base =>
        isDark ? AppColors.darkGlassBorder : AppColors.glassBorder,
    };
  }

  List<BoxShadow> _resolvedShadow(bool isDark) {
    if (isDark) return const [];
    return switch (style) {
      GlassCardStyle.hero => [
        BoxShadow(
          color: AppColors.primary.withValues(alpha: 0.08),
          blurRadius: 24,
          offset: const Offset(0, 10),
        ),
      ],
      GlassCardStyle.section => [
        BoxShadow(
          color: Colors.black.withValues(alpha: 0.04),
          blurRadius: 14,
          offset: const Offset(0, 6),
        ),
      ],
      _ => const [],
    };
  }
}
