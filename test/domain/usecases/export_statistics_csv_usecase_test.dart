// 文件用途：统计 CSV 导出用例测试（文件输出与按天聚合内容）。
// 作者：Codex
// 创建日期：2026-03-06

import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/task_day_stats.dart';
import 'package:yike/domain/repositories/review_task_repository.dart';
import 'package:yike/domain/usecases/export_statistics_csv_usecase.dart';

class _FakeReviewTaskRepository extends Fake implements ReviewTaskRepository {
  Map<DateTime, TaskDayStats> statsByDay = const <DateTime, TaskDayStats>{};
  DateTime? lastStart;
  DateTime? lastEnd;

  @override
  Future<Map<DateTime, TaskDayStats>> getTaskDayStatsInRange(
    DateTime start,
    DateTime end,
  ) async {
    lastStart = start;
    lastEnd = end;
    return statsByDay;
  }
}

void main() {
  test('execute 会导出指定年份 CSV 并写入文件', () async {
    final repo = _FakeReviewTaskRepository()
      ..statsByDay = <DateTime, TaskDayStats>{
        DateTime(2026, 3, 6): const TaskDayStats(
          pendingCount: 3,
          doneCount: 2,
          skippedCount: 1,
        ),
      };
    final useCase = ExportStatisticsCsvUseCase(reviewTaskRepository: repo);
    final dir = await Directory.systemTemp.createTemp('yike_stats_csv_test_');
    addTearDown(() async {
      if (await dir.exists()) {
        await dir.delete(recursive: true);
      }
    });

    final outputPath = '${dir.path}${Platform.pathSeparator}stats.csv';
    final result = await useCase.execute(year: 2026, outputPath: outputPath);
    final content = await result.file.readAsString();

    expect(repo.lastStart, DateTime(2026, 1, 1));
    expect(repo.lastEnd, DateTime(2027, 1, 1));
    expect(result.file.path, outputPath);
    expect(result.fileName, 'yike_statistics_2026.csv');
    expect(result.bytes, greaterThan(0));
    expect(content, contains('date,completed,skipped,pending,completion_rate'));
    expect(content, contains('2026-03-06,2,1,3,40.00'));
  });
}
