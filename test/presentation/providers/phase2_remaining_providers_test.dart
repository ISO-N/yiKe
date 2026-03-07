// 文件用途：Phase 2 剩余关键 Provider 状态流测试，覆盖任务详情、模板、主题与设置的真实装配路径。
// 作者：Codex
// 创建日期：2026-03-06

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:yike/data/database/database.dart';
import 'package:yike/di/providers.dart';
import 'package:yike/domain/entities/app_settings.dart';
import 'package:yike/domain/entities/review_interval_config.dart';
import 'package:yike/domain/entities/review_task.dart';
import 'package:yike/domain/usecases/create_learning_item_usecase.dart';
import 'package:yike/domain/usecases/manage_template_usecase.dart';
import 'package:yike/domain/usecases/manage_topic_usecase.dart';
import 'package:yike/presentation/providers/calendar_provider.dart';
import 'package:yike/presentation/providers/home_tasks_provider.dart';
import 'package:yike/presentation/providers/settings_provider.dart';
import 'package:yike/presentation/providers/statistics_provider.dart';
import 'package:yike/presentation/providers/task_detail_provider.dart';
import 'package:yike/presentation/providers/task_hub_provider.dart';
import 'package:yike/presentation/providers/templates_provider.dart';
import 'package:yike/presentation/providers/topics_provider.dart';

import '../../helpers/test_database.dart';

void main() {
  late AppDatabase db;
  late ProviderContainer container;

  /// 返回本地自然日零点，避免比较时被具体时分秒干扰。
  DateTime startOfDay(DateTime value) {
    return DateTime(value.year, value.month, value.day);
  }

  /// 等待异步 Provider 进入稳定状态，避免直接断言中间帧。
  Future<void> waitFor(
    bool Function() predicate, {
    int maxAttempts = 80,
  }) async {
    for (var i = 0; i < maxAttempts; i++) {
      if (predicate()) return;
      await Future<void>.delayed(Duration.zero);
    }
    fail('等待 Provider 状态稳定超时');
  }

  /// 使用真实用例创建一条学习内容与少量复习计划，便于后续任务详情测试。
  Future<int> createLearningItemWithPlan({
    required String title,
    String? description,
    List<String> subtasks = const <String>[],
    List<String> tags = const <String>[],
  }) async {
    final result = await container.read(createLearningItemUseCaseProvider).execute(
      CreateLearningItemParams(
        title: title,
        description: description,
        subtasks: subtasks,
        tags: tags,
        learningDate: startOfDay(DateTime.now()),
        reviewIntervals: <ReviewIntervalConfigEntity>[
          ReviewIntervalConfigEntity(round: 1, intervalDays: 1, enabled: true),
          ReviewIntervalConfigEntity(round: 2, intervalDays: 3, enabled: true),
          ReviewIntervalConfigEntity(round: 3, intervalDays: 7, enabled: true),
        ],
      ),
    );
    return result.item.id!;
  }

  /// 保持会被 task_detail_provider 主动 invalidate 的关联 Provider 常驻。
  void keepDetailDependenciesAlive() {
    final homeTasksSub = container.listen<HomeTasksState>(
      homeTasksProvider,
      (previous, next) {},
      fireImmediately: true,
    );
    final calendarSub = container.listen<CalendarState>(
      calendarProvider,
      (previous, next) {},
      fireImmediately: true,
    );
    final statisticsSub = container.listen<StatisticsState>(
      statisticsProvider,
      (previous, next) {},
      fireImmediately: true,
    );
    final taskHubSub = container.listen<TaskHubState>(
      taskHubProvider,
      (previous, next) {},
      fireImmediately: true,
    );
    addTearDown(homeTasksSub.close);
    addTearDown(calendarSub.close);
    addTearDown(statisticsSub.close);
    addTearDown(taskHubSub.close);
  }

  setUp(() {
    db = createInMemoryDatabase();
    container = ProviderContainer(
      overrides: <Override>[appDatabaseProvider.overrideWithValue(db)],
    );
  });

  tearDown(() async {
    container.dispose();
    await db.close();
  });

  group('SettingsProvider', () {
    test('加载默认设置并可持久化保存修改', () async {
      final notifier = container.read(settingsProvider.notifier);
      await waitFor(() => !container.read(settingsProvider).isLoading);

      final initialState = container.read(settingsProvider);
      expect(initialState.settings.reminderTime, AppSettingsEntity.defaults.reminderTime);
      expect(
        initialState.settings.notificationsEnabled,
        AppSettingsEntity.defaults.notificationsEnabled,
      );
      expect(
        initialState.settings.doNotDisturbStart,
        AppSettingsEntity.defaults.doNotDisturbStart,
      );

      final next = initialState.settings.copyWith(
        reminderTime: '07:30',
        doNotDisturbStart: '23:00',
        doNotDisturbEnd: '07:00',
        notificationsEnabled: false,
        overdueNotificationEnabled: false,
        goalNotificationEnabled: false,
        streakNotificationEnabled: false,
        notificationPermissionGuideDismissed: true,
        topicGuideDismissed: true,
        lastNotifiedDate: '2026-03-06',
      );

      await notifier.save(next);
      await waitFor(() => !container.read(settingsProvider).isLoading);

      final savedState = container.read(settingsProvider);
      expect(savedState.settings.reminderTime, '07:30');
      expect(savedState.settings.notificationsEnabled, isFalse);
      expect(savedState.settings.topicGuideDismissed, isTrue);

      final persisted = await container.read(settingsRepositoryProvider).getSettings();
      expect(persisted.reminderTime, '07:30');
      expect(persisted.doNotDisturbStart, '23:00');
      expect(persisted.lastNotifiedDate, '2026-03-06');
    });
  });

  group('TemplatesProvider', () {
    test('支持加载、创建、更新、排序与删除模板', () async {
      final notifier = container.read(templatesProvider.notifier);
      await waitFor(() => !container.read(templatesProvider).isLoading);
      expect(container.read(templatesProvider).templates, isEmpty);

      await notifier.create(
        const TemplateParams(
          name: '晨读模板',
          titlePattern: '晨读 {date}',
          notePattern: '词汇复盘',
          tags: <String>['英语'],
        ),
      );
      await waitFor(() {
        final state = container.read(templatesProvider);
        return !state.isLoading && state.templates.length == 1;
      });

      await notifier.create(
        const TemplateParams(
          name: '晚间模板',
          titlePattern: '晚间 {weekday}',
          notePattern: '错题整理',
          tags: <String>['数学'],
        ),
      );
      await waitFor(() {
        final state = container.read(templatesProvider);
        return !state.isLoading && state.templates.length == 2;
      });

      final createdTemplates = container.read(templatesProvider).templates;
      final morning = createdTemplates.firstWhere((t) => t.name == '晨读模板');
      final evening = createdTemplates.firstWhere((t) => t.name == '晚间模板');

      await notifier.update(
        morning,
        const TemplateParams(
          name: '晨读模板-更新',
          titlePattern: '晨读升级 {date}',
          notePattern: '单词 + 句子',
          tags: <String>['英语', '听写'],
        ),
      );
      await waitFor(() {
        final state = container.read(templatesProvider);
        return !state.isLoading &&
            state.templates.any((t) => t.name == '晨读模板-更新');
      });

      final updatedTemplates = container.read(templatesProvider).templates;
      final updatedMorning = updatedTemplates.firstWhere((t) => t.id == morning.id);
      final updatedEvening = updatedTemplates.firstWhere((t) => t.id == evening.id);

      await notifier.reorder([updatedEvening, updatedMorning]);
      await waitFor(() {
        final state = container.read(templatesProvider);
        return !state.isLoading && state.templates.first.id == updatedEvening.id;
      });

      await notifier.delete(updatedMorning.id!);
      await waitFor(() {
        final state = container.read(templatesProvider);
        return !state.isLoading && state.templates.length == 1;
      });

      final finalState = container.read(templatesProvider);
      expect(finalState.templates.single.name, '晚间模板');
      expect(finalState.errorMessage, isNull);
    });
  });

  group('TopicsProvider', () {
    test('支持主题概览的创建、更新、关联刷新与删除', () async {
      final itemId = await createLearningItemWithPlan(
        title: '主题关联任务',
        subtasks: const <String>['复习定义'],
      );
      final notifier = container.read(topicsProvider.notifier);
      final useCase = container.read(manageTopicUseCaseProvider);

      await waitFor(() => !container.read(topicsProvider).isLoading);
      expect(container.read(topicsProvider).overviews, isEmpty);

      final topic = await notifier.create(
        const TopicParams(name: '语言', description: '语言学习'),
      );
      await useCase.addItemToTopic(topic.id!, itemId);
      await notifier.load();
      await waitFor(() {
        final state = container.read(topicsProvider);
        return !state.isLoading && state.overviews.isNotEmpty;
      });

      final createdOverview = container.read(topicsProvider).overviews.single;
      expect(createdOverview.topic.name, '语言');
      expect(createdOverview.itemCount, 1);
      expect(createdOverview.totalCount, greaterThanOrEqualTo(1));

      await notifier.update(
        topic,
        const TopicParams(name: '语言-更新', description: '已重命名'),
      );
      await waitFor(() {
        final state = container.read(topicsProvider);
        return !state.isLoading &&
            state.overviews.single.topic.name == '语言-更新';
      });

      await notifier.delete(topic.id!);
      await waitFor(() {
        final state = container.read(topicsProvider);
        return !state.isLoading && state.overviews.isEmpty;
      });

      final finalState = container.read(topicsProvider);
      expect(finalState.overviews, isEmpty);
      expect(finalState.errorMessage, isNull);
    });
  });

  group('TaskDetailProvider', () {
    test('支持基础信息、描述、子任务与主题的完整编辑链路', () async {
      final itemId = await createLearningItemWithPlan(
        title: '任务详情-编辑',
        description: '初始描述',
        subtasks: const <String>['原始子任务'],
        tags: const <String>['旧标签'],
      );
      keepDetailDependenciesAlive();
      final topicUseCase = container.read(manageTopicUseCaseProvider);
      final topicA = await topicUseCase.create(
        const TopicParams(name: '主题A', description: '原始主题'),
      );
      final topicB = await topicUseCase.create(
        const TopicParams(name: '主题B', description: '替换主题'),
      );
      await topicUseCase.addItemToTopic(topicA.id!, itemId);

      final notifier = container.read(taskDetailProvider(itemId).notifier);
      await waitFor(() => !container.read(taskDetailProvider(itemId)).isLoading);

      final initialState = container.read(taskDetailProvider(itemId));
      expect(initialState.item?.title, '任务详情-编辑');
      expect(initialState.subtasks.single.content, '原始子任务');
      expect(initialState.topics.single.name, '主题A');
      expect(initialState.plan.length, 3);

      await notifier.updateBasicInfo(
        title: '任务详情-编辑-更新',
        tags: const <String>['新标签', '复习'],
        topicIds: <int>{topicB.id!},
      );
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading &&
            state.item?.title == '任务详情-编辑-更新' &&
            state.topics.length == 1 &&
            state.topics.single.name == '主题B';
      });

      await notifier.updateDescription('新的结构化描述');
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.item?.description == '新的结构化描述';
      });

      await notifier.createSubtask(' 第二条子任务 ');
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.subtasks.length == 2;
      });

      final createdSubtask = container
          .read(taskDetailProvider(itemId))
          .subtasks
          .firstWhere((subtask) => subtask.content == '第二条子任务');
      await notifier.updateSubtask(
        createdSubtask.copyWith(content: '第二条子任务-更新'),
      );
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading &&
            state.subtasks.any((subtask) => subtask.content == '第二条子任务-更新');
      });

      final reorderedIds = container
          .read(taskDetailProvider(itemId))
          .subtasks
          .reversed
          .map((subtask) => subtask.id!)
          .toList();
      await notifier.reorderSubtasks(reorderedIds);
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.subtasks.first.id == reorderedIds.first;
      });

      await notifier.deleteSubtask(createdSubtask.id!);
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.subtasks.length == 1;
      });

      final editedState = container.read(taskDetailProvider(itemId));
      expect(editedState.item?.tags, const <String>['新标签', '复习']);
      expect(editedState.subtasks.single.content, '原始子任务');
      expect(editedState.errorMessage, isNull);
    });

    test('支持撤销状态、调整计划、增减轮次与停用只读', () async {
      final itemId = await createLearningItemWithPlan(
        title: '任务详情-计划',
        subtasks: const <String>['梳理笔记'],
      );
      keepDetailDependenciesAlive();
      final notifier = container.read(taskDetailProvider(itemId).notifier);
      await waitFor(() => !container.read(taskDetailProvider(itemId)).isLoading);

      final initialPlan = container.read(taskDetailProvider(itemId)).plan;
      expect(initialPlan.length, 3);

      await container.read(completeReviewTaskUseCaseProvider).execute(
        initialPlan.first.taskId,
      );
      await notifier.load();
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading &&
            state.plan.firstWhere((task) => task.taskId == initialPlan.first.taskId).status ==
                ReviewTaskStatus.done;
      });

      await notifier.undoTaskStatus(initialPlan.first.taskId);
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading &&
            state.plan.firstWhere((task) => task.taskId == initialPlan.first.taskId).status ==
                ReviewTaskStatus.pending;
      });

      final adjustedDate = startOfDay(DateTime.now()).add(const Duration(days: 5));
      await notifier.adjustReviewDate(reviewRound: 2, newDate: adjustedDate);
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        final roundTwo = state.plan.firstWhere((task) => task.reviewRound == 2);
        return !state.isLoading &&
            startOfDay(roundTwo.scheduledDate) == adjustedDate;
      });

      await notifier.addReviewRound();
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.plan.length == 4;
      });

      await notifier.removeReviewRound();
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && state.plan.length == 3;
      });

      await notifier.deactivate();
      await waitFor(() {
        final state = container.read(taskDetailProvider(itemId));
        return !state.isLoading && (state.item?.isDeleted ?? false);
      });

      expect(notifier.isReadOnly, isTrue);
      await expectLater(
        () => notifier.updateBasicInfo(
          title: '停用后不可编辑',
          tags: const <String>['禁止'],
          topicIds: const <int>{},
        ),
        throwsA(isA<StateError>()),
      );
    });
  });
}
