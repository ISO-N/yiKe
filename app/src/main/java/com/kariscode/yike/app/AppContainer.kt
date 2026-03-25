package com.kariscode.yike.app

import android.app.Application
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.backup.BackupService
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.reminder.NotificationHelper
import com.kariscode.yike.data.reminder.ReminderCheckRunner
import com.kariscode.yike.data.reminder.ReminderScheduler
import com.kariscode.yike.data.search.QuestionSearchIndexWriter
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.data.sync.LanSyncCrypto
import com.kariscode.yike.data.sync.LanSyncLocalProfileStore
import com.kariscode.yike.data.sync.LanSyncPortAllocator
import com.kariscode.yike.data.sync.LanSyncRepositoryImpl
import com.kariscode.yike.data.sync.LanSyncSharedSecretProtector
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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * AppContainer 退化成 Koin 驱动的门面后，现有页面仍能沿用同一取依赖方式，
 * 同时把对象图真正迁到 DI 框架管理，避免继续在容器里手写实例装配。
 */
class AppContainer : KoinComponent {
    val application: Application by inject()
    val database: YikeDatabase by inject()
    val timeProvider: TimeProvider by inject()
    val dispatchers: AppDispatchers by inject()
    val reviewScheduler: ReviewScheduler by inject()
    val appSettingsRepository: AppSettingsRepository by inject()
    val deckRepository: DeckRepository by inject()
    val cardRepository: CardRepository by inject()
    val questionRepository: QuestionRepository by inject()
    val questionEditorDraftRepository: QuestionEditorDraftRepository by inject()
    val questionSearchIndexWriter: QuestionSearchIndexWriter by inject()
    val studyInsightsRepository: StudyInsightsRepository by inject()
    val reviewRepository: ReviewRepository by inject()
    val practiceRepository: PracticeRepository by inject()
    val notificationHelper: NotificationHelper by inject()
    val reminderScheduler: ReminderScheduler by inject()
    val reminderCheckRunner: ReminderCheckRunner by inject()
    val backupService: BackupService by inject()
    val lanSyncChangeRecorder: LanSyncChangeRecorder by inject()
    val lanSyncCrypto: LanSyncCrypto by inject()
    val lanSyncSharedSecretProtector: LanSyncSharedSecretProtector by inject()
    val lanSyncLocalProfileStore: LanSyncLocalProfileStore by inject()
    val lanSyncPortAllocator: LanSyncPortAllocator by inject()
    val lanSyncRepository: LanSyncRepository by inject<LanSyncRepositoryImpl>()
    val webConsoleRepository: WebConsoleRepository by inject()
}

