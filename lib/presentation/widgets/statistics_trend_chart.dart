/// 文件用途：统计趋势折线图组件（统计增强 P0）——周/月/年视图切换 + tooltip。
/// 作者：Codex
/// 创建日期：2026-03-04
library;

import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';

import '../../core/constants/app_colors.dart';
import '../../core/constants/app_spacing.dart';
import '../../core/constants/app_typography.dart';
import '../../core/utils/date_utils.dart';
import '../../domain/entities/statistics_insights.dart';
import 'glass_card.dart';

/// 统计趋势折线图（完成率）。
///
/// 说明：
/// - 口径：done / (done + pending)（skipped 不计入）
/// - Y 轴固定 0~100
/// - 桌面端 hover 可触发 tooltip（fl_chart 内建支持 PointerHoverEvent）
class StatisticsTrendChart extends StatefulWidget {
  /// 构造函数。
  ///
  /// 参数：
  /// - [insights] 统计增强数据
  const StatisticsTrendChart({super.key, required this.insights});

  final StatisticsInsightsEntity insights;

  @override
  State<StatisticsTrendChart> createState() => _StatisticsTrendChartState();
}

/// 时间周期枚举（用于切换按钮）
enum _Period { week, month, year }

class _StatisticsTrendChartState extends State<StatisticsTrendChart> {
  _Period _selectedPeriod = _Period.week;

  @override
  Widget build(BuildContext context) {
    // 根据选中状态获取对应的数据点和模式
    final (points, mode) = switch (_selectedPeriod) {
      _Period.week => (widget.insights.weekPoints, _TrendMode.day),
      _Period.month => (widget.insights.monthPoints, _TrendMode.day),
      _Period.year => (widget.insights.yearPoints, _TrendMode.month),
    };

    return GlassCard(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text('完成率趋势', style: AppTypography.h2(context)),
                ),
                // 使用 SegmentedButton 点击切换周/月/年
                SegmentedButton<_Period>(
                  segments: const [
                    ButtonSegment(value: _Period.week, label: Text('本周')),
                    ButtonSegment(value: _Period.month, label: Text('本月')),
                    ButtonSegment(value: _Period.year, label: Text('本年')),
                  ],
                  selected: {_selectedPeriod},
                  onSelectionChanged: (selected) {
                    setState(() => _selectedPeriod = selected.first);
                  },
                  style: ButtonStyle(
                    visualDensity: VisualDensity.compact,
                    textStyle: WidgetStateProperty.all(
                      AppTypography.bodySecondary(context).copyWith(fontSize: 12),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            SizedBox(
              height: 220,
              child: _Line(points: points, mode: mode),
            ),
          ],
        ),
      ),
    );
  }
}

enum _TrendMode { day, month }

class _Line extends StatelessWidget {
  const _Line({required this.points, required this.mode});

  final List<StatisticsTrendPointEntity> points;
  final _TrendMode mode;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final primary = Theme.of(context).colorScheme.primary;
    final gridColor = Theme.of(context).dividerColor.withValues(
      alpha: isDark ? 0.18 : 0.12,
    );

    final spots = <FlSpot>[];
    for (var i = 0; i < points.length; i++) {
      final y = points[i].completionRatePercent;
      spots.add(FlSpot(i.toDouble(), y.isNaN ? 0 : y.clamp(0, 100)));
    }

    // 可访问性（spec-user-experience-improvements.md 3.5.1）：
    // fl_chart 本身不提供可遍历的“数据点语义节点”，这里将数据点信息汇总为 Semantics label，
    // 使 TalkBack/VoiceOver 至少能读出当前图表的关键数据。
    return Semantics(
      container: true,
      label: _semanticsLabel(points, mode: mode),
      child: LayoutBuilder(
        builder: (context, constraints) {
          const yAxisWidth = 40.0;
          const yAxisGap = 6.0;

          // 关键逻辑：让 X 轴在数据点较多时可横向滚动，避免标签挤压。
          // 经验值：日维度（最多 31）每点约 24px；月维度（12）每点更宽以容纳“X月”。
          final pointSpacing = mode == _TrendMode.month ? 56.0 : 24.0;
          final suggestedWidth = (points.isEmpty ? 1 : points.length) * pointSpacing;
          final viewportWidth = (constraints.maxWidth - yAxisWidth - yAxisGap)
              .clamp(0.0, double.infinity);
          final chartWidth =
              suggestedWidth < viewportWidth ? viewportWidth : suggestedWidth;

          return Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const _YAxisLabels(width: yAxisWidth),
              const SizedBox(width: yAxisGap),
              Expanded(
                child: SingleChildScrollView(
                  scrollDirection: Axis.horizontal,
                  child: SizedBox(
                    width: chartWidth,
                    child: LineChart(
                      LineChartData(
                        // 扩大 X 轴范围，两侧各留半格边距，避免标签被裁剪
                        minX: -0.5,
                        maxX: points.isEmpty ? 0.5 : (points.length - 0.5),
                        // 扩大 Y 轴范围，避免线条被裁剪
                        minY: -5,
                        maxY: 105,
                        // 不裁剪线条，允许超出边界
                        clipData: const FlClipData.none(),
                        lineTouchData: LineTouchData(
                          enabled: true,
                          handleBuiltInTouches: true,
                          touchTooltipData: LineTouchTooltipData(
                            tooltipRoundedRadius: 10,
                            tooltipBgColor:
                                isDark ? const Color(0xFF0B1220) : Colors.white,
                            getTooltipItems: (touchedSpots) {
                              return touchedSpots.map((s) {
                                final index = s.x.toInt();
                                if (index < 0 || index >= points.length) {
                                  return null;
                                }
                                final p = points[index];
                                final text = _tooltipText(p, mode: mode);
                                return LineTooltipItem(
                                  text,
                                  TextStyle(
                                    color: isDark
                                        ? AppColors.darkTextPrimary
                                        : AppColors.textPrimary,
                                    fontSize: 12,
                                  ),
                                );
                              }).toList();
                            },
                          ),
                        ),
                        gridData: FlGridData(
                          show: true,
                          drawVerticalLine: false,
                          horizontalInterval: 25,
                          getDrawingHorizontalLine: (_) => FlLine(
                            color: gridColor,
                            strokeWidth: 1,
                          ),
                        ),
                        titlesData: FlTitlesData(
                          topTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          rightTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          // Y 轴固定：由左侧独立组件渲染，因此这里关闭 leftTitles。
                          leftTitles: const AxisTitles(
                            sideTitles: SideTitles(showTitles: false),
                          ),
                          bottomTitles: AxisTitles(
                            sideTitles: SideTitles(
                              showTitles: true,
                              reservedSize: 22,
                              interval:
                                  mode == _TrendMode.month ? 1 : _bottomInterval(points),
                              getTitlesWidget: (value, meta) {
                                // 只在整数位置显示标签，避免重复
                                if (value != value.roundToDouble()) {
                                  return const SizedBox.shrink();
                                }
                                final index = value.toInt();
                                if (index < 0 || index >= points.length) {
                                  return const SizedBox.shrink();
                                }
                                return Padding(
                                  padding: const EdgeInsets.only(top: 6),
                                  child: Text(
                                    _xLabel(points[index].date, mode: mode),
                                    style: AppTypography.bodySecondary(context)
                                        .copyWith(fontSize: 11),
                                  ),
                                );
                              },
                            ),
                          ),
                        ),
                        borderData: FlBorderData(
                          show: true,
                          border: Border.all(color: gridColor),
                        ),
                        lineBarsData: [
                          LineChartBarData(
                            spots: spots,
                            isCurved: true,
                            // 降低平滑度，使水平线段更直，避免相邻100显示为曲线
                            curveSmoothness: 0.05,
                            color: primary,
                            barWidth: 3,
                            isStrokeCapRound: true,
                            dotData: FlDotData(show: points.length <= 12),
                            belowBarData: BarAreaData(
                              show: true,
                              color: primary.withValues(
                                alpha: isDark ? 0.12 : 0.14,
                              ),
                            ),
                          ),
                        ],
                      ),
                      duration: const Duration(milliseconds: 220),
                      curve: Curves.easeOut,
                    ),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  double _bottomInterval(List<StatisticsTrendPointEntity> points) {
    // 关键逻辑：按数据量决定 X 轴标签稀疏度，避免本月（最多 31）挤压重叠。
    if (points.length <= 8) return 1;
    if (points.length <= 14) return 2;
    if (points.length <= 21) return 3;
    return 5;
  }

  String _xLabel(DateTime date, {required _TrendMode mode}) {
    switch (mode) {
      case _TrendMode.day:
        return '${date.day}';
      case _TrendMode.month:
        return '${date.month}月';
    }
  }

  String _tooltipText(StatisticsTrendPointEntity p, {required _TrendMode mode}) {
    final total = p.total;
    if (total <= 0) {
      final label = mode == _TrendMode.month
          ? '${p.date.month}月'
          : YikeDateUtils.formatYmd(p.date);
      return '$label: 无任务';
    }
    final rate = p.completionRatePercent.clamp(0, 100).toStringAsFixed(0);
    if (mode == _TrendMode.month) {
      return '${p.date.month}月: ${p.completed}/$total ($rate%)';
    }
    return '${YikeDateUtils.formatYmd(p.date)}: ${p.completed}/$total ($rate%)';
  }

  String _semanticsLabel(
    List<StatisticsTrendPointEntity> points, {
    required _TrendMode mode,
  }) {
    if (points.isEmpty) return '完成率趋势图，无数据';
    final buffer = StringBuffer('完成率趋势图，数据点：');
    for (final p in points) {
      final label = mode == _TrendMode.month
          ? '${p.date.month}月'
          : YikeDateUtils.formatYmd(p.date);
      if (p.total <= 0) {
        buffer.write('$label 无任务；');
        continue;
      }
      final rate = p.completionRatePercent.clamp(0, 100).toStringAsFixed(0);
      buffer.write('$label 完成率 $rate%；');
    }
    return buffer.toString();
  }
}

class _YAxisLabels extends StatelessWidget {
  const _YAxisLabels({required this.width});

  final double width;

  @override
  Widget build(BuildContext context) {
    // 关键逻辑：Y 轴固定不滚动，独立绘制 0~100 的刻度标签。
    // 改进：添加上下边距，使标签不被裁剪，且与 fl_chart 网格线对齐。
    final style = AppTypography.bodySecondary(context).copyWith(fontSize: 11);
    const ticks = <int>[100, 75, 50, 25, 0];

    // fl_chart 默认的垂直 padding，使网格线不会到达最顶端和最底端
    // 这里模拟相同的布局，使 Y 轴标签与网格线对齐
    const topPadding = 16.0;
    const bottomReserved = 22.0;

    return SizedBox(
      width: width,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final totalHeight = constraints.maxHeight;
          final chartHeight = (totalHeight - bottomReserved).clamp(0.0, double.infinity);
          // 网格线实际占据的高度（去除 topPadding）
          final gridHeight = chartHeight - topPadding;

          // 测量文本高度用于精确对齐
          final textPainter = TextPainter(
            text: TextSpan(text: '100%', style: style),
            textDirection: TextDirection.ltr,
          )..layout();

          // 计算每个刻度标签的位置
          // fl_chart: y=100 在顶部下方 topPadding 处，y=0 在底部上方
          double positionForTick(int tick) {
            // tick=100 -> top = topPadding
            // tick=0 -> top = topPadding + gridHeight
            final basePosition = topPadding + (1 - tick / 100) * gridHeight;
            return basePosition - textPainter.height / 2;
          }

          return Padding(
            padding: const EdgeInsets.only(bottom: bottomReserved),
            child: Stack(
              children: [
                for (final t in ticks)
                  Positioned(
                    left: 0,
                    right: 0,
                    top: positionForTick(t).clamp(
                      0.0,
                      chartHeight - textPainter.height,
                    ),
                    child: Text('$t%', style: style, textAlign: TextAlign.right),
                  ),
              ],
            ),
          );
        },
      ),
    );
  }
}
