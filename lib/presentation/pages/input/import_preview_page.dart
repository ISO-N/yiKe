/// 文件用途：批量导入预览页（ImportPreviewPage），支持 TXT/CSV/Markdown 解析、编辑与导入到录入草稿（F1.1）。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:share_plus/share_plus.dart';

import '../../../core/constants/app_colors.dart';
import '../../../core/constants/app_spacing.dart';
import '../../../core/constants/app_typography.dart';
import '../../../core/utils/file_parser.dart';
import '../../../core/utils/note_migration_parser.dart';
import '../../widgets/glass_card.dart';
import 'draft_learning_item.dart';

class ImportPreviewPage extends ConsumerStatefulWidget {
  /// 批量导入预览页。
  ///
  /// 返回值：页面 Widget。
  /// 异常：无。
  const ImportPreviewPage({
    super.key,
    this.autoPickFileOnOpen = false,
  });

  /// 是否在页面打开后立即弹出文件选择器。
  ///
  /// 说明：
  /// - 默认关闭，避免用户进入后立即被文件选择器打断，影响“粘贴导入”使用体验
  /// - 测试场景也可借此禁用平台文件选择器
  final bool autoPickFileOnOpen;

  @override
  ConsumerState<ImportPreviewPage> createState() => _ImportPreviewPageState();
}

class _ImportPreviewPageState extends ConsumerState<ImportPreviewPage>
    with SingleTickerProviderStateMixin {
  bool _loading = false;
  String? _sourceSummary;
  String? _error;
  List<_PreviewItem> _items = const [];
  late final TabController _tabController;
  late final TextEditingController _pasteController;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _tabController.addListener(() {
      if (!_tabController.indexIsChanging && mounted) {
        setState(() {});
      }
    });
    _pasteController = TextEditingController();

    if (widget.autoPickFileOnOpen) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _pickFile());
    }
  }

  Future<void> _pickFile() async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: const ['txt', 'csv', 'md', 'markdown'],
      );
      if (!mounted) return;
      final path = result?.files.single.path;
      if (path == null) {
        setState(() => _loading = false);
        return;
      }

      final parsed = await FileParser.parseFile(path);
      if (!mounted) return;
      if (parsed.isEmpty) {
        setState(() {
          _loading = false;
          _sourceSummary = path.split(RegExp(r'[\\/]')).last;
          _items = const [];
          _error = '未识别到有效内容，请检查文件格式或内容是否为空。';
        });
        return;
      }

      setState(() {
        _loading = false;
        _sourceSummary = path.split(RegExp(r'[\\/]')).last;
        _items = parsed
            .map((e) => _PreviewItem(item: e, selected: e.isValid))
            .toList();
      });
    } catch (e) {
      setState(() {
        _loading = false;
        _error = '解析失败：$e';
      });
    }
  }

  Future<void> _parsePastedContent() async {
    if (_loading) return;

    final raw = _pasteController.text;
    if (raw.trim().isEmpty) {
      setState(() {
        _sourceSummary = '粘贴内容';
        _items = const [];
        _error = '请输入内容';
      });
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final parsed = FileParser.parsePastedContent(raw);
      if (!mounted) return;
      if (parsed.isEmpty) {
        setState(() {
          _loading = false;
          _sourceSummary = '粘贴内容';
          _items = const [];
          _error = '未识别到有效内容';
        });
        return;
      }

      setState(() {
        _loading = false;
        _sourceSummary = '粘贴内容';
        _items = parsed
            .map((e) => _PreviewItem(item: e, selected: e.isValid))
            .toList();
      });
    } catch (e) {
      setState(() {
        _loading = false;
        _sourceSummary = '粘贴内容';
        _items = const [];
        _error = '解析失败：$e';
      });
    }
  }

  Future<void> _editItem(int index) async {
    final current = _items[index];
    final titleController = TextEditingController(text: current.item.title);
    final descController = TextEditingController(
      text: current.item.description ?? '',
    );
    final subtasksController = TextEditingController(
      text: current.item.subtasks.join('\n'),
    );
    final tagsController = TextEditingController(
      text: current.item.tags.join(', '),
    );

    final ok = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('编辑导入条目'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextField(
                  controller: titleController,
                  decoration: const InputDecoration(labelText: '标题（必填）'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextField(
                  controller: descController,
                  minLines: 2,
                  maxLines: 6,
                  decoration: const InputDecoration(labelText: '描述（选填）'),
                ),
                const SizedBox(height: AppSpacing.md),
                TextField(
                  controller: subtasksController,
                  minLines: 3,
                  maxLines: 8,
                  decoration: const InputDecoration(
                    labelText: '子任务（选填，每行一条）',
                  ),
                ),
                const SizedBox(height: AppSpacing.md),
                TextField(
                  controller: tagsController,
                  decoration: const InputDecoration(labelText: '标签（选填，用逗号分隔）'),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('保存'),
            ),
          ],
        );
      },
    );

    if (!mounted) return;
    if (ok != true) return;

    final title = titleController.text.trim();
    final desc = descController.text.trim();
    final subtasks = _parseSubtasksText(subtasksController.text);
    final tags = tagsController.text
        .split(RegExp(r'[，,]'))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toSet()
        .toList();

    setState(() {
      _items = [
        ..._items.take(index),
        current.copyWith(
          item: current.item.copyWith(
            title: title,
            description: desc.isEmpty ? null : desc,
            subtasks: subtasks,
            tags: tags,
            errorMessage: title.isEmpty ? '标题为空' : null,
          ),
          // 编辑后：仅当条目有效才默认勾选。
          selected: title.isNotEmpty,
        ),
        ..._items.skip(index + 1),
      ];
    });
  }

  Future<void> _confirmImport() async {
    final selected = _items
        .where((e) => e.selected)
        .map((e) => e.item)
        .toList();
    if (selected.isEmpty) {
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请至少选择一条有效内容')));
      return;
    }

    final duplicates = _findDuplicateTitles(selected);
    if (duplicates.isNotEmpty) {
      final action = await _askDuplicateAction(duplicates.length);
      if (!mounted) return;
      if (action == null) return;
      final resolved = _resolveDuplicates(selected, action);
      Navigator.of(context).pop(resolved.map(_toDraft).toList());
      return;
    }

    Navigator.of(context).pop(selected.map(_toDraft).toList());
  }

  DraftLearningItem _toDraft(ParsedItem e) {
    return DraftLearningItem(
      title: e.title,
      description: e.description,
      subtasks: e.subtasks,
      tags: e.tags,
    );
  }

  Set<String> _findDuplicateTitles(List<ParsedItem> items) {
    final seen = <String>{};
    final dup = <String>{};
    for (final item in items) {
      final title = item.title.trim();
      if (title.isEmpty) continue;
      if (!seen.add(title)) dup.add(title);
    }
    return dup;
  }

  Future<_DuplicateAction?> _askDuplicateAction(int count) {
    return showDialog<_DuplicateAction>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('检测到重复标题'),
          content: Text('当前选中内容中存在 $count 个重复标题，是否覆盖或跳过重复项？'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(null),
              child: const Text('取消'),
            ),
            OutlinedButton(
              onPressed: () => Navigator.of(context).pop(_DuplicateAction.skip),
              child: const Text('跳过重复'),
            ),
            FilledButton(
              onPressed: () =>
                  Navigator.of(context).pop(_DuplicateAction.overwrite),
              child: const Text('覆盖重复'),
            ),
          ],
        );
      },
    );
  }

  List<ParsedItem> _resolveDuplicates(
    List<ParsedItem> items,
    _DuplicateAction action,
  ) {
    if (action == _DuplicateAction.skip) {
      // 跳过重复：保留第一次出现。
      final seen = <String>{};
      final list = <ParsedItem>[];
      for (final item in items) {
        final title = item.title.trim();
        if (title.isEmpty) continue;
        if (seen.add(title)) list.add(item);
      }
      return list;
    }

    // 覆盖重复：保留最后一次出现。
    final map = <String, ParsedItem>{};
    for (final item in items) {
      final title = item.title.trim();
      if (title.isEmpty) continue;
      map[title] = item;
    }
    return map.values.toList();
  }

  Widget _buildPreviewSubtitle(BuildContext context, ParsedItem item) {
    final desc = (item.description ?? '').trim();
    if (desc.isNotEmpty) {
      return Text(desc, maxLines: 2, overflow: TextOverflow.ellipsis);
    }
    if (item.subtasks.isNotEmpty) {
      return Text(
        '${item.subtasks.length} 个子任务',
        style: AppTypography.bodySecondary(context),
      );
    }
    return Text('无描述', style: AppTypography.bodySecondary(context));
  }

  List<String> _parseSubtasksText(String raw) {
    final normalized = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final lines = normalized.split('\n');
    final result = <String>[];
    for (final line in lines) {
      final t = line.trim();
      if (t.isEmpty) continue;
      final parsed = NoteMigrationParser.parse(t);
      if (parsed.subtasks.isNotEmpty) {
        result.addAll(parsed.subtasks);
      } else if (parsed.description?.trim().isNotEmpty == true) {
        result.add(parsed.description!.trim());
      }
    }
    return result;
  }

  Future<void> _downloadTemplate(_TemplateFormat fmt) async {
    final assetPath = switch (fmt) {
      _TemplateFormat.txt => 'assets/templates/import_template.txt',
      _TemplateFormat.csv => 'assets/templates/import_template.csv',
      _TemplateFormat.md => 'assets/templates/import_template.md',
    };
    final ext = switch (fmt) {
      _TemplateFormat.txt => 'txt',
      _TemplateFormat.csv => 'csv',
      _TemplateFormat.md => 'md',
    };

    try {
      final content = await rootBundle.loadString(assetPath);
      final dir = await getTemporaryDirectory();
      final file =
          File('${dir.path}${Platform.pathSeparator}yike_import_template.$ext');
      await file.writeAsString(content, flush: true);

      await Share.shareXFiles([XFile(file.path)], text: '忆刻批量导入模板（$ext）');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('下载模板失败：$e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final selectedCount = _items.where((e) => e.selected).length;
    final isPasteTab = _tabController.index == 1;

    return Scaffold(
      appBar: AppBar(
        title: const Text('批量导入'),
        actions: [
          IconButton(
            tooltip: '选择文件',
            onPressed: _loading ? null : _pickFile,
            icon: const Icon(Icons.folder_open),
          ),
          PopupMenuButton<_TemplateFormat>(
            tooltip: '下载模板',
            onSelected: (fmt) => _downloadTemplate(fmt),
            itemBuilder: (context) => const [
              PopupMenuItem(value: _TemplateFormat.txt, child: Text('TXT 模板')),
              PopupMenuItem(value: _TemplateFormat.csv, child: Text('CSV 模板')),
              PopupMenuItem(
                value: _TemplateFormat.md,
                child: Text('Markdown 模板'),
              ),
            ],
            icon: const Icon(Icons.download),
          ),
          TextButton(
            onPressed: _loading ? null : _confirmImport,
            child: const Text('导入'),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              GlassCard(
                child: Padding(
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      TabBar(
                        controller: _tabController,
                        tabs: const [
                          Tab(text: '文件选择'),
                          Tab(text: '粘贴导入'),
                        ],
                      ),
                      const SizedBox(height: AppSpacing.md),
                      SizedBox(
                        height: 160,
                        child: TabBarView(
                          controller: _tabController,
                          children: [
                            _buildFileImportPanel(),
                            _buildPasteImportPanel(),
                          ],
                        ),
                      ),
                      const Divider(height: AppSpacing.xl),
                      Text('导入预览', style: AppTypography.h2(context)),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        _sourceSummary == null
                            ? (isPasteTab
                                  ? '请在上方输入 Markdown 或纯文本内容并点击解析'
                                  : '请选择 TXT/CSV/Markdown 文件进行导入')
                            : '来源：$_sourceSummary',
                        style: AppTypography.bodySecondary(context),
                      ),
                      if (_error != null) ...[
                        const SizedBox(height: AppSpacing.sm),
                        Text(
                          _error!,
                          style: AppTypography.bodySecondary(
                            context,
                          ).copyWith(color: AppColors.error),
                        ),
                      ],
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        '已选 $selectedCount 条 / 共 ${_items.length} 条',
                        style: AppTypography.bodySecondary(context),
                      ),
                      const SizedBox(height: AppSpacing.md),
                      Text(
                        '格式说明',
                        style: AppTypography.h2(context).copyWith(fontSize: 14),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'TXT：每行一个标题\n'
                        'CSV：旧表头（标题,备注,标签）与新表头（标题,描述,子任务,标签）均支持\n'
                        'Markdown：以 # 标题分段，-/*/• 开头为子任务，标签行以“标签:”或“tags:”开头\n'
                        '提示：可通过右上角“下载模板”获取三种格式示例。',
                        style: AppTypography.bodySecondary(context),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              Expanded(
                child: _loading
                    ? const Center(child: CircularProgressIndicator())
                    : _items.isEmpty
                    ? _EmptyHint(isPasteTab: isPasteTab)
                    : ListView.separated(
                        itemCount: _items.length,
                        separatorBuilder: (context, index) =>
                            const SizedBox(height: AppSpacing.md),
                        itemBuilder: (context, index) {
                          final it = _items[index];
                          final hasError = it.item.errorMessage != null;
                          final isDark =
                              Theme.of(context).brightness == Brightness.dark;
                          final normalBorderColor = isDark
                              ? AppColors.darkGlassBorder
                              : AppColors.glassBorder;
                          final borderColor = hasError
                              ? AppColors.error
                              : normalBorderColor;
                          return GlassCard(
                            child: Container(
                              decoration: BoxDecoration(
                                border: Border.all(color: borderColor),
                                borderRadius: BorderRadius.circular(16),
                              ),
                              child: ListTile(
                                leading: Checkbox(
                                  value: it.selected,
                                  onChanged: hasError
                                      ? null
                                      : (v) => setState(
                                          () => _items = [
                                            ..._items.take(index),
                                            it.copyWith(selected: v ?? false),
                                            ..._items.skip(index + 1),
                                          ],
                                        ),
                                ),
                                title: Text(
                                  it.item.title.trim().isEmpty
                                      ? '（无标题）'
                                      : it.item.title,
                                  maxLines: 1,
                                  overflow: TextOverflow.ellipsis,
                                ),
                                subtitle: hasError
                                    ? Text(
                                        it.item.errorMessage!,
                                        style: const TextStyle(
                                          color: AppColors.error,
                                        ),
                                      )
                                    : _buildPreviewSubtitle(context, it.item),
                                trailing: Row(
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    IconButton(
                                      tooltip: '编辑',
                                      onPressed: () => _editItem(index),
                                      icon: const Icon(Icons.edit),
                                    ),
                                    IconButton(
                                      tooltip: '删除',
                                      onPressed: () => setState(() {
                                        _items = [
                                          ..._items.take(index),
                                          ..._items.skip(index + 1),
                                        ];
                                      }),
                                      icon: const Icon(Icons.delete),
                                    ),
                                  ],
                                ),
                                onTap: () => _editItem(index),
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建文件导入面板。
  Widget _buildFileImportPanel() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '选择本地 TXT / CSV / Markdown 文件，解析后会直接复用下方预览、编辑和导入流程。',
          style: AppTypography.bodySecondary(context),
        ),
        const Spacer(),
        FilledButton.icon(
          onPressed: _loading ? null : _pickFile,
          icon: const Icon(Icons.folder_open),
          label: const Text('选择文件'),
        ),
      ],
    );
  }

  /// 构建粘贴导入面板。
  Widget _buildPasteImportPanel() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: TextField(
            key: const Key('paste_import_text_field'),
            controller: _pasteController,
            expands: true,
            minLines: null,
            maxLines: null,
            textAlignVertical: TextAlignVertical.top,
            decoration: const InputDecoration(
              hintText: '支持 Markdown 标题分段，或纯文本每行一个标题',
              border: OutlineInputBorder(),
            ),
          ),
        ),
        const SizedBox(height: AppSpacing.md),
        Align(
          alignment: Alignment.centerLeft,
          child: FilledButton.icon(
            key: const Key('paste_import_parse_button'),
            onPressed: _loading ? null : _parsePastedContent,
            icon: const Icon(Icons.auto_awesome),
            label: const Text('解析'),
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _tabController.dispose();
    _pasteController.dispose();
    super.dispose();
  }
}

class _EmptyHint extends StatelessWidget {
  const _EmptyHint({required this.isPasteTab});

  /// 是否处于粘贴导入页签。
  final bool isPasteTab;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Text(
        isPasteTab ? '暂无导入内容\n粘贴内容后点击“解析”' : '暂无导入内容\n点击“选择文件”开始导入',
        style: AppTypography.bodySecondary(context),
        textAlign: TextAlign.center,
      ),
    );
  }
}

class _PreviewItem {
  const _PreviewItem({required this.item, required this.selected});

  final ParsedItem item;
  final bool selected;

  _PreviewItem copyWith({ParsedItem? item, bool? selected}) {
    return _PreviewItem(
      item: item ?? this.item,
      selected: selected ?? this.selected,
    );
  }
}

enum _DuplicateAction { skip, overwrite }

enum _TemplateFormat { txt, csv, md }
