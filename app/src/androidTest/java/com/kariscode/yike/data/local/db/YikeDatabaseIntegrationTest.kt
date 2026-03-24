package com.kariscode.yike.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kariscode.yike.core.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.QuestionSearchTokenEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.repository.OfflineReviewRepository
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.data.sync.LanSyncCrypto
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 数据层测试直接覆盖 Room 关系、首页统计与评分事务，
 * 是为了验证“级联删除/聚合查询/写库事务”这些最容易损坏数据一致性的高风险路径。
 */
@RunWith(AndroidJUnit4::class)
class YikeDatabaseIntegrationTest {
    private lateinit var database: YikeDatabase

    /**
     * 使用内存数据库能让每个测试在隔离状态下验证真实 SQL 与外键行为，
     * 同时避免污染开发环境数据。
     */
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /**
     * 每个测试结束后关闭数据库，能确保连接与缓存不会泄漏到后续用例。
     */
    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 首页统计必须排除已归档层级，否则内容管理与首页概览会出现口径冲突。
     */
    @Test
    fun getTodayReviewSummary_excludesArchivedContent() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_active", archived = false))
        database.deckDao().upsert(createDeck(id = "deck_archived", archived = true))
        database.cardDao().upsert(createCard(id = "card_active", deckId = "deck_active", archived = false))
        database.cardDao().upsert(createCard(id = "card_archived", deckId = "deck_archived", archived = false))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_active", cardId = "card_active", dueAt = 1_000L),
                createQuestion(id = "q_archived", cardId = "card_archived", dueAt = 1_000L)
            )
        )

        val summary = database.questionDao().getTodayReviewSummary(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = 2_000L
        )

        assertEquals(1, summary.dueCardCount)
        assertEquals(1, summary.dueQuestionCount)
    }

    /**
     * 搜索与今日预览共用的上下文查询必须同时返回层级名称并遵守 due 过滤，
     * 否则页面会出现“能搜到题，但不知道它属于哪里”或“预览混入未到期题”的问题。
     */
    @Test
    fun listQuestionContexts_supportsKeywordDeckAndDueFilters() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_math", archived = false))
        database.deckDao().upsert(createDeck(id = "deck_os", archived = false))
        database.cardDao().upsert(createCard(id = "card_limit", deckId = "deck_math", archived = false))
        database.cardDao().upsert(createCard(id = "card_process", deckId = "deck_os", archived = false))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_due", cardId = "card_limit", dueAt = 1_000L).copy(prompt = "极限定义是什么"),
                createQuestion(id = "q_future", cardId = "card_limit", dueAt = 9_000L).copy(prompt = "极限有哪些性质"),
                createQuestion(id = "q_other", cardId = "card_process", dueAt = 1_000L).copy(prompt = "进程状态有哪些")
            )
        )
        val candidateQuestionIds = listOf(
            QuestionSearchTokenEntity(questionId = "q_due", token = "极限"),
            QuestionSearchTokenEntity(questionId = "q_future", token = "极限")
        ).also { tokens ->
            database.questionSearchTokenDao().insertAll(tokens)
        }.let {
            database.questionSearchTokenDao().listByTokens(tokens = listOf("极限"))
                .map { token -> token.questionId }
                .distinct()
        }

        val rows = database.questionDao().listQuestionContexts(
            keyword = "极限",
            tagKeyword = null,
            status = QuestionEntity.STATUS_ACTIVE,
            deckId = "deck_math",
            cardId = null,
            maxDueAt = 2_000L,
            includeAllQuestionIds = false,
            questionIds = candidateQuestionIds
        )

        assertEquals(1, rows.size)
        assertEquals("deck_math", rows.first().deckId)
        assertEquals("card_limit", rows.first().cardId)
        assertEquals("极限定义是什么", rows.first().prompt)
    }

    /**
     * 评分事务必须同时更新 Question 与新增 ReviewRecord，
     * 否则用户会看到阶段变化了却没有复习历史的半成功状态。
     */
    @Test
    fun submitRating_updatesQuestionAndCreatesReviewRecord() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_1", archived = false))
        database.cardDao().upsert(createCard(id = "card_1", deckId = "deck_1", archived = false))
        database.questionDao().upsertAll(listOf(createQuestion(id = "question_1", cardId = "card_1", dueAt = 1_000L)))

        val repository = OfflineReviewRepository(
            database = database,
            questionDao = database.questionDao(),
            reviewRecordDao = database.reviewRecordDao(),
            reviewScheduler = ReviewSchedulerV1(),
            dispatchers = DefaultAppDispatchers(),
            syncChangeRecorder = LanSyncChangeRecorder(
                syncChangeDao = database.syncChangeDao(),
                crypto = LanSyncCrypto()
            )
        )

        val submission = repository.submitRating(
            questionId = "question_1",
            rating = ReviewRating.GOOD,
            reviewedAtEpochMillis = 10_000L,
            responseTimeMs = 800L
        )

        val updatedQuestion = database.questionDao().findById("question_1")
        val reviewRecords = database.reviewRecordDao().listAll()

        assertNotNull(updatedQuestion)
        assertEquals(1, updatedQuestion?.stageIndex)
        assertEquals(1, updatedQuestion?.reviewCount)
        assertEquals(1, reviewRecords.size)
        assertEquals(submission.reviewRecord.id, reviewRecords.first().id)
    }

    /**
     * 统计页依赖评分分布与卡组拆分聚合，必须验证 AGAIN 比例和平均耗时不会在数据库层被算错。
     */
    @Test
    fun reviewAnalytics_aggregatesDistributionAndDeckBreakdown() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_math", archived = false))
        database.cardDao().upsert(createCard(id = "card_limit", deckId = "deck_math", archived = false))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_1", cardId = "card_limit", dueAt = 1_000L),
                createQuestion(id = "q_2", cardId = "card_limit", dueAt = 1_000L)
            )
        )
        database.reviewRecordDao().insertAll(
            listOf(
                createReviewRecord(id = "rr_1", questionId = "q_1", rating = ReviewRating.AGAIN, reviewedAt = 3_000L, responseTimeMs = 900L),
                createReviewRecord(id = "rr_2", questionId = "q_2", rating = ReviewRating.GOOD, reviewedAt = 4_000L, responseTimeMs = 1_500L)
            )
        )

        val summary = database.reviewRecordDao().getReviewAnalytics(startEpochMillis = null)
        val deckBreakdowns = database.reviewRecordDao().listDeckReviewAnalytics(startEpochMillis = null)

        assertEquals(2, summary.totalReviews)
        assertEquals(1, summary.againCount)
        assertEquals(1, summary.goodCount)
        assertEquals(1, deckBreakdowns.size)
        assertEquals("deck_math", deckBreakdowns.first().deckId)
        assertEquals(1, deckBreakdowns.first().againCount)
    }

    /**
     * 回收站里的归档卡组必须保留题卡统计，
     * 否则用户在恢复前无法判断这个归档项是否值得保留。
     */
    @Test
    fun observeArchivedDeckSummaries_returnsArchivedDecksWithCounts() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_archived", archived = true))
        database.cardDao().upsert(createCard(id = "card_archived", deckId = "deck_archived", archived = false))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_due", cardId = "card_archived", dueAt = 1_000L),
                createQuestion(id = "q_future", cardId = "card_archived", dueAt = 9_000L)
            )
        )

        val summaries = database.deckDao().observeArchivedDeckSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = 2_000L
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("deck_archived", summaries.first().id)
        assertEquals(1, summaries.first().cardCount)
        assertEquals(2, summaries.first().questionCount)
        assertEquals(1, summaries.first().dueQuestionCount)
    }

    /**
     * 首页最近卡组必须优先展示今天仍有到期题目的卡组，
     * 否则用户会在第一屏看不到真正该先处理的内容入口。
     */
    @Test
    fun listRecentActiveDeckSummaries_prioritizesDueDecksThenRecentlyUpdatedDecks() = runBlocking {
        database.deckDao().upsert(
            createDeck(id = "deck_old_due", archived = false).copy(updatedAt = 10L)
        )
        database.deckDao().upsert(
            createDeck(id = "deck_recent_clear", archived = false).copy(updatedAt = 90L)
        )
        database.deckDao().upsert(
            createDeck(id = "deck_latest_due", archived = false).copy(updatedAt = 120L)
        )
        database.cardDao().upsert(createCard(id = "card_old_due", deckId = "deck_old_due", archived = false))
        database.cardDao().upsert(createCard(id = "card_recent_clear", deckId = "deck_recent_clear", archived = false))
        database.cardDao().upsert(createCard(id = "card_latest_due", deckId = "deck_latest_due", archived = false))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_old_due", cardId = "card_old_due", dueAt = 1_000L),
                createQuestion(id = "q_recent_future", cardId = "card_recent_clear", dueAt = 9_000L),
                createQuestion(id = "q_latest_due", cardId = "card_latest_due", dueAt = 1_500L)
            )
        )

        val summaries = database.deckDao().listRecentActiveDeckSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = 2_000L,
            limit = 3
        )

        assertEquals(
            listOf("deck_latest_due", "deck_old_due", "deck_recent_clear"),
            summaries.map { row -> row.id }
        )
    }

    /**
     * 归档卡片列表必须带出所属卡组名称与题量，
     * 否则回收站无法给恢复动作提供足够上下文。
     */
    @Test
    fun observeArchivedCardSummaries_returnsDeckContextAndCounts() = runBlocking {
        database.deckDao().upsert(createDeck(id = "deck_math", archived = false).copy(name = "数学"))
        database.cardDao().upsert(createCard(id = "card_archived", deckId = "deck_math", archived = true))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_due", cardId = "card_archived", dueAt = 1_000L),
                createQuestion(id = "q_future", cardId = "card_archived", dueAt = 9_000L)
            )
        )

        val summaries = database.cardDao().observeArchivedCardSummaries(
            activeStatus = QuestionEntity.STATUS_ACTIVE,
            nowEpochMillis = 2_000L
        ).first()

        assertEquals(1, summaries.size)
        assertEquals("card_archived", summaries.first().id)
        assertEquals("数学", summaries.first().deckName)
        assertEquals(2, summaries.first().questionCount)
        assertEquals(1, summaries.first().dueQuestionCount)
    }

    /**
     * 删除单张卡片后必须级联清理问题与复习记录，
     * 否则回收站和统计页会继续读到失效的下层数据。
     */
    @Test
    fun deleteCard_cascadesQuestionsAndReviewRecords() = runBlocking {
        val deck = createDeck(id = "deck_keep", archived = false)
        val card = createCard(id = "card_delete", deckId = deck.id, archived = false)
        val question = createQuestion(id = "question_delete", cardId = card.id, dueAt = 1_000L)
        val record = ReviewRecordEntity(
            id = "record_delete",
            questionId = question.id,
            rating = ReviewRating.GOOD.name,
            oldStageIndex = 0,
            newStageIndex = 1,
            oldDueAt = 1_000L,
            newDueAt = 2_000L,
            reviewedAt = 1_500L,
            responseTimeMs = 600L,
            note = ""
        )

        database.deckDao().upsert(deck)
        database.cardDao().upsert(card)
        database.questionDao().upsertAll(listOf(question))
        database.reviewRecordDao().insert(record)

        database.cardDao().deleteById(card.id)

        assertNull(database.questionDao().findById(question.id))
        assertEquals(0, database.reviewRecordDao().listAll().size)
        assertNotNull(database.deckDao().findById(deck.id))
    }

    /**
     * 删除顶层卡组后必须级联清理全部下层数据，
     * 否则恢复和统计查询都会受到失效外键的影响。
     */
    @Test
    fun deleteDeck_cascadesCardsQuestionsAndReviewRecords() = runBlocking {
        val deck = createDeck(id = "deck_delete", archived = false)
        val card = createCard(id = "card_delete", deckId = deck.id, archived = false)
        val question = createQuestion(id = "question_delete", cardId = card.id, dueAt = 1_000L)
        val record = ReviewRecordEntity(
            id = "record_delete",
            questionId = question.id,
            rating = ReviewRating.GOOD.name,
            oldStageIndex = 0,
            newStageIndex = 1,
            oldDueAt = 1_000L,
            newDueAt = 2_000L,
            reviewedAt = 1_500L,
            responseTimeMs = 600L,
            note = ""
        )

        database.deckDao().upsert(deck)
        database.cardDao().upsert(card)
        database.questionDao().upsertAll(listOf(question))
        database.reviewRecordDao().insert(record)

        database.deckDao().deleteById(deck.id)

        assertNull(database.cardDao().findById(card.id))
        assertNull(database.questionDao().findById(question.id))
        assertEquals(0, database.reviewRecordDao().listAll().size)
    }

    /**
     * 测试数据使用最小合法结构，可让断言聚焦在关系与事务而不是无关字段拼装。
     */
    private fun createDeck(id: String, archived: Boolean): DeckEntity = DeckEntity(
        id = id,
        name = id,
        description = "",
        tagsJson = "[]",
        intervalStepCount = 8,
        archived = archived,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 卡片辅助构造函数固定公共字段，可减少每个测试对样板数据的重复维护。
     */
    private fun createCard(id: String, deckId: String, archived: Boolean): CardEntity = CardEntity(
        id = id,
        deckId = deckId,
        title = id,
        description = "",
        archived = archived,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 问题辅助构造函数显式保留调度字段，是为了让首页统计与评分事务都运行在真实规则数据上。
     */
    private fun createQuestion(id: String, cardId: String, dueAt: Long): QuestionEntity = QuestionEntity(
        id = id,
        cardId = cardId,
        prompt = id,
        answer = "",
        tagsJson = "[]",
        status = QuestionEntity.STATUS_ACTIVE,
        stageIndex = 0,
        dueAt = dueAt,
        lastReviewedAt = null,
        reviewCount = 0,
        lapseCount = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 复习记录辅助构造函数显式保留评分与耗时字段，是为了让统计聚合测试聚焦在查询口径本身。
     */
    private fun createReviewRecord(
        id: String,
        questionId: String,
        rating: ReviewRating,
        reviewedAt: Long,
        responseTimeMs: Long
    ): ReviewRecordEntity = ReviewRecordEntity(
        id = id,
        questionId = questionId,
        rating = rating.name,
        oldStageIndex = 0,
        newStageIndex = 1,
        oldDueAt = 1_000L,
        newDueAt = 2_000L,
        reviewedAt = reviewedAt,
        responseTimeMs = responseTimeMs,
        note = ""
    )
}
