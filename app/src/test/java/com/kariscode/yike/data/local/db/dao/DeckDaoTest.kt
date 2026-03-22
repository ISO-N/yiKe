package com.kariscode.yike.data.local.db.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * DeckDao 测试直接锁定首页最近卡组的 SQL 口径，
 * 是为了防止首页文案修好后查询又悄悄退回按创建顺序取数。
 */
@RunWith(RobolectricTestRunner::class)
class DeckDaoTest {
    private lateinit var database: YikeDatabase
    private lateinit var deckDao: DeckDao

    /**
     * 使用内存数据库执行真实 SQL，能让排序断言覆盖 Room 查询而不是假实现。
     */
    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YikeDatabase::class.java
        ).allowMainThreadQueries().build()
        deckDao = database.deckDao()
    }

    /**
     * 每条用例结束后关闭数据库，是为了避免上一条测试的数据残留影响排序结果。
     */
    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 最近卡组需要把“今天有到期内容”的卡组顶到前面，再按最近维护时间排序，
     * 这样首页第一屏才会优先露出真正该处理的入口。
     */
    @Test
    fun listRecentActiveDeckSummaries_prioritizesDueDecksBeforeRecentlyUpdatedClearDecks() = runTest {
        deckDao.upsert(createDeck(id = "deck_old_due", updatedAt = 10L))
        deckDao.upsert(createDeck(id = "deck_recent_clear", updatedAt = 90L))
        deckDao.upsert(createDeck(id = "deck_latest_due", updatedAt = 120L))
        database.cardDao().upsert(createCard(id = "card_old_due", deckId = "deck_old_due"))
        database.cardDao().upsert(createCard(id = "card_recent_clear", deckId = "deck_recent_clear"))
        database.cardDao().upsert(createCard(id = "card_latest_due", deckId = "deck_latest_due"))
        database.questionDao().upsertAll(
            listOf(
                createQuestion(id = "q_old_due", cardId = "card_old_due", dueAt = 1_000L),
                createQuestion(id = "q_recent_future", cardId = "card_recent_clear", dueAt = 9_000L),
                createQuestion(id = "q_latest_due", cardId = "card_latest_due", dueAt = 1_500L)
            )
        )

        val summaries = deckDao.listRecentActiveDeckSummaries(
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
     * 最小合法卡组数据固定在单处，是为了让排序测试聚焦查询语义而不是样板字段。
     */
    private fun createDeck(id: String, updatedAt: Long): DeckEntity = DeckEntity(
        id = id,
        name = id,
        description = "",
        tagsJson = "[]",
        intervalStepCount = 8,
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = updatedAt
    )

    /**
     * 卡片辅助构造固定无关字段，避免测试为了插入最小层级反复复制模板。
     */
    private fun createCard(id: String, deckId: String): CardEntity = CardEntity(
        id = id,
        deckId = deckId,
        title = id,
        description = "",
        archived = false,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = 1L
    )

    /**
     * 问题辅助构造显式保留 dueAt 与状态，是为了让“是否到期”成为唯一变量。
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
