package com.kariscode.yike.app

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.core.time.SystemTimeProvider
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.backup.BackupValidator
import com.kariscode.yike.data.editor.FileQuestionEditorDraftRepository
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.ReviewRecordDao
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.dao.SyncPeerCursorDao
import com.kariscode.yike.data.local.db.dao.SyncPeerDao
import com.kariscode.yike.data.repository.OfflineCardRepository
import com.kariscode.yike.data.repository.OfflineDeckRepository
import com.kariscode.yike.data.repository.OfflineQuestionRepository
import com.kariscode.yike.data.repository.OfflineReviewRepository
import com.kariscode.yike.data.repository.OfflineStudyInsightsRepository
import com.kariscode.yike.data.reminder.NotificationHelper
import com.kariscode.yike.data.reminder.ReminderCheckRunner
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.data.settings.DataStoreAppSettingsRepository
import com.kariscode.yike.data.settings.appSettingsDataStore
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.data.sync.LanSyncCrypto
import com.kariscode.yike.data.sync.LanSyncLocalProfileStore
import com.kariscode.yike.data.sync.LanSyncPortAllocator
import com.kariscode.yike.data.sync.KeystoreLanSyncSharedSecretProtector
import com.kariscode.yike.data.sync.LanSyncRepositoryImpl
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.LanSyncRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1

/**
 * 首版采用手动装配依赖，目的是把“谁负责创建什么”集中到单一位置，
 * 以防后续功能增长时出现跨层直接 new 实现导致的分层漂移。
 */
class AppContainer(
    private val application: Application
) {
    /**
     * 需要在多处复用的内容 DAO 成组保存，是为了让依赖装配围绕“内容数据访问”这一语义单元展开，
     * 而不是在多个仓储和服务创建处反复展开一串相同字段。
     */
    private data class ContentDataAccess(
        val deckDao: DeckDao,
        val cardDao: CardDao,
        val questionDao: QuestionDao,
        val reviewRecordDao: ReviewRecordDao
    )

    /**
     * 内容仓储共享同一组三元依赖，是为了让装配时把“线程、时间与同步 journal”看成同一语义单元，
     * 避免后续新增仓储时漏传其中某一项。
     */
    private data class ContentRepositoryDependencies(
        val dispatchers: AppDispatchers,
        val timeProvider: TimeProvider,
        val syncChangeRecorder: LanSyncChangeRecorder
    )

    /**
     * 同步仓储需要同时访问内容 DAO 与同步专属 DAO，成组后可以把局域网同步的基础设施边界表达得更清楚。
     */
    private data class SyncInfrastructureDependencies(
        val syncChangeDao: SyncChangeDao,
        val syncPeerDao: SyncPeerDao,
        val syncPeerCursorDao: SyncPeerCursorDao,
        val contentDataAccess: ContentDataAccess
    )

    /**
     * 抽象时间是为了让调度、提醒与备份的时间计算可预测、可测试，
     * 避免在业务逻辑中散落 System.currentTimeMillis() 导致测试脆弱。
     */
    val timeProvider: TimeProvider = SystemTimeProvider()

    /**
     * 统一 dispatcher 注入是为了让 IO 与计算的线程选择可替换，
     * 并为后续测试中替换为 TestDispatcher 预留入口。
     */
    val dispatchers: AppDispatchers = DefaultAppDispatchers()

    /**
     * 调度器作为纯业务组件保留在容器内单例化，能确保复习事务、测试与未来恢复流程使用同一规则。
     */
    val reviewScheduler: ReviewSchedulerV1 = ReviewSchedulerV1()

    /**
     * Room 数据库必须作为单例存在于进程内，
     * 否则多实例会导致事务与缓存行为不可预测，尤其会影响评分提交与恢复导入的安全性。
     */
    val database: YikeDatabase by lazy {
        Room.databaseBuilder(
            application,
            YikeDatabase::class.java,
            "yike.db"
        ).build()
    }

    private val deckDao by lazy { database.deckDao() }
    private val cardDao by lazy { database.cardDao() }
    private val questionDao by lazy { database.questionDao() }
    private val reviewRecordDao by lazy { database.reviewRecordDao() }
    private val syncChangeDao by lazy { database.syncChangeDao() }
    private val syncPeerDao by lazy { database.syncPeerDao() }
    private val syncPeerCursorDao by lazy { database.syncPeerCursorDao() }

    /**
     * 内容 DAO 统一成组后，统计、复习、备份和同步等复合能力就能共享同一份数据访问入口。
     */
    private val contentDataAccess: ContentDataAccess by lazy {
        ContentDataAccess(
            deckDao = deckDao,
            cardDao = cardDao,
            questionDao = questionDao,
            reviewRecordDao = reviewRecordDao
        )
    }

    /**
     * 同步加密能力放在容器内共享，是为了让配对、鉴权和本地密钥落盘都围绕同一实现工作。
     */
    val lanSyncCrypto: LanSyncCrypto by lazy {
        LanSyncCrypto()
    }

    /**
     * 共享密钥保护策略单独装配后，同步仓储能解耦 Android Keystore 细节，同时继续保持默认安全策略。
     */
    val lanSyncSharedSecretProtector: KeystoreLanSyncSharedSecretProtector by lazy {
        KeystoreLanSyncSharedSecretProtector(lanSyncCrypto)
    }

    /**
     * 本机同步身份与显示名单独装配，是为了避免同步页直接耦合 DataStore 细节。
     */
    val lanSyncLocalProfileStore: LanSyncLocalProfileStore by lazy {
        LanSyncLocalProfileStore(
            context = application,
            crypto = lanSyncCrypto
        )
    }

    /**
     * 变更记录器作为共享基础设施存在，能让内容写入、设置写入和复习事务共用同一份 journal 规则。
     */
    val lanSyncChangeRecorder: LanSyncChangeRecorder by lazy {
        LanSyncChangeRecorder(
            syncChangeDao = syncChangeDao,
            crypto = lanSyncCrypto
        )
    }

    /**
     * 端口冲突处理单独抽象后，同步服务启动时就不必把扫描逻辑硬编码进 HTTP 服务实现。
     */
    val lanSyncPortAllocator: LanSyncPortAllocator by lazy {
        LanSyncPortAllocator()
    }

    /**
     * 设置仓储放在容器层创建，能保证全应用对默认值与读写路径的一致理解，
     * 并为后续在写入后触发提醒重建提供单点入口。
     */
    val appSettingsRepository: AppSettingsRepository by lazy {
        DataStoreAppSettingsRepository(
            dataStore = application.appSettingsDataStore,
            timeProvider = timeProvider,
            syncChangeRecorder = lanSyncChangeRecorder
        )
    }

    /**
     * 共享内容仓储依赖单独成组后，卡组、卡片和问题仓储的装配能保持同一口径。
     */
    private val contentRepositoryDependencies: ContentRepositoryDependencies by lazy {
        ContentRepositoryDependencies(
            dispatchers = dispatchers,
            timeProvider = timeProvider,
            syncChangeRecorder = lanSyncChangeRecorder
        )
    }

    /**
     * 同步基础设施分组成块后，局域网同步仓储的构造参数更接近实际职责边界，便于后续继续拆内部协作者。
     */
    private val syncInfrastructureDependencies: SyncInfrastructureDependencies by lazy {
        SyncInfrastructureDependencies(
            syncChangeDao = syncChangeDao,
            syncPeerDao = syncPeerDao,
            syncPeerCursorDao = syncPeerCursorDao,
            contentDataAccess = contentDataAccess
        )
    }

    /**
     * 内容管理仓储统一在此装配，原因是它们共享同一数据库实例，
     * 并且后续首页统计、提醒 Worker 与备份导出都会复用同一套查询口径。
     */
    val deckRepository: DeckRepository by lazy {
        val dependencies = contentRepositoryDependencies
        OfflineDeckRepository(
            deckDao = deckDao,
            dispatchers = dependencies.dispatchers,
            timeProvider = dependencies.timeProvider,
            syncChangeRecorder = dependencies.syncChangeRecorder
        )
    }

    val cardRepository: CardRepository by lazy {
        val dependencies = contentRepositoryDependencies
        OfflineCardRepository(
            cardDao = cardDao,
            dispatchers = dependencies.dispatchers,
            timeProvider = dependencies.timeProvider,
            syncChangeRecorder = dependencies.syncChangeRecorder
        )
    }

    val questionRepository: QuestionRepository by lazy {
        val dependencies = contentRepositoryDependencies
        OfflineQuestionRepository(
            questionDao = questionDao,
            dispatchers = dependencies.dispatchers,
            timeProvider = dependencies.timeProvider,
            syncChangeRecorder = dependencies.syncChangeRecorder
        )
    }

    /**
     * 编辑草稿单独落到私有文件目录，是为了把“临时输入恢复”与正式业务数据的数据库演进彻底分离。
     */
    val questionEditorDraftRepository: QuestionEditorDraftRepository by lazy {
        FileQuestionEditorDraftRepository(context = application)
    }

    /**
     * 洞察仓储独立装配后，统计、预览和搜索页面就能共享同一套聚合查询，
     * 不必在多个 ViewModel 中重复组合 DAO。
     */
    val studyInsightsRepository: StudyInsightsRepository by lazy {
        OfflineStudyInsightsRepository(
            questionDao = contentDataAccess.questionDao,
            reviewRecordDao = contentDataAccess.reviewRecordDao,
            dispatchers = dispatchers
        )
    }

    /**
     * 复习专用仓储需要数据库事务与调度器协同，因此单独装配可避免页面层手工拼接多份依赖。
     */
    val reviewRepository: ReviewRepository by lazy {
        OfflineReviewRepository(
            database = database,
            questionDao = contentDataAccess.questionDao,
            reviewRecordDao = contentDataAccess.reviewRecordDao,
            reviewScheduler = reviewScheduler,
            dispatchers = dispatchers,
            syncChangeRecorder = lanSyncChangeRecorder
        )
    }

    /**
     * 通知辅助组件集中化后，应用初始化与 Worker 都能复用同一渠道和通知语义。
     */
    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context = application)
    }

    /**
     * ReminderScheduler 统一处理提醒任务重建，可避免设置页、恢复页和广播恢复各自拼接 WorkRequest。
     */
    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(
            workManager = WorkManager.getInstance(application),
            appSettingsRepository = appSettingsRepository,
            timeProvider = timeProvider
        )
    }

    /**
     * Worker 依赖独立执行器后，提醒主路径可以在不启动真实 Worker 的情况下完成单元测试。
     */
    val reminderCheckRunner: ReminderCheckRunner by lazy {
        ReminderCheckRunner(
            appSettingsRepository = appSettingsRepository,
            questionRepository = questionRepository,
            reminderNotifier = notificationHelper,
            reminderScheduler = reminderScheduler,
            timeProvider = timeProvider
        )
    }

    /**
     * 备份服务聚合导出、校验与恢复，是为了把高风险数据操作从页面层彻底隔离出去。
     */
    val backupService: BackupService by lazy {
        BackupService(
            application = application,
            database = database,
            deckDao = contentDataAccess.deckDao,
            cardDao = contentDataAccess.cardDao,
            questionDao = contentDataAccess.questionDao,
            reviewRecordDao = contentDataAccess.reviewRecordDao,
            appSettingsRepository = appSettingsRepository,
            backupValidator = BackupValidator(),
            timeProvider = timeProvider,
            dispatchers = dispatchers
        )
    }

    /**
     * 局域网同步仓储把发现、服务广播与恢复流程聚合起来，能让设置页入口只围绕单一能力工作。
     */
    val lanSyncRepository: LanSyncRepository by lazy {
        val syncDependencies = syncInfrastructureDependencies
        LanSyncRepositoryImpl(
            context = application,
            database = database,
            appSettingsRepository = appSettingsRepository,
            reminderScheduler = reminderScheduler,
            timeProvider = timeProvider,
            dispatchers = dispatchers,
            localProfileStore = lanSyncLocalProfileStore,
            crypto = lanSyncCrypto,
            sharedSecretProtector = lanSyncSharedSecretProtector,
            portAllocator = lanSyncPortAllocator,
            syncChangeDao = syncDependencies.syncChangeDao,
            syncPeerDao = syncDependencies.syncPeerDao,
            syncPeerCursorDao = syncDependencies.syncPeerCursorDao,
            deckDao = syncDependencies.contentDataAccess.deckDao,
            cardDao = syncDependencies.contentDataAccess.cardDao,
            questionDao = syncDependencies.contentDataAccess.questionDao,
            reviewRecordDao = syncDependencies.contentDataAccess.reviewRecordDao
        )
    }

    /**
     * 当前阶段仅保留 Application 引用，为后续 Room/DataStore/WorkManager 初始化提供上下文。
     */
    fun application(): Application = application
}
