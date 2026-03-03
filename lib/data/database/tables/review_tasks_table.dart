/// 文件用途：Drift 表定义 - 复习任务表（review_tasks）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import 'learning_items_table.dart';

/// 复习任务表。
///
/// 说明：每条学习内容会生成 5 次复习任务（v1.0 固定间隔）。
@TableIndex(name: 'idx_scheduled_date', columns: {#scheduledDate})
@TableIndex(name: 'idx_status', columns: {#status})
@TableIndex(name: 'idx_learning_item_id', columns: {#learningItemId})
@TableIndex(name: 'idx_completed_at_status', columns: {#completedAt, #status})
@TableIndex(name: 'idx_skipped_at_status', columns: {#skippedAt, #status})
// 性能优化（v10）：任务中心时间线按 occurredAt + id 做稳定排序与游标分页，
// 这里增加复合索引提升大数据量下的分页查询性能。
@TableIndex(name: 'idx_occurred_at_id', columns: {#occurredAt, #id})
@TableIndex(
  name: 'idx_status_occurred_at_id',
  columns: {#status, #occurredAt, #id},
)
class ReviewTasks extends Table {
  /// 主键 ID。
  IntColumn get id => integer().autoIncrement()();

  /// 业务唯一标识（UUID v4）。
  ///
  /// 说明：
  /// - 用于备份/恢复的“合并去重”与外键修复（uuid → id 映射）
  /// - 迁移时会通过 SQL 为历史库补齐该列并回填为真实 UUID，再建立唯一索引
  TextColumn get uuid =>
      text()
          .withLength(min: 1, max: 36)
          // 关键逻辑：插入时自动生成 uuid，避免默认空字符串触发唯一索引冲突。
          .clientDefault(() => const Uuid().v4())
          ();

  /// 外键：关联的学习内容 ID（删除学习内容时级联删除）。
  IntColumn get learningItemId =>
      integer().references(LearningItems, #id, onDelete: KeyAction.cascade)();

  /// 复习轮次（1-10）。
  ///
  /// 说明：数据库层不再使用 CHECK 约束限制范围，最大轮次由应用层控制。
  IntColumn get reviewRound => integer()();

  /// 计划复习日期。
  DateTimeColumn get scheduledDate => dateTime()();

  /// 任务发生时间（用于任务中心时间线排序与游标分页）。
  ///
  /// 口径（与 spec-performance-optimization.md 一致）：
  /// - pending：occurredAt = scheduledDate
  /// - done：occurredAt = completedAt ?? scheduledDate
  /// - skipped：occurredAt = skippedAt ?? scheduledDate
  ///
  /// 说明：
  /// - 该列为性能优化新增列（v10），用于避免分页查询中反复计算 CASE/COALESCE 导致索引失效
  /// - 迁移会回填历史数据；应用层在所有写入口维护该列，尽量保证非空
  DateTimeColumn get occurredAt => dateTime().nullable()();

  /// 任务状态：pending(待复习)/done(已完成)/skipped(已跳过)。
  TextColumn get status => text().withDefault(const Constant('pending'))();

  /// 完成时间（完成后记录）。
  DateTimeColumn get completedAt => dateTime().nullable()();

  /// 跳过时间（跳过后记录）。
  DateTimeColumn get skippedAt => dateTime().nullable()();

  /// 创建时间。
  DateTimeColumn get createdAt => dateTime().withDefault(currentDateAndTime)();

  /// 更新时间（用于同步冲突解决，v3.0 新增）。
  DateTimeColumn get updatedAt => dateTime().nullable()();

  /// 是否为模拟数据（v3.1：用于 Debug 模式生成/清理、同步/导出隔离）。
  BoolColumn get isMockData => boolean().withDefault(const Constant(false))();

  // 注意：最大轮次约束在应用层完成（见 AddReviewRoundUseCase / EbbinghausUtils.maxReviewRound）。
}
