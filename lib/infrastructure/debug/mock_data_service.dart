/// 文件用途：Debug 模拟数据服务（v3.1），用于一键生成/清理学习内容与复习任务。
/// 作者：Codex
/// 创建日期：2026-02-26
library;

import 'dart:math';

import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';

import '../../core/utils/ebbinghaus_utils.dart';
import '../../data/database/daos/learning_item_dao.dart';
import '../../data/database/daos/review_task_dao.dart';
import '../../data/database/database.dart';

/// 模拟数据模板。
enum MockDataTemplate {
  /// 随机混合（英语单词/历史事件/自定义）。
  random,

  /// 英语单词模板。
  englishWords,

  /// 历史事件模板。
  historyEvents,

  /// 自定义模板（使用 [MockDataConfig.customPrefix]）。
  custom,
}

/// 模拟数据生成配置。
class MockDataConfig {
  const MockDataConfig({
    this.contentCount = 10,
    this.taskCount = 50,
    this.daysRange = 30,
    this.template = MockDataTemplate.random,
    this.customPrefix = '自定义',
  });

  /// 学习内容数量（1-100）。
  final int contentCount;

  /// 复习任务数量（1-500）。
  final int taskCount;

  /// 复习日期范围：最近 N 天（7/14/30/60/90）。
  final int daysRange;

  /// 生成模板。
  final MockDataTemplate template;

  /// 自定义模板前缀。
  final String customPrefix;
}

/// 模拟数据生成结果。
class MockDataGenerateResult {
  const MockDataGenerateResult({
    required this.insertedItemCount,
    required this.insertedTaskCount,
  });

  final int insertedItemCount;
  final int insertedTaskCount;
}

/// Debug 模拟数据服务。
///
/// 说明：
/// - 仅 Debug 模式下可用（release 下调用会抛出异常）
/// - 生成的数据统一标记 isMockData=true，便于隔离同步/导出与一键清理
class MockDataService {
  /// 构造函数。
  ///
  /// 参数：
  /// - [db] 数据库实例
  /// - [learningItemDao] 学习内容 DAO
  /// - [reviewTaskDao] 复习任务 DAO
  /// - [random] 随机数生成器（可选；用于测试注入固定种子）
  MockDataService({
    required this.db,
    required LearningItemDao learningItemDao,
    required ReviewTaskDao reviewTaskDao,
    Random? random,
  }) : _learningItemDao = learningItemDao,
       _reviewTaskDao = reviewTaskDao,
       _random = random ?? Random();

  final AppDatabase db;
  final LearningItemDao _learningItemDao;
  final ReviewTaskDao _reviewTaskDao;
  final Random _random;

  static const Uuid _uuid = Uuid();

  /// 生成模拟数据（学习内容 + 复习任务）。
  ///
  /// 返回值：生成结果（插入条数）。
  /// 异常：
  /// - 非 Debug 模式下调用会抛出 [StateError]
  /// - 参数不合法时抛出 [ArgumentError]
  ///
  /// 额外说明：
  /// - v1.4 规格增强后，默认复习间隔扩展至 10 轮（见 [EbbinghausUtils]）
  /// - 生成器会以“学习日期 + 轮次间隔”的口径推导 scheduledDate，避免生成不符合业务语义的数据
  Future<MockDataGenerateResult> generate(
    MockDataConfig config, {
    DateTime? nowOverride,
  }) async {
    if (!kDebugMode) {
      throw StateError('MockDataService 仅允许在 Debug 模式下使用');
    }
    _validateConfig(config);

    final now = nowOverride ?? DateTime.now();
    final todayStart = DateTime(now.year, now.month, now.day);

    // 复习日期范围：最近 N 天（包含今天）。
    final scheduledStart = todayStart.subtract(
      Duration(days: config.daysRange - 1),
    );
    final scheduledEndExclusive = todayStart.add(const Duration(days: 1));

    final intervalsDays = EbbinghausUtils.defaultIntervalsDays;
    final maxRound = intervalsDays.length;

    // 生成学习日期时优先覆盖前 5 轮（兼容 v1.0 的“常用间隔”），以提升“最近 N 天”范围内可生成任务的密度。
    // 当 daysRange 较小时，无法保证所有前 5 轮都能落在范围内，此时会退化为更宽的学习日期取值范围。
    final coverageRounds = min(5, maxRound);
    final coverageMaxIntervalDays = intervalsDays[coverageRounds - 1];

    // 优先策略：让 round=1..coverageRounds 尽可能落在“最近 N 天（包含今天）”范围内：
    // - 对 round=1：learningDay >= scheduledStart - 1
    // - 对 round=coverageMax：learningDay <= todayStart - coverageMaxIntervalDays
    final preferredEarliestLearningDay = scheduledStart.subtract(
      const Duration(days: 1),
    );
    final preferredLatestLearningDay = todayStart.subtract(
      Duration(days: coverageMaxIntervalDays),
    );

    // 兜底策略：当 daysRange 太小导致 preferred 区间为空时，放宽 learningDay 范围，但仍禁止 learningDay=今天（避免生成“未来任务”）。
    final fallbackEarliestLearningDay = scheduledStart.subtract(
      Duration(days: coverageMaxIntervalDays),
    );
    final fallbackLatestLearningDay = todayStart.subtract(
      const Duration(days: 1),
    );

    final (
      earliestLearningDay,
      latestLearningDay,
    ) = !preferredEarliestLearningDay.isAfter(preferredLatestLearningDay)
        ? (preferredEarliestLearningDay, preferredLatestLearningDay)
        : (fallbackEarliestLearningDay, fallbackLatestLearningDay);

    final items = <({int id, DateTime learningDay})>[];

    return db.transaction(() async {
      // 1) 插入学习内容（数量较少，逐条插入便于获取 ID）。
      for (var i = 0; i < config.contentCount; i++) {
        final learningDay = _randomDay(
          start: earliestLearningDay,
          endInclusive: latestLearningDay,
        );
        final title = _mockTitle(config: config, index: i);
        // v2.6：任务结构升级后，Mock 数据优先写入 description/subtasks（note 渐进式废弃）。
        final description = _mockDescription(learningDay: learningDay, now: now);
        final subtasks = _mockSubtasks(config: config, index: i);

        final id = await _learningItemDao.insertLearningItem(
          LearningItemsCompanion.insert(
            uuid: Value(_uuid.v4()),
            title: title,
            description: Value(description),
            tags: const Value('[]'),
            learningDate: learningDay,
            createdAt: Value(now),
            updatedAt: const Value.absent(),
            isMockData: const Value(true),
          ),
        );

        // 说明：子任务表有独立的 isMockData 字段，需一并写入，避免影响同步/备份/导出与清理口径。
        for (var j = 0; j < subtasks.length; j++) {
          final content = subtasks[j].trim();
          if (content.isEmpty) continue;
          await db.into(db.learningSubtasks).insert(
            LearningSubtasksCompanion.insert(
              uuid: Value(_uuid.v4()),
              learningItemId: id,
              content: content,
              sortOrder: Value(j),
              createdAt: now,
              updatedAt: Value(now),
              isMockData: const Value(true),
            ),
          );
        }
        items.add((id: id, learningDay: learningDay));
      }

      // 2) 插入复习任务（批量插入提升性能）。
      //
      // 关键规则：
      // - scheduledDate 必须由 learningDay + intervalDays 推导，确保数据满足“学习日期 -> 复习计划”的语义
      // - 同一条学习内容的同一轮次最多生成 1 条任务（避免下游按轮次聚合时出现歧义）
      final candidates = <({int itemId, int round, DateTime scheduledDay})>[];
      for (final item in items) {
        for (var round = 1; round <= maxRound; round++) {
          final intervalDays = intervalsDays[round - 1];
          final scheduledDay = DateTime(
            item.learningDay.year,
            item.learningDay.month,
            item.learningDay.day,
          ).add(Duration(days: intervalDays));

          if (scheduledDay.isBefore(scheduledStart) ||
              !scheduledDay.isBefore(scheduledEndExclusive)) {
            continue;
          }
          candidates.add((
            itemId: item.id,
            round: round,
            scheduledDay: scheduledDay,
          ));
        }
      }

      if (candidates.length < config.taskCount) {
        throw StateError(
          '可生成的任务数量不足：在最近 ${config.daysRange} 天范围内，最多可生成 ${candidates.length} 条任务；'
          '当前配置要求生成 ${config.taskCount} 条。请尝试：增加学习内容数量、扩大日期范围，或减少任务数量。',
        );
      }

      candidates.shuffle(_random);
      final selected = candidates
          .take(config.taskCount)
          .toList(growable: false);

      final companions = <ReviewTasksCompanion>[];
      for (final c in selected) {
        final scheduledAt = _withRandomTime(c.scheduledDay);
        final status = _pickStatus(scheduledAt: scheduledAt, now: now);
        final (completedAt, skippedAt) = _statusTimestamps(
          status: status,
          scheduledAt: scheduledAt,
        );
        // 性能优化（v10）：维护 occurredAt 口径，供任务中心时间线排序与游标分页使用。
        final occurredAt = switch (status) {
          'pending' => scheduledAt,
          'done' => completedAt ?? scheduledAt,
          'skipped' => skippedAt ?? scheduledAt,
          _ => scheduledAt,
        };

        companions.add(
          ReviewTasksCompanion.insert(
            uuid: Value(_uuid.v4()),
            learningItemId: c.itemId,
            reviewRound: c.round,
            scheduledDate: scheduledAt,
            occurredAt: Value(occurredAt),
            status: Value(status),
            completedAt: Value(completedAt),
            skippedAt: Value(skippedAt),
            createdAt: Value(now),
            updatedAt: const Value.absent(),
            isMockData: const Value(true),
          ),
        );
      }

      await _reviewTaskDao.insertReviewTasks(companions);

      return MockDataGenerateResult(
        insertedItemCount: items.length,
        insertedTaskCount: companions.length,
      );
    });
  }

  /// 清理所有模拟数据（按 isMockData=true）。
  ///
  /// 返回值：删除条数（items/tasks）。
  Future<(int deletedItems, int deletedTasks)> clearMockData() async {
    if (!kDebugMode) {
      throw StateError('MockDataService 仅允许在 Debug 模式下使用');
    }
    return db.transaction(() async {
      // 先删任务，再删内容：避免未来出现“任务引用非 Mock 内容”的混杂场景。
      final deletedTasks = await _reviewTaskDao.deleteMockReviewTasks();
      final deletedItems = await _learningItemDao.deleteMockLearningItems();
      return (deletedItems, deletedTasks);
    });
  }

  /// 清空全部数据（危险操作，仅 Debug）。
  ///
  /// 说明：
  /// - 仅用于开发调试，避免污染真实用户数据
  /// - 会删除学习内容、复习任务、主题、模板、同步日志等业务数据
  /// - 不删除设置项（避免调试时反复配置）
  Future<void> clearAllData() async {
    if (!kDebugMode) {
      throw StateError('MockDataService 仅允许在 Debug 模式下使用');
    }

    await db.transaction(() async {
      // 注意删除顺序：先删关系表，再删主表。
      await (db.delete(db.topicItemRelations)).go();
      await (db.delete(db.reviewTasks)).go();
      await (db.delete(db.learningItems)).go();
      await (db.delete(db.learningTemplates)).go();
      await (db.delete(db.learningTopics)).go();

      // 同步相关数据一并清理，避免“脏映射/脏游标”影响下一轮测试。
      await (db.delete(db.syncEntityMappings)).go();
      await (db.delete(db.syncLogs)).go();
      await (db.delete(db.syncDevices)).go();
    });
  }

  void _validateConfig(MockDataConfig config) {
    if (config.contentCount < 1 || config.contentCount > 100) {
      throw ArgumentError('学习内容数量需在 1-100 之间');
    }
    if (config.taskCount < 1 || config.taskCount > 500) {
      throw ArgumentError('复习任务数量需在 1-500 之间');
    }
    const allowed = {7, 14, 30, 60, 90};
    if (!allowed.contains(config.daysRange)) {
      throw ArgumentError('复习日期范围仅支持 7/14/30/60/90 天');
    }
  }

  String _pickStatus({required DateTime scheduledAt, required DateTime now}) {
    // 经验分布：
    // - 今天及未来（理论上不会出现未来，但仍做保护）尽量保持 pending
    // - 历史日期：混入 done/skipped，便于测试筛选与统计
    final scheduledDay = DateTime(
      scheduledAt.year,
      scheduledAt.month,
      scheduledAt.day,
    );
    final today = DateTime(now.year, now.month, now.day);
    if (!scheduledDay.isBefore(today)) return 'pending';

    final r = _random.nextInt(100);
    if (r < 55) return 'pending';
    if (r < 90) return 'done';
    return 'skipped';
  }

  (DateTime? completedAt, DateTime? skippedAt) _statusTimestamps({
    required String status,
    required DateTime scheduledAt,
  }) {
    final base = scheduledAt.add(Duration(minutes: _random.nextInt(8 * 60)));
    switch (status) {
      case 'done':
        return (base, null);
      case 'skipped':
        return (null, base);
      case 'pending':
      default:
        return (null, null);
    }
  }

  DateTime _randomDay({
    required DateTime start,
    required DateTime endInclusive,
  }) {
    final startDay = DateTime(start.year, start.month, start.day);
    final endDay = DateTime(
      endInclusive.year,
      endInclusive.month,
      endInclusive.day,
    );
    final days = endDay.difference(startDay).inDays;
    final offset = days <= 0 ? 0 : _random.nextInt(days + 1);
    return startDay.add(Duration(days: offset));
  }

  DateTime _withRandomTime(DateTime day) {
    final h = _random.nextInt(24);
    final m = _random.nextInt(60);
    return DateTime(day.year, day.month, day.day, h, m);
  }

  String _mockTitle({required MockDataConfig config, required int index}) {
    final template = _resolveTemplate(config.template);
    final raw = switch (template) {
      MockDataTemplate.englishWords => _englishWordTitle(index),
      MockDataTemplate.historyEvents => _historyEventTitle(index),
      MockDataTemplate.custom =>
        '${config.customPrefix.trim().isEmpty ? '自定义' : config.customPrefix.trim()} #${index + 1}',
      MockDataTemplate.random => _englishWordTitle(index),
    };
    // ≤50 字：过长时截断（避免触发表约束）。
    return raw.length <= 50 ? raw : raw.substring(0, 50);
  }

  MockDataTemplate _resolveTemplate(MockDataTemplate template) {
    if (template != MockDataTemplate.random) return template;
    final options = const [
      MockDataTemplate.englishWords,
      MockDataTemplate.historyEvents,
      MockDataTemplate.custom,
    ];
    return options[_random.nextInt(options.length)];
  }

  String _englishWordTitle(int index) {
    const words = [
      'abandon',
      'abstract',
      'accelerate',
      'accurate',
      'achieve',
      'adapt',
      'adventure',
      'ancient',
      'analyze',
      'approach',
      'assemble',
      'balance',
      'benefit',
      'brief',
      'capture',
      'clarify',
      'combine',
      'compare',
      'concept',
      'confirm',
      'contrast',
      'crucial',
      'decline',
      'define',
      'derive',
      'detect',
      'efficient',
      'emerge',
      'emphasis',
      'enhance',
      'estimate',
      'evidence',
      'expand',
      'feature',
      'flexible',
      'focus',
      'fundamental',
      'generate',
      'hypothesis',
      'identify',
      'illustrate',
      'impact',
      'improve',
      'indicate',
      'influence',
      'innovate',
      'integrate',
      'interpret',
      'maintain',
      'measure',
      'method',
      'notion',
      'obtain',
      'participate',
      'perceive',
      'persist',
      'potential',
      'priority',
      'process',
      'progress',
      'promote',
      'recover',
      'reflect',
      'relevant',
      'resolve',
      'resource',
      'respond',
      'significant',
      'strategy',
      'structure',
      'sustain',
      'transform',
      'valid',
      'verify',
    ];
    final word = words[index % words.length];
    return '单词：$word';
  }

  String _historyEventTitle(int index) {
    const events = [
      '工业革命的起源',
      '文艺复兴的核心思想',
      '大航海时代的影响',
      '第一次世界大战导火索',
      '第二次世界大战转折点',
      '冷战格局形成原因',
      '丝绸之路的意义',
      '秦统一六国的条件',
      '唐宋变革的主要特征',
      '明清海禁政策的后果',
      '近代科学革命的代表人物',
      '启蒙运动的主要观点',
      '美国独立战争的背景',
      '法国大革命的阶段',
      '苏联解体的原因',
    ];
    final e = events[index % events.length];
    return '历史：$e';
  }

  String _mockDescription({required DateTime learningDay, required DateTime now}) {
    final y = learningDay.year;
    final m = learningDay.month.toString().padLeft(2, '0');
    final d = learningDay.day.toString().padLeft(2, '0');
    return 'Mock 数据：用于调试与体验优化验证（生成于 ${now.toIso8601String()}，学习日 $y-$m-$d）。';
  }

  /// 生成子任务列表（用于模拟“清单能力”）。
  ///
  /// 说明：
  /// - 仅用于 Debug 数据，不追求业务真实性，但需覆盖排序/展示/搜索等链路
  /// - 通过 index 的奇偶与模板类型混入不同样式，便于回归验证
  List<String> _mockSubtasks({required MockDataConfig config, required int index}) {
    final template = _resolveTemplate(config.template);
    // 简单策略：偶数条生成 0 个子任务（仅描述），奇数条生成 2-3 个子任务。
    if (index.isEven) return const <String>[];

    return switch (template) {
      MockDataTemplate.englishWords => <String>[
        '记忆要点：拼写与发音',
        '例句：用该单词造句',
        if (index % 3 == 0) '同义词/反义词：补充对照',
      ],
      MockDataTemplate.historyEvents => <String>[
        '时间：梳理关键时间线',
        '原因：总结主要原因',
        if (index % 3 == 0) '影响：列出 2-3 个影响点',
      ],
      MockDataTemplate.custom => <String>[
        '子任务 1：准备材料',
        '子任务 2：完成练习',
        if (index % 3 == 0) '子任务 3：复盘总结',
      ],
      MockDataTemplate.random => <String>[
        '子任务 1：拆解步骤',
        '子任务 2：完成并记录',
      ],
    };
  }
}
