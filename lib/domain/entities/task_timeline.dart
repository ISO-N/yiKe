/// 文件用途：领域实体 - 任务时间线分页与游标（用于任务中心按“发生时间”正序展示）。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import 'review_task.dart';

/// 任务时间线游标（用于游标分页）。
///
/// 说明：按 (occurredAt ASC, taskId ASC) 排序时，下一页游标取“当前页最后一条”。
class TaskTimelineCursorEntity {
  /// 构造函数。
  const TaskTimelineCursorEntity({
    required this.occurredAt,
    required this.taskId,
  });

  /// 发生时间（pending 用 scheduledDate，done 用 completedAt，skipped 用 skippedAt；空值回退 scheduledDate）。
  final DateTime occurredAt;

  /// 任务 ID（用于同一 occurredAt 的稳定排序）。
  final int taskId;
}

/// 任务时间线条目（用于 UI 按日期分组展示）。
class ReviewTaskTimelineItemEntity {
  /// 构造函数。
  const ReviewTaskTimelineItemEntity({
    required this.task,
    required this.occurredAt,
  });

  /// 展示用任务视图实体。
  final ReviewTaskViewEntity task;

  /// 发生时间（用于排序/分组）。
  final DateTime occurredAt;
}

/// 任务时间线分页结果。
class TaskTimelinePageEntity {
  /// 构造函数。
  const TaskTimelinePageEntity({
    required this.items,
    required this.nextCursor,
  });

  /// 当前页条目（已按发生时间正序排列）。
  final List<ReviewTaskTimelineItemEntity> items;

  /// 下一页游标；为空表示没有更多数据。
  final TaskTimelineCursorEntity? nextCursor;
}
