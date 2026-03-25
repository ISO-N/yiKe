package com.kariscode.yike.data.backup

import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import com.kariscode.yike.data.sync.SyncChangePayload
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 顶层备份模型固定化后，导出与恢复就能围绕同一份结构演进，
 * 避免页面、校验器和存储实现各自维护一套字段定义。
 */
@Serializable
data class BackupDocument(
    val app: BackupAppInfo,
    val full: BackupFullPayload? = null,
    val incremental: BackupIncrementalPayload? = null
)

/**
 * 应用元信息进入备份文件，是为了在恢复时能判断文件代际并保留导出时间上下文。
 */
@Serializable
data class BackupAppInfo(
    val name: String,
    val backupVersion: Int,
    val exportedAt: String,
    val kind: BackupDocumentKind = BackupDocumentKind.FULL
)

/**
 * 备份类型显式入模后，恢复阶段就能先判断“这是完整覆盖还是增量应用”，
 * 避免只靠字段是否为空去猜测文件语义。
 */
@Serializable
enum class BackupDocumentKind {
    @SerialName("full")
    FULL,

    @SerialName("incremental")
    INCREMENTAL
}

/**
 * 完整备份负载保持原有层级结构，是为了继续支持“导出一份即可完整恢复”的离线兜底能力。
 */
@Serializable
data class BackupFullPayload(
    val settings: BackupSettings,
    val decks: List<BackupDeck>,
    val cards: List<BackupCard>,
    val questions: List<BackupQuestion>,
    val reviewRecords: List<BackupReviewRecord>
)

/**
 * 增量备份单独承载基线时间和变更列表，是为了让恢复流程可以直接复用同步流水而不再发明第二套差异协议。
 */
@Serializable
data class BackupIncrementalPayload(
    val baseBackupAt: String,
    val changes: List<SyncChangePayload>
)

/**
 * 设置数据必须跟业务数据一并进入完整备份，才能在恢复后把提醒能力与版本状态一起还原。
 */
@Serializable
data class BackupSettings(
    val dailyReminderEnabled: Boolean,
    val dailyReminderTime: String,
    val schemaVersion: Int,
    val backupLastAt: String? = null,
    val themeMode: String = ThemeMode.LIGHT.storageValue,
    val streakAchievementUnlocks: List<BackupStreakAchievementUnlock> = emptyList()
)

/**
 * 成就解锁记录需要随设置一起进入备份，
 * 这样跨设备迁移或误删恢复后仍能保留用户的学习进度反馈。
 */
@Serializable
data class BackupStreakAchievementUnlock(
    val id: String,
    val unlockedAt: String
)

/**
 * Deck 备份模型保持与领域字段一一对应，是为了恢复时能直接重建层级根节点而无需再推导。
 */
@Serializable
data class BackupDeck(
    val id: String,
    val name: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val intervalStepCount: Int = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Card 备份模型保留 deckId 引用，是为了恢复时能够重建完整层级关系。
 */
@Serializable
data class BackupCard(
    val id: String,
    val deckId: String,
    val title: String,
    val description: String,
    val archived: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Question 备份模型完整保留调度字段，原因是复习状态是用户数据的一部分，不能在恢复时重新计算替代。
 */
@Serializable
data class BackupQuestion(
    val id: String,
    val cardId: String,
    val prompt: String,
    val answer: String,
    val tags: List<String>,
    val status: String,
    val stageIndex: Int,
    val dueAt: String,
    val lastReviewedAt: String? = null,
    val reviewCount: Int,
    val lapseCount: Int,
    val createdAt: String,
    val updatedAt: String
)

/**
 * ReviewRecord 备份模型保存完整前后变化链路，是为了恢复后依旧能还原评分历史。
 */
@Serializable
data class BackupReviewRecord(
    val id: String,
    val questionId: String,
    val rating: String,
    val oldStageIndex: Int,
    val newStageIndex: Int,
    val oldDueAt: String,
    val newDueAt: String,
    val reviewedAt: String,
    val responseTimeMs: Long? = null,
    val note: String = ""
)
