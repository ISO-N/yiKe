/// 文件用途：学习内容仓储实现（LearningItemRepositoryImpl）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../../domain/entities/learning_item.dart';
import '../../domain/entities/tag_usage_stat.dart';
import '../../domain/repositories/learning_item_repository.dart';
import '../sync/sync_log_writer.dart';
import '../database/daos/learning_item_dao.dart';
import '../database/database.dart';

/// 学习内容仓储实现。
class LearningItemRepositoryImpl implements LearningItemRepository {
  /// 构造函数。
  ///
  /// 参数：
  /// - [dao] 学习内容 DAO。
  /// 异常：无。
  LearningItemRepositoryImpl(this.dao, {SyncLogWriter? syncLogWriter})
    : _sync = syncLogWriter;

  final LearningItemDao dao;
  final SyncLogWriter? _sync;

  static const Uuid _uuid = Uuid();

  @override
  Future<LearningItemEntity> create(LearningItemEntity item) async {
    final now = DateTime.now();
    final ensuredUuid = item.uuid.trim().isEmpty ? _uuid.v4() : item.uuid.trim();
    final id = await dao.insertLearningItem(
      LearningItemsCompanion.insert(
        uuid: Value(ensuredUuid),
        title: item.title,
        description:
            item.description == null
                ? const Value.absent()
                : Value(item.description),
        note: item.note == null ? const Value.absent() : Value(item.note),
        tags: Value(jsonEncode(item.tags)),
        learningDate: item.learningDate,
        createdAt: Value(item.createdAt),
        updatedAt: Value(now),
      ),
    );
    final saved = item.copyWith(id: id, updatedAt: now, uuid: ensuredUuid);

    final sync = _sync;
    if (sync == null) return saved;

    final ts = now.millisecondsSinceEpoch;
    final origin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: id,
      appliedAtMs: ts,
    );
    await sync.logEvent(
      origin: origin,
      entityType: 'learning_item',
      operation: 'create',
      data: {
        'title': saved.title,
        'description': saved.description,
        'note': saved.note,
        'tags': saved.tags,
        'learning_date': saved.learningDate.toIso8601String(),
        'created_at': saved.createdAt.toIso8601String(),
        'updated_at': (saved.updatedAt ?? saved.createdAt).toIso8601String(),
        'is_deleted': saved.isDeleted,
        'deleted_at': saved.deletedAt?.toIso8601String(),
        'is_mock_data': saved.isMockData,
      },
      timestampMs: ts,
    );

    return saved;
  }

  @override
  Future<void> delete(int id) async {
    final existing = await dao.getLearningItemById(id);
    final isMockData = existing?.isMockData ?? false;

    // v3.1：Mock 数据不参与同步，因此不写入 delete 日志。
    if (!isMockData) {
      final ts = DateTime.now().millisecondsSinceEpoch;
      await _sync?.logDelete(
        entityType: 'learning_item',
        localEntityId: id,
        timestampMs: ts,
      );
    }
    await dao.deleteLearningItem(id);
  }

  @override
  Future<List<LearningItemEntity>> getAll() async {
    final rows = await dao.getAllLearningItems();
    return rows.map(_toEntity).toList();
  }

  @override
  Future<List<LearningItemEntity>> getByDate(DateTime date) async {
    final rows = await dao.getLearningItemsByDate(date);
    return rows.map(_toEntity).toList();
  }

  @override
  Future<LearningItemEntity?> getById(int id) async {
    final row = await dao.getLearningItemById(id);
    if (row == null) return null;
    return _toEntity(row);
  }

  @override
  Future<List<LearningItemEntity>> getByTag(String tag) async {
    final rows = await dao.getLearningItemsByTag(tag);
    return rows.map(_toEntity).toList();
  }

  @override
  Future<List<String>> getAllTags() {
    return dao.getAllTags();
  }

  @override
  Future<List<TagUsageStatEntity>> getTagUsageRanking({
    required DateTime recentSince,
    int? limit,
  }) {
    return dao.getTagUsageRanking(recentSince: recentSince, limit: limit);
  }

  @override
  Future<Map<String, int>> getTagDistribution() {
    return dao.getTagDistribution();
  }

  @override
  Future<LearningItemEntity> update(LearningItemEntity item) async {
    if (item.id == null) {
      throw ArgumentError('更新学习内容时 id 不能为空');
    }

    final existing = await dao.getLearningItemById(item.id!);
    if (existing == null) {
      throw StateError('学习内容不存在（id=${item.id}）');
    }
    if (existing.isDeleted) {
      throw StateError('学习内容已停用，无法修改（id=${item.id}）');
    }

    final now = DateTime.now();
    final ts = now.millisecondsSinceEpoch;
    final ok = await dao.updateLearningItem(
      LearningItem(
        id: item.id!,
        uuid: existing.uuid,
        title: item.title,
        description: item.description,
        note: item.note,
        tags: jsonEncode(item.tags),
        learningDate: item.learningDate,
        createdAt: item.createdAt,
        updatedAt: now,
        isDeleted: existing.isDeleted,
        deletedAt: existing.deletedAt,
        isMockData: existing.isMockData,
      ),
    );
    if (!ok) {
      throw StateError('学习内容更新失败（id=${item.id}）');
    }
    final saved = item.copyWith(updatedAt: now);

    final sync = _sync;
    if (sync == null || existing.isMockData) return saved;

    final origin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: item.id!,
      appliedAtMs: ts,
    );
    await sync.logEvent(
      origin: origin,
      entityType: 'learning_item',
      operation: 'update',
      data: {
        'title': saved.title,
        'description': saved.description,
        'note': saved.note,
        'tags': saved.tags,
        'learning_date': saved.learningDate.toIso8601String(),
        'created_at': saved.createdAt.toIso8601String(),
        'updated_at': (saved.updatedAt ?? saved.createdAt).toIso8601String(),
        'is_deleted': saved.isDeleted,
        'deleted_at': saved.deletedAt?.toIso8601String(),
        'is_mock_data': saved.isMockData,
      },
      timestampMs: ts,
    );

    return saved;
  }

  @override
  Future<void> updateNote({required int id, required String? note}) async {
    final existing = await dao.getLearningItemById(id);
    if (existing == null) {
      throw StateError('学习内容不存在（id=$id）');
    }
    if (existing.isDeleted) {
      throw StateError('学习内容已停用，无法编辑备注（id=$id）');
    }

    final normalized = note?.trim().isEmpty == true ? null : note?.trim();
    final updated = await dao.updateLearningItemNote(id, normalized);
    if (updated <= 0) {
      throw StateError('学习内容备注更新失败（id=$id）');
    }

    final sync = _sync;
    if (sync == null || existing.isMockData) return;

    final row = await dao.getLearningItemById(id);
    if (row == null) return;

    final now = DateTime.now();
    final ts = now.millisecondsSinceEpoch;
    final origin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: id,
      appliedAtMs: ts,
    );
    await sync.logEvent(
      origin: origin,
      entityType: 'learning_item',
      operation: 'update',
      data: {
        'title': row.title,
        'description': row.description,
        'note': row.note,
        'tags': _parseTags(row.tags),
        'learning_date': row.learningDate.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': (row.updatedAt ?? row.createdAt).toIso8601String(),
        'is_deleted': row.isDeleted,
        'deleted_at': row.deletedAt?.toIso8601String(),
        'is_mock_data': row.isMockData,
      },
      timestampMs: ts,
    );
  }

  @override
  Future<void> updateDescription({
    required int id,
    required String? description,
  }) async {
    final existing = await dao.getLearningItemById(id);
    if (existing == null) {
      throw StateError('学习内容不存在（id=$id）');
    }
    if (existing.isDeleted) {
      throw StateError('学习内容已停用，无法编辑描述（id=$id）');
    }

    final normalized =
        description?.trim().isEmpty == true ? null : description?.trim();
    final updated = await dao.updateLearningItemDescription(id, normalized);
    if (updated <= 0) {
      throw StateError('学习内容描述更新失败（id=$id）');
    }

    final sync = _sync;
    if (sync == null || existing.isMockData) return;

    final row = await dao.getLearningItemById(id);
    if (row == null) return;

    final now = DateTime.now();
    final ts = now.millisecondsSinceEpoch;
    final origin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: id,
      appliedAtMs: ts,
    );
    await sync.logEvent(
      origin: origin,
      entityType: 'learning_item',
      operation: 'update',
      data: {
        'title': row.title,
        'description': row.description,
        'note': row.note,
        'tags': _parseTags(row.tags),
        'learning_date': row.learningDate.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': (row.updatedAt ?? row.createdAt).toIso8601String(),
        'is_deleted': row.isDeleted,
        'deleted_at': row.deletedAt?.toIso8601String(),
        'is_mock_data': row.isMockData,
      },
      timestampMs: ts,
    );
  }

  @override
  Future<void> deactivate(int id) async {
    final existing = await dao.getLearningItemById(id);
    if (existing == null) {
      throw StateError('学习内容不存在（id=$id）');
    }
    if (existing.isDeleted) return;

    final updated = await dao.deactivateLearningItem(id);
    if (updated <= 0) {
      throw StateError('学习内容停用失败（id=$id）');
    }

    final sync = _sync;
    if (sync == null || existing.isMockData) return;

    final row = await dao.getLearningItemById(id);
    if (row == null) return;

    final now = DateTime.now();
    final ts = now.millisecondsSinceEpoch;
    final origin = await sync.resolveOriginKey(
      entityType: 'learning_item',
      localEntityId: id,
      appliedAtMs: ts,
    );
    await sync.logEvent(
      origin: origin,
      entityType: 'learning_item',
      operation: 'update',
      data: {
        'title': row.title,
        'description': row.description,
        'note': row.note,
        'tags': _parseTags(row.tags),
        'learning_date': row.learningDate.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': (row.updatedAt ?? row.createdAt).toIso8601String(),
        'is_deleted': row.isDeleted,
        'deleted_at': row.deletedAt?.toIso8601String(),
        'is_mock_data': row.isMockData,
      },
      timestampMs: ts,
    );
  }

  LearningItemEntity _toEntity(LearningItem row) {
    return LearningItemEntity(
      uuid: row.uuid,
      id: row.id,
      title: row.title,
      description: row.description,
      note: row.note,
      tags: _parseTags(row.tags),
      learningDate: row.learningDate,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt,
      isDeleted: row.isDeleted,
      deletedAt: row.deletedAt,
      isMockData: row.isMockData,
    );
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
