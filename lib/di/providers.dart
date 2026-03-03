/// 文件用途：依赖注入 Provider 集合（数据库/DAO/Repository/UseCase/Service）。
/// 作者：Codex
/// 创建日期：2026-02-25
library;

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/database/daos/learning_item_dao.dart';
import '../data/database/daos/learning_subtask_dao.dart';
import '../data/database/daos/learning_template_dao.dart';
import '../data/database/daos/learning_topic_dao.dart';
import '../data/database/daos/backup_dao.dart';
import '../data/database/daos/review_task_dao.dart';
import '../data/database/daos/settings_dao.dart';
import '../data/database/daos/sync_device_dao.dart';
import '../data/database/daos/sync_entity_mapping_dao.dart';
import '../data/database/daos/sync_log_dao.dart';
import '../data/database/database.dart';
import '../data/repositories/backup_repository_impl.dart';
import '../data/repositories/learning_item_repository_impl.dart';
import '../data/repositories/learning_subtask_repository_impl.dart';
import '../data/repositories/learning_template_repository_impl.dart';
import '../data/repositories/learning_topic_repository_impl.dart';
import '../data/repositories/review_task_repository_impl.dart';
import '../data/repositories/settings_repository_impl.dart';
import '../data/repositories/task_structure_migration_repository_impl.dart';
import '../data/repositories/theme_settings_repository_impl.dart';
import '../data/repositories/ui_preferences_repository_impl.dart';
import '../data/sync/sync_log_writer.dart';
import '../domain/repositories/backup_repository.dart';
import '../domain/repositories/learning_item_repository.dart';
import '../domain/repositories/learning_subtask_repository.dart';
import '../domain/repositories/learning_template_repository.dart';
import '../domain/repositories/learning_topic_repository.dart';
import '../domain/repositories/review_task_repository.dart';
import '../domain/repositories/settings_repository.dart';
import '../domain/repositories/task_structure_migration_repository.dart';
import '../domain/repositories/theme_settings_repository.dart';
import '../domain/repositories/ui_preferences_repository.dart';
import '../domain/services/ocr_service.dart';
import '../domain/usecases/export_backup_usecase.dart';
import '../domain/usecases/get_backup_list_usecase.dart';
import '../domain/usecases/import_backup_usecase.dart';
import '../infrastructure/storage/secure_storage_service.dart';
import '../infrastructure/storage/backup_storage.dart';
import '../infrastructure/sync/device_identity_service.dart';
import '../domain/usecases/complete_review_task_usecase.dart';
import '../domain/usecases/create_learning_item_usecase.dart';
import '../domain/usecases/export_data_usecase.dart';
import '../domain/usecases/get_calendar_tasks_usecase.dart';
import '../domain/usecases/get_home_tasks_usecase.dart';
import '../domain/usecases/get_statistics_usecase.dart';
import '../domain/usecases/get_tasks_by_time_usecase.dart';
import '../domain/usecases/get_today_completed_tasks_usecase.dart';
import '../domain/usecases/get_today_skipped_tasks_usecase.dart';
import '../domain/usecases/import_learning_items_usecase.dart';
import '../domain/usecases/manage_template_usecase.dart';
import '../domain/usecases/manage_topic_usecase.dart';
import '../domain/usecases/migrate_note_to_subtasks_usecase.dart';
import '../domain/usecases/ocr_recognition_usecase.dart';
import '../domain/usecases/remove_review_round_usecase.dart';
import '../domain/usecases/skip_review_task_usecase.dart';
import '../domain/usecases/undo_task_status_usecase.dart';
import '../domain/usecases/create_subtask_usecase.dart';
import '../domain/usecases/update_subtask_usecase.dart';
import '../domain/usecases/delete_subtask_usecase.dart';
import '../domain/usecases/reorder_subtasks_usecase.dart';
import '../domain/usecases/update_learning_item_description_usecase.dart';
import '../domain/usecases/update_learning_item_note_usecase.dart';
import '../domain/usecases/deactivate_learning_item_usecase.dart';
import '../domain/usecases/get_review_plan_usecase.dart';
import '../domain/usecases/adjust_review_date_usecase.dart';
import '../domain/usecases/add_review_round_usecase.dart';
import '../infrastructure/ocr/ocr_service.dart' as infra_ocr;
import '../infrastructure/speech/speech_service.dart';

/// 数据库 Provider（需要在启动时 override 注入真实实例）。
final appDatabaseProvider = Provider<AppDatabase>((ref) {
  throw UnimplementedError('appDatabaseProvider 必须在启动时被 override 注入。');
});

/// 设备 ID Provider（需要在启动时生成并注入，供同步/日志使用）。
final deviceIdProvider = Provider<String>((ref) {
  // 说明：正常运行时会在 AppInjection.createContainer 中 override 注入真实 deviceId。
  // 测试环境（如 widget_test）若未注入也不应崩溃，因此提供一个稳定兜底值。
  return 'yike_device_fallback';
});

/// DAO Providers
final learningItemDaoProvider = Provider<LearningItemDao>((ref) {
  return LearningItemDao(ref.read(appDatabaseProvider));
});

final learningSubtaskDaoProvider = Provider<LearningSubtaskDao>((ref) {
  return LearningSubtaskDao(ref.read(appDatabaseProvider));
});

final reviewTaskDaoProvider = Provider<ReviewTaskDao>((ref) {
  return ReviewTaskDao(ref.read(appDatabaseProvider));
});

final settingsDaoProvider = Provider<SettingsDao>((ref) {
  return SettingsDao(ref.read(appDatabaseProvider));
});

/// 备份 DAO Provider（用于导出读取）。
final backupDaoProvider = Provider<BackupDao>((ref) {
  return BackupDao(ref.read(appDatabaseProvider));
});

final learningTemplateDaoProvider = Provider<LearningTemplateDao>((ref) {
  return LearningTemplateDao(ref.read(appDatabaseProvider));
});

final learningTopicDaoProvider = Provider<LearningTopicDao>((ref) {
  return LearningTopicDao(ref.read(appDatabaseProvider));
});

final syncDeviceDaoProvider = Provider<SyncDeviceDao>((ref) {
  return SyncDeviceDao(ref.read(appDatabaseProvider));
});

final syncLogDaoProvider = Provider<SyncLogDao>((ref) {
  return SyncLogDao(ref.read(appDatabaseProvider));
});

final syncEntityMappingDaoProvider = Provider<SyncEntityMappingDao>((ref) {
  return SyncEntityMappingDao(ref.read(appDatabaseProvider));
});

/// 同步日志写入器 Provider（供数据层仓储复用）。
final syncLogWriterProvider = Provider<SyncLogWriter>((ref) {
  return SyncLogWriter(
    syncLogDao: ref.read(syncLogDaoProvider),
    syncEntityMappingDao: ref.read(syncEntityMappingDaoProvider),
    localDeviceId: ref.read(deviceIdProvider),
  );
});

/// 基础设施 Providers
final secureStorageServiceProvider = Provider<SecureStorageService>((ref) {
  return SecureStorageService();
});

/// 备份文件存储 Provider（应用私有目录）。
final backupStorageProvider = Provider<BackupStorage>((ref) {
  return const BackupStorage();
});

/// 设备身份服务 Provider（用于同步/配对）。
final deviceIdentityServiceProvider = Provider<DeviceIdentityService>((ref) {
  return DeviceIdentityService(
    secureStorageService: ref.read(secureStorageServiceProvider),
  );
});

final speechServiceProvider = Provider<SpeechService>((ref) {
  return SpeechService();
});

final ocrServiceProvider = Provider<OcrService>((ref) {
  return const infra_ocr.MlKitOcrService();
});

/// Repository Providers
final learningItemRepositoryProvider = Provider<LearningItemRepository>((ref) {
  return LearningItemRepositoryImpl(
    ref.read(learningItemDaoProvider),
    syncLogWriter: ref.read(syncLogWriterProvider),
  );
});

final learningSubtaskRepositoryProvider =
    Provider<LearningSubtaskRepository>((ref) {
      return LearningSubtaskRepositoryImpl(
        dao: ref.read(learningSubtaskDaoProvider),
        syncLogWriter: ref.read(syncLogWriterProvider),
      );
    });

final learningTemplateRepositoryProvider = Provider<LearningTemplateRepository>(
  (ref) {
    return LearningTemplateRepositoryImpl(
      ref.read(learningTemplateDaoProvider),
      syncLogWriter: ref.read(syncLogWriterProvider),
    );
  },
);

final learningTopicRepositoryProvider = Provider<LearningTopicRepository>((
  ref,
) {
  return LearningTopicRepositoryImpl(
    ref.read(learningTopicDaoProvider),
    syncLogWriter: ref.read(syncLogWriterProvider),
  );
});

final reviewTaskRepositoryProvider = Provider<ReviewTaskRepository>((ref) {
  return ReviewTaskRepositoryImpl.withSubtasks(
    dao: ref.read(reviewTaskDaoProvider),
    learningSubtaskDao: ref.read(learningSubtaskDaoProvider),
    syncLogWriter: ref.read(syncLogWriterProvider),
  );
});

final settingsRepositoryProvider = Provider<SettingsRepository>((ref) {
  return SettingsRepositoryImpl(
    dao: ref.read(settingsDaoProvider),
    secureStorageService: ref.read(secureStorageServiceProvider),
    syncLogWriter: ref.read(syncLogWriterProvider),
  );
});

/// 主题设置仓储 Provider。
///
/// 说明：使用与 SettingsRepository 相同的加密存储策略（SettingsCrypto）。
final themeSettingsRepositoryProvider = Provider<ThemeSettingsRepository>((
  ref,
) {
  return ThemeSettingsRepositoryImpl(
    dao: ref.read(settingsDaoProvider),
    secureStorageService: ref.read(secureStorageServiceProvider),
    syncLogWriter: ref.read(syncLogWriterProvider),
  );
});

/// UI 本地偏好仓储 Provider（仅本机，不参与同步）。
final uiPreferencesRepositoryProvider = Provider<UiPreferencesRepository>((
  ref,
) {
  return UiPreferencesRepositoryImpl(
    dao: ref.read(settingsDaoProvider),
    secureStorageService: ref.read(secureStorageServiceProvider),
  );
});

/// 备份仓储 Provider。
final backupRepositoryProvider = Provider<BackupRepository>((ref) {
  return BackupRepositoryImpl(
    db: ref.read(appDatabaseProvider),
    backupDao: ref.read(backupDaoProvider),
    settingsRepository: ref.read(settingsRepositoryProvider),
    themeSettingsRepository: ref.read(themeSettingsRepositoryProvider),
    storage: ref.read(backupStorageProvider),
  );
});

/// 任务结构迁移仓储 Provider（note → description + subtasks）。
final taskStructureMigrationRepositoryProvider =
    Provider<TaskStructureMigrationRepository>((ref) {
      return TaskStructureMigrationRepositoryImpl(
        db: ref.read(appDatabaseProvider),
        learningSubtaskDao: ref.read(learningSubtaskDaoProvider),
        syncLogWriter: ref.read(syncLogWriterProvider),
      );
    });

/// UseCase Providers
final createLearningItemUseCaseProvider = Provider<CreateLearningItemUseCase>((
  ref,
) {
  return CreateLearningItemUseCase(
    learningItemRepository: ref.read(learningItemRepositoryProvider),
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final importLearningItemsUseCaseProvider = Provider<ImportLearningItemsUseCase>(
  (ref) {
    return ImportLearningItemsUseCase(
      create: ref.read(createLearningItemUseCaseProvider),
    );
  },
);

final manageTemplateUseCaseProvider = Provider<ManageTemplateUseCase>((ref) {
  return ManageTemplateUseCase(
    repository: ref.read(learningTemplateRepositoryProvider),
  );
});

final manageTopicUseCaseProvider = Provider<ManageTopicUseCase>((ref) {
  return ManageTopicUseCase(
    repository: ref.read(learningTopicRepositoryProvider),
  );
});

final ocrRecognitionUseCaseProvider = Provider<OcrRecognitionUseCase>((ref) {
  return OcrRecognitionUseCase(ocrService: ref.read(ocrServiceProvider));
});

final getHomeTasksUseCaseProvider = Provider<GetHomeTasksUseCase>((ref) {
  return GetHomeTasksUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final completeReviewTaskUseCaseProvider = Provider<CompleteReviewTaskUseCase>((
  ref,
) {
  return CompleteReviewTaskUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final skipReviewTaskUseCaseProvider = Provider<SkipReviewTaskUseCase>((ref) {
  return SkipReviewTaskUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final undoTaskStatusUseCaseProvider = Provider<UndoTaskStatusUseCase>((ref) {
  return UndoTaskStatusUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final updateLearningItemNoteUseCaseProvider =
    Provider<UpdateLearningItemNoteUseCase>((ref) {
      return UpdateLearningItemNoteUseCase(
        learningItemRepository: ref.read(learningItemRepositoryProvider),
      );
    });

final updateLearningItemDescriptionUseCaseProvider =
    Provider<UpdateLearningItemDescriptionUseCase>((ref) {
      return UpdateLearningItemDescriptionUseCase(
        learningItemRepository: ref.read(learningItemRepositoryProvider),
      );
    });

final deactivateLearningItemUseCaseProvider =
    Provider<DeactivateLearningItemUseCase>((ref) {
      return DeactivateLearningItemUseCase(
        learningItemRepository: ref.read(learningItemRepositoryProvider),
      );
    });

final getReviewPlanUseCaseProvider = Provider<GetReviewPlanUseCase>((ref) {
  return GetReviewPlanUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final adjustReviewDateUseCaseProvider = Provider<AdjustReviewDateUseCase>((
  ref,
) {
  return AdjustReviewDateUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final addReviewRoundUseCaseProvider = Provider<AddReviewRoundUseCase>((ref) {
  return AddReviewRoundUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final removeReviewRoundUseCaseProvider =
    Provider<RemoveReviewRoundUseCase>((ref) {
      return RemoveReviewRoundUseCase(
        reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
      );
    });

final migrateNoteToSubtasksUseCaseProvider =
    Provider<MigrateNoteToSubtasksUseCase>((ref) {
      return MigrateNoteToSubtasksUseCase(
        repository: ref.read(taskStructureMigrationRepositoryProvider),
      );
    });

final createSubtaskUseCaseProvider = Provider<CreateSubtaskUseCase>((ref) {
  return CreateSubtaskUseCase(
    learningItemRepository: ref.read(learningItemRepositoryProvider),
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
  );
});

final updateSubtaskUseCaseProvider = Provider<UpdateSubtaskUseCase>((ref) {
  return UpdateSubtaskUseCase(
    learningItemRepository: ref.read(learningItemRepositoryProvider),
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
  );
});

final deleteSubtaskUseCaseProvider = Provider<DeleteSubtaskUseCase>((ref) {
  return DeleteSubtaskUseCase(
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
  );
});

final reorderSubtasksUseCaseProvider = Provider<ReorderSubtasksUseCase>((ref) {
  return ReorderSubtasksUseCase(
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
  );
});

final getTodayCompletedTasksUseCaseProvider =
    Provider<GetTodayCompletedTasksUseCase>((ref) {
      return GetTodayCompletedTasksUseCase(
        reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
      );
    });

final getTodaySkippedTasksUseCaseProvider =
    Provider<GetTodaySkippedTasksUseCase>((ref) {
      return GetTodaySkippedTasksUseCase(
        reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
      );
    });

final getTasksByTimeUseCaseProvider = Provider<GetTasksByTimeUseCase>((ref) {
  return GetTasksByTimeUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

/// v2.0 UseCase Providers
final getCalendarTasksUseCaseProvider = Provider<GetCalendarTasksUseCase>((
  ref,
) {
  return GetCalendarTasksUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

final getStatisticsUseCaseProvider = Provider<GetStatisticsUseCase>((ref) {
  return GetStatisticsUseCase(
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
    learningItemRepository: ref.read(learningItemRepositoryProvider),
  );
});

final exportDataUseCaseProvider = Provider<ExportDataUseCase>((ref) {
  return ExportDataUseCase(
    learningItemRepository: ref.read(learningItemRepositoryProvider),
    learningSubtaskRepository: ref.read(learningSubtaskRepositoryProvider),
    reviewTaskRepository: ref.read(reviewTaskRepositoryProvider),
  );
});

/// v1.5：备份与恢复 UseCase Providers
final exportBackupUseCaseProvider = Provider<ExportBackupUseCase>((ref) {
  return ExportBackupUseCase(repository: ref.read(backupRepositoryProvider));
});

final getBackupListUseCaseProvider = Provider<GetBackupListUseCase>((ref) {
  return GetBackupListUseCase(repository: ref.read(backupRepositoryProvider));
});

final importBackupUseCaseProvider = Provider<ImportBackupUseCase>((ref) {
  return ImportBackupUseCase(repository: ref.read(backupRepositoryProvider));
});
