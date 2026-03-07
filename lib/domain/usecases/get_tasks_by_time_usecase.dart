/// 文件用途：用例 - 按“发生时间”正序获取任务时间线（任务中心，支持游标分页与状态筛选）。
/// 作者：Codex
/// 创建日期：2026-02-28
library;

import '../entities/review_task.dart';
import '../entities/task_timeline.dart';
import '../repositories/review_task_repository.dart';

/// 获取任务时间线用例（任务中心）。
class GetTasksByTimeUseCase {
  /// 构造函数。
  const GetTasksByTimeUseCase({
    required ReviewTaskRepository reviewTaskRepository,
  }) : _reviewTaskRepository = reviewTaskRepository;

  final ReviewTaskRepository _reviewTaskRepository;

  /// 获取任务时间线分页数据。
  ///
  /// 参数：
  /// - [status] 状态筛选；为空表示全部
  /// - [scheduledDateBefore] 计划日期上界（开区间 `<`）；为空表示不限制
  /// - [scheduledDateOnOrAfter] 计划日期下界（闭区间 `>=`）；为空表示不限制
  /// - [cursor] 游标；为空表示首页
  /// - [limit] 每页条数（建议 20）
  Future<TaskTimelinePageEntity> execute({
    ReviewTaskStatus? status,
    DateTime? scheduledDateBefore,
    DateTime? scheduledDateOnOrAfter,
    TaskTimelineCursorEntity? cursor,
    int limit = 20,
  }) {
    return _reviewTaskRepository.getTaskTimelinePage(
      status: status,
      scheduledDateBefore: scheduledDateBefore,
      scheduledDateOnOrAfter: scheduledDateOnOrAfter,
      cursor: cursor,
      limit: limit,
    );
  }

  /// 获取全量任务状态计数（用于筛选栏展示）。
  Future<(int all, int pending, int done, int skipped)> getStatusCounts() {
    return _reviewTaskRepository.getGlobalTaskStatusCounts();
  }
}
