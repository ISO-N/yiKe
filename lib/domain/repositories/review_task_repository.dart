/// 文件用途：仓储接口 - 复习任务（ReviewTaskRepository）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import '../entities/review_task.dart';
import '../entities/task_day_stats.dart';
import '../entities/task_timeline.dart';

/// 复习任务仓储接口。
abstract class ReviewTaskRepository {
  /// 创建单个复习任务。
  Future<ReviewTaskEntity> create(ReviewTaskEntity task);

  /// 批量创建复习任务。
  Future<List<ReviewTaskEntity>> createBatch(List<ReviewTaskEntity> tasks);

  /// 获取今日待复习任务（pending）。
  Future<List<ReviewTaskViewEntity>> getTodayPendingTasks();

  /// 获取逾期待复习任务（pending，scheduledDate < 今日）。
  Future<List<ReviewTaskViewEntity>> getOverduePendingTasks();

  /// 获取今日已完成任务（completedAt = 今天）。
  ///
  /// 说明：按“完成时间”口径，而非 scheduledDate。
  Future<List<ReviewTaskViewEntity>> getTodayCompletedTasks();

  /// 获取今日已跳过任务（skippedAt = 今天）。
  ///
  /// 说明：按“跳过时间”口径，而非 scheduledDate。
  Future<List<ReviewTaskViewEntity>> getTodaySkippedTasks();

  /// 获取指定日期的全部任务（包含完成/跳过），用于小组件与通知内容生成。
  Future<List<ReviewTaskViewEntity>> getTasksByDate(DateTime date);

  /// 标记任务完成。
  Future<void> completeTask(int id);

  /// 标记任务跳过。
  Future<void> skipTask(int id);

  /// 批量标记完成。
  Future<void> completeTasks(List<int> ids);

  /// 批量标记跳过。
  Future<void> skipTasks(List<int> ids);

  /// 撤销任务状态（done/skipped → pending）。
  ///
  /// 规则：
  /// - status 置为 pending
  /// - completedAt/skippedAt 清空（两者都清空，避免历史脏数据影响口径）
  Future<void> undoTaskStatus(int id);

  /// 获取指定日期统计（completed/total）。
  Future<(int completed, int total)> getTaskStats(DateTime date);

  /// F6：获取指定月份每天的任务统计（用于日历圆点标记）。
  ///
  /// 返回值：以“当天 00:00:00”为 key 的统计 Map。
  Future<Map<DateTime, TaskDayStats>> getMonthlyTaskStats(int year, int month);

  /// F7：获取指定日期范围内，每天的任务状态统计（用于趋势图/热力图/统计导出）。
  ///
  /// 口径：
  /// - 按 scheduledDate 归因到自然日（以本地 00:00 为日界线）
  /// - 统计 pending/done/skipped 三种状态数量
  ///
  /// 参数：
  /// - [start] 起始时间（包含）
  /// - [end] 结束时间（不包含）
  /// 返回值：以“当天 00:00:00”为 key 的统计 Map。
  /// 异常：数据库查询失败时可能抛出异常。
  Future<Map<DateTime, TaskDayStats>> getTaskDayStatsInRange(
    DateTime start,
    DateTime end,
  );

  /// F6：获取指定日期范围的任务列表（含学习内容信息）。
  ///
  /// 参数：
  /// - [start] 起始时间（包含）
  /// - [end] 结束时间（不包含）
  Future<List<ReviewTaskViewEntity>> getTasksInRange(
    DateTime start,
    DateTime end,
  );

  /// F7：获取连续打卡天数。
  ///
  /// 口径：
  /// - 从今天往前，连续每天至少完成 1 条任务算打卡成功
  /// - 跳过(skipped)不算断签，也不算完成
  /// - 某天没有任务，不中断打卡链
  ///
  /// 参数：
  /// - [today] 用于测试/注入的“今天”（默认取当前时间）
  Future<int> getConsecutiveCompletedDays({DateTime? today});

  /// F7：获取指定日期范围的完成率口径数据。
  ///
  /// 说明：
  /// - 分子：done 数量
  /// - 分母：done + pending 数量（skipped 不参与）
  Future<(int completed, int total)> getTaskStatsInRange(
    DateTime start,
    DateTime end,
  );

  /// F8：获取全部复习任务（用于数据导出）。
  Future<List<ReviewTaskEntity>> getAllTasks();

  /// 获取全量任务状态计数（用于任务中心筛选栏展示）。
  ///
  /// 返回值：(all, pending, done, skipped)。
  Future<(int all, int pending, int done, int skipped)>
  getGlobalTaskStatusCounts();

  /// 按“发生时间”倒序获取任务时间线分页数据（用于任务中心）。
  ///
  /// 参数：
  /// - [status] 状态筛选；为空表示全部
  /// - [scheduledDateBefore] 计划日期上界（开区间 `<`）；为空表示不限制
  /// - [scheduledDateOnOrAfter] 计划日期下界（闭区间 `>=`）；为空表示不限制
  /// - [cursor] 游标；为空表示首页
  /// - [limit] 每页条数（建议 20）
  ///
  /// 返回值：分页结果（包含下一页游标）。
  Future<TaskTimelinePageEntity> getTaskTimelinePage({
    ReviewTaskStatus? status,
    DateTime? scheduledDateBefore,
    DateTime? scheduledDateOnOrAfter,
    TaskTimelineCursorEntity? cursor,
    int limit = 20,
  });

  /// 获取指定学习内容的完整复习计划（按轮次正序）。
  ///
  /// 说明：用于任务详情 Sheet 展示与调整计划。
  Future<List<ReviewTaskViewEntity>> getReviewPlan(int learningItemId);

  /// 调整指定轮次的计划日期（定位键：learningItemId + reviewRound）。
  Future<void> adjustReviewDate({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
  });

  /// 为指定学习内容增加 1 轮复习任务（最大轮次由上层校验）。
  Future<void> addReviewRound(int learningItemId);

  /// 为指定学习内容减少 1 轮复习任务（删除最大轮次）。
  ///
  /// 说明：
  /// - 由上层负责校验最小轮次（至少保留 1 轮）
  /// - 该操作会物理删除 review_tasks，并级联删除 review_records
  /// - 成功后必须写入同步 delete 日志，避免远端数据漂移
  Future<void> removeLatestReviewRound(int learningItemId);
}
