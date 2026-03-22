package com.kariscode.yike.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.sync.InMemorySyncChangeDao
import com.kariscode.yike.data.sync.createInspectableTestSyncRecorder
import com.kariscode.yike.data.sync.storageValue
import com.kariscode.yike.domain.error.QuestionNotFoundException
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OfflineReviewRepository 集成测试使用 Room 内存数据库验证评分提交流程，
 * 确保事务原子性、阶段更新与 lapse 计数在真实数据库环境下行为正确。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineReviewRepositoryTest {

    private lateinit var database: YikeDatabase
    private lateinit var repository: OfflineReviewRepository
    private lateinit var syncChangeDao: InMemorySyncChangeDao

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YikeDatabase::class.java
        ).allowMainThreadQueries().build()

        val testSyncRecorder = createInspectableTestSyncRecorder()
        syncChangeDao = testSyncRecorder.syncChangeDao
        repository = OfflineReviewRepository(
            database = database,
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            reviewScheduler = ReviewSchedulerV1(),
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            },
            syncChangeRecorder = testSyncRecorder.recorder
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ── 事务原子性：Question 更新与 ReviewRecord 写入同时成功 ───

    /**
     * 评分提交后 Question 与 ReviewRecord 必须同时落库，
     * 否则会出现"阶段变了但没有复习记录"的半成功状态。
     */
    @Test
    fun submitRating_createsReviewRecordAndUpdatesQuestion() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 0)

        val submission = repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 10_000L,
            responseTimeMs = 500L
        )

        val updatedEntity = database.questionDao().findById("q_1")
        val allRecords = database.reviewRecordDao().listAll()

        assertNotNull(updatedEntity)
        assertEquals(1, updatedEntity!!.stageIndex)
        assertEquals(1, updatedEntity.reviewCount)
        assertEquals(10_000L, updatedEntity.lastReviewedAt)

        assertEquals(1, allRecords.size)
        assertEquals(submission.reviewRecord.id, allRecords.first().id)
        assertEquals("q_1", allRecords.first().questionId)
        assertEquals("GOOD", allRecords.first().rating)
        assertEquals(0, allRecords.first().oldStageIndex)
        assertEquals(1, allRecords.first().newStageIndex)
    }

    // ── stageIndex 正确更新 ────────────────────────────────────

    /**
     * GOOD 评分应从 stage 0 升到 stage 1，验证调度器与写库的联动。
     */
    @Test
    fun submitRating_good_advancesStageByOne() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 2)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 20_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(3, updated.stageIndex)
    }

    /**
     * 中度过期时 GOOD 应先用来保住阶段，验证仓储已把题目的 dueAt 传给调度器参与衰减判断。
     */
    @Test
    fun submitRating_goodWithModerateOverdue_restoresInsteadOfAdvancing() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(
            id = "q_1",
            cardId = "card_1",
            stageIndex = 4,
            dueAt = 15L * 86_400_000L
        )

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 35L * 86_400_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(4, updated.stageIndex)
        assertEquals(naturalDueAt(reviewedAtEpochMillis = 35L * 86_400_000L, intervalDays = 15), updated.dueAt)
    }

    /**
     * 极端长期过期时高阶段卡片应先被拉回低阶段，避免仓储层仍按旧规则把它留在长周期区间。
     */
    @Test
    fun submitRating_goodWithSevereOverdue_resetsHighStageBeforeScheduling() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(
            id = "q_1",
            cardId = "card_1",
            stageIndex = 7,
            dueAt = 180L * 86_400_000L
        )

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 980L * 86_400_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(1, updated.stageIndex)
        assertEquals(naturalDueAt(reviewedAtEpochMillis = 980L * 86_400_000L, intervalDays = 2), updated.dueAt)
    }

    /**
     * EASY 评分应跳两级，验证调度器跳级逻辑在写库端的体现。
     */
    @Test
    fun submitRating_easy_advancesStageByTwo() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 1)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.EASY,
            reviewedAtEpochMillis = 30_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(3, updated.stageIndex)
    }

    /**
     * 当卡组把间隔次数收窄到 4 段时，评分后阶段应停留在该卡组允许的最高层级，
     * 这样短期卡组就不会继续被推到 7 段长周期。
     */
    @Test
    fun submitRating_respectsDeckIntervalStepCountCap() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1", intervalStepCount = 4)
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 3)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 35_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(3, updated.stageIndex)
        assertEquals(naturalDueAt(reviewedAtEpochMillis = 35_000L, intervalDays = 7), updated.dueAt)
    }

    /**
     * HARD 评分应降一级但不低于 0。
     */
    @Test
    fun submitRating_hard_decreasesStageByOne() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 3)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.HARD,
            reviewedAtEpochMillis = 40_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(2, updated.stageIndex)
    }

    /**
     * AGAIN 评分应重置 stageIndex 到 0，无论之前处于哪个阶段。
     */
    @Test
    fun submitRating_again_resetsStageToZero() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 5)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = 50_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(0, updated.stageIndex)
    }

    // ── AGAIN 时 lapseCount +1 ─────────────────────────────────

    /**
     * AGAIN 评分时 lapseCount 必须 +1，这是遗忘统计的核心依据。
     */
    @Test
    fun submitRating_again_incrementsLapseCount() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 3, lapseCount = 0)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = 60_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(1, updated.lapseCount)
    }

    /**
     * GOOD 评分时 lapseCount 不应变化，只有 AGAIN 才触发遗忘计数。
     */
    @Test
    fun submitRating_good_doesNotIncrementLapseCount() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 2, lapseCount = 3)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 70_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(3, updated.lapseCount)
    }

    // ── lapse 计数正确累加 ──────────────────────────────────────

    /**
     * 连续多次 AGAIN 评分时 lapseCount 应逐次 +1，验证累加而非重置。
     */
    @Test
    fun submitRating_multipleAgain_accumulatesLapseCount() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 4, lapseCount = 2)

        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = 80_000L,
            responseTimeMs = null
        )
        repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = 90_000L,
            responseTimeMs = null
        )

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(4, updated.lapseCount)
    }

    /**
     * 混合评分场景下只有 AGAIN 才增加 lapseCount，GOOD/EASY/HARD 不影响。
     */
    @Test
    fun submitRating_mixedRatings_onlyAgainIncreasesLapseCount() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 0, lapseCount = 0)

        repository.submitRating("q_1", ReviewRating.GOOD, 100_000L, null)
        repository.submitRating("q_1", ReviewRating.AGAIN, 200_000L, null)
        repository.submitRating("q_1", ReviewRating.HARD, 300_000L, null)
        repository.submitRating("q_1", ReviewRating.EASY, 400_000L, null)
        repository.submitRating("q_1", ReviewRating.AGAIN, 500_000L, null)

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(2, updated.lapseCount)
    }

    // ── reviewCount 递增 ───────────────────────────────────────

    /**
     * 每次评分提交 reviewCount 都应 +1，无论评分类型。
     */
    @Test
    fun submitRating_alwaysIncrementsReviewCount() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 0)

        repository.submitRating("q_1", ReviewRating.GOOD, 100_000L, null)
        repository.submitRating("q_1", ReviewRating.AGAIN, 200_000L, null)
        repository.submitRating("q_1", ReviewRating.HARD, 300_000L, null)

        val updated = database.questionDao().findById("q_1")!!
        assertEquals(3, updated.reviewCount)
    }

    // ── ReviewRecord 新旧值一致性 ──────────────────────────────

    /**
     * ReviewRecord 中的 oldStageIndex/newStageIndex 必须准确反映变更前后的阶段，
     * 否则复习历史链路无法用于调度排查。
     */
    @Test
    fun submitRating_reviewRecordCapturesOldAndNewStage() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")
        seedQuestion(id = "q_1", cardId = "card_1", stageIndex = 3)

        val submission = repository.submitRating(
            questionId = "q_1",
            rating = ReviewRating.AGAIN,
            reviewedAtEpochMillis = 110_000L,
            responseTimeMs = 1200L
        )

        assertEquals(3, submission.reviewRecord.oldStageIndex)
        assertEquals(0, submission.reviewRecord.newStageIndex)
        assertEquals(ReviewRating.AGAIN, submission.reviewRecord.rating)
        assertEquals(1200L, submission.reviewRecord.responseTimeMs)
    }

    // ── 不存在的 questionId ────────────────────────────────────

    /**
     * 提交评分时如果 questionId 不存在，应抛出异常而非静默失败。
     */
    @Test(expected = QuestionNotFoundException::class)
    fun submitRating_nonExistentQuestion_throwsException() = runTest {
        seedHierarchy(deckId = "deck_1", cardId = "card_1")

        repository.submitRating(
            questionId = "non_existent",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 120_000L,
            responseTimeMs = null
        )
    }

    /**
     * 按卡片读取待复习问题时必须排除未到期、已归档和非激活内容，
     * 否则复习页会把不应进入本轮的问题混进来。
     */
    @Test
    fun listDueQuestionsByCard_returnsOnlyActiveUnarchivedDueQuestions() = runTest {
        seedHierarchy(deckId = "deck_active", cardId = "card_active")
        seedHierarchy(deckId = "deck_other", cardId = "card_other")
        database.cardDao().upsert(
            CardEntity(
                id = "card_archived",
                deckId = "deck_active",
                title = "card_archived",
                description = "",
                archived = true,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        seedQuestion(id = "q_due", cardId = "card_active", stageIndex = 1, dueAt = 1_000L)
        seedQuestion(id = "q_future", cardId = "card_active", stageIndex = 1, dueAt = 9_000L)
        seedQuestion(id = "q_archived_card", cardId = "card_archived", stageIndex = 1, dueAt = 1_000L)
        seedQuestion(
            id = "q_inactive",
            cardId = "card_active",
            stageIndex = 1,
            dueAt = 1_000L,
            status = QuestionEntity.STATUS_ARCHIVED
        )
        seedQuestion(id = "q_other_card", cardId = "card_other", stageIndex = 1, dueAt = 1_000L)

        val dueQuestions = repository.listDueQuestionsByCard(cardId = "card_active", nowEpochMillis = 2_000L)

        assertEquals(listOf("q_due"), dueQuestions.map { it.id })
    }

    /**
     * 评分提交除了改题目和写历史外，还必须写入同步 journal，
     * 否则后续局域网同步无法把这次评分传播到其他设备。
     */
    @Test
    fun submitRating_recordsQuestionAndReviewRecordJournalEntries() = runTest {
        seedHierarchy(deckId = "deck_sync", cardId = "card_sync")
        seedQuestion(id = "q_sync", cardId = "card_sync", stageIndex = 2, dueAt = 1_000L)

        repository.submitRating(
            questionId = "q_sync",
            rating = ReviewRating.HARD,
            reviewedAtEpochMillis = 130_000L,
            responseTimeMs = 700L
        )

        val changes = syncChangeDao.listAfter(afterSeq = 0L)

        assertEquals(2, changes.size)
        assertEquals(SyncEntityType.QUESTION.storageValue(), changes[0].entityType)
        assertEquals(SyncChangeOperation.UPSERT.storageValue(), changes[0].operation)
        assertEquals("q_sync", changes[0].entityId)
        assertEquals(SyncEntityType.REVIEW_RECORD.storageValue(), changes[1].entityType)
        assertEquals(SyncChangeOperation.UPSERT.storageValue(), changes[1].operation)
        assertEquals(130_000L, changes[1].modifiedAt)
    }

    // ── helpers ────────────────────────────────────────────────

    private suspend fun seedHierarchy(
        deckId: String,
        cardId: String,
        intervalStepCount: Int = 8
    ) {
        database.deckDao().upsert(
            DeckEntity(
                id = deckId, name = deckId, description = "", tagsJson = "[]",
                intervalStepCount = intervalStepCount,
                archived = false, sortOrder = 0, createdAt = 1L, updatedAt = 1L
            )
        )
        database.cardDao().upsert(
            CardEntity(
                id = cardId, deckId = deckId, title = cardId, description = "",
                archived = false, sortOrder = 0, createdAt = 1L, updatedAt = 1L
            )
        )
    }

    private suspend fun seedQuestion(
        id: String,
        cardId: String,
        stageIndex: Int,
        lapseCount: Int = 0,
        dueAt: Long = 1_000L,
        status: String = QuestionEntity.STATUS_ACTIVE
    ) {
        database.questionDao().upsertAll(
            listOf(
                QuestionEntity(
                    id = id, cardId = cardId, prompt = id, answer = "",
                    tagsJson = "[]", status = status,
                    stageIndex = stageIndex, dueAt = dueAt,
                    lastReviewedAt = null, reviewCount = 0, lapseCount = lapseCount,
                    createdAt = 1L, updatedAt = 1L
                )
            )
        )
    }

    /**
     * 测试辅助方法按运行时系统时区计算自然日 dueAt，是为了让仓储集成测试与生产代码共享同一默认时区语义。
     */
    private fun naturalDueAt(reviewedAtEpochMillis: Long, intervalDays: Int): Long =
        Instant.ofEpochMilli(reviewedAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(intervalDays.toLong())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
