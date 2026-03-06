/// 文件用途：提供页面语义区块组件（Hero、Section、Plain、Summary、Action）。
/// 作者：Codex
/// 创建日期：2026-03-06
library;

import 'package:flutter/material.dart';

import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/constants/app_visual_tokens.dart';
import 'glass_card.dart';

/// Hero 级卡片：承载页面首屏主结论与主操作。
class HeroCard extends StatelessWidget {
  /// 构造函数。
  const HeroCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.xl),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      style: GlassCardStyle.hero,
      borderRadius: AppRadii.hero,
      child: Padding(padding: padding, child: child),
    );
  }
}

/// Section 级卡片：承载主要业务模块。
class SectionCard extends StatelessWidget {
  /// 构造函数。
  const SectionCard({
    super.key,
    required this.title,
    required this.child,
    this.subtitle,
    this.trailing,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final String title;
  final String? subtitle;
  final Widget child;
  final Widget? trailing;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      style: GlassCardStyle.section,
      child: Padding(
        padding: padding,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(child: Text(title, style: AppTypography.h2(context))),
                trailing ?? const SizedBox.shrink(),
              ],
            ),
            if (subtitle case final text?) ...[
              const SizedBox(height: AppSpacing.sm),
              Text(text, style: AppTypography.bodySecondary(context)),
            ],
            const SizedBox(height: AppSpacing.lg),
            child,
          ],
        ),
      ),
    );
  }
}

/// Plain 级面板：承载正文列表、表单和图表等内容。
class PlainPanel extends StatelessWidget {
  /// 构造函数。
  const PlainPanel({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(AppSpacing.lg),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      style: GlassCardStyle.plain,
      child: Padding(padding: padding, child: child),
    );
  }
}

/// Summary 级条带：承载轻量摘要、筛选或图例。
class SummaryStrip extends StatelessWidget {
  /// 构造函数。
  const SummaryStrip({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.symmetric(
      horizontal: AppSpacing.lg,
      vertical: AppSpacing.md,
    ),
  });

  final Widget child;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      style: GlassCardStyle.strip,
      borderRadius: AppRadii.soft,
      child: Padding(padding: padding, child: child),
    );
  }
}

/// 动作行：承载次级入口。
class ActionRow extends StatelessWidget {
  /// 构造函数。
  const ActionRow({
    super.key,
    required this.icon,
    required this.title,
    this.subtitle,
    this.trailing,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String? subtitle;
  final Widget? trailing;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      style: GlassCardStyle.plain,
      borderRadius: AppRadii.soft,
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadii.soft),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(
            horizontal: AppSpacing.lg,
            vertical: AppSpacing.md,
          ),
          child: Row(
            children: [
              Icon(icon),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(title, style: AppTypography.body(context)),
                    if (subtitle case final text?) ...[
                      const SizedBox(height: 4),
                      Text(text, style: AppTypography.bodySecondary(context)),
                    ],
                  ],
                ),
              ),
              const SizedBox(width: AppSpacing.md),
              trailing ?? const Icon(Icons.chevron_right),
            ],
          ),
        ),
      ),
    );
  }
}
