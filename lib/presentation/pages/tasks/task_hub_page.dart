/// 文件用途：任务中心页面（全量任务时间线，支持筛选、分组与游标分页）。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_strings.dart';
import '../../providers/task_hub_provider.dart';
import '../../providers/ui_preferences_provider.dart';
import '../../widgets/gradient_background.dart';
import '../../widgets/task_filter_bar.dart';
import '../../widgets/yike_refresh_indicator.dart';
import '../home/widgets/home_time_filter_bar.dart';
import 'widgets/task_hub_timeline_list.dart';

/// 任务中心页面。
class TaskHubPage extends ConsumerStatefulWidget {
  /// 构造函数。
  const TaskHubPage({super.key});

  @override
  ConsumerState<TaskHubPage> createState() => _TaskHubPageState();
}

class _TaskHubPageState extends ConsumerState<TaskHubPage> {
  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.removeListener(_onScroll);
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    // 游标分页：接近底部时自动加载下一页，避免 offset 分页的性能问题。
    if (!_scrollController.hasClients) return;
    final position = _scrollController.position;
    if (position.maxScrollExtent <= 0) return;
    if (position.pixels >= position.maxScrollExtent - 240) {
      ref.read(taskHubProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(taskHubProvider);
    final notifier = ref.read(taskHubProvider.notifier);
    final blurEnabled = ref.watch(taskListBlurEnabledProvider);
    final hapticEnabled = ref.watch(hapticFeedbackEnabledProvider);

    return Scaffold(
      appBar: AppBar(title: const Text(AppStrings.taskHubTitle)),
      body: GradientBackground(
        child: SafeArea(
          child: YiKeRefreshIndicator(
            hapticEnabledByUser: hapticEnabled,
            onRefresh: notifier.refresh,
            // 性能优化（Phase 1）：使用 CustomScrollView + SliverList 虚拟化时间线，避免一次性构建全部卡片。
            child: CustomScrollView(
              key: const PageStorageKey('task_hub_scroll'),
              controller: _scrollController,
              slivers: [
                SliverPadding(
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.lg,
                    AppSpacing.lg,
                    AppSpacing.lg,
                    0,
                  ),
                  sliver: SliverToBoxAdapter(
                    child: HomeTimeFilterBar(
                      filter: state.timeFilter,
                      onChanged: (next) => notifier.setTimeFilter(next),
                    ),
                  ),
                ),
                const SliverToBoxAdapter(
                  child: SizedBox(height: AppSpacing.lg),
                ),
                SliverPadding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.lg,
                  ),
                  sliver: SliverToBoxAdapter(
                    child: TaskFilterBar(
                      filter: state.filter,
                      counts: state.counts,
                      onChanged: (next) => notifier.setFilter(next),
                    ),
                  ),
                ),
                const SliverToBoxAdapter(
                  child: SizedBox(height: AppSpacing.lg),
                ),
                SliverPadding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.lg,
                  ),
                  sliver: TaskHubTimelineSliver(
                    state: state,
                    notifier: notifier,
                    blurEnabled: blurEnabled,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
