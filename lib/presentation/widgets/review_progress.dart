/// 文件用途：首页复习进度展示组件（v3.1 F14.3），包含环形进度与可展开统计信息。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'dart:ui';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../providers/statistics_provider.dart';
import '../providers/today_progress_provider.dart';
import 'glass_card.dart';

/// 首页复习进度组件。
class ReviewProgressWidget extends ConsumerStatefulWidget {
  /// 构造函数。
  ///
  /// 返回值：组件 Widget。
  /// 异常：无。
  const ReviewProgressWidget({super.key});

  @override
  ConsumerState<ReviewProgressWidget> createState() =>
      _ReviewProgressWidgetState();
}

class _ReviewProgressWidgetState extends ConsumerState<ReviewProgressWidget> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final todayAsync = ref.watch(todayProgressProvider);
    final stats = ref.watch(statisticsProvider);

    return GlassCard(
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: () => setState(() => _expanded = !_expanded),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text('今日复习进度', style: AppTypography.h2(context)),
                  ),
                  Icon(
                    _expanded ? Icons.expand_less : Icons.info_outline,
                    size: 20,
                    color: Theme.of(context).hintColor,
                  ),
                ],
              ),
              const SizedBox(height: AppSpacing.md),
              todayAsync.when(
                loading: () => const _ProgressMain(
                  completed: 0,
                  total: 0,
                  showLoading: true,
                ),
                error: (e, _) => Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const _ProgressMain(completed: 0, total: 0),
                    const SizedBox(height: AppSpacing.sm),
                    Text(
                      '加载失败：$e',
                      style: const TextStyle(color: AppColors.error),
                    ),
                  ],
                ),
                data: (t) => _ProgressMain(
                  completed: t.$1,
                  total: t.$2,
                  expanded: _expanded,
                  stats: stats,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ProgressMain extends StatelessWidget {
  const _ProgressMain({
    required this.completed,
    required this.total,
    this.expanded = false,
    this.showLoading = false,
    this.stats,
  });

  final int completed;
  final int total;
  final bool expanded;
  final bool showLoading;
  final StatisticsState? stats;

  @override
  Widget build(BuildContext context) {
    // 为满足 v1.1.0「进度环动画增强」，将主进度区域拆分为可维护前后值的内部组件。
    return _AnimatedProgressMain(
      completed: completed,
      total: total,
      expanded: expanded,
      showLoading: showLoading,
      stats: stats,
    );
  }
}

class _AnimatedProgressMain extends StatefulWidget {
  const _AnimatedProgressMain({
    required this.completed,
    required this.total,
    required this.expanded,
    required this.showLoading,
    required this.stats,
  });

  final int completed;
  final int total;
  final bool expanded;
  final bool showLoading;
  final StatisticsState? stats;

  @override
  State<_AnimatedProgressMain> createState() => _AnimatedProgressMainState();
}

class _AnimatedProgressMainState extends State<_AnimatedProgressMain> {
  // v1.1.0：首次从“无数据→有数据”不动画，因此需要记录是否已完成首次加载。
  bool _hasLoadedOnce = false;

  // 用于同步动画的起始值（由 didUpdateWidget 赋值为“上一帧 end”）。
  double? _fromProgress;
  int? _fromCompleted;
  int? _fromTotal;

  @override
  void didUpdateWidget(covariant _AnimatedProgressMain oldWidget) {
    super.didUpdateWidget(oldWidget);

    // loading → data：首次展示不动画（起始值直接等于目标值）。
    if (!_hasLoadedOnce && !widget.showLoading) {
      return;
    }

    // 仅在数值发生变化时记录上一帧值，作为下一次动画的 begin。
    if (oldWidget.completed != widget.completed ||
        oldWidget.total != widget.total) {
      _fromCompleted = oldWidget.completed;
      _fromTotal = oldWidget.total;
      final oldProgress = oldWidget.total <= 0
          ? 0.0
          : (oldWidget.completed / oldWidget.total).clamp(0.0, 1.0);
      _fromProgress = oldProgress;
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = isDark ? AppColors.primaryLight : AppColors.primary;

    final ringColor = _ringColor(
      primary: primary,
      completed: widget.completed,
      total: widget.total,
    );
    final progress = widget.total <= 0
        ? 0.0
        : (widget.completed / widget.total).clamp(0.0, 1.0);

    final disableAnimations = MediaQuery.of(context).disableAnimations;

    // 首次加载：从“无数据”到“有数据”不动画，避免造成误导性反馈。
    if (!_hasLoadedOnce && !widget.showLoading) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        setState(() {
          _hasLoadedOnce = true;
          _fromCompleted = widget.completed;
          _fromTotal = widget.total;
          _fromProgress = progress;
        });
      });
    }

    final shouldAnimate =
        !disableAnimations && _hasLoadedOnce && !widget.showLoading;
    final duration = shouldAnimate
        ? const Duration(milliseconds: 300)
        : Duration.zero;

    final fromCompleted = _fromCompleted ?? widget.completed;
    final fromTotal = _fromTotal ?? widget.total;
    final fromProgress = _fromProgress ?? progress;

    return Column(
      children: [
        TweenAnimationBuilder<double>(
          tween: Tween<double>(begin: 0, end: 1),
          duration: duration,
          curve: Curves.easeOutCubic,
          builder: (context, t, _) {
            final animatedProgress = lerpDouble(
              fromProgress,
              progress,
              t,
            )!.clamp(0.0, 1.0);
            final animatedCompleted =
                (fromCompleted + (widget.completed - fromCompleted) * t)
                    .round();
            final animatedTotal = (fromTotal + (widget.total - fromTotal) * t)
                .round();
            final percentText = (animatedProgress * 100).toStringAsFixed(0);

            return Row(
              children: [
                RepaintBoundary(
                  // 关键逻辑：使用“内圆 + 自适应文字”的组合，避免在字体缩放/100% 等情况下
                  // 百分比文字侵入圆环内侧边缘，导致视觉上“内圆被文字遮挡/环被切掉”的问题。
                  child: _DonutProgressRing(
                    progress: animatedProgress,
                    ringColor: ringColor,
                    label: '$percentText%',
                    size: 80,
                    strokeWidth: 8,
                  ),
                ),
                const SizedBox(width: AppSpacing.lg),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '$animatedCompleted / $animatedTotal',
                        style: AppTypography.h2(context).copyWith(fontSize: 24),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        _statusText(
                          completed: animatedCompleted,
                          total: animatedTotal,
                        ),
                        style: AppTypography.bodySecondary(context),
                      ),
                    ],
                  ),
                ),
                if (widget.showLoading)
                  const Padding(
                    padding: EdgeInsets.only(left: AppSpacing.sm),
                    child: SizedBox(
                      width: 18,
                      height: 18,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  ),
              ],
            );
          },
        ),
        if (widget.expanded) ...[
          const SizedBox(height: AppSpacing.lg),
          const Divider(height: 1),
          const SizedBox(height: AppSpacing.lg),
          _DetailRows(
            completed: widget.completed,
            total: widget.total,
            stats: widget.stats,
          ),
        ],
      ],
    );
  }

  Color _ringColor({
    required Color primary,
    required int completed,
    required int total,
  }) {
    // 状态判断：先判断超额完成（预留扩展），再判断已完成/进行中/未开始。
    if (total > 0 && completed > total) return AppColors.cta;
    if (total > 0 && completed >= total) return AppColors.success;
    if (completed > 0 && completed < total) return primary;
    return const Color(0xFF9CA3AF); // Gray
  }

  String _statusText({required int completed, required int total}) {
    if (total <= 0 || completed <= 0) return '未开始';
    if (completed >= total) return '已完成';
    return '进行中';
  }
}

/// 环形进度（甜甜圈样式）组件：用于在固定尺寸内展示进度环、内圆与居中文字。
///
/// 设计目标：
/// - 文字必须始终落在“内圆”可用区域内，不覆盖圆环的内侧边缘；
/// - 支持系统字体缩放（无障碍字号）与 `100%` 等宽度更大的文本；
/// - 内圆提供稳定背景，避免毛玻璃/渐变背景下中心文字可读性下降。
class _DonutProgressRing extends StatelessWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [progress] 进度值（0~1）。
  /// - [ringColor] 圆环颜色。
  /// - [label] 圆环中心文字（例如 `75%`）。
  /// - [size] 组件尺寸（宽高相等）。
  /// - [strokeWidth] 圆环描边宽度。
  /// 返回值：Widget。
  /// 异常：无（内部会对 [progress] 做安全 clamp）。
  const _DonutProgressRing({
    required this.progress,
    required this.ringColor,
    required this.label,
    required this.size,
    required this.strokeWidth,
  });

  final double progress;
  final Color ringColor;
  final String label;
  final double size;
  final double strokeWidth;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    // 关键逻辑：内圆底色与 GlassCard 保持一致，但略微提高不透明度，
    // 用于提升文字可读性，并形成明确的“内圆”视觉层次。
    final innerFillColor = (isDark ? AppColors.darkGlassSurface : AppColors.glassSurface)
        .withValues(alpha: isDark ? 0.90 : 0.92);

    // 关键逻辑：使用自绘（CustomPainter）替代 CircularProgressIndicator + Stack。
    // 原因：在 Android “粗体文本”/字体度量变化时，Stack 的中心文字即便不 overflow，
    // 也可能视觉上贴到圆环甚至压到圆环上；自绘可根据文字真实尺寸动态缩放，严格保证留白。
    return SizedBox.square(
      dimension: size,
      child: RepaintBoundary(
        child: CustomPaint(
          painter: _DonutProgressRingPainter(
            progress: progress,
            ringColor: ringColor,
            trackColor: ringColor.withValues(alpha: isDark ? 0.22 : 0.16),
            innerFillColor: innerFillColor,
            label: label,
            strokeWidth: strokeWidth,
          ),
        ),
      ),
    );
  }
}

/// 甜甜圈进度环绘制器：负责绘制底轨、进度弧、内圆与自适应居中文字。
///
/// 关键点：
/// - 通过 `TextPainter` 获取文字真实尺寸，并计算缩放比例，保证文字永不侵入圆环区域；
/// - 内圆半径明确小于圆环内侧边缘，避免“把环盖淡/盖薄”的问题（用户反馈的深色模式变淡）。
class _DonutProgressRingPainter extends CustomPainter {
  /// 构造函数。
  ///
  /// 参数：
  /// - [progress] 进度 0~1。
  /// - [ringColor] 进度弧颜色。
  /// - [trackColor] 底轨颜色。
  /// - [innerFillColor] 内圆填充色。
  /// - [label] 居中显示文本。
  /// - [strokeWidth] 圆环宽度。
  /// 返回值：Painter。
  /// 异常：无（内部会 clamp progress）。
  _DonutProgressRingPainter({
    required this.progress,
    required this.ringColor,
    required this.trackColor,
    required this.innerFillColor,
    required this.label,
    required this.strokeWidth,
  });

  final double progress;
  final Color ringColor;
  final Color trackColor;
  final Color innerFillColor;
  final String label;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    final safeProgress = progress.isNaN ? 0.0 : progress.clamp(0.0, 1.0);

    final center = Offset(size.width / 2, size.height / 2);

    // 关键逻辑：半径使用 “外边界 - 半个描边”，确保描边不会超出绘制区域。
    final ringRadius = math.min(size.width, size.height) / 2 - strokeWidth / 2;

    final ringPaintBase = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..strokeCap = StrokeCap.round
      ..isAntiAlias = true;

    // 1) 底轨圆环（不随进度变化）
    final trackPaint = ringPaintBase..color = trackColor;
    canvas.drawCircle(center, ringRadius, trackPaint);

    // 2) 进度弧（从 12 点方向开始顺时针）
    if (safeProgress > 0) {
      final progressPaint = ringPaintBase..color = ringColor;
      final sweepAngle = 2 * math.pi * safeProgress;
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: ringRadius),
        -math.pi / 2,
        sweepAngle,
        false,
        progressPaint,
      );
    }

    // 3) 内圆：严格小于圆环内侧边缘，避免覆盖进度环导致“变淡/变薄”。
    // 圆环内侧半径约为 ringRadius - strokeWidth / 2；
    // 再减去 1.5px 作为抗锯齿缓冲，保证边缘干净。
    final innerRadius = (ringRadius - strokeWidth / 2 - 1.5).clamp(0.0, ringRadius);
    final innerPaint = Paint()
      ..style = PaintingStyle.fill
      ..color = innerFillColor
      ..isAntiAlias = true;
    canvas.drawCircle(center, innerRadius, innerPaint);

    // 4) 文字：测量真实尺寸后按需缩放，确保始终落在内圆安全区内。
    // 关键逻辑：在“粗体文本”场景下，字形外扩更明显，因此安全边距取稍大值。
    final textSafePadding = 6.0;
    final maxTextDiameter = (innerRadius * 2 - textSafePadding * 2).clamp(0.0, size.shortestSide);

    if (maxTextDiameter <= 0) return;

    final textStyle = TextStyle(
      fontSize: 18,
      fontWeight: FontWeight.w800,
      color: ringColor,
      height: 1.0, // 关键逻辑：减少行高带来的额外占用，降低贴边概率。
    );

    final textPainter = TextPainter(
      text: TextSpan(text: label, style: textStyle),
      textAlign: TextAlign.center,
      textDirection: TextDirection.ltr,
      maxLines: 1,
      ellipsis: null,
    )..layout();

    final textWidth = textPainter.width;
    final textHeight = textPainter.height;
    final textMaxSide = math.max(textWidth, textHeight);

    // 关键逻辑：仅在需要时缩小，避免小数字看起来过小。
    final scale = textMaxSide <= 0 ? 1.0 : math.min(1.0, maxTextDiameter / textMaxSide);

    canvas.save();
    canvas.translate(center.dx, center.dy);
    canvas.scale(scale, scale);
    final offset = Offset(-textPainter.width / 2, -textPainter.height / 2);
    textPainter.paint(canvas, offset);
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _DonutProgressRingPainter oldDelegate) {
    // 关键逻辑：任一输入变化都需要重绘。
    return oldDelegate.progress != progress ||
        oldDelegate.ringColor != ringColor ||
        oldDelegate.trackColor != trackColor ||
        oldDelegate.innerFillColor != innerFillColor ||
        oldDelegate.label != label ||
        oldDelegate.strokeWidth != strokeWidth;
  }
}

class _DetailRows extends StatelessWidget {
  const _DetailRows({
    required this.completed,
    required this.total,
    required this.stats,
  });

  final int completed;
  final int total;
  final StatisticsState? stats;

  @override
  Widget build(BuildContext context) {
    final week = stats;
    final isLoading = week == null || week.isLoading;

    final weekCompleted = week?.weekCompleted ?? 0;
    final weekTotal = week?.weekTotal ?? 0;
    final monthCompleted = week?.monthCompleted ?? 0;
    final monthTotal = week?.monthTotal ?? 0;
    final streak = week?.consecutiveCompletedDays ?? 0;

    return Column(
      children: [
        _Row(title: '今日', value: _ratioText(completed, total)),
        const SizedBox(height: 8),
        _Row(
          title: '本周',
          value: isLoading ? '加载中…' : _ratioText(weekCompleted, weekTotal),
        ),
        const SizedBox(height: 8),
        _Row(
          title: '本月',
          value: isLoading ? '加载中…' : _ratioText(monthCompleted, monthTotal),
        ),
        const SizedBox(height: 8),
        _Row(title: '连续学习', value: isLoading ? '加载中…' : '$streak 天'),
      ],
    );
  }

  String _ratioText(int completed, int total) {
    if (total <= 0) return '$completed/$total (0%)';
    final p = (completed / total * 100).clamp(0, 999).toStringAsFixed(0);
    return '$completed/$total ($p%)';
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.title, required this.value});

  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(title, style: AppTypography.bodySecondary(context)),
        ),
        Text(
          value,
          style: AppTypography.body(
            context,
          ).copyWith(fontWeight: FontWeight.w700),
        ),
      ],
    );
  }
}
