/// 文件用途：Drift 数据库定义与初始化（SQLite）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'dart:io';

import 'package:drift/drift.dart';
import 'package:drift/native.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'tables/learning_items_table.dart';
import 'tables/learning_subtasks_table.dart';
import 'tables/review_records_table.dart';
import 'tables/learning_templates_table.dart';
import 'tables/learning_topics_table.dart';
import 'tables/review_tasks_table.dart';
import 'tables/settings_table.dart';
import 'tables/sync_devices_table.dart';
import 'tables/sync_entity_mappings_table.dart';
import 'tables/sync_logs_table.dart';
import 'tables/topic_item_relations_table.dart';

import 'package:uuid/uuid.dart';

part 'database.g.dart';

/// 应用数据库。
///
/// 说明：
/// - 数据库文件：`yike.db`
/// - v1.0 MVP：本地离线可用，任务数据不加密；设置项可在上层做加密存储。
@DriftDatabase(
  tables: [
    LearningItems,
    LearningSubtasks,
    ReviewTasks,
    ReviewRecords,
    AppSettingsTable,
    LearningTemplates,
    LearningTopics,
    TopicItemRelations,
    SyncDevices,
    SyncLogs,
    SyncEntityMappings,
  ],
)
class AppDatabase extends _$AppDatabase {
  /// 创建数据库实例。
  ///
  /// 参数：
  /// - [executor] Drift QueryExecutor。
  /// 返回值：AppDatabase。
  /// 异常：无（打开失败会在首次操作时抛出）。
  AppDatabase(super.executor);

  /// 打开默认数据库（文件落在应用文档目录）。
  ///
  /// 返回值：AppDatabase。
  /// 异常：路径获取失败/文件打开失败时可能抛出异常。
  static Future<AppDatabase> open() async {
    final executor = LazyDatabase(() async {
      final dir = await getApplicationDocumentsDirectory();
      final file = File(p.join(dir.path, 'yike.db'));
      // 性能优化（spec-performance-optimization.md / Phase 2）：
      // 使用 Drift 的后台 isolate 打开数据库，避免大查询在 UI isolate 上执行造成卡顿尖峰。
      return NativeDatabase.createInBackground(file);
    });
    return AppDatabase(executor);
  }

  @override
  int get schemaVersion => 11;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (migrator) async {
      await migrator.createAll();
      // v1.4：为“调整计划/增加轮次”提供唯一定位键（learning_item_id + review_round）。
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_tasks_learning_item_round ON review_tasks (learning_item_id, review_round)',
      );

      // v1.5：备份恢复 - 为核心表增加稳定 uuid 唯一索引（用于合并去重）。
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_items_uuid ON learning_items (uuid)',
      );
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_tasks_uuid ON review_tasks (uuid)',
      );
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_templates_uuid ON learning_templates (uuid)',
      );
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_topics_uuid ON learning_topics (uuid)',
      );
      await customStatement(
        'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_records_uuid ON review_records (uuid)',
      );

      // v10：为时间线 occurred_at 建索引（部分平台 createAll 对复合索引创建时机存在差异，兜底一次）。
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_occurred_at_id ON review_tasks (occurred_at, id)',
      );
      await customStatement(
        'CREATE INDEX IF NOT EXISTS idx_status_occurred_at_id ON review_tasks (status, occurred_at, id)',
      );

      // v11：创建学习内容全文检索（FTS5）表与触发器，并对存量数据做一次性回填。
      await _createOrRebuildLearningItemsFts();
    },
    onUpgrade: (migrator, from, to) async {
      // v2.1：新增学习模板、学习主题与关联表。
      if (from < 2) {
        await migrator.createTable(learningTemplates);
        await migrator.createTable(learningTopics);
        await migrator.createTable(topicItemRelations);
      }

      // v3.0：新增局域网同步相关表 + 复习任务更新时间字段。
      if (from < 3) {
        await migrator.createTable(syncDevices);
        await migrator.createTable(syncLogs);
        await migrator.createTable(syncEntityMappings);

        await migrator.addColumn(reviewTasks, reviewTasks.updatedAt);

        // 兼容：为历史任务补齐 updatedAt，便于同步冲突解决（以 createdAt 兜底）。
        await customStatement(
          'UPDATE review_tasks SET updated_at = created_at WHERE updated_at IS NULL',
        );
      }

      // v3.1：新增 isMockData 字段（用于 Debug 模拟数据隔离）。
      if (from < 4) {
        await migrator.addColumn(learningItems, learningItems.isMockData);
        await migrator.addColumn(reviewTasks, reviewTasks.isMockData);
      }

      // v3.2：补偿性迁移——确保局域网同步相关表存在。
      //
      // 背景：历史版本中曾出现“schemaVersion 已提升，但同步表未创建”的情况，导致：
      // - 主机端能显示配对码（内存态），但配对确认写库失败 -> 客户端提示“同步失败”
      // - 两端“已连接设备”列表始终为空（表不存在或查询失败）
      //
      // 处理：在 v5 强制兜底创建缺失表（仅在确实缺失时创建，避免影响已有数据）。
      if (from < 5) {
        final rows = await customSelect(
          "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sync_devices','sync_logs','sync_entity_mappings')",
        ).get();
        final existing = rows.map((r) => r.read<String>('name')).toSet();

        if (!existing.contains('sync_devices')) {
          await migrator.createTable(syncDevices);
        }
        if (!existing.contains('sync_logs')) {
          await migrator.createTable(syncLogs);
        }
        if (!existing.contains('sync_entity_mappings')) {
          await migrator.createTable(syncEntityMappings);
        }
      }

      // v3.3：为任务历史/任务中心新增索引（提升 completedAt/skippedAt 查询性能）。
      if (from < 6) {
        await customStatement(
          'CREATE INDEX IF NOT EXISTS idx_completed_at_status ON review_tasks (completed_at, status)',
        );
        await customStatement(
          'CREATE INDEX IF NOT EXISTS idx_skipped_at_status ON review_tasks (skipped_at, status)',
        );
      }

      // v1.4：任务操作增强
      // - learning_items：新增 is_deleted / deleted_at（软删除）
      // - review_tasks：移除 review_round 的 CHECK 约束，允许扩展至 10 轮（应用层控制上限）
      // - 新增唯一索引：review_tasks(learning_item_id, review_round)
      if (from < 7) {
        Future<bool> hasColumn(String table, String column) async {
          final rows = await customSelect('PRAGMA table_info($table)').get();
          return rows.any((r) => r.read<String>('name') == column);
        }

        // 兼容：若 user_version 异常回退（或历史脏库）导致列已存在，则跳过 addColumn，避免重复添加报错。
        if (!await hasColumn('learning_items', 'is_deleted')) {
          await migrator.addColumn(learningItems, learningItems.isDeleted);
        }
        if (!await hasColumn('learning_items', 'deleted_at')) {
          await migrator.addColumn(learningItems, learningItems.deletedAt);
        }

        // Drift 会通过重建表的方式应用约束差异（移除 CHECK 约束）。
        // ignore: experimental_member_use
        await migrator.alterTable(TableMigration(reviewTasks));

        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_tasks_learning_item_round ON review_tasks (learning_item_id, review_round)',
        );
      }

      // v1.5：备份恢复 - 增加稳定 uuid 字段 + 复习记录表。
      if (from < 8) {
        Future<bool> hasTable(String table) async {
          final rows = await customSelect(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
            variables: [Variable<String>(table)],
          ).get();
          return rows.isNotEmpty;
        }

        Future<bool> hasColumn(String table, String column) async {
          // PRAGMA 在表不存在时返回空列表，因此无需额外判空。
          final rows = await customSelect('PRAGMA table_info($table)').get();
          return rows.any((r) => r.read<String>('name') == column);
        }

        // 1) 新增 uuid 列（默认空字符串，随后回填真实 UUID）。
        //
        // 兼容：历史脏库/回退 user_version 时，表结构可能已包含 uuid 列，这里需先探测再添加，
        // 避免触发 “duplicate column name” 迁移失败。
        if (await hasTable('learning_items') &&
            !await hasColumn('learning_items', 'uuid')) {
          await customStatement(
            "ALTER TABLE learning_items ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
          );
        }
        if (await hasTable('review_tasks') &&
            !await hasColumn('review_tasks', 'uuid')) {
          await customStatement(
            "ALTER TABLE review_tasks ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
          );
        }
        if (await hasTable('learning_templates') &&
            !await hasColumn('learning_templates', 'uuid')) {
          await customStatement(
            "ALTER TABLE learning_templates ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
          );
        }
        if (await hasTable('learning_topics') &&
            !await hasColumn('learning_topics', 'uuid')) {
          await customStatement(
            "ALTER TABLE learning_topics ADD COLUMN uuid TEXT NOT NULL DEFAULT ''",
          );
        }

        // 2) 新增 review_records 表（兼容：脏库可能已存在）。
        if (!await hasTable('review_records')) {
          await migrator.createTable(reviewRecords);
        }

        // 3) 回填历史数据的 uuid（逐条写入，确保唯一）。
        const uuidGen = Uuid();
        if (await hasTable('learning_items') &&
            await hasColumn('learning_items', 'uuid')) {
          await _backfillUuid(table: 'learning_items', uuidGen: uuidGen);
        }
        if (await hasTable('review_tasks') &&
            await hasColumn('review_tasks', 'uuid')) {
          await _backfillUuid(table: 'review_tasks', uuidGen: uuidGen);
        }
        if (await hasTable('learning_templates') &&
            await hasColumn('learning_templates', 'uuid')) {
          await _backfillUuid(table: 'learning_templates', uuidGen: uuidGen);
        }
        if (await hasTable('learning_topics') &&
            await hasColumn('learning_topics', 'uuid')) {
          await _backfillUuid(table: 'learning_topics', uuidGen: uuidGen);
        }

        // 4) 基于历史任务状态回填 review_records（仅 done/skipped）。
        if (await hasTable('review_records') && await hasTable('review_tasks')) {
          await _backfillReviewRecordsFromTasks(uuidGen: uuidGen);
        }

        // 5) 建立 uuid 唯一索引（回填完成后再建，避免默认空字符串冲突）。
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_items_uuid ON learning_items (uuid)',
        );
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_tasks_uuid ON review_tasks (uuid)',
        );
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_templates_uuid ON learning_templates (uuid)',
        );
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_learning_topics_uuid ON learning_topics (uuid)',
        );
        await customStatement(
          'CREATE UNIQUE INDEX IF NOT EXISTS idx_review_records_uuid ON review_records (uuid)',
        );
      }

      // v2.6：任务结构增强 - learning_items.description + learning_subtasks 表。
      if (from < 9) {
        Future<bool> hasTable(String table) async {
          final rows = await customSelect(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
            variables: [Variable<String>(table)],
          ).get();
          return rows.isNotEmpty;
        }

        Future<bool> hasColumn(String table, String column) async {
          final rows = await customSelect('PRAGMA table_info($table)').get();
          return rows.any((r) => r.read<String>('name') == column);
        }

        // 1) 新增 description 列（兼容：历史脏库可能已存在）。
        if (await hasTable('learning_items') &&
            !await hasColumn('learning_items', 'description')) {
          await migrator.addColumn(learningItems, learningItems.description);
        }

        // 2) 新建 learning_subtasks 表（兼容：历史脏库表可能已存在）。
        if (!await hasTable('learning_subtasks')) {
          await migrator.createTable(learningSubtasks);
        }

        // 3) 创建索引（兼容：重复执行）。
        await customStatement(
          'CREATE INDEX IF NOT EXISTS idx_learning_subtasks_item_order ON learning_subtasks (learning_item_id, sort_order)',
        );
      }

      // v10：任务中心时间线性能优化 - occurred_at 落地列 + 复合索引。
      if (from < 10) {
        Future<bool> hasTable(String table) async {
          final rows = await customSelect(
            "SELECT name FROM sqlite_master WHERE type='table' AND name = ?",
            variables: [Variable<String>(table)],
          ).get();
          return rows.isNotEmpty;
        }

        Future<bool> hasColumn(String table, String column) async {
          final rows = await customSelect('PRAGMA table_info($table)').get();
          return rows.any((r) => r.read<String>('name') == column);
        }

        if (await hasTable('review_tasks') &&
            !await hasColumn('review_tasks', 'occurred_at')) {
          await migrator.addColumn(reviewTasks, reviewTasks.occurredAt);
        }

        // 回填历史 occurred_at（尽量保证非空，便于后续走索引）。
        await customStatement(
          '''
UPDATE review_tasks
SET occurred_at = CASE status
  WHEN 'pending' THEN scheduled_date
  WHEN 'done' THEN COALESCE(completed_at, scheduled_date)
  WHEN 'skipped' THEN COALESCE(skipped_at, scheduled_date)
  ELSE scheduled_date
END
WHERE occurred_at IS NULL
''',
        );

        // 复合索引：occurred_at + id（稳定排序/游标分页），以及 status 组合索引（筛选场景）。
        await customStatement(
          'CREATE INDEX IF NOT EXISTS idx_occurred_at_id ON review_tasks (occurred_at, id)',
        );
        await customStatement(
          'CREATE INDEX IF NOT EXISTS idx_status_occurred_at_id ON review_tasks (status, occurred_at, id)',
        );
      }

      // v11：全文检索（FTS5）- 建表 + 触发器 + 回填。
      if (from < 11) {
        await _createOrRebuildLearningItemsFts();
      }
    },
    beforeOpen: (details) async {
      // 开启外键约束，确保级联删除生效。
      await customStatement('PRAGMA foreign_keys = ON');
    },
  );

  /// 创建/重建 learning_items 的 FTS5 表（含 subtasks 聚合），并回填存量数据。
  ///
  /// 说明：
  /// - 使用 contentless FTS5 表（rowid = learning_items.id），避免外部内容表限制
  /// - 通过触发器在 learning_items / learning_subtasks 变更时维护索引一致性
  /// - 对于 is_deleted=1 的学习内容，不写入 FTS，避免搜索命中已停用数据
  Future<void> _createOrRebuildLearningItemsFts() async {
    // 1) 创建 FTS 表（若已存在则跳过）。
    //
    // 兼容：部分平台/测试环境可能缺少 FTS5 扩展能力。此时应保持应用可用并回退到 LIKE 搜索。
    try {
      await customStatement(
        '''
CREATE VIRTUAL TABLE IF NOT EXISTS learning_items_fts
USING fts5(
  title,
  description,
  note,
  subtasks,
  tokenize = 'unicode61'
)
''',
      );
    } catch (_) {
      return;
    }

    // 2) 触发器：learning_items 变更维护。
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_items_fts_ai
AFTER INSERT ON learning_items
BEGIN
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    NEW.id,
    NEW.title,
    NEW.description,
    NEW.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = NEW.id), '')
  WHERE NEW.is_deleted = 0;
END
''',
    );
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_items_fts_au
AFTER UPDATE ON learning_items
BEGIN
  DELETE FROM learning_items_fts WHERE rowid = OLD.id;
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    NEW.id,
    NEW.title,
    NEW.description,
    NEW.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = NEW.id), '')
  WHERE NEW.is_deleted = 0;
END
''',
    );
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_items_fts_ad
AFTER DELETE ON learning_items
BEGIN
  DELETE FROM learning_items_fts WHERE rowid = OLD.id;
END
''',
    );

    // 3) 触发器：learning_subtasks 变更维护（聚合字段 subtasks）。
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_subtasks_fts_ai
AFTER INSERT ON learning_subtasks
BEGIN
  DELETE FROM learning_items_fts WHERE rowid = NEW.learning_item_id;
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    li.id,
    li.title,
    li.description,
    li.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = li.id), '')
  FROM learning_items li
  WHERE li.id = NEW.learning_item_id AND li.is_deleted = 0;
END
''',
    );
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_subtasks_fts_au
AFTER UPDATE ON learning_subtasks
BEGIN
  DELETE FROM learning_items_fts WHERE rowid = OLD.learning_item_id;
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    li.id,
    li.title,
    li.description,
    li.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = li.id), '')
  FROM learning_items li
  WHERE li.id = OLD.learning_item_id AND li.is_deleted = 0;

  -- 若子任务被移动到其他学习内容，同步更新新旧两侧的索引。
  DELETE FROM learning_items_fts WHERE rowid = NEW.learning_item_id;
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    li.id,
    li.title,
    li.description,
    li.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = li.id), '')
  FROM learning_items li
  WHERE li.id = NEW.learning_item_id AND li.is_deleted = 0;
END
''',
    );
    await customStatement(
      '''
CREATE TRIGGER IF NOT EXISTS trg_learning_subtasks_fts_ad
AFTER DELETE ON learning_subtasks
BEGIN
  DELETE FROM learning_items_fts WHERE rowid = OLD.learning_item_id;
  INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
  SELECT
    li.id,
    li.title,
    li.description,
    li.note,
    COALESCE((SELECT group_concat(content, '\n') FROM learning_subtasks WHERE learning_item_id = li.id), '')
  FROM learning_items li
  WHERE li.id = OLD.learning_item_id AND li.is_deleted = 0;
END
''',
    );

    // 4) 回填：用当前 learning_items + learning_subtasks 生成全量索引（幂等）。
    //
    // 说明：
    // - 这里采用“清空后重建”策略，保证触发器缺失/历史脏数据场景也能被修复
    // - FTS 表本身不参与同步与备份一致性（可由源数据重建）
    await customStatement('DELETE FROM learning_items_fts');
    await customStatement(
      '''
INSERT INTO learning_items_fts(rowid, title, description, note, subtasks)
SELECT
  li.id,
  li.title,
  li.description,
  li.note,
  COALESCE((SELECT group_concat(ls.content, '\n') FROM learning_subtasks ls WHERE ls.learning_item_id = li.id), '')
FROM learning_items li
WHERE li.is_deleted = 0
''',
    );
  }

  /// 为指定表回填 uuid（仅处理 uuid 为空字符串的记录）。
  ///
  /// 参数：
  /// - [table] 表名（需包含 id 与 uuid 两列）
  /// - [uuidGen] UUID 生成器
  /// 返回值：Future（无返回值）。
  /// 异常：数据库读写失败时可能抛出异常。
  Future<void> _backfillUuid({
    required String table,
    required Uuid uuidGen,
  }) async {
    final rows = await customSelect(
      'SELECT id FROM $table WHERE uuid = ?',
      variables: [const Variable<String>('')],
    ).get();
    for (final row in rows) {
      final id = row.read<int>('id');
      await customStatement(
        'UPDATE $table SET uuid = ? WHERE id = ?',
        [uuidGen.v4(), id],
      );
    }
  }

  /// 基于历史 review_tasks 回填 review_records（done/skipped）。
  ///
  /// 说明：
  /// - 仅用于从旧版本升级到包含 review_records 的版本
  /// - 以任务的 completedAt/skippedAt 作为 occurredAt（缺失时回退 scheduledDate）
  Future<void> _backfillReviewRecordsFromTasks({required Uuid uuidGen}) async {
    final rows = await customSelect(
      '''
SELECT id, status, completed_at, skipped_at, scheduled_date
FROM review_tasks
WHERE status IN ('done', 'skipped')
''',
    ).get();

    for (final row in rows) {
      final taskId = row.read<int>('id');
      final action = row.read<String>('status');
      final completedAt = row.read<DateTime?>('completed_at');
      final skippedAt = row.read<DateTime?>('skipped_at');
      final scheduledDate = row.read<DateTime>('scheduled_date');
      final occurredAt = completedAt ?? skippedAt ?? scheduledDate;

      // 保护：避免极端情况下重复运行迁移导致重复插入。
      final existing = await customSelect(
        'SELECT id FROM review_records WHERE review_task_id = ? AND action = ? AND occurred_at = ? LIMIT 1',
        variables: [
          Variable<int>(taskId),
          Variable<String>(action),
          Variable<DateTime>(occurredAt),
        ],
      ).getSingleOrNull();
      if (existing != null) continue;

      await customStatement(
        'INSERT INTO review_records (uuid, review_task_id, action, occurred_at, created_at) VALUES (?, ?, ?, ?, ?)',
        [uuidGen.v4(), taskId, action, occurredAt, occurredAt],
      );
    }
  }
}
