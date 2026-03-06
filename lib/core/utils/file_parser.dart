/// 文件用途：工具类 - 文件解析器（FileParser），用于批量导入（F1.1）。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'dart:convert';
import 'dart:io';

import 'package:csv/csv.dart';
import 'package:gbk_codec/gbk_codec.dart';

import 'note_migration_parser.dart';

/// 解析后的条目（用于导入预览）。
class ParsedItem {
  /// 构造函数。
  const ParsedItem({
    required this.title,
    this.description,
    this.subtasks = const [],
    this.tags = const [],
    this.errorMessage,
  });

  /// 标题（必填）。
  final String title;

  /// 描述（可选）。
  final String? description;

  /// 子任务清单（可选）。
  final List<String> subtasks;

  /// 标签（可选）。
  final List<String> tags;

  /// 行级错误（可选）：用于预览页标红提示。
  final String? errorMessage;

  bool get isValid => errorMessage == null && title.trim().isNotEmpty;

  ParsedItem copyWith({
    String? title,
    String? description,
    List<String>? subtasks,
    List<String>? tags,
    String? errorMessage,
  }) {
    return ParsedItem(
      title: title ?? this.title,
      description: description ?? this.description,
      subtasks: subtasks ?? this.subtasks,
      tags: tags ?? this.tags,
      errorMessage: errorMessage,
    );
  }
}

/// 文件解析器。
class FileParser {
  FileParser._();

  /// 从本地文件解析学习内容条目。
  ///
  /// 支持：
  /// - TXT：每行一条标题
  /// - CSV：
  ///   - 旧格式：标题,备注,标签
  ///   - 新格式：标题,描述,子任务,标签
  /// - Markdown：以 `#` 标题行分段，按规则解析 description/subtasks/tags
  ///
  /// 异常：文件读取或解析失败时可能抛出异常。
  static Future<List<ParsedItem>> parseFile(String filePath) async {
    final file = File(filePath);
    final bytes = await file.readAsBytes();
    final ext = _lowerExt(filePath);

    if (ext == '.txt') {
      final content = _decodeUtf8OrGbk(bytes);
      return _parseTxt(content);
    }
    if (ext == '.csv') {
      final content = _decodeUtf8OrGbk(bytes);
      return _parseCsv(content);
    }
    if (ext == '.md' || ext == '.markdown') {
      final content = _decodeUtf8OrGbk(bytes);
      return _parseMarkdown(content);
    }

    throw ArgumentError('不支持的文件格式：$ext');
  }

  /// 解析粘贴导入内容。
  ///
  /// 说明：
  /// - 若包含 Markdown 标题行，则按 Markdown 规则解析
  /// - 否则按 TXT 规则解析（每行一个标题）
  /// - 仅保留包含有效字符（中文/英文/数字）的条目，避免“只有标点符号”被误识别为内容
  static List<ParsedItem> parsePastedContent(String content) {
    final normalized = content.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    if (normalized.trim().isEmpty) return const [];

    final looksLikeMarkdown = normalized
        .split('\n')
        .any((line) => RegExp(r'^\s*#+\s+').hasMatch(line));
    final parsed = looksLikeMarkdown
        ? parseMarkdownContent(normalized)
        : parseTxtContent(normalized);

    return parsed.where((item) => _hasMeaningfulContent(item)).toList();
  }

  /// 解析粘贴的纯文本内容。
  ///
  /// 说明：暴露该方法便于 UI 直接复用 TXT 解析规则。
  static List<ParsedItem> parseTxtContent(String content) {
    return _parseTxt(content);
  }

  /// 解析粘贴的 Markdown 内容。
  ///
  /// 说明：暴露该方法便于 UI 直接复用 Markdown 解析规则。
  static List<ParsedItem> parseMarkdownContent(String content) {
    return _parseMarkdown(content);
  }

  static String _lowerExt(String filePath) {
    final idx = filePath.lastIndexOf('.');
    if (idx < 0) return '';
    return filePath.substring(idx).toLowerCase();
  }

  /// CSV 支持 GBK/UTF-8 自动识别：优先 UTF-8，失败则回退 GBK。
  static String _decodeUtf8OrGbk(List<int> bytes) {
    try {
      return utf8.decode(bytes, allowMalformed: false);
    } catch (_) {
      // 关键逻辑：GBK/GB2312 在部分历史 CSV 中常见，这里做兜底解码。
      // 保护：若第三方 GBK 解码在特定环境不可用/异常，则回退为“允许损坏的 UTF-8”，避免导入流程直接崩溃。
      try {
        return gbk.decode(bytes);
      } catch (_) {
        return utf8.decode(bytes, allowMalformed: true);
      }
    }
  }

  /// 解析 TXT（每行一条）。
  static List<ParsedItem> _parseTxt(String content) {
    final lines = content.split(RegExp(r'\r?\n'));
    final items = <ParsedItem>[];
    for (final line in lines) {
      final title = line.trim();
      if (title.isEmpty) continue;
      items.add(ParsedItem(title: title));
    }
    return items;
  }

  /// 解析 CSV（标题,备注,标签）。
  static List<ParsedItem> _parseCsv(String content) {
    final converter = const CsvToListConverter(
      eol: '\n',
      shouldParseNumbers: false,
    );
    final rows = converter.convert(content);
    if (rows.isEmpty) return const [];

    // 允许首行表头：
    // - 旧格式：标题,备注,标签
    // - 新格式：标题,描述,子任务,标签
    var startIndex = 0;
    List<String>? headers;
    final firstRow = rows.first;
    if (firstRow.isNotEmpty) {
      final cells = firstRow.map((e) => (e ?? '').toString().trim()).toList();
      final hasHeader =
          cells.isNotEmpty &&
          (cells[0] == '标题' || cells[0].toLowerCase() == 'title');
      if (hasHeader) {
        startIndex = 1;
        headers = cells;
      }
    }

    int? indexOfHeader(List<String> hs, List<String> keys) {
      for (var i = 0; i < hs.length; i++) {
        final v = hs[i].trim();
        for (final key in keys) {
          if (v == key || v.toLowerCase() == key.toLowerCase()) return i;
        }
      }
      return null;
    }

    final items = <ParsedItem>[];
    for (var i = startIndex; i < rows.length; i++) {
      final row = rows[i];

      String cell(int idx) =>
          idx >= 0 && idx < row.length ? (row[idx] ?? '').toString() : '';

      final titleIndex =
          headers == null
              ? 0
              : (indexOfHeader(headers, const ['标题', 'title']) ?? 0);
      final noteIndex =
          headers == null
              ? 1
              : (indexOfHeader(headers, const ['备注', 'note']) ?? -1);
      final descIndex =
          headers == null
              ? -1
              : (indexOfHeader(headers, const ['描述', 'description']) ?? -1);
      final subtasksIndex =
          headers == null
              ? -1
              : (indexOfHeader(headers, const ['子任务', 'subtasks']) ?? -1);
      final tagsIndex =
          headers == null
              ? 2
              : (indexOfHeader(headers, const ['标签', 'tags']) ?? -1);

      final title = cell(titleIndex).trim();
      final rawTags = tagsIndex >= 0 ? cell(tagsIndex).trim() : '';
      final tags = _parseTags(rawTags);

      // 1) 旧格式：使用“备注”列，走统一的智能解析规则，保证与迁移行为一致。
      final rawNote = noteIndex >= 0 ? cell(noteIndex).trim() : '';

      // 2) 新格式：描述与子任务拆分列。
      final rawDesc = descIndex >= 0 ? cell(descIndex).trim() : '';
      final rawSubtasks = subtasksIndex >= 0 ? cell(subtasksIndex).trim() : '';

      final hasNewColumns = descIndex >= 0 || subtasksIndex >= 0;

      String? description;
      List<String> subtasks = const [];

      if (hasNewColumns) {
        description = rawDesc.isEmpty ? null : rawDesc;
        subtasks = _parseSubtasksCell(rawSubtasks);
      } else if (rawNote.isNotEmpty) {
        final parsed = NoteMigrationParser.parse(rawNote);
        description = parsed.description;
        subtasks = parsed.subtasks;
      }

      if (title.isEmpty) {
        items.add(
          ParsedItem(
            title: '',
            description: description,
            subtasks: subtasks,
            tags: tags,
            errorMessage: '标题为空',
          ),
        );
        continue;
      }

      items.add(
        ParsedItem(
          title: title,
          description: description,
          subtasks: subtasks,
          tags: tags,
        ),
      );
    }
    return items;
  }

  /// 解析 Markdown（标题行以 # 开头）。
  static List<ParsedItem> _parseMarkdown(String content) {
    final lines = content.split(RegExp(r'\r?\n'));
    final items = <ParsedItem>[];

    String? currentTitle;
    final descLines = <String>[];
    final subtasks = <String>[];
    final tags = <String>[];
    var inDescription = true;

    void flush() {
      if (currentTitle == null) return;
      final title = currentTitle.trim();
      if (title.isEmpty) return;
      final desc = descLines.join('\n').trim();
      items.add(
        ParsedItem(
          title: title,
          description: desc.isEmpty ? null : desc,
          subtasks: List<String>.from(subtasks),
          tags: List<String>.from(tags),
          errorMessage: title.isEmpty ? '标题为空' : null,
        ),
      );
      descLines.clear();
      subtasks.clear();
      tags.clear();
      inDescription = true;
    }

    for (final raw in lines) {
      final line = raw.trimRight();
      final match = RegExp(r'^(#+)\s+(.*)$').firstMatch(line);
      if (match != null) {
        // 新标题开始：提交上一条。
        flush();
        currentTitle = match.group(2)?.trim();
        continue;
      }

      if (currentTitle == null) continue;

      final trimmed = line.trim();
      if (trimmed.isEmpty) {
        // 空行：作为 description 结束条件之一。
        if (inDescription && descLines.isNotEmpty) {
          inDescription = false;
        }
        continue;
      }

      // 标签行：形如 “标签: a,b” 或 “tags: a,b”。
      final tagMatch =
          RegExp(r'^(标签|tags)\s*:\s*(.*)$', caseSensitive: false).firstMatch(
            trimmed,
          );
      if (tagMatch != null) {
        final rawTags = (tagMatch.group(2) ?? '').trim();
        tags
          ..clear()
          ..addAll(_parseTags(rawTags));
        inDescription = false;
        continue;
      }

      // 列表行：子任务。
      if (_looksLikeMarkdownBullet(trimmed)) {
        inDescription = false;
        final content = _stripMarkdownBullet(trimmed).trim();
        if (content.isNotEmpty) subtasks.add(content);
        continue;
      }

      // 普通文本：优先归入 description；若 description 已截止，则作为“非规范但可用”的子任务行兜底。
      if (inDescription) {
        descLines.add(line);
      } else {
        subtasks.add(trimmed);
      }
    }
    flush();

    return items;
  }

  /// 标签分隔规则：支持中文/英文逗号，兼容 CSV 中常见的分号分隔。
  static List<String> _parseTags(String raw) {
    if (raw.trim().isEmpty) return const [];
    return raw
        .split(RegExp(r'[，,;；]'))
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toSet()
        .toList();
  }

  static List<String> _parseSubtasksCell(String raw) {
    if (raw.trim().isEmpty) return const [];

    final normalized = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final lines = normalized.split('\n');
    final result = <String>[];
    for (final l in lines) {
      final t = l.trim();
      if (t.isEmpty) continue;
      final cleaned = NoteMigrationParser.parse(t).subtasks.isNotEmpty
          ? NoteMigrationParser.parse(t).subtasks.first
          : t;
      final v = cleaned.trim();
      if (v.isNotEmpty) result.add(v);
    }
    return result;
  }

  static bool _looksLikeMarkdownBullet(String trimmedLine) {
    return trimmedLine.startsWith('- ') ||
        trimmedLine.startsWith('* ') ||
        trimmedLine.startsWith('• ');
  }

  static String _stripMarkdownBullet(String trimmedLine) {
    final t = trimmedLine.trimLeft();
    if (t.startsWith('-') || t.startsWith('*') || t.startsWith('•')) {
      return t.substring(1).trimLeft();
    }
    return t;
  }

  /// 判断条目是否包含可识别的有效内容。
  ///
  /// 说明：
  /// - 用于粘贴导入场景，避免“!!!”“……”这类纯标点被当作标题
  /// - 只要求标题、描述、子任务、标签中任意一处包含中文/英文/数字即可
  static bool _hasMeaningfulContent(ParsedItem item) {
    final pattern = RegExp(r'[A-Za-z0-9\u4E00-\u9FFF]');
    if (pattern.hasMatch(item.title)) return true;
    if (item.description != null && pattern.hasMatch(item.description!)) {
      return true;
    }
    if (item.subtasks.any(pattern.hasMatch)) return true;
    if (item.tags.any(pattern.hasMatch)) return true;
    return false;
  }
}
