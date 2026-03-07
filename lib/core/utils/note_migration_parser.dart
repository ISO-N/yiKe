/// 文件用途：工具类 - 备注智能解析（note → description + subtasks）。
/// 作者：Codex
/// 创建日期：2026-03-01
library;

/// 备注迁移解析结果。
class NoteMigrationResult {
  /// 构造函数。
  const NoteMigrationResult({required this.description, required this.subtasks});

  /// 描述（可空）。
  final String? description;

  /// 子任务列表（可为空）。
  final List<String> subtasks;
}

/// 备注迁移解析器。
///
/// 说明：
/// - 用于三类入口保持一致的“产品可解释”迁移规则：
///   1) 数据迁移（历史 learning_items.note）
///   2) CSV 旧表头导入（备注列）
///   3) OCR/模板等仍输出“纯文本备注”的链路
class NoteMigrationParser {
  NoteMigrationParser._();

  /// 将 note 文本解析为 description + subtasks。
  ///
  /// 规则（与 spec 对齐）：
  /// - 含列表符号（-、•、1.、①）：按行解析为 subtasks
  /// - 单行/短文本（<50 字符）：迁移到 description
  /// - 多行且无列表符号：第一段 → description，其余行 → subtasks
  static NoteMigrationResult parse(String raw) {
    final normalized = raw.replaceAll('\r\n', '\n').replaceAll('\r', '\n');
    final trimmedAll = normalized.trim();
    if (trimmedAll.isEmpty) {
      return const NoteMigrationResult(description: null, subtasks: []);
    }

    final lines = normalized.split('\n');
    final hasList = lines.any((l) => _looksLikeListLine(l));
    if (hasList) {
      final subtasks = <String>[];
      for (final l in lines) {
        final t = l.trim();
        if (t.isEmpty) continue;
        final cleaned = _stripListPrefix(t).trim();
        if (cleaned.isEmpty) continue;
        subtasks.add(cleaned);
      }
      return NoteMigrationResult(description: null, subtasks: subtasks);
    }

    // 单行：统一迁移到 description（短文本优先满足 spec，长文本也更符合“描述”的语义）。
    final nonEmptyLines =
        lines.map((e) => e.trimRight()).where((e) => e.trim().isNotEmpty).toList();
    if (nonEmptyLines.length <= 1) {
      return NoteMigrationResult(description: trimmedAll, subtasks: const []);
    }

    // 多行且无列表符号：第一段 → description，其余行 → subtasks
    final paragraphLines = <String>[];
    var cursor = 0;
    for (; cursor < lines.length; cursor++) {
      final line = lines[cursor];
      if (line.trim().isEmpty) break;
      paragraphLines.add(line.trimRight());
    }
    final description =
        paragraphLines.join('\n').trim().isEmpty ? null : paragraphLines.join('\n').trim();

    // 剩余部分：按行拆为 subtasks（跳过空行）。
    final subtasks = <String>[];
    for (var i = cursor; i < lines.length; i++) {
      final t = lines[i].trim();
      if (t.isEmpty) continue;
      subtasks.add(t);
    }

    // 保护：若第一段为空（例如开头就是空行），则将首个非空行作为 description。
    if (description == null) {
      final first = nonEmptyLines.first.trim();
      final rest = nonEmptyLines.skip(1).map((e) => e.trim()).where((e) => e.isNotEmpty).toList();
      return NoteMigrationResult(description: first, subtasks: rest);
    }

    // spec：短文本（<50）强调可解释性；这里不强制截断，只保证语义归类正确。
    return NoteMigrationResult(description: description, subtasks: subtasks);
  }

  static bool _looksLikeListLine(String line) {
    final t = line.trimLeft();
    if (t.isEmpty) return false;
    if (t.startsWith('-') || t.startsWith('*') || t.startsWith('•')) return true;
    if (RegExp(r'^\d+[.)]\s+').hasMatch(t)) return true;
    if (RegExp(r'^[①②③④⑤⑥⑦⑧⑨⑩]\s*').hasMatch(t)) return true;
    return false;
  }

  static String _stripListPrefix(String line) {
    var t = line.trimLeft();
    if (t.startsWith('-') || t.startsWith('*') || t.startsWith('•')) {
      t = t.substring(1).trimLeft();
      return t;
    }
    final m1 = RegExp(r'^(\d+)([.)])\s+(.*)$').firstMatch(t);
    if (m1 != null) {
      return (m1.group(3) ?? '').trimLeft();
    }
    final m2 = RegExp(r'^([①②③④⑤⑥⑦⑧⑨⑩])\s*(.*)$').firstMatch(t);
    if (m2 != null) {
      return (m2.group(2) ?? '').trimLeft();
    }
    return t;
  }
}

