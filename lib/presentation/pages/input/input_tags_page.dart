/// 文件用途：录入页标签选择页面，展示全部标签并支持多选切换。
/// 作者：Codex
/// 创建日期：2026-03-08
library;

import 'package:flutter/material.dart';

import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../domain/entities/tag_usage_stat.dart';
import '../../widgets/glass_card.dart';

/// 录入页“全部标签”选择页面。
class InputTagsPage extends StatefulWidget {
  /// 构造函数。
  const InputTagsPage({
    super.key,
    required this.tags,
    required this.initialSelectedTags,
  });

  /// 已排序的全部标签。
  final List<TagUsageStatEntity> tags;

  /// 初始选中标签。
  final List<String> initialSelectedTags;

  @override
  State<InputTagsPage> createState() => _InputTagsPageState();
}

class _InputTagsPageState extends State<InputTagsPage> {
  late final TextEditingController _searchController;
  late final Set<String> _selectedTags;

  @override
  void initState() {
    super.initState();
    _searchController = TextEditingController();
    _selectedTags = widget.initialSelectedTags.toSet();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _toggleTag(String tag) {
    setState(() {
      if (_selectedTags.contains(tag)) {
        _selectedTags.remove(tag);
      } else {
        _selectedTags.add(tag);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final keyword = _searchController.text.trim();
    final visibleTags = keyword.isEmpty
        ? widget.tags
        : widget.tags.where((entry) => entry.tag.contains(keyword)).toList();

    return Scaffold(
      appBar: AppBar(
        title: const Text('全部标签'),
        actions: [
          TextButton(
            onPressed: () {
              final selected = widget.tags
                  .where((entry) => _selectedTags.contains(entry.tag))
                  .map((entry) => entry.tag)
                  .toList();
              Navigator.of(context).pop(selected);
            },
            child: const Text('完成'),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.lg),
          children: [
            TextField(
              controller: _searchController,
              decoration: const InputDecoration(
                labelText: '搜索标签',
                prefixIcon: Icon(Icons.search),
              ),
              onChanged: (_) => setState(() {}),
            ),
            const SizedBox(height: AppSpacing.lg),
            if (visibleTags.isEmpty)
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.xl),
                  child: Text(
                    keyword.isEmpty ? '暂无标签' : '未找到匹配标签',
                    style: AppTypography.bodySecondary(context),
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            else
              ...visibleTags.map((entry) {
                final checked = _selectedTags.contains(entry.tag);
                return Padding(
                  padding: const EdgeInsets.only(bottom: AppSpacing.sm),
                  child: GlassCard(
                    child: CheckboxListTile(
                      value: checked,
                      title: Text(entry.tag),
                      subtitle: Text(
                        '近 7 天 ${entry.recentUseCount} 次 · 累计 ${entry.totalUseCount} 次',
                        style: AppTypography.bodySecondary(context),
                      ),
                      controlAffinity: ListTileControlAffinity.leading,
                      onChanged: (_) => _toggleTag(entry.tag),
                    ),
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }
}
