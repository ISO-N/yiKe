/// 文件用途：同步服务（F12）——负责：
/// 1) 将本地数据变更写入同步日志（由仓储层调用）
/// 2) 接收并应用远端事件（映射到本地实体 ID）
/// 3) 在 /sync/exchange 中执行双向交换（增量 + 幂等）
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'dart:convert';

import 'package:drift/drift.dart';
import 'package:uuid/uuid.dart';

import '../../data/database/daos/settings_dao.dart';
import '../../data/database/daos/sync_entity_mapping_dao.dart';
import '../../data/database/daos/sync_log_dao.dart';
import '../../data/database/database.dart';
import '../../infrastructure/storage/secure_storage_service.dart';
import '../../infrastructure/storage/settings_crypto.dart';
import 'sync_models.dart';

/// 同步服务。
class SyncService {
  SyncService({
    required this.db,
    required this.syncLogDao,
    required this.syncEntityMappingDao,
    required this.settingsDao,
    required this.secureStorageService,
    required this.localDeviceId,
  }) : _settingsCrypto = SettingsCrypto(
         secureStorageService: secureStorageService,
       );

  final AppDatabase db;
  final SyncLogDao syncLogDao;
  final SyncEntityMappingDao syncEntityMappingDao;
  final SettingsDao settingsDao;
  final SecureStorageService secureStorageService;
  final String localDeviceId;

  final SettingsCrypto _settingsCrypto;

  // v1.5：备份恢复引入稳定 uuid 字段后，所有“直接写库”的入口都必须确保插入时写入 uuid，
  // 否则会触发 uuid 唯一索引冲突（默认空字符串会重复）。
  static const Uuid _uuid = Uuid();

  static const String entityLearningItem = 'learning_item';
  static const String entityLearningSubtask = 'learning_subtask';
  static const String entityReviewTask = 'review_task';
  static const String entityTemplate = 'learning_template';
  static const String entityTopic = 'learning_topic';
  static const String entityTopicItemRelation = 'topic_item_relation';
  static const String entitySettingsBundle = 'settings_bundle';

  // 快照完成标记：用于避免每次同步都全表扫描生成快照日志。
  static const String _snapshotDoneKeyPrefix = 'sync_snapshot_done_v2';

  /// 确保本机“本地源数据”已生成快照日志（用于首次全量同步）。
  ///
  /// 说明：
  /// - v3.0 引入跨设备实体映射机制：本方法仅为“本机作为 origin 的记录”生成 create 日志
  /// - 对于从其他设备同步过来的记录，其对应事件会在接收时写入 sync_logs，因此无需重复生成
  /// 参数：
  /// - [includeMockData] 是否包含 Debug 模拟数据（isMockData=true）。
  ///
  /// 说明：
  /// - 默认不包含模拟数据，避免调试数据污染真实同步结果
  /// - 若需要用模拟数据测试同步链路，可在调试设置中开启
  Future<void> ensureLocalSnapshotLogs({bool includeMockData = false}) async {
    // 性能优化：快照只需执行一次。
    // - 历史存量数据：由快照生成 create 日志
    // - 后续增量：由仓储层 SyncLogWriter 写入日志
    if (await _readSnapshotDone(includeMockData: includeMockData)) return;

    await _snapshotLearningItems(includeMockData: includeMockData);
    await _snapshotLearningSubtasks(includeMockData: includeMockData);
    await _snapshotReviewTasks(includeMockData: includeMockData);
    await _snapshotTemplates();
    await _snapshotTopics();
    await _snapshotTopicItemRelations();

    await _writeSnapshotDone(includeMockData: includeMockData, value: true);
  }

  /// 根据 SyncLog 行构建 SyncEvent。
  SyncEvent _toEvent(SyncLog row) {
    final data = _decodeJsonObject(row.data);
    return SyncEvent(
      deviceId: row.deviceId,
      entityType: row.entityType,
      entityId: row.entityId,
      operation: SyncOperation.values.firstWhere(
        (e) => e.name == row.operation,
        orElse: () => SyncOperation.update,
      ),
      data: data,
      timestampMs: row.timestampMs,
      localVersion: row.localVersion,
    );
  }

  /// 将本地日志（自某游标之后）转换为事件列表。
  Future<List<SyncEvent>> buildLocalEventsSince(int sinceMs) async {
    final rows = await syncLogDao.getLogsFromDeviceSince(
      localDeviceId,
      sinceMs,
    );
    return rows.map(_toEvent).toList();
  }

  /// 查询对端需要的增量事件（自某游标之后），可选排除某个设备的源事件。
  Future<List<SyncEvent>> buildOutgoingEventsSince(
    int sinceMs, {
    String? excludeDeviceId,
  }) async {
    final rows = await syncLogDao.getLogsSince(
      sinceMs,
      excludeDeviceId: excludeDeviceId,
    );
    return rows.map(_toEvent).toList();
  }

  /// 将远端事件持久化到本地 sync_logs（用于传播给其他设备，幂等）。
  Future<void> persistIncomingEvents(List<SyncEvent> events) async {
    if (events.isEmpty) return;
    final companions = events.map(_toLogCompanion).toList();
    await syncLogDao.insertLogs(companions);
  }

  /// 应用远端事件到本地业务表，并更新实体映射（幂等 + Last-Write-Wins）。
  ///
  /// 参数：
  /// - [events] 待应用事件
  /// - [isMaster] 当前设备是否为主机（用于“设置以主机为准”策略）
  Future<void> applyIncomingEvents(
    List<SyncEvent> events, {
    required bool isMaster,
  }) async {
    if (events.isEmpty) return;

    // 性能优化：批量应用放在事务中，降低大量事件下的卡顿与耗时。
    await db.transaction(() async {
      for (final event in events) {
        // 规则：设置项以主机为准，主机忽略来自其他设备的 settings_bundle。
        if (event.entityType == entitySettingsBundle &&
            isMaster &&
            event.deviceId != localDeviceId) {
          continue;
        }

        await _applySingleEvent(event);
      }
    });
  }

  /// 处理交换请求：先持久化并应用对端事件，再返回本端增量。
  Future<SyncExchangeResponse> handleExchangeRequest(
    SyncExchangeRequest request, {
    required bool isMaster,
    required bool includeMockData,
  }) async {
    await persistIncomingEvents(request.events);
    await applyIncomingEvents(request.events, isMaster: isMaster);

    // 关键逻辑：当对端首次同步（sinceMs=0）时，需要确保本端“快照日志”已生成。
    //
    // 背景：此前仅在“发起同步的一方”调用 ensureLocalSnapshotLogs，导致：
    // - A 点“立即同步”时，B 作为响应方不会返回自己的存量数据（sync_logs 为空）
    // - 体验上就像“立即同步是单向的”
    // 因此这里在首次交换时做一次兜底生成，保证一轮 exchange 即可实现双向同步。
    if (request.sinceMs == 0) {
      await ensureLocalSnapshotLogs(includeMockData: includeMockData);
    }

    final outgoing = await buildOutgoingEventsSince(
      request.sinceMs,
      excludeDeviceId: request.fromDeviceId,
    );

    return SyncExchangeResponse(
      serverNowMs: DateTime.now().millisecondsSinceEpoch,
      events: outgoing,
    );
  }

  /// 将 SyncEvent 转为 SyncLogsCompanion（用于插入本地日志表）。
  SyncLogsCompanion _toLogCompanion(SyncEvent event) {
    return SyncLogsCompanion(
      deviceId: Value(event.deviceId),
      entityType: Value(event.entityType),
      entityId: Value(event.entityId),
      operation: Value(event.operation.name),
      data: Value(jsonEncode(event.data)),
      timestampMs: Value(event.timestampMs),
      localVersion: Value(event.localVersion),
    );
  }

  Future<void> _applySingleEvent(SyncEvent event) async {
    final mapping = await syncEntityMappingDao.getMapping(
      entityType: event.entityType,
      originDeviceId: event.deviceId,
      originEntityId: event.entityId,
    );

    final lastApplied = mapping?.lastAppliedAtMs;
    if (lastApplied != null && lastApplied >= event.timestampMs) {
      return;
    }

    switch (event.entityType) {
      case entityLearningItem:
        await _applyLearningItem(event, mapping);
        return;
      case entityLearningSubtask:
        await _applyLearningSubtask(event, mapping);
        return;
      case entityReviewTask:
        await _applyReviewTask(event, mapping);
        return;
      case entityTemplate:
        await _applyTemplate(event, mapping);
        return;
      case entityTopic:
        await _applyTopic(event, mapping);
        return;
      case entityTopicItemRelation:
        await _applyTopicItemRelation(event, mapping);
        return;
      case entitySettingsBundle:
        await _applySettingsBundle(event);
        await _upsertMapping(
          event: event,
          localEntityId: 1,
          appliedAtMs: event.timestampMs,
          isDeleted: false,
        );
        return;
      default:
        // 未识别实体类型：忽略（为后续扩展预留）。
        return;
    }
  }

  Future<void> _applyLearningItem(
    SyncEvent event,
    SyncEntityMapping? mapping,
  ) async {
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.learningItems,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final title = (event.data['title'] as String?)?.trim() ?? '';
    if (title.isEmpty) return;

    final description = event.data['description'] as String?;
    final note = event.data['note'] as String?;
    final isMockData = (event.data['is_mock_data'] as bool?) ?? false;
    final isDeleted = (event.data['is_deleted'] as bool?) ?? false;
    final tags =
        (event.data['tags'] as List?)?.whereType<String>().toList() ?? const [];
    final learningDate = _parseDateTime(event.data['learning_date']);
    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();
    final updatedAt = _parseDateTime(event.data['updated_at']);
    final deletedAt = _parseDateTime(event.data['deleted_at']);

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.learningItems)
          .insert(
            LearningItemsCompanion.insert(
              uuid: Value(_uuid.v4()),
              title: title,
              description:
                  description == null
                      ? const Value.absent()
                      : Value(description),
              note: note == null ? const Value.absent() : Value(note),
              tags: Value(jsonEncode(tags)),
              learningDate: learningDate ?? DateTime.now(),
              createdAt: Value(createdAt),
              updatedAt: Value(updatedAt ?? createdAt),
              isDeleted: Value(isDeleted),
              deletedAt: Value(deletedAt),
              isMockData: Value(isMockData),
            ),
          );

      await _upsertMapping(
        event: event,
        localEntityId: newId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    await (db.update(
      db.learningItems,
    )..where((t) => t.id.equals(existingLocalId))).write(
      LearningItemsCompanion(
        title: Value(title),
        description: Value(description),
        note: Value(note),
        tags: Value(jsonEncode(tags)),
        learningDate: Value(learningDate ?? DateTime.now()),
        updatedAt: Value(updatedAt ?? DateTime.now()),
        isDeleted: Value(isDeleted),
        deletedAt: Value(deletedAt),
        isMockData: Value(isMockData),
      ),
    );

    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applyLearningSubtask(
    SyncEvent event,
    SyncEntityMapping? mapping,
  ) async {
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.learningSubtasks,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final learningOriginDeviceId =
        event.data['learning_origin_device_id'] as String?;
    final learningOriginEntityId =
        event.data['learning_origin_entity_id'] as int?;
    if (learningOriginDeviceId == null || learningOriginEntityId == null) {
      return;
    }

    final learningLocalId = await syncEntityMappingDao.getLocalEntityId(
      entityType: entityLearningItem,
      originDeviceId: learningOriginDeviceId,
      originEntityId: learningOriginEntityId,
    );
    if (learningLocalId == null) return;

    final content = (event.data['content'] as String?)?.trim() ?? '';
    if (content.isEmpty) return;

    final sortOrder = event.data['sort_order'] as int? ?? 0;
    final uuid = (event.data['uuid'] as String?)?.trim();
    final isMockData = (event.data['is_mock_data'] as bool?) ?? false;
    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();
    final updatedAt = _parseDateTime(event.data['updated_at']);

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.learningSubtasks)
          .insert(
            LearningSubtasksCompanion.insert(
              uuid: Value((uuid == null || uuid.isEmpty) ? _uuid.v4() : uuid),
              learningItemId: learningLocalId,
              content: content,
              sortOrder: Value(sortOrder),
              createdAt: createdAt,
              updatedAt: Value(updatedAt ?? createdAt),
              isMockData: Value(isMockData),
            ),
          );

      await _upsertMapping(
        event: event,
        localEntityId: newId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    await (db.update(
      db.learningSubtasks,
    )..where((t) => t.id.equals(existingLocalId))).write(
      LearningSubtasksCompanion(
        learningItemId: Value(learningLocalId),
        uuid: Value((uuid == null || uuid.isEmpty) ? _uuid.v4() : uuid),
        content: Value(content),
        sortOrder: Value(sortOrder),
        updatedAt: Value(updatedAt ?? DateTime.now()),
        isMockData: Value(isMockData),
      ),
    );

    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applyReviewTask(
    SyncEvent event,
    SyncEntityMapping? mapping,
  ) async {
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.reviewTasks,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final learningOriginDeviceId =
        event.data['learning_origin_device_id'] as String?;
    final learningOriginEntityId =
        event.data['learning_origin_entity_id'] as int?;
    if (learningOriginDeviceId == null || learningOriginEntityId == null) {
      return;
    }

    final learningLocalId = await syncEntityMappingDao.getLocalEntityId(
      entityType: entityLearningItem,
      originDeviceId: learningOriginDeviceId,
      originEntityId: learningOriginEntityId,
    );
    if (learningLocalId == null) return;

    final reviewRound = event.data['review_round'] as int? ?? 1;
    final scheduledDate =
        _parseDateTime(event.data['scheduled_date']) ?? DateTime.now();
    // 性能优化（v10）：occurred_at 为落地列，优先使用事件携带的值；缺失时按口径回推。
    final occurredAtFromEvent = _parseDateTime(event.data['occurred_at']);
    final status = (event.data['status'] as String?) ?? 'pending';
    final isMockData = (event.data['is_mock_data'] as bool?) ?? false;
    final completedAt = _parseDateTime(event.data['completed_at']);
    final skippedAt = _parseDateTime(event.data['skipped_at']);
    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();
    final updatedAt = _parseDateTime(event.data['updated_at']);

    final occurredAt =
        occurredAtFromEvent ??
        switch (status) {
          'pending' => scheduledDate,
          'done' => completedAt ?? scheduledDate,
          'skipped' => skippedAt ?? scheduledDate,
          _ => scheduledDate,
        };

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.reviewTasks)
          .insert(
            ReviewTasksCompanion.insert(
              uuid: Value(_uuid.v4()),
              learningItemId: learningLocalId,
              reviewRound: reviewRound,
              scheduledDate: scheduledDate,
              occurredAt: Value(occurredAt),
              status: Value(status),
              completedAt: Value(completedAt),
              skippedAt: Value(skippedAt),
              createdAt: Value(createdAt),
              updatedAt: Value(updatedAt ?? createdAt),
              isMockData: Value(isMockData),
            ),
          );

      await _upsertMapping(
        event: event,
        localEntityId: newId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    await (db.update(
      db.reviewTasks,
    )..where((t) => t.id.equals(existingLocalId))).write(
      ReviewTasksCompanion(
        learningItemId: Value(learningLocalId),
        reviewRound: Value(reviewRound),
        scheduledDate: Value(scheduledDate),
        occurredAt: Value(occurredAt),
        status: Value(status),
        completedAt: Value(completedAt),
        skippedAt: Value(skippedAt),
        updatedAt: Value(updatedAt ?? DateTime.now()),
        isMockData: Value(isMockData),
      ),
    );

    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applyTemplate(
    SyncEvent event,
    SyncEntityMapping? mapping,
  ) async {
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.learningTemplates,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final name = (event.data['name'] as String?)?.trim() ?? '';
    final titlePattern = (event.data['title_pattern'] as String?)?.trim() ?? '';
    if (name.isEmpty || titlePattern.isEmpty) return;

    final notePattern = event.data['note_pattern'] as String?;
    final tags =
        (event.data['tags'] as List?)?.whereType<String>().toList() ?? const [];
    final sortOrder = event.data['sort_order'] as int? ?? 0;
    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();
    final updatedAt = _parseDateTime(event.data['updated_at']);

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.learningTemplates)
          .insert(
            LearningTemplatesCompanion.insert(
              uuid: Value(_uuid.v4()),
              name: name,
              titlePattern: titlePattern,
              notePattern: notePattern == null
                  ? const Value.absent()
                  : Value(notePattern),
              tags: Value(jsonEncode(tags)),
              sortOrder: Value(sortOrder),
              createdAt: Value(createdAt),
              updatedAt: Value(updatedAt ?? createdAt),
            ),
          );

      await _upsertMapping(
        event: event,
        localEntityId: newId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    await (db.update(
      db.learningTemplates,
    )..where((t) => t.id.equals(existingLocalId))).write(
      LearningTemplatesCompanion(
        name: Value(name),
        titlePattern: Value(titlePattern),
        notePattern: Value(notePattern),
        tags: Value(jsonEncode(tags)),
        sortOrder: Value(sortOrder),
        updatedAt: Value(updatedAt ?? DateTime.now()),
      ),
    );

    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applyTopic(SyncEvent event, SyncEntityMapping? mapping) async {
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.learningTopics,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final name = (event.data['name'] as String?)?.trim() ?? '';
    if (name.isEmpty) return;
    final description = event.data['description'] as String?;
    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();
    final updatedAt = _parseDateTime(event.data['updated_at']);

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.learningTopics)
          .insert(
            LearningTopicsCompanion.insert(
              uuid: Value(_uuid.v4()),
              name: name,
              description: description == null
                  ? const Value.absent()
                  : Value(description),
              createdAt: Value(createdAt),
              updatedAt: Value(updatedAt ?? createdAt),
            ),
          );

      await _upsertMapping(
        event: event,
        localEntityId: newId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    await (db.update(
      db.learningTopics,
    )..where((t) => t.id.equals(existingLocalId))).write(
      LearningTopicsCompanion(
        name: Value(name),
        description: Value(description),
        updatedAt: Value(updatedAt ?? DateTime.now()),
      ),
    );

    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applyTopicItemRelation(
    SyncEvent event,
    SyncEntityMapping? mapping,
  ) async {
    // 关联表仅做 create/delete；update 视为忽略。
    if (event.operation == SyncOperation.delete) {
      final localId = mapping?.localEntityId;
      if (localId != null) {
        await (db.delete(
          db.topicItemRelations,
        )..where((t) => t.id.equals(localId))).go();
        await syncEntityMappingDao.markDeleted(
          entityType: event.entityType,
          originDeviceId: event.deviceId,
          originEntityId: event.entityId,
          appliedAtMs: event.timestampMs,
        );
      }
      return;
    }

    final topicOriginDeviceId = event.data['topic_origin_device_id'] as String?;
    final topicOriginEntityId = event.data['topic_origin_entity_id'] as int?;
    final itemOriginDeviceId = event.data['item_origin_device_id'] as String?;
    final itemOriginEntityId = event.data['item_origin_entity_id'] as int?;
    if (topicOriginDeviceId == null ||
        topicOriginEntityId == null ||
        itemOriginDeviceId == null ||
        itemOriginEntityId == null) {
      return;
    }

    final topicLocalId = await syncEntityMappingDao.getLocalEntityId(
      entityType: entityTopic,
      originDeviceId: topicOriginDeviceId,
      originEntityId: topicOriginEntityId,
    );
    final itemLocalId = await syncEntityMappingDao.getLocalEntityId(
      entityType: entityLearningItem,
      originDeviceId: itemOriginDeviceId,
      originEntityId: itemOriginEntityId,
    );
    if (topicLocalId == null || itemLocalId == null) return;

    final createdAt =
        _parseDateTime(event.data['created_at']) ?? DateTime.now();

    final existingLocalId = mapping?.localEntityId;
    if (existingLocalId == null || mapping?.isDeleted == true) {
      final newId = await db
          .into(db.topicItemRelations)
          .insert(
            TopicItemRelationsCompanion.insert(
              topicId: topicLocalId,
              learningItemId: itemLocalId,
              createdAt: Value(createdAt),
            ),
            mode: InsertMode.insertOrIgnore,
          );

      // insertOrIgnore 返回 0 时表示已存在：此时查询出实际行 ID。
      final resolvedId = newId == 0
          ? await (db.select(db.topicItemRelations)..where(
                  (t) =>
                      t.topicId.equals(topicLocalId) &
                      t.learningItemId.equals(itemLocalId),
                ))
                .getSingle()
                .then((r) => r.id)
          : newId;

      await _upsertMapping(
        event: event,
        localEntityId: resolvedId,
        appliedAtMs: event.timestampMs,
        isDeleted: false,
      );
      return;
    }

    // 若已存在映射，直接刷新 lastAppliedAtMs。
    await _upsertMapping(
      event: event,
      localEntityId: existingLocalId,
      appliedAtMs: event.timestampMs,
      isDeleted: false,
    );
  }

  Future<void> _applySettingsBundle(SyncEvent event) async {
    final map = event.data;

    Future<void> upsertEncrypted(String key, Object value) async {
      final encrypted = await _settingsCrypto.encrypt(jsonEncode(value));
      await settingsDao.upsertValue(key, encrypted);
    }

    // 说明：设置表是 key-value，接收端按当前设备密钥重新加密存储。
    if (map.containsKey('reminder_time')) {
      await upsertEncrypted('reminder_time', map['reminder_time']);
    }
    if (map.containsKey('do_not_disturb_start')) {
      await upsertEncrypted(
        'do_not_disturb_start',
        map['do_not_disturb_start'],
      );
    }
    if (map.containsKey('do_not_disturb_end')) {
      await upsertEncrypted('do_not_disturb_end', map['do_not_disturb_end']);
    }
    if (map.containsKey('notifications_enabled')) {
      await upsertEncrypted(
        'notifications_enabled',
        map['notifications_enabled'],
      );
    }
    if (map.containsKey('review_intervals')) {
      await upsertEncrypted('review_intervals', map['review_intervals']);
    }
    if (map.containsKey('theme_mode')) {
      // 主题设置仓储使用 key=theme_mode，value 为加密 JSON：{"mode":"system|light|dark"}
      await upsertEncrypted('theme_mode', {'mode': map['theme_mode']});
    }
  }

  Future<void> _upsertMapping({
    required SyncEvent event,
    required int localEntityId,
    required int appliedAtMs,
    required bool isDeleted,
  }) async {
    await syncEntityMappingDao.upsertMapping(
      SyncEntityMappingsCompanion(
        entityType: Value(event.entityType),
        originDeviceId: Value(event.deviceId),
        originEntityId: Value(event.entityId),
        localEntityId: Value(localEntityId),
        lastAppliedAtMs: Value(appliedAtMs),
        isDeleted: Value(isDeleted),
      ),
    );
  }

  String _snapshotDoneKey({required bool includeMockData}) {
    final mode = includeMockData ? 'with_mock' : 'no_mock';
    return '$_snapshotDoneKeyPrefix:$mode:$localDeviceId';
  }

  Future<bool> _readSnapshotDone({required bool includeMockData}) async {
    final raw = await settingsDao.getValue(
      _snapshotDoneKey(includeMockData: includeMockData),
    );
    if (raw == null) return false;
    try {
      final decrypted = await _settingsCrypto.decrypt(raw);
      return decrypted.trim().toLowerCase() == 'true';
    } catch (_) {
      return false;
    }
  }

  Future<void> _writeSnapshotDone({
    required bool includeMockData,
    required bool value,
  }) async {
    final encrypted = await _settingsCrypto.encrypt(value.toString());
    await settingsDao.upsertValue(
      _snapshotDoneKey(includeMockData: includeMockData),
      encrypted,
    );
  }

  Map<String, dynamic> _decodeJsonObject(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is Map) return decoded.cast<String, dynamic>();
      return const {};
    } catch (_) {
      return const {};
    }
  }

  DateTime? _parseDateTime(dynamic value) {
    if (value is String && value.trim().isNotEmpty) {
      try {
        return DateTime.parse(value);
      } catch (_) {
        return null;
      }
    }
    return null;
  }

  Future<void> _snapshotLearningItems({required bool includeMockData}) async {
    final rows = await db.select(db.learningItems).get();
    final mappingByLocalId = await _getMappingsByLocalIds(
      entityType: entityLearningItem,
      localEntityIds: rows.map((e) => e.id).toList(),
    );

    final batch = <SyncLogsCompanion>[];
    for (final row in rows) {
      // v3.1：Mock 数据默认不参与同步（可在调试开关中允许）。
      if (row.isMockData && !includeMockData) continue;

      final mapping = mappingByLocalId[row.id];

      // 若已存在且非本机 origin，则跳过（远端数据由接收端写入日志）。
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      final ts = (row.updatedAt ?? row.createdAt).millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityLearningItem,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final data = <String, dynamic>{
        'title': row.title,
        'description': row.description,
        'note': row.note,
        'tags': _parseStringList(row.tags),
        'learning_date': row.learningDate.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': (row.updatedAt ?? row.createdAt).toIso8601String(),
        'is_deleted': row.isDeleted,
        'deleted_at': row.deletedAt?.toIso8601String(),
        // 调试字段：用于保持模拟数据在跨设备同步后仍可一键清理。
        'is_mock_data': row.isMockData,
      };

      batch.add(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityLearningItem),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );

      if (batch.length >= 200) {
        await syncLogDao.insertLogs(batch);
        batch.clear();
        // 让出事件循环，避免大数据量快照时阻塞 UI。
        await Future<void>.delayed(Duration.zero);
      }
    }

    if (batch.isNotEmpty) {
      await syncLogDao.insertLogs(batch);
    }
  }

  Future<void> _snapshotLearningSubtasks({required bool includeMockData}) async {
    final rows = await db.select(db.learningSubtasks).get();
    if (rows.isEmpty) return;

    final mappingByLocalId = await _getMappingsByLocalIds(
      entityType: entityLearningSubtask,
      localEntityIds: rows.map((e) => e.id).toList(),
    );

    final learningItemIds = rows.map((e) => e.learningItemId).toSet().toList();
    final learningMappingByLocalId = await _getMappingsByLocalIds(
      entityType: entityLearningItem,
      localEntityIds: learningItemIds,
    );

    final batch = <SyncLogsCompanion>[];
    for (final row in rows) {
      if (row.isMockData && !includeMockData) continue;

      final mapping = mappingByLocalId[row.id];
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      final learningMapping = learningMappingByLocalId[row.learningItemId];
      final learningOrigin = learningMapping == null
          ? await _originForLocal(
              entityType: entityLearningItem,
              localEntityId: row.learningItemId,
            )
          : _OriginKey(
              deviceId: learningMapping.originDeviceId,
              entityId: learningMapping.originEntityId,
            );
      if (learningOrigin == null) continue;

      final updatedAt = row.updatedAt ?? row.createdAt;
      final ts = updatedAt.millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityLearningSubtask,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final data = <String, dynamic>{
        'uuid': row.uuid,
        'learning_origin_device_id': learningOrigin.deviceId,
        'learning_origin_entity_id': learningOrigin.entityId,
        'content': row.content,
        'sort_order': row.sortOrder,
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': updatedAt.toIso8601String(),
        'is_mock_data': row.isMockData,
      };

      batch.add(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityLearningSubtask),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );

      if (batch.length >= 200) {
        await syncLogDao.insertLogs(batch);
        batch.clear();
        await Future<void>.delayed(Duration.zero);
      }
    }

    if (batch.isNotEmpty) {
      await syncLogDao.insertLogs(batch);
    }
  }

  Future<void> _snapshotReviewTasks({required bool includeMockData}) async {
    final rows = await db.select(db.reviewTasks).get();
    final mappingByLocalId = await _getMappingsByLocalIds(
      entityType: entityReviewTask,
      localEntityIds: rows.map((e) => e.id).toList(),
    );

    final learningItemIds = rows.map((e) => e.learningItemId).toSet().toList();
    final learningMappingByLocalId = await _getMappingsByLocalIds(
      entityType: entityLearningItem,
      localEntityIds: learningItemIds,
    );

    final batch = <SyncLogsCompanion>[];
    for (final row in rows) {
      // v3.1：Mock 数据默认不参与同步（可在调试开关中允许）。
      if (row.isMockData && !includeMockData) continue;

      final mapping = mappingByLocalId[row.id];
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      // 复习任务引用学习内容：需要把 learningItemId 解析为 origin key。
      final learningMapping = learningMappingByLocalId[row.learningItemId];
      final learningOrigin = learningMapping == null
          ? await _originForLocal(
              entityType: entityLearningItem,
              localEntityId: row.learningItemId,
            )
          : _OriginKey(
              deviceId: learningMapping.originDeviceId,
              entityId: learningMapping.originEntityId,
            );
      if (learningOrigin == null) continue;

      final updatedAt = row.updatedAt ?? row.createdAt;
      final ts = updatedAt.millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityReviewTask,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final occurredAt =
          row.occurredAt ??
          switch (row.status) {
            'pending' => row.scheduledDate,
            'done' => row.completedAt ?? row.scheduledDate,
            'skipped' => row.skippedAt ?? row.scheduledDate,
            _ => row.scheduledDate,
          };

      final data = <String, dynamic>{
        'learning_origin_device_id': learningOrigin.deviceId,
        'learning_origin_entity_id': learningOrigin.entityId,
        'review_round': row.reviewRound,
        'scheduled_date': row.scheduledDate.toIso8601String(),
        'occurred_at': occurredAt.toIso8601String(),
        'status': row.status,
        'completed_at': row.completedAt?.toIso8601String(),
        'skipped_at': row.skippedAt?.toIso8601String(),
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': updatedAt.toIso8601String(),
        // 调试字段：用于保持模拟数据在跨设备同步后仍可一键清理。
        'is_mock_data': row.isMockData,
      };

      batch.add(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityReviewTask),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );

      if (batch.length >= 200) {
        await syncLogDao.insertLogs(batch);
        batch.clear();
        // 让出事件循环，避免大数据量快照时阻塞 UI。
        await Future<void>.delayed(Duration.zero);
      }
    }

    if (batch.isNotEmpty) {
      await syncLogDao.insertLogs(batch);
    }
  }

  Future<void> _snapshotTemplates() async {
    final rows = await db.select(db.learningTemplates).get();
    for (final row in rows) {
      final mapping = await syncEntityMappingDao.getByLocalEntityId(
        entityType: entityTemplate,
        localEntityId: row.id,
      );
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      final updatedAt = row.updatedAt ?? row.createdAt;
      final ts = updatedAt.millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityTemplate,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final data = <String, dynamic>{
        'name': row.name,
        'title_pattern': row.titlePattern,
        'note_pattern': row.notePattern,
        'tags': _parseStringList(row.tags),
        'sort_order': row.sortOrder,
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': updatedAt.toIso8601String(),
      };

      await syncLogDao.insertLog(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityTemplate),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );
    }
  }

  Future<void> _snapshotTopics() async {
    final rows = await db.select(db.learningTopics).get();
    for (final row in rows) {
      final mapping = await syncEntityMappingDao.getByLocalEntityId(
        entityType: entityTopic,
        localEntityId: row.id,
      );
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      final updatedAt = row.updatedAt ?? row.createdAt;
      final ts = updatedAt.millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityTopic,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final data = <String, dynamic>{
        'name': row.name,
        'description': row.description,
        'created_at': row.createdAt.toIso8601String(),
        'updated_at': updatedAt.toIso8601String(),
      };

      await syncLogDao.insertLog(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityTopic),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );
    }
  }

  Future<void> _snapshotTopicItemRelations() async {
    final rows = await db.select(db.topicItemRelations).get();
    for (final row in rows) {
      final mapping = await syncEntityMappingDao.getByLocalEntityId(
        entityType: entityTopicItemRelation,
        localEntityId: row.id,
      );
      if (mapping != null &&
          (mapping.originDeviceId != localDeviceId ||
              mapping.originEntityId != row.id)) {
        continue;
      }

      final topicOrigin = await _originForLocal(
        entityType: entityTopic,
        localEntityId: row.topicId,
      );
      final itemOrigin = await _originForLocal(
        entityType: entityLearningItem,
        localEntityId: row.learningItemId,
      );
      if (topicOrigin == null || itemOrigin == null) continue;

      final ts = row.createdAt.millisecondsSinceEpoch;
      await _ensureLocalOriginMapping(
        entityType: entityTopicItemRelation,
        localEntityId: row.id,
        appliedAtMs: ts,
      );

      final data = <String, dynamic>{
        'topic_origin_device_id': topicOrigin.deviceId,
        'topic_origin_entity_id': topicOrigin.entityId,
        'item_origin_device_id': itemOrigin.deviceId,
        'item_origin_entity_id': itemOrigin.entityId,
        'created_at': row.createdAt.toIso8601String(),
      };

      await syncLogDao.insertLog(
        SyncLogsCompanion(
          deviceId: Value(localDeviceId),
          entityType: const Value(entityTopicItemRelation),
          entityId: Value(row.id),
          operation: const Value('create'),
          data: Value(jsonEncode(data)),
          timestampMs: Value(ts),
          localVersion: const Value(0),
        ),
      );
    }
  }

  /// 批量按 localEntityId 预取映射，减少大数据量下的 N 次查询。
  ///
  /// 说明：
  /// - SQLite 默认变量上限较低，这里按固定大小分块查询
  /// - 返回值以 localEntityId 为 key，便于快照流程快速判断“是否为远端 origin”
  Future<Map<int, SyncEntityMapping>> _getMappingsByLocalIds({
    required String entityType,
    required List<int> localEntityIds,
  }) async {
    if (localEntityIds.isEmpty) return const {};

    const chunkSize = 400;
    final result = <int, SyncEntityMapping>{};

    for (var i = 0; i < localEntityIds.length; i += chunkSize) {
      final chunk = localEntityIds
          .skip(i)
          .take(chunkSize)
          .toList(growable: false);
      final rows =
          await (db.select(db.syncEntityMappings)..where(
                (t) =>
                    t.entityType.equals(entityType) &
                    t.localEntityId.isIn(chunk),
              ))
              .get();
      for (final row in rows) {
        result[row.localEntityId] = row;
      }
    }

    return result;
  }

  Future<void> _ensureLocalOriginMapping({
    required String entityType,
    required int localEntityId,
    required int appliedAtMs,
  }) async {
    await syncEntityMappingDao.upsertMapping(
      SyncEntityMappingsCompanion(
        entityType: Value(entityType),
        originDeviceId: Value(localDeviceId),
        originEntityId: Value(localEntityId),
        localEntityId: Value(localEntityId),
        lastAppliedAtMs: Value(appliedAtMs),
        isDeleted: const Value(false),
      ),
    );
  }

  Future<_OriginKey?> _originForLocal({
    required String entityType,
    required int localEntityId,
  }) async {
    final mapping = await syncEntityMappingDao.getByLocalEntityId(
      entityType: entityType,
      localEntityId: localEntityId,
    );
    if (mapping != null) {
      return _OriginKey(
        deviceId: mapping.originDeviceId,
        entityId: mapping.originEntityId,
      );
    }

    // 若缺失映射，按本机 origin 兜底创建（保证后续同步可定位）。
    final ts = DateTime.now().millisecondsSinceEpoch;
    await _ensureLocalOriginMapping(
      entityType: entityType,
      localEntityId: localEntityId,
      appliedAtMs: ts,
    );
    return _OriginKey(deviceId: localDeviceId, entityId: localEntityId);
  }

  List<String> _parseStringList(String raw) {
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<String>()
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList();
    } catch (_) {
      return const [];
    }
  }
}

class _OriginKey {
  const _OriginKey({required this.deviceId, required this.entityId});

  final String deviceId;
  final int entityId;
}
