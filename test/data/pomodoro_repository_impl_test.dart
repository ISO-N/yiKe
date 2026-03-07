// 文件用途：PomodoroRepositoryImpl 单元测试，覆盖 CRUD 映射与统计聚合行为。
// 作者：Codex
// 创建日期：2026-03-07

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/daos/pomodoro_record_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/pomodoro_repository_impl.dart';
import 'package:yike/domain/entities/pomodoro_record.dart';

import '../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late PomodoroRepositoryImpl repository;

  setUp(() {
    db = createInMemoryDatabase();
    repository = PomodoroRepositoryImpl(dao: PomodoroRecordDao(db));
  });

  tearDown(() async {
    await db.close();
  });

  group('PomodoroRepositoryImpl', () {
    test('create/get/update/delete 会保持实体映射一致', () async {
      final id = await repository.createRecord(
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 7, 9),
          durationMinutes: 25,
          phaseType: 'work',
          completed: false,
        ),
      );

      var records = await repository.getAllRecords();
      expect(records, hasLength(1));
      expect(records.single.id, id);
      expect(records.single.phaseType, 'work');

      final updated = records.single.copyWith(
        durationMinutes: 30,
        completed: true,
      );
      expect(await repository.updateRecord(updated), isTrue);

      records = await repository.getAllRecords();
      expect(records.single.durationMinutes, 30);
      expect(records.single.completed, isTrue);

      expect(await repository.deleteRecord(id), 1);
      expect(await repository.getAllRecords(), isEmpty);
    });

    test('getStats 仅统计已完成的工作阶段，并区分今日/本周/累计分钟', () async {
      final now = DateTime(2026, 3, 12, 18);
      final records = <PomodoroRecordEntity>[
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 12, 9),
          durationMinutes: 25,
          phaseType: 'work',
          completed: true,
        ),
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 11, 14),
          durationMinutes: 50,
          phaseType: 'work',
          completed: true,
        ),
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 10, 15),
          durationMinutes: 15,
          phaseType: 'shortBreak',
          completed: true,
        ),
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 12, 16),
          durationMinutes: 25,
          phaseType: 'work',
          completed: false,
        ),
        PomodoroRecordEntity(
          startTime: DateTime(2026, 3, 1, 9),
          durationMinutes: 40,
          phaseType: 'work',
          completed: true,
        ),
      ];

      for (final record in records) {
        await repository.createRecord(record);
      }

      final stats = await repository.getStats(now: now);
      expect(stats.todayCompletedCount, 1);
      expect(stats.weekCompletedCount, 2);
      expect(stats.totalFocusMinutes, 115);
    });
  });
}
