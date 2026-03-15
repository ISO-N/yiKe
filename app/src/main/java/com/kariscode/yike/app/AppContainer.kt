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
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.repository.OfflineCardRepository
import com.kariscode.yike.data.repository.OfflineDeckRepository
import com.kariscode.yike.data.repository.OfflineQuestionRepository
import com.kariscode.yike.data.repository.OfflineReviewRepository
import com.kariscode.yike.data.reminder.NotificationHelper
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.data.settings.DataStoreAppSettingsRepository
import com.kariscode.yike.data.settings.appSettingsDataStore
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1

/**
 * 首版采用手动装配依赖，目的是把“谁负责创建什么”集中到单一位置，
 * 以防后续功能增长时出现跨层直接 new 实现导致的分层漂移。
 */
class AppContainer(
    private val application: Application
) {
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

    /**
     * 设置仓储放在容器层创建，能保证全应用对默认值与读写路径的一致理解，
     * 并为后续在写入后触发提醒重建提供单点入口。
     */
    val appSettingsRepository: AppSettingsRepository by lazy {
        DataStoreAppSettingsRepository(
            dataStore = application.appSettingsDataStore
        )
    }

    /**
     * 内容管理仓储统一在此装配，原因是它们共享同一数据库实例，
     * 并且后续首页统计、提醒 Worker 与备份导出都会复用同一套查询口径。
     */
    val deckRepository: DeckRepository by lazy {
        OfflineDeckRepository(deckDao = database.deckDao(), dispatchers = dispatchers)
    }

    val cardRepository: CardRepository by lazy {
        OfflineCardRepository(cardDao = database.cardDao(), dispatchers = dispatchers)
    }

    val questionRepository: QuestionRepository by lazy {
        OfflineQuestionRepository(questionDao = database.questionDao(), dispatchers = dispatchers)
    }

    /**
     * 复习专用仓储需要数据库事务与调度器协同，因此单独装配可避免页面层手工拼接多份依赖。
     */
    val reviewRepository: ReviewRepository by lazy {
        OfflineReviewRepository(
            database = database,
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            reviewScheduler = reviewScheduler,
            dispatchers = dispatchers
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
     * 备份服务聚合导出、校验与恢复，是为了把高风险数据操作从页面层彻底隔离出去。
     */
    val backupService: BackupService by lazy {
        BackupService(
            application = application,
            database = database,
            deckDao = database.deckDao(),
            cardDao = database.cardDao(),
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            appSettingsRepository = appSettingsRepository,
            backupValidator = BackupValidator(),
            timeProvider = timeProvider,
            dispatchers = dispatchers
        )
    }

    /**
     * 当前阶段仅保留 Application 引用，为后续 Room/DataStore/WorkManager 初始化提供上下文。
     */
    fun application(): Application = application
}
