/// 文件用途：任务详情底部 Sheet（按 learningItemId 展示学习内容信息与完整复习计划）。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/ebbinghaus_utils.dart';
import '../../../di/providers.dart';
import '../../../domain/entities/learning_subtask.dart';
import '../../../domain/entities/learning_topic.dart';
import '../../../domain/entities/review_task.dart';
import '../../../domain/usecases/manage_topic_usecase.dart';
import '../../providers/task_detail_provider.dart';

/// 任务详情 Sheet。
///
/// 说明：
/// - 默认用于“查看详情”场景（不自动弹出任何编辑弹窗）
/// - 当 [openEditOnLoad] 为 true 时，会在内容加载完成后自动打开“编辑基本信息”Sheet
class TaskDetailSheet extends ConsumerStatefulWidget {
  const TaskDetailSheet({
    super.key,
    required this.learningItemId,
    this.openEditOnLoad = false,
  });

  final int learningItemId;

  /// 是否在加载完成后自动打开“编辑基本信息”Sheet（用于上下文菜单快捷编辑）。
  final bool openEditOnLoad;

  @override
  ConsumerState<TaskDetailSheet> createState() => _TaskDetailSheetState();
}

class _TaskDetailSheetState extends ConsumerState<TaskDetailSheet> {
  bool _didAutoOpenEdit = false;

  /// 尝试自动弹出编辑 Sheet（仅执行一次）。
  ///
  /// 触发条件：
  /// - 路由携带 edit=1（即 widget.openEditOnLoad=true）
  /// - 数据已加载完成且 learning item 存在
  /// - 未停用（readOnly=false）
  void _tryAutoOpenEdit(TaskDetailState state) {
    if (_didAutoOpenEdit) return;
    if (!widget.openEditOnLoad) return;
    if (state.isLoading) return;
    final item = state.item;
    if (item == null) return;
    if (item.isDeleted) return;

    _didAutoOpenEdit = true;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final currentTopicIds = state.topics.map((e) => e.id).whereType<int>().toSet();
      // ignore: unused_result
      await showModalBottomSheet<bool>(
        context: context,
        isScrollControlled: true,
        showDragHandle: true,
        builder: (context) {
          return _EditBasicInfoSheet(
            learningItemId: widget.learningItemId,
            initialTitle: item.title,
            initialTags: item.tags,
            initialTopicIds: currentTopicIds,
            readOnly: false,
          );
        },
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(taskDetailProvider(widget.learningItemId));
    final notifier = ref.read(taskDetailProvider(widget.learningItemId).notifier);

    // 关键逻辑：支持从上下文菜单“编辑”入口自动弹出编辑 Sheet。
    _tryAutoOpenEdit(state);

    void showSnack(String text) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
    }

    Future<void> runAction(Future<void> Function() action, {required String ok}) async {
      try {
        await action();
        showSnack(ok);
      } catch (e) {
        showSnack('操作失败：$e');
      }
    }

    final item = state.item;
    final plan = [...state.plan]..sort((a, b) => a.reviewRound.compareTo(b.reviewRound));
    final isReadOnly = item?.isDeleted ?? false;

    return Scaffold(
      backgroundColor: Theme.of(context).colorScheme.surface,
      body: SafeArea(
        top: false,
        child: Column(
          children: [
            _SheetHeader(
              title: item == null ? '任务详情' : item.title,
              isDeleted: isReadOnly,
              deletedAt: item?.deletedAt,
              onClose: () => Navigator.of(context).maybePop(),
            ),
            Expanded(
              child: state.isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : item == null
                      ? _EmptyDetail(
                          message: '学习内容不存在或已被移除',
                          onBack: () => Navigator.of(context).maybePop(),
                        )
                      : ListView(
                          padding: const EdgeInsets.all(AppSpacing.lg),
                          children: [
                            if (state.errorMessage != null) ...[
                              _ErrorCard(message: state.errorMessage!),
                              const SizedBox(height: AppSpacing.md),
                            ],
                            _InfoCard(
                              title: item.title,
                              tags: item.tags,
                              topics: state.topics,
                              learningDate: item.learningDate,
                              isReadOnly: isReadOnly,
                              onEditBasicInfo: () async {
                                final currentTopicIds = state.topics
                                    .map((e) => e.id)
                                    .whereType<int>()
                                    .toSet();

                                final ok = await showModalBottomSheet<bool>(
                                  context: context,
                                  isScrollControlled: true,
                                  showDragHandle: true,
                                  builder: (context) {
                                    return _EditBasicInfoSheet(
                                      learningItemId: widget.learningItemId,
                                      initialTitle: item.title,
                                      initialTags: item.tags,
                                      initialTopicIds: currentTopicIds,
                                      readOnly: isReadOnly,
                                    );
                                  },
                                );

                                if (ok == true) {
                                  showSnack('基本信息已更新');
                                }
                              },
                            ),
                            const SizedBox(height: AppSpacing.lg),
                            _DescriptionCard(
                              description: item.description,
                              legacyNote: item.note,
                              isReadOnly: isReadOnly,
                              onEdit: () async {
                                final next = await _showEditDescriptionDialog(
                                  context,
                                  initial: item.description ?? item.note,
                                  readOnly: isReadOnly,
                                );
                                if (next == null) return;
                                await runAction(
                                  () => notifier.updateDescription(next),
                                  ok: '描述已更新',
                                );
                              },
                            ),
                            const SizedBox(height: AppSpacing.lg),
                            _SubtasksCard(
                              subtasks: state.subtasks,
                              isReadOnly: isReadOnly,
                              onAdd: () async {
                                final content = await _showEditSubtaskDialog(
                                  context,
                                  title: '新增子任务',
                                  initial: '',
                                  readOnly: isReadOnly,
                                );
                                if (content == null) return;
                                await runAction(
                                  () => notifier.createSubtask(content),
                                  ok: '子任务已添加',
                                );
                              },
                              onEdit: (subtask) async {
                                final next = await _showEditSubtaskDialog(
                                  context,
                                  title: '编辑子任务',
                                  initial: subtask.content,
                                  readOnly: isReadOnly,
                                );
                                if (next == null) return;
                                await runAction(
                                  () => notifier.updateSubtask(
                                    subtask.copyWith(content: next),
                                  ),
                                  ok: '子任务已更新',
                                );
                              },
                              onDelete: (id) async {
                                final ok = await _confirmDeleteSubtask(context);
                                if (ok != true) return;
                                await runAction(
                                  () => notifier.deleteSubtask(id),
                                  ok: '子任务已删除',
                                );
                              },
                              onReorder: (ids) async {
                                await runAction(
                                  () => notifier.reorderSubtasks(ids),
                                  ok: '排序已更新',
                                );
                              },
                            ),
                            const SizedBox(height: AppSpacing.lg),
                            _ActionRow(
                              isReadOnly: isReadOnly,
                              canAddRound: _canAddRound(plan),
                              canRemoveRound: _canRemoveRound(plan),
                              onDeactivate: () async {
                                final confirmed = await _confirmDeactivate(context);
                                if (confirmed != true) return;
                                await runAction(
                                  notifier.deactivate,
                                  ok: '已停用学习内容',
                                );
                              },
                              onAdjustPlan: () async {
                                await _showAdjustPlanSheet(
                                  context,
                                  plan: plan,
                                  isReadOnly: isReadOnly,
                                  onAdjust: (round, date) async {
                                    await runAction(
                                      () => notifier.adjustReviewDate(
                                        reviewRound: round,
                                        newDate: date,
                                      ),
                                      ok: '计划已更新',
                                    );
                                  },
                                );
                              },
                              onAddRound: () async {
                                final confirmed = await _confirmAddRound(
                                  context,
                                  currentMaxRound: _maxRound(plan),
                                  isReadOnly: isReadOnly,
                                );
                                if (confirmed != true) return;
                                await runAction(
                                  notifier.addReviewRound,
                                  ok: '已增加一轮复习',
                                );
                              },
                              onRemoveRound: () async {
                                final latest = _latestRoundTask(plan);
                                if (latest == null) return;
                                final confirmed = await _confirmRemoveRound(
                                  context,
                                  round: latest.reviewRound,
                                  status: latest.status,
                                  isReadOnly: isReadOnly,
                                );
                                if (confirmed != true) return;
                                await runAction(
                                  notifier.removeReviewRound,
                                  ok: '已减少一轮复习',
                                );
                              },
                              onViewPlan: () async {
                                await _showViewPlanSheet(context, plan: plan);
                              },
                            ),
                            const SizedBox(height: AppSpacing.lg),
                            Text('复习计划', style: AppTypography.h2(context)),
                            const SizedBox(height: AppSpacing.sm),
                            if (plan.isEmpty)
                              Text(
                                '暂无复习任务',
                                style: AppTypography.bodySecondary(context),
                              )
                            else
                              ...plan.map(
                                (t) => Padding(
                                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                                  child: _PlanTile(
                                    task: t,
                                    readOnly: isReadOnly,
                                    onUndo: t.status == ReviewTaskStatus.pending || isReadOnly
                                        ? null
                                        : () async {
                                            final confirmed = await _confirmUndo(context);
                                            if (confirmed != true) return;
                                            await runAction(
                                              () => notifier.undoTaskStatus(t.taskId),
                                              ok: '已撤销',
                                            );
                                          },
                                  ),
                                ),
                              ),
                            const SizedBox(height: 80),
                          ],
                        ),
            ),
          ],
        ),
      ),
    );
  }

  bool _canAddRound(List<ReviewTaskViewEntity> plan) {
    if (plan.isEmpty) return false;
    final max = _maxRound(plan);
    return max < EbbinghausUtils.maxReviewRound;
  }

  bool _canRemoveRound(List<ReviewTaskViewEntity> plan) {
    if (plan.isEmpty) return false;
    return _maxRound(plan) > 1;
  }

  ReviewTaskViewEntity? _latestRoundTask(List<ReviewTaskViewEntity> plan) {
    if (plan.isEmpty) return null;
    var latest = plan.first;
    for (final t in plan) {
      if (t.reviewRound > latest.reviewRound) latest = t;
    }
    return latest;
  }

  int _maxRound(List<ReviewTaskViewEntity> plan) {
    var max = 0;
    for (final t in plan) {
      if (t.reviewRound > max) max = t.reviewRound;
    }
    return max;
  }
}

class _SheetHeader extends StatelessWidget {
  const _SheetHeader({
    required this.title,
    required this.isDeleted,
    required this.deletedAt,
    required this.onClose,
  });

  final String title;
  final bool isDeleted;
  final DateTime? deletedAt;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final subtitle =
        isDeleted ? '已停用${deletedAt == null ? '' : ' · ${DateFormat('yyyy-MM-dd HH:mm').format(deletedAt!)}'}' : null;

    return Padding(
      padding: const EdgeInsets.only(
        left: AppSpacing.lg,
        right: AppSpacing.sm,
        top: AppSpacing.sm,
        bottom: AppSpacing.sm,
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Center(
                  child: Container(
                    width: 44,
                    height: 4,
                    decoration: BoxDecoration(
                      color: Theme.of(context).dividerColor,
                      borderRadius: BorderRadius.circular(999),
                    ),
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: AppTypography.h2(context),
                ),
                if (subtitle != null) ...[
                  const SizedBox(height: 4),
                  Text(subtitle, style: AppTypography.bodySecondary(context)),
                ],
              ],
            ),
          ),
          IconButton(
            tooltip: '关闭',
            onPressed: onClose,
            icon: const Icon(Icons.close),
          ),
        ],
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({
    required this.title,
    required this.tags,
    required this.topics,
    required this.learningDate,
    required this.isReadOnly,
    required this.onEditBasicInfo,
  });

  final String title;
  final List<String> tags;
  final List<LearningTopicEntity> topics;
  final DateTime learningDate;
  final bool isReadOnly;
  final VoidCallback onEditBasicInfo;

  @override
  Widget build(BuildContext context) {
    final topicNames = topics.map((e) => e.name).toList()..sort();
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '基本信息',
                    style: AppTypography.h2(context).copyWith(fontSize: 14),
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: isReadOnly ? null : onEditBasicInfo,
                  icon: const Icon(Icons.edit, size: 18),
                  label: const Text('编辑'),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            _InfoRow(label: '任务名', value: title),
            const SizedBox(height: 6),
            _InfoRow(
              label: '学习日期',
              value: DateFormat('yyyy-MM-dd').format(learningDate),
            ),
            const SizedBox(height: 6),
            _InfoRow(
              label: '主题',
              value: topicNames.isEmpty ? '未关联' : topicNames.join('，'),
            ),
            const SizedBox(height: AppSpacing.sm),
            if (tags.isEmpty)
              Text('标签：未设置', style: AppTypography.bodySecondary(context))
            else ...[
              Text('标签', style: AppTypography.bodySecondary(context)),
              const SizedBox(height: 6),
              Wrap(
                spacing: 6,
                runSpacing: 6,
                children: tags.take(16).map((t) {
                  return Chip(
                    label: Text(t),
                    visualDensity: VisualDensity.compact,
                  );
                }).toList(),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// 信息行（用于 TaskDetailSheet 基本信息展示）。
class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        SizedBox(
          width: 64,
          child: Text(label, style: AppTypography.bodySecondary(context)),
        ),
        Expanded(
          child: Text(
            value,
            style: AppTypography.body(context).copyWith(fontWeight: FontWeight.w600),
          ),
        ),
      ],
    );
  }
}

/// 编辑基本信息 Sheet（任务名/标签/主题）。
///
/// 说明：
/// - 尽量对齐录入页的交互：标签输入 + 常用标签快捷添加；主题选择支持新建
/// - 保存后由 [TaskDetailNotifier] 刷新任务详情与关联页面缓存
class _EditBasicInfoSheet extends ConsumerStatefulWidget {
  const _EditBasicInfoSheet({
    required this.learningItemId,
    required this.initialTitle,
    required this.initialTags,
    required this.initialTopicIds,
    required this.readOnly,
  });

  final int learningItemId;
  final String initialTitle;
  final List<String> initialTags;
  final Set<int> initialTopicIds;
  final bool readOnly;

  @override
  ConsumerState<_EditBasicInfoSheet> createState() => _EditBasicInfoSheetState();
}

class _EditBasicInfoSheetState extends ConsumerState<_EditBasicInfoSheet> {
  late final TextEditingController _titleController;
  late final TextEditingController _tagsController;

  bool _loading = true;
  bool _saving = false;
  List<String> _availableTags = const [];
  List<LearningTopicEntity> _allTopics = const [];
  Set<int> _selectedTopicIds = <int>{};

  @override
  void initState() {
    super.initState();
    _titleController = TextEditingController(text: widget.initialTitle);
    _tagsController = TextEditingController(text: widget.initialTags.join(', '));
    _selectedTopicIds = {...widget.initialTopicIds};
    _load();
  }

  @override
  void dispose() {
    _titleController.dispose();
    _tagsController.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final tagFuture = ref.read(learningItemRepositoryProvider).getAllTags();
      final topicsFuture = ref.read(manageTopicUseCaseProvider).getAll();
      final tags = await tagFuture;
      final topics = await topicsFuture;

      if (!mounted) return;
      setState(() {
        _availableTags = tags;
        _allTopics = topics.where((e) => e.id != null).toList()
          ..sort((a, b) => a.name.compareTo(b.name));
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loading = false);
    }
  }

  void _showSnack(String text) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  List<String> _parseTags(String raw) {
    // 关键逻辑：兼容中文逗号/英文逗号，并对结果去空/去重，保持与录入页一致的“逗号分隔”体验。
    final parts = raw.split(RegExp(r'[,，]'));
    final seen = <String>{};
    final out = <String>[];
    for (final p in parts) {
      final t = p.trim();
      if (t.isEmpty) continue;
      if (seen.add(t)) out.add(t);
    }
    return out;
  }

  void _appendTag(String tag) {
    final current = _parseTags(_tagsController.text);
    if (current.contains(tag)) return;
    current.add(tag);
    _tagsController.text = current.join(', ');
  }

  String _topicSummary() {
    if (_selectedTopicIds.isEmpty) return '不选择主题';
    final names = _allTopics
        .where((t) => t.id != null && _selectedTopicIds.contains(t.id))
        .map((e) => e.name)
        .toList()
      ..sort();
    if (names.isEmpty) return '已选择 ${_selectedTopicIds.length} 个主题';
    if (names.length <= 2) return names.join('，');
    return '${names.take(2).join('，')} 等 ${names.length} 个';
  }

  Future<void> _pickTopics() async {
    if (widget.readOnly) return;
    final selected = {..._selectedTopicIds};
    final searchController = TextEditingController();

    final picked = await showDialog<Set<int>>(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setLocal) {
            final keyword = searchController.text.trim();
            final filtered = keyword.isEmpty
                ? _allTopics
                : _allTopics.where((e) => e.name.contains(keyword)).toList();

            return AlertDialog(
              title: const Text('选择主题（可多选）'),
              content: SizedBox(
                width: double.maxFinite,
                height: 420,
                child: Column(
                  children: [
                    TextField(
                      controller: searchController,
                      decoration: const InputDecoration(
                        hintText: '搜索主题',
                        prefixIcon: Icon(Icons.search),
                      ),
                      onChanged: (_) => setLocal(() {}),
                    ),
                    const SizedBox(height: AppSpacing.md),
                    Row(
                      children: [
                        Expanded(
                          child: OutlinedButton.icon(
                            onPressed: () async {
                              final created = await _createTopic();
                              if (created == null) return;
                              if (!context.mounted) return;
                              setLocal(() {
                                _allTopics = [..._allTopics, created]
                                  ..sort((a, b) => a.name.compareTo(b.name));
                                if (created.id != null) {
                                  selected.add(created.id!);
                                }
                              });
                            },
                            icon: const Icon(Icons.add, size: 18),
                            label: const Text('新建主题'),
                          ),
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        TextButton(
                          onPressed: () => setLocal(selected.clear),
                          child: const Text('清空'),
                        ),
                      ],
                    ),
                    const SizedBox(height: AppSpacing.sm),
                    Expanded(
                      child: filtered.isEmpty
                          ? Center(
                              child: Text(
                                keyword.isEmpty ? '暂无主题' : '未找到匹配主题',
                                style: AppTypography.bodySecondary(context),
                              ),
                            )
                          : ListView.builder(
                              itemCount: filtered.length,
                              itemBuilder: (context, i) {
                                final t = filtered[i];
                                final id = t.id;
                                if (id == null) return const SizedBox.shrink();
                                final checked = selected.contains(id);
                                return CheckboxListTile(
                                  value: checked,
                                  dense: true,
                                  title: Text(t.name),
                                  subtitle: (t.description ?? '').trim().isEmpty
                                      ? null
                                      : Text(
                                          t.description!,
                                          maxLines: 1,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                  onChanged: (v) {
                                    setLocal(() {
                                      if (v == true) {
                                        selected.add(id);
                                      } else {
                                        selected.remove(id);
                                      }
                                    });
                                  },
                                );
                              },
                            ),
                    ),
                  ],
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(null),
                  child: const Text('取消'),
                ),
                FilledButton(
                  onPressed: () => Navigator.of(context).pop(selected),
                  child: const Text('确定'),
                ),
              ],
            );
          },
        );
      },
    );

    if (!mounted) return;
    if (picked == null) return;
    setState(() => _selectedTopicIds = picked);
  }

  Future<LearningTopicEntity?> _createTopic() async {
    final useCase = ref.read(manageTopicUseCaseProvider);
    final nameController = TextEditingController();
    final descController = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('新建主题'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(labelText: '主题名称（必填）'),
            ),
            const SizedBox(height: AppSpacing.md),
            TextField(
              controller: descController,
              minLines: 2,
              maxLines: 4,
              decoration: const InputDecoration(labelText: '主题描述（选填）'),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('创建'),
          ),
        ],
      ),
    );
    if (ok != true) return null;

    final name = nameController.text.trim();
    final desc = descController.text.trim();
    if (name.isEmpty) {
      _showSnack('主题名称不能为空');
      return null;
    }

    try {
      final created = await useCase.create(
        TopicParams(name: name, description: desc.isEmpty ? null : desc),
      );
      return created;
    } catch (e) {
      _showSnack('创建失败：$e');
      return null;
    }
  }

	  Future<void> _save() async {
	    if (widget.readOnly || _saving) return;
	    final title = _titleController.text.trim();
	    if (title.isEmpty) {
	      _showSnack('请输入任务名');
      return;
    }
    if (title.length > 50) {
      _showSnack('任务名最多 50 字');
      return;
    }

	    final tags = _parseTags(_tagsController.text);
	
	    setState(() => _saving = true);
	    try {
	      await ref
	          .read(taskDetailProvider(widget.learningItemId).notifier)
	          .updateBasicInfo(
	            title: title,
	            tags: tags,
	            topicIds: _selectedTopicIds,
	          );
	      if (!mounted) return;
	      Navigator.of(context).pop(true);
	    } catch (e) {
	      _showSnack('保存失败：$e');
	    } finally {
	      // 避免在 finally 中 return（lint: control_flow_in_finally）。
	      if (mounted) {
	        setState(() => _saving = false);
	      }
	    }
	  }

  @override
  Widget build(BuildContext context) {
    final bottom = MediaQuery.of(context).viewInsets.bottom;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.fromLTRB(
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg,
          AppSpacing.lg + bottom,
        ),
        child: Stack(
          children: [
            Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('编辑基本信息', style: AppTypography.h2(context)),
                const SizedBox(height: AppSpacing.md),
                TextField(
                  controller: _titleController,
                  enabled: !widget.readOnly && !_saving,
                  autofocus: true,
                  maxLength: 50,
                  decoration: const InputDecoration(
                    labelText: '任务名（必填）',
                    hintText: '例如：背诵英语单词 Day 1',
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                TextField(
                  controller: _tagsController,
                  enabled: !widget.readOnly && !_saving,
                  decoration: const InputDecoration(
                    labelText: '标签（选填，用逗号分隔）',
                    hintText: '例如：Java, 面试',
                  ),
                ),
                const SizedBox(height: AppSpacing.sm),
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('主题'),
                  subtitle: Text(
                    _topicSummary(),
                    style: AppTypography.bodySecondary(context),
                  ),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: (_saving || widget.readOnly) ? null : _pickTopics,
                ),
                const SizedBox(height: AppSpacing.sm),
                if (_loading)
                  Text(
                    '正在加载标签与主题…',
                    style: AppTypography.bodySecondary(context),
                  )
                else if (_availableTags.isEmpty)
                  Text('还没有标签，创建一个吧', style: AppTypography.bodySecondary(context))
                else
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: _availableTags.take(12).map((t) {
                      return ActionChip(
                        label: Text(t),
                        onPressed: (_saving || widget.readOnly) ? null : () => _appendTag(t),
                      );
                    }).toList(),
                  ),
                const SizedBox(height: AppSpacing.lg),
                Row(
                  children: [
                    Expanded(
                      child: TextButton(
                        onPressed: _saving ? null : () => Navigator.of(context).pop(false),
                        child: const Text('取消'),
                      ),
                    ),
                    Expanded(
                      child: FilledButton(
                        onPressed: (_saving || widget.readOnly) ? null : _save,
                        child: Text(_saving ? '保存中…' : '保存'),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            if (_saving)
              Positioned.fill(
                child: ColoredBox(
                  color: Colors.black.withAlpha((0.10 * 255).round()),
                  child: const Center(child: CircularProgressIndicator()),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _DescriptionCard extends StatelessWidget {
  const _DescriptionCard({
    required this.description,
    required this.legacyNote,
    required this.isReadOnly,
    required this.onEdit,
  });

  final String? description;
  final String? legacyNote;
  final bool isReadOnly;
  final VoidCallback onEdit;

  @override
  Widget build(BuildContext context) {
    final desc = (description ?? '').trim();
    final note = (legacyNote ?? '').trim();
    final content = desc.isNotEmpty ? desc : (note.isNotEmpty ? note : '');
    final label = desc.isNotEmpty ? '描述' : (note.isNotEmpty ? '旧备注（待迁移）' : '描述');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    label,
                    style: AppTypography.h2(context).copyWith(fontSize: 14),
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: isReadOnly ? null : onEdit,
                  icon: const Icon(Icons.edit, size: 18),
                  label: const Text('编辑描述'),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              content.isEmpty ? '（无）' : content,
              style: AppTypography.bodySecondary(context),
            ),
          ],
        ),
      ),
    );
  }
}

class _SubtasksCard extends StatelessWidget {
  const _SubtasksCard({
    required this.subtasks,
    required this.isReadOnly,
    required this.onAdd,
    required this.onEdit,
    required this.onDelete,
    required this.onReorder,
  });

  final List<LearningSubtaskEntity> subtasks;
  final bool isReadOnly;
  final VoidCallback onAdd;
  final void Function(LearningSubtaskEntity subtask) onEdit;
  final void Function(int id) onDelete;
  final void Function(List<int> orderedIds) onReorder;

  @override
  Widget build(BuildContext context) {
    final sorted = [...subtasks]..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '子任务',
                    style: AppTypography.h2(context).copyWith(fontSize: 14),
                  ),
                ),
                OutlinedButton.icon(
                  onPressed: isReadOnly ? null : onAdd,
                  icon: const Icon(Icons.add, size: 18),
                  label: const Text('新增'),
                ),
              ],
            ),
            const SizedBox(height: AppSpacing.sm),
            if (sorted.isEmpty)
              Text('（无）', style: AppTypography.bodySecondary(context))
            else
              ReorderableListView.builder(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: sorted.length,
                buildDefaultDragHandles: false,
                onReorder:
                    isReadOnly
                        ? (oldIndex, newIndex) {}
                        : (oldIndex, newIndex) {
                          var target = newIndex;
                          if (newIndex > oldIndex) target = newIndex - 1;
                          final next = [...sorted];
                          final moved = next.removeAt(oldIndex);
                          next.insert(target, moved);
                          final ids = next.map((e) => e.id).whereType<int>().toList();
                          if (ids.length != next.length) return;
                          onReorder(ids);
                        },
                itemBuilder: (context, index) {
                  final s = sorted[index];
                  return ListTile(
                    key: ValueKey('subtask_${s.id ?? s.uuid}'),
                    contentPadding: EdgeInsets.zero,
                    leading: isReadOnly
                        ? const Icon(Icons.drag_handle, color: Colors.grey)
                        : ReorderableDragStartListener(
                            index: index,
                            child: const Icon(Icons.drag_handle),
                          ),
                    title: Text(
                      s.content,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        IconButton(
                          tooltip: '编辑',
                          onPressed: isReadOnly ? null : () => onEdit(s),
                          icon: const Icon(Icons.edit, size: 20),
                        ),
                        IconButton(
                          tooltip: '删除',
                          onPressed: isReadOnly || s.id == null ? null : () => onDelete(s.id!),
                          icon: const Icon(Icons.delete_outline, size: 20),
                        ),
                      ],
                    ),
                    onTap: isReadOnly ? null : () => onEdit(s),
                  );
                },
              ),
          ],
        ),
      ),
    );
  }
}

class _ActionRow extends StatelessWidget {
  const _ActionRow({
    required this.isReadOnly,
    required this.canAddRound,
    required this.canRemoveRound,
    required this.onDeactivate,
    required this.onAdjustPlan,
    required this.onAddRound,
    required this.onRemoveRound,
    required this.onViewPlan,
  });

  final bool isReadOnly;
  final bool canAddRound;
  final bool canRemoveRound;
  final VoidCallback onDeactivate;
  final VoidCallback onAdjustPlan;
  final VoidCallback onAddRound;
  final VoidCallback onRemoveRound;
  final VoidCallback onViewPlan;

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: AppSpacing.sm,
      runSpacing: AppSpacing.sm,
      children: [
        OutlinedButton.icon(
          onPressed: isReadOnly ? null : onAdjustPlan,
          icon: const Icon(Icons.event),
          label: const Text('调整计划'),
        ),
        OutlinedButton.icon(
          onPressed: isReadOnly || !canAddRound ? null : onAddRound,
          icon: const Icon(Icons.add),
          label: Text(canAddRound ? '增加轮次' : '已达上限'),
        ),
        OutlinedButton.icon(
          onPressed: isReadOnly || !canRemoveRound ? null : onRemoveRound,
          icon: const Icon(Icons.remove),
          label: Text(canRemoveRound ? '减少轮次' : '已达下限'),
        ),
        OutlinedButton.icon(
          onPressed: onViewPlan,
          icon: const Icon(Icons.view_list),
          label: const Text('查看计划'),
        ),
        OutlinedButton.icon(
          onPressed: isReadOnly ? null : onDeactivate,
          icon: const Icon(Icons.pause_circle_outline),
          label: const Text('停用'),
        ),
      ],
    );
  }
}

class _PlanTile extends StatelessWidget {
  const _PlanTile({required this.task, required this.readOnly, required this.onUndo});

  final ReviewTaskViewEntity task;
  final bool readOnly;
  final VoidCallback? onUndo;

  @override
  Widget build(BuildContext context) {
    final statusText = switch (task.status) {
      ReviewTaskStatus.pending => '待复习',
      ReviewTaskStatus.done => '已完成',
      ReviewTaskStatus.skipped => '已跳过',
    };
    final date = DateFormat('yyyy-MM-dd').format(task.scheduledDate);

    String? extra;
    if (task.status == ReviewTaskStatus.done && task.completedAt != null) {
      extra = '完成于 ${DateFormat('yyyy-MM-dd HH:mm').format(task.completedAt!)}';
    } else if (task.status == ReviewTaskStatus.skipped && task.skippedAt != null) {
      extra = '跳过于 ${DateFormat('yyyy-MM-dd HH:mm').format(task.skippedAt!)}';
    }

    return Card(
      child: ListTile(
        title: Text('第${task.reviewRound}轮 · $date'),
        subtitle: Text(extra ?? statusText),
        trailing: onUndo == null
            ? null
            : OutlinedButton(
                onPressed: onUndo,
                child: const Text('撤销'),
              ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Card(
      color: Theme.of(context).colorScheme.errorContainer,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Text(
          message,
          style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
        ),
      ),
    );
  }
}

class _EmptyDetail extends StatelessWidget {
  const _EmptyDetail({required this.message, required this.onBack});

  final String message;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(message, style: AppTypography.bodySecondary(context)),
            const SizedBox(height: AppSpacing.lg),
            FilledButton(onPressed: onBack, child: const Text('返回')),
          ],
        ),
      ),
    );
  }
}

Future<String?> _showEditDescriptionDialog(
  BuildContext context, {
  required String? initial,
  required bool readOnly,
}) async {
  if (readOnly) return null;
  final controller = TextEditingController(text: initial ?? '');
  return showDialog<String?>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('编辑描述'),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 6,
          decoration: const InputDecoration(hintText: '请输入描述（可为空）'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(null),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('保存'),
          ),
        ],
      );
    },
  );
}

Future<String?> _showEditSubtaskDialog(
  BuildContext context, {
  required String title,
  required String initial,
  required bool readOnly,
}) async {
  if (readOnly) return null;
  final controller = TextEditingController(text: initial);
  return showDialog<String?>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          maxLines: 4,
          decoration: const InputDecoration(hintText: '请输入子任务内容'),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(null),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(controller.text),
            child: const Text('保存'),
          ),
        ],
      );
    },
  );
}

Future<bool?> _confirmDeleteSubtask(BuildContext context) {
  return showDialog<bool>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('删除子任务'),
        content: const Text('确定删除该子任务吗？该操作不可恢复。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('删除'),
          ),
        ],
      );
    },
  );
}

Future<bool?> _confirmDeactivate(BuildContext context) {
  return showDialog<bool>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('停用该学习内容？'),
        content: const Text(
          '停用后该学习内容的所有复习任务将不再出现，且不会生成后续复习轮次。是否确认停用？',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('确认停用'),
          ),
        ],
      );
    },
  );
}

Future<bool?> _confirmUndo(BuildContext context) {
  return showDialog<bool>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('撤销任务状态？'),
        content: const Text('该任务将恢复为待复习状态，是否确认撤销？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('确认撤销'),
          ),
        ],
      );
    },
  );
}

Future<bool?> _confirmAddRound(
  BuildContext context, {
  required int currentMaxRound,
  required bool isReadOnly,
}) {
  if (isReadOnly) return Future.value(false);
  return showDialog<bool>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('增加复习轮次'),
        content: Text(
          '当前轮次为第 $currentMaxRound 轮，将增加 1 轮复习。系统将自动计算新的复习日期，是否确认？',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('确认增加'),
          ),
        ],
      );
    },
  );
}

Future<bool?> _confirmRemoveRound(
  BuildContext context, {
  required int round,
  required ReviewTaskStatus status,
  required bool isReadOnly,
}) {
  if (isReadOnly) return Future.value(false);

  final statusText = switch (status) {
    ReviewTaskStatus.pending => 'pending',
    ReviewTaskStatus.done => '已完成',
    ReviewTaskStatus.skipped => '已跳过',
  };

  final content = switch (status) {
    ReviewTaskStatus.pending => '当前轮次为第 $round 轮，将删除第 $round 轮复习任务。该操作不可恢复，是否确认？',
    ReviewTaskStatus.done ||
    ReviewTaskStatus.skipped =>
      '当前轮次为第 $round 轮（$statusText），将删除第 $round 轮复习任务。该操作会影响历史统计连续性，是否确认？',
  };

  return showDialog<bool>(
    context: context,
    builder: (context) {
      return AlertDialog(
        title: const Text('减少复习轮次'),
        content: Text(content),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('确认减少'),
          ),
        ],
      );
    },
  );
}

Future<void> _showViewPlanSheet(
  BuildContext context, {
  required List<ReviewTaskViewEntity> plan,
}) async {
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) {
      final sorted = [...plan]..sort((a, b) => a.reviewRound.compareTo(b.reviewRound));
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('完整复习计划', style: AppTypography.h2(context)),
              const SizedBox(height: AppSpacing.sm),
              Flexible(
                child: ListView.builder(
                  shrinkWrap: true,
                  itemCount: sorted.length,
                  itemBuilder: (context, index) {
                    final t = sorted[index];
                    final statusText = switch (t.status) {
                      ReviewTaskStatus.pending => '待复习',
                      ReviewTaskStatus.done => '已完成',
                      ReviewTaskStatus.skipped => '已跳过',
                    };
                    return ListTile(
                      dense: true,
                      title: Text('第${t.reviewRound}轮 · ${DateFormat('yyyy-MM-dd').format(t.scheduledDate)}'),
                      subtitle: Text(statusText),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}

Future<void> _showAdjustPlanSheet(
  BuildContext context, {
  required List<ReviewTaskViewEntity> plan,
  required bool isReadOnly,
  required Future<void> Function(int reviewRound, DateTime newDate) onAdjust,
}) async {
  if (isReadOnly) return;
  await showModalBottomSheet<void>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (context) {
      final sorted = [...plan]..sort((a, b) => a.reviewRound.compareTo(b.reviewRound));
      final tomorrow = DateTime(DateTime.now().year, DateTime.now().month, DateTime.now().day)
          .add(const Duration(days: 1));

      DateTime minAllowedFor(int index) {
        final prev = index > 0 ? sorted[index - 1] : null;
        if (prev == null) return tomorrow;
        final prevDay = DateTime(prev.scheduledDate.year, prev.scheduledDate.month, prev.scheduledDate.day);
        final candidate = prevDay.add(const Duration(days: 1));
        return candidate.isAfter(tomorrow) ? candidate : tomorrow;
      }

      DateTime maxAllowedFor(int index) {
        final next = index + 1 < sorted.length ? sorted[index + 1] : null;
        if (next == null) {
          // 无硬性限制：DatePicker 需要 lastDate，这里给一个很大的上界（不作为强制产品限制）。
          return DateTime.now().add(const Duration(days: 3650));
        }
        final nextDay = DateTime(next.scheduledDate.year, next.scheduledDate.month, next.scheduledDate.day);
        return nextDay.subtract(const Duration(days: 1));
      }

      Future<void> pick(int index, {required bool isAdvance}) async {
        final t = sorted[index];
        if (t.status != ReviewTaskStatus.pending) return;

        final min = minAllowedFor(index);
        final max = maxAllowedFor(index);
        if (max.isBefore(min)) return;

        final current = DateTime(t.scheduledDate.year, t.scheduledDate.month, t.scheduledDate.day);
        final suggested = isAdvance
            ? current.subtract(const Duration(days: 1))
            : current.add(const Duration(days: 1));
        final initial = suggested.isBefore(min)
            ? min
            : (suggested.isAfter(max) ? max : suggested);

        final picked = await showDatePicker(
          context: context,
          initialDate: initial,
          firstDate: min,
          lastDate: max,
          helpText: '建议不超过 1 年（仅提示，不强制）',
        );
        if (picked == null) return;
        await onAdjust(t.reviewRound, picked);
      }

      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('调整后续复习计划', style: AppTypography.h2(context)),
              const SizedBox(height: AppSpacing.sm),
              Expanded(
                child: ListView.builder(
                  itemCount: sorted.length,
                  itemBuilder: (context, index) {
                    final t = sorted[index];
                    final statusText = switch (t.status) {
                      ReviewTaskStatus.pending => '待复习',
                      ReviewTaskStatus.done => '已完成',
                      ReviewTaskStatus.skipped => '已跳过',
                    };
                    final min = minAllowedFor(index);
                    final max = maxAllowedFor(index);
                    final canAdjust =
                        t.status == ReviewTaskStatus.pending && !max.isBefore(min);

                    return Card(
                      child: Padding(
                        padding: const EdgeInsets.all(AppSpacing.md),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '第${t.reviewRound}轮 · ${DateFormat('yyyy-MM-dd').format(t.scheduledDate)}',
                              style: AppTypography.body(context).copyWith(fontWeight: FontWeight.w700),
                            ),
                            const SizedBox(height: 4),
                            Text(statusText, style: AppTypography.bodySecondary(context)),
                            const SizedBox(height: AppSpacing.sm),
                            Row(
                              children: [
                                OutlinedButton(
                                  onPressed: canAdjust ? () => pick(index, isAdvance: true) : null,
                                  child: const Text('提前'),
                                ),
                                const SizedBox(width: AppSpacing.sm),
                                OutlinedButton(
                                  onPressed: canAdjust ? () => pick(index, isAdvance: false) : null,
                                  child: const Text('延后'),
                                ),
                              ],
                            ),
                            const SizedBox(height: 4),
                            Text(
                              '可选范围：${DateFormat('yyyy-MM-dd').format(min)} ~ ${DateFormat('yyyy-MM-dd').format(max)}',
                              style: AppTypography.bodySecondary(context).copyWith(fontSize: 12),
                            ),
                          ],
                        ),
                      ),
                    );
                  },
                ),
              ),
            ],
          ),
        ),
      );
    },
  );
}
