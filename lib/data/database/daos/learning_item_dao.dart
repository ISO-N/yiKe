/// 文件用途：LearningItemDao - 学习内容数据库访问封装（Drift）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'dart:convert';

import 'package:drift/drift.dart';

import '../../../domain/entities/tag_usage_stat.dart';
import '../database.dart';

/// 学习内容 DAO。
///
/// 说明：封装学习内容相关的 CRUD 与查询逻辑。
class LearningItemDao {
  /// 构造函数。
  ///
  /// 参数：
  /// - [db] 数据库实例。
  /// 异常：无。
  LearningItemDao(this.db);

  final AppDatabase db;

  /// 插入学习内容。
  ///
  /// 返回值：新记录 ID。
  /// 异常：数据库写入失败时可能抛出异常。
  Future<int> insertLearningItem(LearningItemsCompanion companion) {
    return db.into(db.learningItems).insert(companion);
  }

  /// 更新学习内容。
  ///
  /// 返回值：是否更新成功。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<bool> updateLearningItem(LearningItem item) {
    return db.update(db.learningItems).replace(item);
  }

  /// 更新学习内容备注（仅更新 note 字段）。
  ///
  /// 返回值：更新行数。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<int> updateLearningItemNote(int id, String? note) {
    final now = DateTime.now();
    return (db.update(db.learningItems)..where((t) => t.id.equals(id))).write(
      LearningItemsCompanion(note: Value(note), updatedAt: Value(now)),
    );
  }

  /// 更新学习内容描述（仅更新 description 字段）。
  ///
  /// 返回值：更新行数。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<int> updateLearningItemDescription(int id, String? description) {
    final now = DateTime.now();
    return (db.update(db.learningItems)..where((t) => t.id.equals(id))).write(
      LearningItemsCompanion(
        description: Value(description),
        updatedAt: Value(now),
      ),
    );
  }

  /// 停用学习内容（软删除）。
  ///
  /// 规则：
  /// - is_deleted = 1
  /// - deleted_at = now
  /// - updated_at = now
  ///
  /// 返回值：更新行数。
  /// 异常：数据库更新失败时可能抛出异常。
  Future<int> deactivateLearningItem(int id) {
    final now = DateTime.now();
    return (db.update(db.learningItems)..where((t) => t.id.equals(id))).write(
      LearningItemsCompanion(
        isDeleted: const Value(true),
        deletedAt: Value(now),
        updatedAt: Value(now),
      ),
    );
  }

  /// 删除学习内容（级联删除关联复习任务）。
  ///
  /// 返回值：删除行数。
  /// 异常：数据库删除失败时可能抛出异常。
  Future<int> deleteLearningItem(int id) {
    return (db.delete(db.learningItems)..where((t) => t.id.equals(id))).go();
  }

  /// 根据 ID 查询学习内容。
  ///
  /// 返回值：学习内容或 null。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<LearningItem?> getLearningItemById(int id) {
    return (db.select(
      db.learningItems,
    )..where((t) => t.id.equals(id))).getSingleOrNull();
  }

  /// 查询所有学习内容（按创建时间倒序）。
  ///
  /// 返回值：学习内容列表。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<LearningItem>> getAllLearningItems() {
    return (db.select(db.learningItems)
          ..where((t) => t.isDeleted.equals(false))
          ..orderBy([(t) => OrderingTerm.desc(t.createdAt)]))
        .get();
  }

  /// 获取全库学习内容数量（排除已停用）。
  ///
  /// 说明：
  /// - 用于首页空状态“冷启动/常规空态”的判定口径
  /// - 仅统计 is_deleted=0 的学习内容
  /// - 不区分是否为模拟数据（is_mock_data），避免 Debug 数据存在时误判为冷启动
  ///
  /// 返回值：学习内容数量。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<int> getLearningItemCount() async {
    final t = db.learningItems;
    final exp = t.id.count();
    final row =
        await (db.selectOnly(t)
              ..addColumns([exp])
              ..where(t.isDeleted.equals(false)))
            .getSingle();
    return row.read(exp) ?? 0;
  }

  /// F14.1：按关键词搜索学习内容（title/note）。
  ///
  /// 说明：
  /// - 搜索字段：title/description/note/subtasks.content（不包含 tags）
  /// - 默认最多返回 50 条
  /// - v11：优先使用 FTS5（全文检索）提升大数据量场景下的检索性能；失败时回退 LIKE
  /// - LIKE 为模糊匹配（SQLite 对 ASCII 默认大小写不敏感，非 ASCII 行为与系统 collation 相关）
  ///
  /// 参数：
  /// - [keyword] 关键词（会 trim；空字符串返回空列表）
  /// - [limit] 结果上限（1-200，超出会被裁剪）
  /// 返回值：匹配的学习内容列表（按 createdAt 倒序）。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<LearningItem>> searchLearningItems({
    required String keyword,
    int limit = 50,
  }) async {
    final q = keyword.trim();
    if (q.isEmpty) return const <LearningItem>[];

    final capped = limit.clamp(1, 200);

    // Phase 4：全文检索（FTS5）。
    //
    // 说明：
    // - 使用 rowid = learning_items.id 的索引表（learning_items_fts）
    // - 结果仍按 created_at DESC 排序，保持与旧版本 LIKE 口径一致（避免 UI 大幅变更）
    final ftsQuery = _buildFtsQuery(q);
    if (ftsQuery != null) {
      try {
        const ftsSql = '''
SELECT li.*
FROM learning_items li
WHERE li.is_deleted = 0
  AND li.id IN (
    SELECT rowid FROM learning_items_fts
    WHERE learning_items_fts MATCH ?
  )
ORDER BY li.created_at DESC
LIMIT ?
''';
        final rows = await db
            .customSelect(
              ftsSql,
              variables: [Variable<String>(ftsQuery), Variable<int>(capped)],
              readsFrom: {db.learningItems, db.learningSubtasks},
            )
            .get();
        return rows.map((r) => db.learningItems.map(r.data)).toList();
      } catch (_) {
        // FTS 表缺失/扩展不可用等情况：自动回退到 LIKE，保证功能正确性。
      }
    }

    final pattern = '%$q%';

    // 关键逻辑：使用 EXISTS 查询 subtasks，避免 join 导致的重复行与分页错乱。
    const sql = '''
SELECT li.*
FROM learning_items li
WHERE li.is_deleted = 0
  AND (
    li.title LIKE ?
    OR (li.description IS NOT NULL AND li.description LIKE ?)
    OR (li.note IS NOT NULL AND li.note LIKE ?)
    OR EXISTS (
      SELECT 1 FROM learning_subtasks ls
      WHERE ls.learning_item_id = li.id AND ls.content LIKE ?
    )
  )
ORDER BY li.created_at DESC
LIMIT ?
''';

    final rows = await db
        .customSelect(
          sql,
          variables: [
            Variable<String>(pattern),
            Variable<String>(pattern),
            Variable<String>(pattern),
            Variable<String>(pattern),
            Variable<int>(capped),
          ],
          readsFrom: {db.learningItems, db.learningSubtasks},
        )
        .get();
    return rows.map((r) => db.learningItems.map(r.data)).toList();
  }

  /// 构建 FTS5 MATCH 查询字符串（按空白拆分并做最小清洗）。
  ///
  /// 说明：
  /// - 对每个 token 追加 `*` 做前缀匹配，尽量接近 LIKE 的“包含”体验
  /// - 若清洗后为空，返回 null 表示放弃 FTS（回退 LIKE）
  String? _buildFtsQuery(String keyword) {
    final raw = keyword.trim();
    if (raw.isEmpty) return null;

    final parts = raw.split(RegExp(r'\s+')).map((e) => e.trim()).toList();
    final tokens = <String>[];
    for (final p in parts) {
      if (p.isEmpty) continue;
      // 关键逻辑：移除可能破坏 MATCH 语法的字符，避免异常导致搜索不可用。
      // 说明：这里不用 raw string，避免引号与反斜杠的转义陷阱。
      final cleaned = p.replaceAll(RegExp("[\"'`\\\\]"), '').trim();
      if (cleaned.isEmpty) continue;
      // 语法说明：FTS5 支持 `token*` 前缀匹配；此处无需额外插值括号。
      tokens.add('$cleaned*');
    }
    if (tokens.isEmpty) return null;
    return tokens.join(' AND ');
  }

  /// 删除所有模拟学习内容（v3.1 Debug）。
  ///
  /// 说明：
  /// - 按 isMockData=true 条件删除
  /// - 由于 review_tasks 对 learning_items 具有级联删除，删除学习内容会自动清理关联任务
  /// 返回值：删除行数。
  /// 异常：数据库删除失败时可能抛出异常。
  Future<int> deleteMockLearningItems() {
    return (db.delete(
      db.learningItems,
    )..where((t) => t.isMockData.equals(true))).go();
  }

  /// 根据日期查询学习内容（按学习日期）。
  ///
  /// 参数：
  /// - [date] 目标日期（按年月日）。
  /// 返回值：学习内容列表。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<LearningItem>> getLearningItemsByDate(DateTime date) {
    final start = DateTime(date.year, date.month, date.day);
    final end = start.add(const Duration(days: 1));
    return (db.select(db.learningItems)
          ..where((t) => t.isDeleted.equals(false))
          ..where((t) => t.learningDate.isBetweenValues(start, end))
          ..orderBy([(t) => OrderingTerm.desc(t.createdAt)]))
        .get();
  }

  /// 根据标签查询学习内容（v1.0 MVP：使用 LIKE 在 JSON 文本中匹配）。
  ///
  /// 参数：
  /// - [tag] 标签名。
  /// 返回值：学习内容列表。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<LearningItem>> getLearningItemsByTag(String tag) {
    // v1.0 MVP：JSON 文本匹配，避免引入复杂的 JSON1 SQL 依赖。
    final pattern = '%"${tag.trim()}"%';
    return (db.select(db.learningItems)
          ..where((t) => t.isDeleted.equals(false))
          ..where((t) => t.tags.like(pattern))
          ..orderBy([(t) => OrderingTerm.desc(t.createdAt)]))
        .get();
  }

  /// 获取所有标签（去重、按字母排序）。
  ///
  /// 返回值：标签列表。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<List<String>> getAllTags() async {
    final query = db.selectOnly(db.learningItems)
      ..addColumns([db.learningItems.tags]);
    query.where(db.learningItems.isDeleted.equals(false));
    final rows = await query.get();
    final set = <String>{};
    for (final row in rows) {
      final tagsJson = row.read(db.learningItems.tags) ?? '[]';
      set.addAll(_parseTags(tagsJson));
    }
    final list = set.toList()..sort();
    return list;
  }

  /// 获取标签使用排行（用于录入页快捷标签与全部标签页）。
  ///
  /// 排序规则：
  /// - 近 7 天使用次数降序
  /// - 累计使用次数降序
  /// - 最近使用时间降序
  /// - 标签名升序
  Future<List<TagUsageStatEntity>> getTagUsageRanking({
    required DateTime recentSince,
    int? limit,
  }) async {
    final query = db.selectOnly(db.learningItems)
      ..addColumns([db.learningItems.tags, db.learningItems.createdAt]);
    query.where(db.learningItems.isDeleted.equals(false));
    final rows = await query.get();

    final recentCounts = <String, int>{};
    final totalCounts = <String, int>{};
    final lastUsedMap = <String, DateTime>{};

    for (final row in rows) {
      final tagsJson = row.read(db.learningItems.tags) ?? '[]';
      final createdAt = row.read(db.learningItems.createdAt) ?? DateTime.now();
      final tags = _parseTags(tagsJson).toSet();

      for (final tag in tags) {
        totalCounts[tag] = (totalCounts[tag] ?? 0) + 1;
        if (!createdAt.isBefore(recentSince)) {
          recentCounts[tag] = (recentCounts[tag] ?? 0) + 1;
        }
        final lastUsed = lastUsedMap[tag];
        if (lastUsed == null || createdAt.isAfter(lastUsed)) {
          lastUsedMap[tag] = createdAt;
        }
      }
    }

    final ranked = totalCounts.keys.map((tag) {
      return TagUsageStatEntity(
        tag: tag,
        recentUseCount: recentCounts[tag] ?? 0,
        totalUseCount: totalCounts[tag] ?? 0,
        lastUsedAt: lastUsedMap[tag] ?? recentSince,
      );
    }).toList()
      ..sort((left, right) {
        final recent = right.recentUseCount.compareTo(left.recentUseCount);
        if (recent != 0) return recent;
        final total = right.totalUseCount.compareTo(left.totalUseCount);
        if (total != 0) return total;
        final lastUsed = right.lastUsedAt.compareTo(left.lastUsedAt);
        if (lastUsed != 0) return lastUsed;
        return left.tag.compareTo(right.tag);
      });

    if (limit == null) return ranked;
    final safeLimit = limit.clamp(0, ranked.length).toInt();
    return ranked.take(safeLimit).toList();
  }

  /// F7：获取各标签的学习内容数量（用于饼图）。
  ///
  /// 口径：
  /// - 按 learning_items.tags（JSON 数组）聚合
  /// - 多标签的 item 每个标签各计一次
  /// 返回值：Map（key=tag，value=count，不保证排序）。
  Future<Map<String, int>> getTagDistribution() async {
    final query = db.selectOnly(db.learningItems)
      ..addColumns([db.learningItems.tags]);
    query.where(db.learningItems.isDeleted.equals(false));
    final rows = await query.get();
    final map = <String, int>{};
    for (final row in rows) {
      final tagsJson = row.read(db.learningItems.tags) ?? '[]';
      final tags = _parseTags(tagsJson);
      // 避免同一条记录中重复标签导致计数放大。
      for (final tag in tags.toSet()) {
        map[tag] = (map[tag] ?? 0) + 1;
      }
    }
    return map;
  }

  List<String> _parseTags(String tagsJson) {
    try {
      final decoded = jsonDecode(tagsJson);
      if (decoded is List) {
        return decoded
            .whereType<String>()
            .map((e) => e.trim())
            .where((e) => e.isNotEmpty)
            .toList();
      }
      return const [];
    } catch (_) {
      return const [];
    }
  }
}
