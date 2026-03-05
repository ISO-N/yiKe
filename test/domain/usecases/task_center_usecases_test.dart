// 文件用途：领域用例与实体单元测试（任务中心相关：时间线、状态计数、调整计划、增加轮次等）。
// 作者：Codex
// 创建日期：2026-02-28

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/entities/task_timeline.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/add_review_round_usecase.dart';
import 'package:yike/domain/usecases/adjust_review_date_usecase.dart';
import 'package:yike/domain/usecases/deactivate_learning_item_usecase.dart';
import 'package:yike/domain/usecases/get_review_plan_usecase.dart';
import 'package:yike/domain/usecases/get_tasks_by_time_usecase.dart';
import 'package:yike/domain/usecases/undo_task_status_usecase.dart';
import 'package:yike/domain/usecases/update_learning_item_note_usecase.dart';

/// 说明：使用 Fake 来满足仓储接口的“只实现被测方法”需求，避免引入第三方 Mock 依赖。
class _FakeReviewTaskRepository extends Fake implements ReviewTaskRepository {
  List<ReviewTaskViewEntity> plan = const [];

  int? lastAdjustLearningItemId;
  int? lastAdjustReviewRound;
  DateTime? lastAdjustScheduledDate;

  int? lastAddRoundLearningItemId;

  int? lastUndoTaskId;

  ReviewTaskStatus? lastTimelineStatus;
  TaskTimelineCursorEntity? lastTimelineCursor;
  int? lastTimelineLimit;
  TaskTimelinePageEntity timelineResult = const TaskTimelinePageEntity(
    items: [],
    nextCursor: null,
  );

  (int all, int pending, int done, int skipped) statusCounts = (0, 0, 0, 0);

  @override
  Future<List<ReviewTaskViewEntity>> getReviewPlan(int learningItemId) async {
    return plan;
  }

  @override
  Future<void> adjustReviewDate({
    required int learningItemId,
    required int reviewRound,
    required DateTime scheduledDate,
  }) async {
    lastAdjustLearningItemId = learningItemId;
    lastAdjustReviewRound = reviewRound;
    lastAdjustScheduledDate = scheduledDate;
  }

  @override
  Future<void> addReviewRound(int learningItemId) async {
    lastAddRoundLearningItemId = learningItemId;
  }

  @override
  Future<void> undoTaskStatus(int id) async {
    lastUndoTaskId = id;
  }

  @override
  Future<TaskTimelinePageEntity> getTaskTimelinePage({
    ReviewTaskStatus? status,
    DateTime? scheduledDateBefore,
    DateTime? scheduledDateOnOrAfter,
    TaskTimelineCursorEntity? cursor,
    int limit = 20,
  }) async {
    lastTimelineStatus = status;
    lastTimelineCursor = cursor;
    lastTimelineLimit = limit;
    return timelineResult;
  }

  @override
  Future<(int all, int pending, int done, int skipped)>
  getGlobalTaskStatusCounts() async {
    return statusCounts;
  }
}

class _FakeLearningItemRepository extends Fake
    implements LearningItemRepository {
  int? lastDeactivateId;
  int? lastUpdateNoteId;
  String? lastUpdateNoteValue;

  @override
  Future<void> deactivate(int id) async {
    lastDeactivateId = id;
  }

  @override
  Future<void> updateNote({required int id, required String? note}) async {
    lastUpdateNoteId = id;
    lastUpdateNoteValue = note;
  }
}

ReviewTaskViewEntity _view({
  required int taskId,
  required int learningItemId,
  required int reviewRound,
  required DateTime scheduledDate,
  ReviewTaskStatus status = ReviewTaskStatus.pending,
  bool isDeleted = false,
}) {
  return ReviewTaskViewEntity(
    taskId: taskId,
    learningItemId: learningItemId,
    title: 'T',
    description: null,
    note: null,
    subtaskCount: 0,
    tags: const ['a'],
    reviewRound: reviewRound,
    scheduledDate: scheduledDate,
    status: status,
    completedAt: null,
    skippedAt: null,
    isDeleted: isDeleted,
    deletedAt: isDeleted ? DateTime(2026, 2, 28) : null,
  );
}

void main() {
  group('任务中心相关用例：透传与约束', () {
    test('GetReviewPlanUseCase 会透传到仓储', () async {
      final repo = _FakeReviewTaskRepository()
        ..plan = [
          _view(
            taskId: 1,
            learningItemId: 10,
            reviewRound: 1,
            scheduledDate: DateTime(2026, 2, 28),
          ),
        ];
      final useCase = GetReviewPlanUseCase(reviewTaskRepository: repo);

      final got = await useCase.execute(10);
      expect(got.length, 1);
      expect(got.single.reviewRound, 1);
    });

    test('UndoTaskStatusUseCase 会透传到仓储', () async {
      final repo = _FakeReviewTaskRepository();
      final useCase = UndoTaskStatusUseCase(reviewTaskRepository: repo);

      await useCase.execute(123);
      expect(repo.lastUndoTaskId, 123);
    });

    test('GetTasksByTimeUseCase.execute 会透传参数并返回分页结果', () async {
      final repo = _FakeReviewTaskRepository()
        ..timelineResult = TaskTimelinePageEntity(
          items: [
            ReviewTaskTimelineItemEntity(
              task: _view(
                taskId: 1,
                learningItemId: 10,
                reviewRound: 1,
                scheduledDate: DateTime(2026, 2, 28),
              ),
              occurredAt: DateTime(2026, 2, 28),
            ),
          ],
          nextCursor: TaskTimelineCursorEntity(
            occurredAt: DateTime(2026, 2, 28),
            taskId: 1,
          ),
        );
      final useCase = GetTasksByTimeUseCase(reviewTaskRepository: repo);

      final cursor = TaskTimelineCursorEntity(
        occurredAt: DateTime(2026, 2, 27),
        taskId: 99,
      );
      final page = await useCase.execute(
        status: ReviewTaskStatus.pending,
        cursor: cursor,
        limit: 7,
      );

      expect(repo.lastTimelineStatus, ReviewTaskStatus.pending);
      expect(repo.lastTimelineCursor, cursor);
      expect(repo.lastTimelineLimit, 7);
      expect(page.items.length, 1);
      expect(page.nextCursor, isNotNull);
    });

    test('GetTasksByTimeUseCase.getStatusCounts 会透传并返回计数', () async {
      final repo = _FakeReviewTaskRepository()..statusCounts = (9, 2, 3, 4);
      final useCase = GetTasksByTimeUseCase(reviewTaskRepository: repo);

      final (all, pending, done, skipped) = await useCase.getStatusCounts();
      expect(all, 9);
      expect(pending, 2);
      expect(done, 3);
      expect(skipped, 4);
    });

    test('DeactivateLearningItemUseCase 会透传到仓储', () async {
      final repo = _FakeLearningItemRepository();
      final useCase = DeactivateLearningItemUseCase(
        learningItemRepository: repo,
      );

      await useCase.execute(77);
      expect(repo.lastDeactivateId, 77);
    });

    test('UpdateLearningItemNoteUseCase 会透传到仓储', () async {
      final repo = _FakeLearningItemRepository();
      final useCase = UpdateLearningItemNoteUseCase(
        learningItemRepository: repo,
      );

      await useCase.execute(learningItemId: 88, note: '  n  ');
      expect(repo.lastUpdateNoteId, 88);
      expect(repo.lastUpdateNoteValue, '  n  ');
    });
  });

  group('AdjustReviewDateUseCase：规则校验与归一化', () {
    test('计划不存在/已停用/任务不存在/非 pending 均会抛 StateError', () async {
      final repo = _FakeReviewTaskRepository()..plan = const [];
      final useCase = AdjustReviewDateUseCase(reviewTaskRepository: repo);

      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 1,
          newDate: DateTime(2026, 2, 28),
        ),
        throwsA(isA<StateError>()),
      );

      repo.plan = [
        _view(
          taskId: 1,
          learningItemId: 1,
          reviewRound: 1,
          scheduledDate: DateTime(2026, 2, 28),
          isDeleted: true,
        ),
      ];
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 1,
          newDate: DateTime(2026, 3, 1),
        ),
        throwsA(isA<StateError>()),
      );

      repo.plan = [
        _view(
          taskId: 1,
          learningItemId: 1,
          reviewRound: 1,
          scheduledDate: DateTime(2026, 2, 28),
        ),
      ];
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 2,
          newDate: DateTime(2026, 3, 1),
        ),
        throwsA(isA<StateError>()),
      );

      repo.plan = [
        _view(
          taskId: 1,
          learningItemId: 1,
          reviewRound: 1,
          scheduledDate: DateTime(2026, 2, 28),
          status: ReviewTaskStatus.done,
        ),
      ];
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 1,
          newDate: DateTime(2026, 3, 1),
        ),
        throwsA(isA<StateError>()),
      );
    });

    test('新日期不能早于明天（按天归一化）', () async {
      final repo = _FakeReviewTaskRepository()
        ..plan = [
          _view(
            taskId: 1,
            learningItemId: 1,
            reviewRound: 1,
            scheduledDate: DateTime(2026, 2, 28),
          ),
        ];
      final useCase = AdjustReviewDateUseCase(reviewTaskRepository: repo);

      final now = DateTime.now();
      final today = DateTime(now.year, now.month, now.day);
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 1,
          newDate: today, // 今天不允许
        ),
        throwsA(isA<StateError>()),
      );
    });

    test('新日期需落在前后轮次允许范围内，且会按天归一化后写入仓储', () async {
      final now = DateTime.now();
      final today = DateTime(now.year, now.month, now.day);
      final tomorrow = today.add(const Duration(days: 1));

      final repo = _FakeReviewTaskRepository()
        ..plan = [
          // 轮次顺序可乱序，useCase 内部会排序后寻找 prev/next。
          _view(
            taskId: 1,
            learningItemId: 1,
            reviewRound: 3,
            scheduledDate: tomorrow.add(const Duration(days: 3, hours: 9)),
          ),
          _view(
            taskId: 2,
            learningItemId: 1,
            reviewRound: 1,
            scheduledDate: tomorrow.add(const Duration(hours: 9)),
          ),
          _view(
            taskId: 3,
            learningItemId: 1,
            reviewRound: 2,
            scheduledDate: tomorrow.add(const Duration(days: 1, hours: 9)),
          ),
        ];
      final useCase = AdjustReviewDateUseCase(reviewTaskRepository: repo);

      // prev=round1(tomorrow)，因此 minAllowed=tomorrow+1；next=round3(tomorrow+3)，maxAllowed=tomorrow+2。
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 2,
          newDate: tomorrow, // 早于 minAllowed
        ),
        throwsA(isA<StateError>()),
      );
      await expectLater(
        () => useCase.execute(
          learningItemId: 1,
          reviewRound: 2,
          newDate: tomorrow.add(const Duration(days: 3)), // 晚于 maxAllowed
        ),
        throwsA(isA<StateError>()),
      );

      final expectedDay = tomorrow.add(const Duration(days: 2));
      await useCase.execute(
        learningItemId: 1,
        reviewRound: 2,
        // 传入非零点时间，验证会归一化为 00:00。
        newDate: expectedDay.add(const Duration(hours: 20, minutes: 1)),
      );
      expect(repo.lastAdjustLearningItemId, 1);
      expect(repo.lastAdjustReviewRound, 2);
      expect(repo.lastAdjustScheduledDate, expectedDay);
    });
  });

  group('AddReviewRoundUseCase：最大轮次与停用约束', () {
    test('计划不存在/已停用/已达最大轮次均会抛 StateError', () async {
      final repo = _FakeReviewTaskRepository()..plan = const [];
      final useCase = AddReviewRoundUseCase(reviewTaskRepository: repo);

      await expectLater(() => useCase.execute(1), throwsA(isA<StateError>()));

      repo.plan = [
        _view(
          taskId: 1,
          learningItemId: 1,
          reviewRound: 1,
          scheduledDate: DateTime(2026, 2, 28),
          isDeleted: true,
        ),
      ];
      await expectLater(() => useCase.execute(1), throwsA(isA<StateError>()));

      // maxReviewRound=10
      repo.plan = List.generate(
        10,
        (i) => _view(
          taskId: i + 1,
          learningItemId: 1,
          reviewRound: i + 1,
          scheduledDate: DateTime(2026, 2, 28).add(Duration(days: i)),
        ),
      );
      await expectLater(() => useCase.execute(1), throwsA(isA<StateError>()));
    });

    test('轮次未满时会调用仓储 addReviewRound', () async {
      final repo = _FakeReviewTaskRepository()
        ..plan = [
          _view(
            taskId: 1,
            learningItemId: 1,
            reviewRound: 1,
            scheduledDate: DateTime(2026, 2, 28),
          ),
        ];
      final useCase = AddReviewRoundUseCase(reviewTaskRepository: repo);

      await useCase.execute(1);
      expect(repo.lastAddRoundLearningItemId, 1);
    });
  });
}
