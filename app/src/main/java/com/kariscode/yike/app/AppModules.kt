package com.kariscode.yike.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.work.WorkManager
import com.kariscode.yike.BuildConfig
import com.kariscode.yike.data.backup.BackupOperations
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.core.domain.time.SystemTimeProvider
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.backup.BackupValidator
import com.kariscode.yike.data.export.CsvExporter
import com.kariscode.yike.data.editor.FileQuestionEditorDraftRepository
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.repository.OfflineCardRepository
import com.kariscode.yike.data.repository.OfflineDeckRepository
import com.kariscode.yike.data.repository.OfflinePracticeRepository
import com.kariscode.yike.data.repository.OfflineQuestionRepository
import com.kariscode.yike.data.repository.OfflineReviewRepository
import com.kariscode.yike.data.repository.OfflineStudyInsightsRepository
import com.kariscode.yike.data.reminder.NotificationHelper
import com.kariscode.yike.data.reminder.ReminderCheckRunner
import com.kariscode.yike.data.reminder.ReminderNotifier
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.data.reminder.ReminderSyncScheduler
import com.kariscode.yike.data.search.QuestionSearchIndexer
import com.kariscode.yike.data.search.QuestionSearchIndexWriter
import com.kariscode.yike.data.settings.DataStoreAppSettingsRepository
import com.kariscode.yike.data.settings.appSettingsDataStore
import com.kariscode.yike.data.sync.KeystoreLanSyncSharedSecretProtector
import com.kariscode.yike.data.sync.LanSyncChangeApplier
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.data.sync.LanSyncConflictResolver
import com.kariscode.yike.data.sync.LanSyncCrypto
import com.kariscode.yike.data.sync.LanSyncLocalProfileStore
import com.kariscode.yike.data.sync.LanSyncPortAllocator
import com.kariscode.yike.data.sync.LanSyncRepositoryImpl
import com.kariscode.yike.data.sync.LanSyncSharedSecretProtector
import com.kariscode.yike.data.webconsole.WebConsoleRepositoryImpl
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.LanSyncRepository
import com.kariscode.yike.domain.repository.PracticeRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.repository.WebConsoleRepository
import com.kariscode.yike.domain.scheduler.ReviewScheduler
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import com.kariscode.yike.domain.usecase.GetHomeOverviewUseCase
import com.kariscode.yike.domain.usecase.GetDeckAvailableTagsUseCase
import com.kariscode.yike.domain.usecase.GetDeckCardMasterySummaryUseCase
import com.kariscode.yike.domain.usecase.GetQuestionSearchMetadataUseCase
import com.kariscode.yike.domain.usecase.LoadQuestionEditorContentUseCase
import com.kariscode.yike.domain.usecase.LoadDeckCardContextUseCase
import com.kariscode.yike.domain.usecase.LoadReviewCardSessionUseCase
import com.kariscode.yike.domain.usecase.ObserveCardSummariesUseCase
import com.kariscode.yike.domain.usecase.ObserveDeckSummariesUseCase
import com.kariscode.yike.domain.usecase.SaveQuestionEditorChangesUseCase
import com.kariscode.yike.domain.usecase.SaveCardUseCase
import com.kariscode.yike.domain.usecase.SaveDeckUseCase
import com.kariscode.yike.domain.usecase.SearchQuestionsUseCase
import com.kariscode.yike.domain.usecase.SubmitReviewRatingUseCase
import com.kariscode.yike.domain.usecase.ToggleCardArchiveUseCase
import com.kariscode.yike.domain.usecase.ToggleDeckArchiveUseCase
import com.kariscode.yike.domain.usecase.DeleteCardUseCase
import com.kariscode.yike.feature.analytics.AnalyticsViewModel
import com.kariscode.yike.feature.backup.BackupRestoreViewModel
import com.kariscode.yike.feature.card.CardListViewModel
import com.kariscode.yike.feature.deck.DeckListViewModel
import com.kariscode.yike.feature.editor.QuestionEditorViewModel
import com.kariscode.yike.feature.home.HomeViewModel
import com.kariscode.yike.feature.practice.PracticeSessionViewModel
import com.kariscode.yike.feature.practice.PracticeSetupViewModel
import com.kariscode.yike.feature.preview.TodayPreviewViewModel
import com.kariscode.yike.feature.recyclebin.RecycleBinViewModel
import com.kariscode.yike.feature.review.ReviewCardViewModel
import com.kariscode.yike.feature.review.ReviewQueueViewModel
import com.kariscode.yike.feature.search.QuestionSearchViewModel
import com.kariscode.yike.feature.settings.SettingsViewModel
import com.kariscode.yike.feature.sync.LanSyncViewModel
import com.kariscode.yike.feature.webconsole.WebConsoleViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 核心基础设施单独成组后，时间、线程和数据库等高成本对象可以被稳定复用，
 * 避免业务模块顺手创建临时实例导致行为漂移。
 */
private val coreModule = module {
    single<Application> { androidContext() as Application }
    single<TimeProvider> { SystemTimeProvider() }
    single<AppDispatchers> { DefaultAppDispatchers() }
    single<ReviewScheduler> { ReviewSchedulerV1() }
    single {
        Room.databaseBuilder(
            androidContext(),
            YikeDatabase::class.java,
            "yike.db"
        ).build()
    }
    single { get<YikeDatabase>().deckDao() }
    single { get<YikeDatabase>().cardDao() }
    single { get<YikeDatabase>().questionDao() }
    single { get<YikeDatabase>().questionSearchTokenDao() }
    single { get<YikeDatabase>().reviewRecordDao() }
    single { get<YikeDatabase>().syncChangeDao() }
    single { get<YikeDatabase>().syncPeerDao() }
    single { get<YikeDatabase>().syncPeerCursorDao() }
    single { WorkManager.getInstance(androidContext()) }
}

/**
 * 仓储模块统一描述数据边界，是为了让页面层以后新增能力时继续依赖接口而不是具体实现。
 */
private val repositoryModule = module {
    single { LanSyncCrypto() }
    single<LanSyncSharedSecretProtector> { KeystoreLanSyncSharedSecretProtector(get()) }
    single { LanSyncLocalProfileStore(context = androidContext(), crypto = get()) }
    single { LanSyncChangeRecorder(syncChangeDao = get(), crypto = get()) }
    single { LanSyncPortAllocator() }
    single<QuestionSearchIndexWriter> {
        QuestionSearchIndexer(
            questionDao = get(),
            questionSearchTokenDao = get()
        )
    }

    single<AppSettingsRepository> {
        DataStoreAppSettingsRepository(
            dataStore = androidContext().appSettingsDataStore,
            timeProvider = get(),
            syncChangeRecorder = get()
        )
    }
    single<DeckRepository> {
        OfflineDeckRepository(
            deckDao = get(),
            dispatchers = get(),
            timeProvider = get(),
            syncChangeRecorder = get()
        )
    }
    single<CardRepository> {
        OfflineCardRepository(
            cardDao = get(),
            dispatchers = get(),
            timeProvider = get(),
            syncChangeRecorder = get()
        )
    }
    single<QuestionRepository> {
        OfflineQuestionRepository(
            questionDao = get(),
            dispatchers = get(),
            timeProvider = get(),
            syncChangeRecorder = get(),
            questionSearchIndexWriter = get()
        )
    }
    single<QuestionEditorDraftRepository> { FileQuestionEditorDraftRepository(context = androidContext()) }
    single<StudyInsightsRepository> {
        OfflineStudyInsightsRepository(
            questionDao = get(),
            questionSearchTokenDao = get(),
            reviewRecordDao = get(),
            dispatchers = get()
        )
    }
    single<ReviewRepository> {
        OfflineReviewRepository(
            database = get(),
            questionDao = get(),
            reviewRecordDao = get(),
            reviewScheduler = get(),
            dispatchers = get(),
            syncChangeRecorder = get()
        )
    }
    single<PracticeRepository> {
        OfflinePracticeRepository(
            questionDao = get(),
            dispatchers = get()
        )
    }
}

/**
 * 服务模块继续围绕“提醒、备份、同步、网页后台”四条高风险能力收口，
 * 这样 Application、Worker 与页面都能共享完全一致的业务协作者。
 */
private val serviceModule = module {
    single<ReminderNotifier> { NotificationHelper(context = androidContext()) }
    single { get<ReminderNotifier>() as NotificationHelper }
    single<ReminderSyncScheduler> {
        ReminderScheduler(
            workManager = get(),
            appSettingsRepository = get(),
            timeProvider = get()
        )
    }
    single { get<ReminderSyncScheduler>() as ReminderScheduler }
    single {
        ReminderCheckRunner(
            appSettingsRepository = get(),
            questionRepository = get(),
            reminderNotifier = get(),
            reminderScheduler = get(),
            timeProvider = get()
        )
    }
    single { BackupValidator() }
    single { LanSyncConflictResolver() }
    single {
        LanSyncChangeApplier(
            database = get(),
            appSettingsRepository = get(),
            reminderScheduler = get(),
            deckDao = get(),
            cardDao = get(),
            questionDao = get(),
            reviewRecordDao = get(),
            conflictResolver = get(),
            questionSearchIndexWriter = get()
        )
    }
    single {
        BackupService(
            application = get(),
            database = get(),
            deckDao = get(),
            cardDao = get(),
            questionDao = get(),
            reviewRecordDao = get(),
            syncChangeDao = get(),
            appSettingsRepository = get(),
            backupValidator = get(),
            changeApplier = get(),
            questionSearchIndexWriter = get(),
            timeProvider = get(),
            dispatchers = get()
        )
    }
    single<BackupOperations> { get<BackupService>() }
    single {
        CsvExporter(
            application = get(),
            questionDao = get(),
            dispatchers = get()
        )
    }
    single {
        LanSyncRepositoryImpl(
            context = androidContext(),
            database = get(),
            appSettingsRepository = get(),
            reminderScheduler = get(),
            timeProvider = get(),
            dispatchers = get(),
            localProfileStore = get(),
            crypto = get(),
            sharedSecretProtector = get(),
            portAllocator = get(),
            syncChangeDao = get(),
            syncPeerDao = get(),
            syncPeerCursorDao = get(),
            deckDao = get(),
            cardDao = get(),
            questionDao = get(),
            reviewRecordDao = get(),
            conflictResolver = get(),
            changeApplier = get()
        )
    }
    single<LanSyncRepository> { get<LanSyncRepositoryImpl>() }
    single<WebConsoleRepository> {
        WebConsoleRepositoryImpl(
            context = androidContext(),
            deckRepository = get(),
            cardRepository = get(),
            questionRepository = get(),
            reviewRepository = get(),
            practiceRepository = get(),
            studyInsightsRepository = get(),
            appSettingsRepository = get(),
            backupService = get(),
            reminderScheduler = get(),
            timeProvider = get(),
            dispatchers = get()
        )
    }
}

/**
 * 已经落地的 UseCase 继续由 DI 统一提供，是为了让后续 ViewModel 迁移不再重复手写工厂装配。
 */
private val useCaseModule = module {
    factory { GetHomeOverviewUseCase(questionRepository = get(), deckRepository = get()) }
    factory { ObserveDeckSummariesUseCase(deckRepository = get(), timeProvider = get()) }
    factory { GetDeckAvailableTagsUseCase(studyInsightsRepository = get()) }
    factory { SaveDeckUseCase(deckRepository = get(), timeProvider = get()) }
    factory { ToggleDeckArchiveUseCase(deckRepository = get(), timeProvider = get()) }
    factory { LoadDeckCardContextUseCase(deckRepository = get()) }
    factory { ObserveCardSummariesUseCase(cardRepository = get(), timeProvider = get()) }
    factory { SaveCardUseCase(cardRepository = get(), timeProvider = get()) }
    factory { ToggleCardArchiveUseCase(cardRepository = get(), timeProvider = get()) }
    factory { DeleteCardUseCase(cardRepository = get()) }
    factory { GetDeckCardMasterySummaryUseCase(studyInsightsRepository = get()) }
    factory { LoadReviewCardSessionUseCase(cardRepository = get(), reviewRepository = get()) }
    factory { SubmitReviewRatingUseCase(reviewRepository = get(), nowEpochMillisProvider = get<com.kariscode.yike.core.domain.time.TimeProvider>()::nowEpochMillis) }
    factory { GetQuestionSearchMetadataUseCase(studyInsightsRepository = get(), deckRepository = get(), cardRepository = get()) }
    factory { SearchQuestionsUseCase(studyInsightsRepository = get()) }
    factory { LoadQuestionEditorContentUseCase(cardRepository = get(), questionRepository = get(), questionEditorDraftRepository = get()) }
    factory { SaveQuestionEditorChangesUseCase(cardRepository = get(), questionRepository = get(), questionEditorDraftRepository = get(), timeProvider = get()) }
}

/**
 * ViewModel 统一纳入 DI 后，Screen 层就不再需要重复拼 factory，
 * 路由参数和生命周期状态也能沿同一套注入方式持续演进。
 */
private val viewModelModule = module {
    viewModel {
        HomeViewModel(
            getHomeOverviewUseCase = get(),
            studyInsightsRepository = get(),
            appSettingsRepository = get(),
            timeProvider = get()
        )
    }
    viewModel {
        DeckListViewModel(
            deckRepository = get(),
            studyInsightsRepository = get(),
            timeProvider = get()
        )
    }
    viewModel { parameters ->
        CardListViewModel(
            deckId = parameters.get(),
            deckRepository = get(),
            cardRepository = get(),
            studyInsightsRepository = get(),
            timeProvider = get()
        )
    }
    viewModel { parameters ->
        QuestionEditorViewModel(
            cardId = parameters.get(),
            deckId = parameters.get(),
            questionEditorDraftRepository = get(),
            loadQuestionEditorContentUseCase = get(),
            saveQuestionEditorChangesUseCase = get(),
            timeProvider = get()
        )
    }
    viewModel {
        ReviewQueueViewModel(
            questionRepository = get(),
            timeProvider = get()
        )
    }
    viewModel { parameters ->
        ReviewCardViewModel(
            cardId = parameters.get(),
            loadReviewCardSessionUseCase = get(),
            submitReviewRatingUseCase = get(),
            timeProvider = get()
        )
    }
    viewModel {
        SettingsViewModel(
            appSettingsRepository = get(),
            reminderScheduler = get(),
            appVersionName = BuildConfig.VERSION_NAME
        )
    }
    viewModel {
        BackupRestoreViewModel(
            backupService = get(),
            csvExporter = get(),
            appSettingsRepository = get(),
            reminderScheduler = get()
        )
    }
    viewModel {
        RecycleBinViewModel(
            deckRepository = get(),
            cardRepository = get(),
            timeProvider = get()
        )
    }
    viewModel {
        TodayPreviewViewModel(
            studyInsightsRepository = get(),
            timeProvider = get()
        )
    }
    viewModel {
        AnalyticsViewModel(
            studyInsightsRepository = get(),
            appSettingsRepository = get(),
            timeProvider = get()
        )
    }
    viewModel { parameters ->
        QuestionSearchViewModel(
            initialDeckId = parameters.get(),
            initialCardId = parameters.get(),
            initialTag = parameters.get(),
            getQuestionSearchMetadataUseCase = get(),
            searchQuestionsUseCase = get(),
            timeProvider = get()
        )
    }
    viewModel { parameters ->
        PracticeSetupViewModel(
            initialArgs = parameters.get(),
            practiceRepository = get()
        )
    }
    viewModel { parameters ->
        PracticeSessionViewModel(
            args = parameters.get(),
            practiceRepository = get(),
            timeProvider = get(),
            savedStateHandle = get<SavedStateHandle>()
        )
    }
    viewModel {
        LanSyncViewModel(
            lanSyncRepository = get()
        )
    }
    viewModel {
        WebConsoleViewModel(
            webConsoleRepository = get()
        )
    }
}

/**
 * 应用级 Koin modules 把数据库、仓储和用例装配收口在单点，
 * 是为了替换手写 new 链路，同时让后续继续扩展 ViewModel/UseCase 时复用同一对象图。
 */
val yikeModules = listOf(coreModule, repositoryModule, serviceModule, useCaseModule, viewModelModule)

