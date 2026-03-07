// 文件用途：ExportDataUseCase 单元测试（F8：JSON/CSV 序列化与写文件）。
// 作者：Codex
// 创建日期：2026-02-25

import 'dart:convert';
import 'dart:io';

import 'package:drift/drift.dart' as drift;
import 'package:flutter_test/flutter_test.dart';
import 'package:path_provider_platform_interface/path_provider_platform_interface.dart';
import 'package:yike/data/database/daos/learning_item_dao.dart';
import 'package:yike/data/database/daos/learning_subtask_dao.dart';
import 'package:yike/data/database/daos/review_task_dao.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/data/repositories/learning_item_repository_impl.dart';
import 'package:yike/data/repositories/learning_subtask_repository_impl.dart';
import 'package:yike/data/repositories/review_task_repository_impl.dart';
import 'package:yike/domain/usecases/export_data_usecase.dart';

import '../../helpers/test_database.dart';

/// PathProviderPlatform 假实现：仅提供 documentsPath。
class _FakePathProviderPlatform extends PathProviderPlatform {
  _FakePathProviderPlatform(this.documentsPath);

  final String documentsPath;

  @override
  Future<String?> getApplicationDocumentsPath() async => documentsPath;
}

void main() {
  late AppDatabase db;
  late ExportDataUseCase useCase;

  setUp(() {
    db = createInMemoryDatabase();
    useCase = ExportDataUseCase(
      learningItemRepository: LearningItemRepositoryImpl(LearningItemDao(db)),
      learningSubtaskRepository: LearningSubtaskRepositoryImpl(
        dao: LearningSubtaskDao(db),
      ),
      reviewTaskRepository: ReviewTaskRepositoryImpl(dao: ReviewTaskDao(db)),
    );
  });

  tearDown(() async {
    await db.close();
  });

  Future<int> insertItem() async {
    return db
        .into(db.learningItems)
        .insert(
          LearningItemsCompanion.insert(
            title: 'T1',
            note: const drift.Value('N1'),
            tags: drift.Value(jsonEncode(['a', 'b'])),
            learningDate: DateTime(2026, 2, 25),
            createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
          ),
        );
  }

  Future<int> insertTask({required int itemId}) async {
    return db
        .into(db.reviewTasks)
        .insert(
          ReviewTasksCompanion.insert(
            learningItemId: itemId,
            reviewRound: 1,
            scheduledDate: DateTime(2026, 2, 26, 9),
            status: const drift.Value('done'),
            completedAt: drift.Value(DateTime(2026, 2, 26, 9)),
            createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
          ),
        );
  }

  test('JSON 导出：文件生成且字段齐全', () async {
    final tmp = await Directory.systemTemp.createTemp('yike_export_test_');
    final original = PathProviderPlatform.instance;
    PathProviderPlatform.instance = _FakePathProviderPlatform(tmp.path);

    try {
      final itemId = await insertItem();
      await insertTask(itemId: itemId);

      final result = await useCase.execute(
        const ExportParams(
          format: ExportFormat.json,
          includeItems: true,
          includeTasks: true,
        ),
      );

      expect(await result.file.exists(), true);
      expect(result.totalCount, 2);

      final json =
          jsonDecode(await result.file.readAsString()) as Map<String, Object?>;
      expect(json['version'], '2.1');
      expect((json['items'] as List).length, 1);
      expect((json['tasks'] as List).length, 1);
    } finally {
      PathProviderPlatform.instance = original;
    }
  });

  test('CSV 导出：包含学习内容与复习任务两个区域', () async {
    final tmp = await Directory.systemTemp.createTemp('yike_export_test_');
    final original = PathProviderPlatform.instance;
    PathProviderPlatform.instance = _FakePathProviderPlatform(tmp.path);

    try {
      final itemId = await insertItem();
      await insertTask(itemId: itemId);

      final result = await useCase.execute(
        const ExportParams(
          format: ExportFormat.csv,
          includeItems: true,
          includeTasks: true,
        ),
      );

      final content = await result.file.readAsString();
      expect(content.contains('学习内容'), true);
      expect(content.contains('复习任务'), true);
      expect(
        content.contains(
          'id,title,description,subtasks,tags,learningDate,createdAt,isDeleted,deletedAt',
        ),
        true,
      );
      expect(
        content.contains(
          'id,learningItemId,reviewRound,scheduledDate,status,completedAt,skippedAt,createdAt',
        ),
        true,
      );
    } finally {
      PathProviderPlatform.instance = original;
    }
  });

  test('preview 与 execute 会处理空选择、空数据与 CSV 转义', () async {
    final tmp = await Directory.systemTemp.createTemp('yike_export_test_');
    final original = PathProviderPlatform.instance;
    PathProviderPlatform.instance = _FakePathProviderPlatform(tmp.path);

    try {
      await expectLater(
        useCase.preview(
          const ExportParams(
            format: ExportFormat.json,
            includeItems: false,
            includeTasks: false,
          ),
        ),
        throwsA(isA<ExportException>()),
      );

      await expectLater(
        useCase.execute(
          const ExportParams(
            format: ExportFormat.csv,
            includeItems: true,
            includeTasks: false,
          ),
        ),
        throwsA(isA<ExportException>()),
      );

      final itemId = await db.into(db.learningItems).insert(
        LearningItemsCompanion.insert(
          title: 'T,1',
          description: const drift.Value('含有"引号"'),
          note: const drift.Value('原始备注'),
          tags: drift.Value(jsonEncode(<String>['a,b', 'c'])),
          learningDate: DateTime(2026, 2, 25),
          createdAt: drift.Value(DateTime(2026, 2, 25, 9)),
        ),
      );
      await db.into(db.learningSubtasks).insert(
        LearningSubtasksCompanion.insert(
          learningItemId: itemId,
          content: '第一行\n第二行',
          sortOrder: const drift.Value(0),
          createdAt: DateTime(2026, 2, 25, 10),
        ),
      );

      final preview = await useCase.preview(
        const ExportParams(
          format: ExportFormat.csv,
          includeItems: true,
          includeTasks: false,
        ),
      );
      final result = await useCase.execute(
        const ExportParams(
          format: ExportFormat.csv,
          includeItems: true,
          includeTasks: false,
        ),
      );

      expect(preview.itemCount, 1);
      expect(preview.taskCount, 0);
      final content = await result.file.readAsString();
      expect(content, contains('"T,1"'));
      expect(content, contains('"含有""引号"""'));
      expect(content, contains('"第一行\n第二行"'));
      expect(content, contains('"a,b;c"'));
    } finally {
      PathProviderPlatform.instance = original;
    }
  });
}
