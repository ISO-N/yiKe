package com.kariscode.yike.data.local.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kariscode.yike.core.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.repository.OfflineReviewRepository
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
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
            dispatchers = DefaultAppDispatchers()
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

        database.deckDao().delete(deck)

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
}
