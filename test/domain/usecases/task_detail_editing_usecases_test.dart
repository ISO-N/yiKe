// 文件用途：任务详情相关领域用例测试（子任务、基本信息、主题关联、备注迁移）。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_test/flutter_test.dart';
import 'package:yike/domain/entities/learning_item.dart';
import 'package:yike/domain/entities/learning_subtask.dart';
import 'package:yike/domain/entities/learning_topic.dart';
import 'package:yike/domain/repositories/learning_item_repository.dart';
import 'package:yike/domain/repositories/learning_subtask_repository.dart';
import 'package:yike/domain/repositories/learning_topic_repository.dart';
import 'package:yike/domain/repositories/task_structure_migration_repository.dart';
import 'package:yike/domain/usecases/create_subtask_usecase.dart';
import 'package:yike/domain/usecases/delete_subtask_usecase.dart';
import 'package:yike/domain/usecases/migrate_note_to_subtasks_usecase.dart';
import 'package:yike/domain/usecases/reorder_subtasks_usecase.dart';
import 'package:yike/domain/usecases/set_learning_item_topics_usecase.dart';
import 'package:yike/domain/usecases/update_learning_item_meta_usecase.dart';
import 'package:yike/domain/usecases/update_subtask_usecase.dart';

class _FakeLearningItemRepository extends Fake
    implements LearningItemRepository {
  LearningItemEntity? item;
  LearningItemEntity? lastUpdated;

  @override
  Future<LearningItemEntity?> getById(int id) async {
    if (item?.id == id) return item;
    return null;
  }

  @override
  Future<LearningItemEntity> update(LearningItemEntity item) async {
    lastUpdated = item;
    this.item = item;
    return item;
  }
}

class _FakeLearningSubtaskRepository extends Fake
    implements LearningSubtaskRepository {
  List<LearningSubtaskEntity> subtasks = <LearningSubtaskEntity>[];
  LearningSubtaskEntity? lastCreated;
  LearningSubtaskEntity? lastUpdated;
  int? deletedId;
  int? reorderedLearningItemId;
  List<int>? reorderedIds;

  @override
  Future<List<LearningSubtaskEntity>> getByLearningItemId(
    int learningItemId,
  ) async {
    return subtasks.where((e) => e.learningItemId == learningItemId).toList();
  }

  @override
  Future<LearningSubtaskEntity> create(LearningSubtaskEntity subtask) async {
    lastCreated = subtask;
    final saved = subtask.copyWith(id: 100 + subtasks.length);
    subtasks = [...subtasks, saved];
    return saved;
  }

  @override
  Future<LearningSubtaskEntity> update(LearningSubtaskEntity subtask) async {
    lastUpdated = subtask;
    return subtask;
  }

  @override
  Future<void> delete(int id) async {
    deletedId = id;
  }

  @override
  Future<void> reorder(int learningItemId, List<int> subtaskIds) async {
    reorderedLearningItemId = learningItemId;
    reorderedIds = subtaskIds;
  }
}

class _FakeLearningTopicRepository extends Fake
    implements LearningTopicRepository {
  List<LearningTopicEntity> topics = const <LearningTopicEntity>[];
  final List<(int topicId, int learningItemId)> added = <(int, int)>[];
  final List<(int topicId, int learningItemId)> removed = <(int, int)>[];

  @override
  Future<List<LearningTopicEntity>> getAll() async => topics;

  @override
  Future<void> addItemToTopic(int topicId, int learningItemId) async {
    added.add((topicId, learningItemId));
  }

  @override
  Future<void> removeItemFromTopic(int topicId, int learningItemId) async {
    removed.add((topicId, learningItemId));
  }
}

class _FakeTaskStructureMigrationRepository extends Fake
    implements TaskStructureMigrationRepository {
  final List<List<LegacyNoteMigrationItem>> batches;
  final List<_AppliedMigration> applied = <_AppliedMigration>[];
  var _index = 0;

  _FakeTaskStructureMigrationRepository(this.batches);

  @override
  Future<List<LegacyNoteMigrationItem>> getPendingLegacyNoteItems({
    int limit = 200,
  }) async {
    if (_index >= batches.length) return const <LegacyNoteMigrationItem>[];
    final batch = batches[_index];
    _index++;
    return batch;
  }

  @override
  Future<void> applyMigrationForItem({
    required int learningItemId,
    required bool isMockData,
    required String? migratedDescription,
    required List<String> migratedSubtasks,
  }) async {
    applied.add(
      _AppliedMigration(
        learningItemId: learningItemId,
        isMockData: isMockData,
        migratedDescription: migratedDescription,
        migratedSubtasks: migratedSubtasks,
      ),
    );
  }
}

class _AppliedMigration {
  const _AppliedMigration({
    required this.learningItemId,
    required this.isMockData,
    required this.migratedDescription,
    required this.migratedSubtasks,
  });

  final int learningItemId;
  final bool isMockData;
  final String? migratedDescription;
  final List<String> migratedSubtasks;
}

LearningItemEntity _item({
  required int id,
  bool isDeleted = false,
  bool isMockData = false,
}) {
  return LearningItemEntity(
    uuid: 'item-$id',
    id: id,
    title: '原始标题',
    tags: const <String>['旧标签'],
    learningDate: DateTime(2026, 3, 6),
    createdAt: DateTime(2026, 3, 6, 9),
    updatedAt: DateTime(2026, 3, 6, 9),
    isDeleted: isDeleted,
    isMockData: isMockData,
  );
}

void main() {
  group('子任务相关用例', () {
    test('CreateSubtaskUseCase 会 trim 内容并追加到末尾', () async {
      final itemRepo = _FakeLearningItemRepository()
        ..item = _item(id: 1, isMockData: true);
      final subtaskRepo = _FakeLearningSubtaskRepository()
        ..subtasks = <LearningSubtaskEntity>[
          LearningSubtaskEntity(
            uuid: 's-1',
            id: 1,
            learningItemId: 1,
            content: '已有子任务',
            sortOrder: 2,
            createdAt: DateTime(2026, 3, 6, 9),
            updatedAt: DateTime(2026, 3, 6, 9),
          ),
        ];
      final useCase = CreateSubtaskUseCase(
        learningItemRepository: itemRepo,
        learningSubtaskRepository: subtaskRepo,
      );

      final created = await useCase.execute(
        learningItemId: 1,
        content: '  新子任务  ',
      );

      expect(created.id, isNotNull);
      expect(subtaskRepo.lastCreated?.content, '新子任务');
      expect(subtaskRepo.lastCreated?.sortOrder, 3);
      expect(subtaskRepo.lastCreated?.isMockData, isTrue);
    });

    test('UpdateSubtaskUseCase 会校验 id、停用状态与空内容', () async {
      final itemRepo = _FakeLearningItemRepository()..item = _item(id: 1);
      final subtaskRepo = _FakeLearningSubtaskRepository();
      final useCase = UpdateSubtaskUseCase(
        learningItemRepository: itemRepo,
        learningSubtaskRepository: subtaskRepo,
      );

      await expectLater(
        () => useCase.execute(
          LearningSubtaskEntity(
            uuid: 's-1',
            learningItemId: 1,
            content: '内容',
            sortOrder: 0,
            createdAt: DateTime(2026, 3, 6, 9),
          ),
        ),
        throwsArgumentError,
      );

      itemRepo.item = _item(id: 1, isDeleted: true);
      await expectLater(
        () => useCase.execute(
          LearningSubtaskEntity(
            uuid: 's-2',
            id: 2,
            learningItemId: 1,
            content: '内容',
            sortOrder: 0,
            createdAt: DateTime(2026, 3, 6, 9),
          ),
        ),
        throwsA(isA<StateError>()),
      );

      itemRepo.item = _item(id: 1);
      await expectLater(
        () => useCase.execute(
          LearningSubtaskEntity(
            uuid: 's-3',
            id: 3,
            learningItemId: 1,
            content: '   ',
            sortOrder: 0,
            createdAt: DateTime(2026, 3, 6, 9),
          ),
        ),
        throwsArgumentError,
      );
    });

    test('DeleteSubtaskUseCase 与 ReorderSubtasksUseCase 透传到仓储', () async {
      final subtaskRepo = _FakeLearningSubtaskRepository();

      await DeleteSubtaskUseCase(
        learningSubtaskRepository: subtaskRepo,
      ).execute(9);
      expect(subtaskRepo.deletedId, 9);

      await ReorderSubtasksUseCase(
        learningSubtaskRepository: subtaskRepo,
      ).execute(learningItemId: 3, subtaskIds: const <int>[7, 5, 6]);
      expect(subtaskRepo.reorderedLearningItemId, 3);
      expect(subtaskRepo.reorderedIds, const <int>[7, 5, 6]);
    });
  });

  group('基本信息与主题关联用例', () {
    test('UpdateLearningItemMetaUseCase 会归一化标题与标签', () async {
      final itemRepo = _FakeLearningItemRepository()..item = _item(id: 10);
      final useCase = UpdateLearningItemMetaUseCase(
        learningItemRepository: itemRepo,
      );

      await useCase.execute(
        learningItemId: 10,
        title: '  新标题  ',
        tags: const <String>['  数学 ', '', '数学', ' 英语 '],
      );

      expect(itemRepo.lastUpdated?.title, '新标题');
      expect(itemRepo.lastUpdated?.tags, const <String>['数学', '英语']);
    });

    test('SetLearningItemTopicsUseCase 会做差量 add/remove', () async {
      final itemRepo = _FakeLearningItemRepository()..item = _item(id: 42);
      final topicRepo = _FakeLearningTopicRepository()
        ..topics = <LearningTopicEntity>[
          LearningTopicEntity(
            uuid: 'topic-1',
            id: 1,
            name: '主题1',
            description: null,
            itemIds: const <int>[42],
            createdAt: DateTime(2026, 3, 6, 9),
            updatedAt: DateTime(2026, 3, 6, 9),
          ),
          LearningTopicEntity(
            uuid: 'topic-2',
            id: 2,
            name: '主题2',
            description: null,
            itemIds: const <int>[42],
            createdAt: DateTime(2026, 3, 6, 9),
            updatedAt: DateTime(2026, 3, 6, 9),
          ),
          LearningTopicEntity(
            uuid: 'topic-3',
            id: 3,
            name: '主题3',
            description: null,
            itemIds: const <int>[],
            createdAt: DateTime(2026, 3, 6, 9),
            updatedAt: DateTime(2026, 3, 6, 9),
          ),
        ];
      final useCase = SetLearningItemTopicsUseCase(
        learningTopicRepository: topicRepo,
        learningItemRepository: itemRepo,
      );

      await useCase.execute(learningItemId: 42, topicIds: <int>{2, 3});

      expect(topicRepo.removed, <(int, int)>[(1, 42)]);
      expect(topicRepo.added, <(int, int)>[(3, 42)]);
    });
  });

  group('备注迁移用例', () {
    test('MigrateNoteToSubtasksUseCase 会分批迁移并累计处理数', () async {
      final repo = _FakeTaskStructureMigrationRepository(
        <List<LegacyNoteMigrationItem>>[
          <LegacyNoteMigrationItem>[
            const LegacyNoteMigrationItem(
              learningItemId: 1,
              note: '第一段描述\n第二条',
              isMockData: false,
            ),
          ],
          <LegacyNoteMigrationItem>[
            const LegacyNoteMigrationItem(
              learningItemId: 2,
              note: '- 子任务1\n- 子任务2',
              isMockData: true,
            ),
          ],
        ],
      );
      final useCase = MigrateNoteToSubtasksUseCase(repository: repo);

      final migrated = await useCase.execute(batchSize: 1);

      expect(migrated, 2);
      expect(repo.applied.length, 2);
      expect(repo.applied.first.migratedDescription, '第一段描述\n第二条');
      expect(repo.applied.first.migratedSubtasks, isEmpty);
      expect(repo.applied.last.migratedSubtasks, const <String>[
        '子任务1',
        '子任务2',
      ]);
      expect(repo.applied.last.isMockData, isTrue);
    });
  });
}
