package com.kariscode.yike.data.repository

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.sync.FixedTimeProvider
import com.kariscode.yike.data.sync.createTestSyncChangeRecorder
import com.kariscode.yike.data.local.db.dao.ArchivedCardSummaryRow
import com.kariscode.yike.data.local.db.dao.CardDao
import com.kariscode.yike.data.local.db.dao.CardSummaryRow
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.testsupport.testCardEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OfflineCardRepository 测试验证仓储层的映射和委托逻辑，
 * 确保 DAO 结果正确转换为领域模型，以及写入操作正确委托到 DAO。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineCardRepositoryTest {

    private lateinit var fakeDao: FakeCardDao
    private lateinit var repository: OfflineCardRepository

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        fakeDao = FakeCardDao()
        repository = OfflineCardRepository(
            cardDao = fakeDao,
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            },
            timeProvider = FixedTimeProvider(now = 999L),
            syncChangeRecorder = createTestSyncChangeRecorder()
        )
    }

    // ---- observeActiveCards ----

    /**
     * 观察式查询必须将 Entity 正确映射为领域模型，
     * 否则 UI 层会收到未转换的数据或字段错位。
     */
    @Test
    fun observeActiveCards_mapsEntitiesToDomainModels() = runTest {
        val entity = testCardEntity(id = "card_1", deckId = "deck_1")
        fakeDao.activeCardsFlow.value = listOf(entity)

        val cards = repository.observeActiveCards("deck_1").first()

        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals("card_1", card.id)
        assertEquals("deck_1", card.deckId)
        assertEquals("card_1", card.title)
    }

    /**
     * 空列表场景必须正常传递，避免映射逻辑对空集合抛异常。
     */
    @Test
    fun observeActiveCards_emptyList_returnsEmptyFlow() = runTest {
        fakeDao.activeCardsFlow.value = emptyList()

        val cards = repository.observeActiveCards("deck_1").first()

        assertTrue(cards.isEmpty())
    }

    // ---- listActiveCards ----

    /**
     * 快照查询需要与观察查询保持相同的映射口径，
     * 否则搜索初始化和列表展示会出现不一致的数据。
     */
    @Test
    fun listActiveCards_mapsEntitiesToDomainModels() = runTest {
        val entities = listOf(
            testCardEntity(id = "card_a", deckId = "deck_1"),
            testCardEntity(id = "card_b", deckId = "deck_1")
        )
        fakeDao.activeCardsList = entities

        val cards = repository.listActiveCards("deck_1")

        assertEquals(2, cards.size)
        assertEquals("card_a", cards[0].id)
        assertEquals("card_b", cards[1].id)
    }

    @Test
    fun listActiveCards_emptyDeck_returnsEmptyList() = runTest {
        fakeDao.activeCardsList = emptyList()

        val cards = repository.listActiveCards("deck_nonexistent")

        assertTrue(cards.isEmpty())
    }

    // ---- findById ----

    /**
     * 按 id 查找存在的卡片必须返回正确映射的领域模型，
     * 编辑页依赖此方法从路由参数重建表单状态。
     */
    @Test
    fun findById_existingCard_returnsMappedDomainModel() = runTest {
        val entity = testCardEntity(id = "card_1", deckId = "deck_1")
        fakeDao.storedCards["card_1"] = entity

        val card = repository.findById("card_1")

        assertNotNull(card)
        assertEquals("card_1", card!!.id)
        assertEquals("deck_1", card.deckId)
        assertEquals("card_1", card.title)
        assertEquals(0, card.sortOrder)
    }

    /**
     * 查找不存在的卡片必须返回 null，避免上层因非空断言崩溃。
     */
    @Test
    fun findById_nonExistentCard_returnsNull() = runTest {
        val card = repository.findById("missing_card")

        assertNull(card)
    }

    // ---- upsert ----

    /**
     * Upsert 必须将领域模型正确转换为 Entity 写入 DAO，
     * 否则新建或编辑后持久化的数据与用户输入不一致。
     */
    @Test
    fun upsert_delegatesToDaoWithCorrectEntity() = runTest {
        val card = Card(
            id = "card_new",
            deckId = "deck_1",
            title = "新卡片",
            description = "描述",
            archived = false,
            sortOrder = 2,
            createdAt = 100L,
            updatedAt = 200L
        )

        repository.upsert(card)

        val saved = fakeDao.storedCards["card_new"]
        assertNotNull(saved)
        assertEquals("card_new", saved!!.id)
        assertEquals("deck_1", saved.deckId)
        assertEquals("新卡片", saved.title)
        assertEquals("描述", saved.description)
        assertEquals(false, saved.archived)
        assertEquals(2, saved.sortOrder)
        assertEquals(100L, saved.createdAt)
        assertEquals(200L, saved.updatedAt)
    }

    /**
     * Upsert 同一 id 应覆盖已有数据，确保编辑场景下更新生效。
     */
    @Test
    fun upsert_existingCard_overwritesPreviousData() = runTest {
        val original = Card(
            id = "card_1", deckId = "deck_1", title = "原标题",
            description = "", archived = false, sortOrder = 0,
            createdAt = 100L, updatedAt = 100L
        )
        repository.upsert(original)

        val updated = original.copy(title = "新标题", updatedAt = 200L)
        repository.upsert(updated)

        val saved = fakeDao.storedCards["card_1"]
        assertEquals("新标题", saved!!.title)
        assertEquals(200L, saved.updatedAt)
    }

    // ---- setArchived ----

    /**
     * 归档操作必须正确委托到 DAO 层，字段值传递不能错位。
     */
    @Test
    fun setArchived_delegatesToDaoWithCorrectParameters() = runTest {
        repository.setArchived(cardId = "card_1", archived = true, updatedAt = 500L)

        val call = fakeDao.archivedCalls.single()
        assertEquals("card_1", call.cardId)
        assertEquals(true, call.archived)
        assertEquals(500L, call.updatedAt)
    }

    // ---- delete ----

    /**
     * 删除操作必须将正确的 cardId 传递给 DAO，
     * 避免误删其他卡片或因参数错位导致删除无效。
     */
    @Test
    fun delete_delegatesToDaoWithCorrectId() = runTest {
        fakeDao.storedCards["card_1"] = testCardEntity(id = "card_1", deckId = "deck_1")

        repository.delete("card_1")

        assertTrue(fakeDao.deletedIds.contains("card_1"))
    }

    // ---- observeActiveCardSummaries ----

    /**
     * 摘要观察查询必须正确传递参数并映射聚合行为领域模型，
     * 否则卡片列表的统计展示会与实际数据不符。
     */
    @Test
    fun observeActiveCardSummaries_mapsSummaryRowsToDomainModels() = runTest {
        val row = CardSummaryRow(
            id = "card_1", deckId = "deck_1", title = "卡片标题",
            description = "描述", archived = false, sortOrder = 0,
            createdAt = 100L, updatedAt = 200L,
            questionCount = 5, dueQuestionCount = 2
        )
        fakeDao.summaryRowsFlow.value = listOf(row)

        val summaries = repository.observeActiveCardSummaries("deck_1", 1000L).first()

        assertEquals(1, summaries.size)
        val summary = summaries.first()
        assertEquals("card_1", summary.card.id)
        assertEquals(5, summary.questionCount)
        assertEquals(2, summary.dueQuestionCount)
    }

    /**
     * 回收站摘要需要携带所属卡组名称，
     * 否则页面恢复卡片时无法向用户交代恢复后的归属上下文。
     */
    @Test
    fun observeArchivedCardSummaries_mapsRowsToDomainModels() = runTest {
        val row = ArchivedCardSummaryRow(
            id = "card_2",
            deckId = "deck_9",
            deckName = "英语",
            title = "现在完成时",
            description = "",
            archived = true,
            sortOrder = 0,
            createdAt = 100L,
            updatedAt = 200L,
            questionCount = 3,
            dueQuestionCount = 1
        )
        fakeDao.archivedSummaryRowsFlow.value = listOf(row)

        val summaries = repository.observeArchivedCardSummaries(1000L).first()

        assertEquals(1, summaries.size)
        assertEquals("deck_9", summaries.first().card.deckId)
        assertEquals("英语", summaries.first().deckName)
    }

    /**
     * FakeCardDao 记录写入操作并返回预设数据，
     * 使测试可以验证 Repository 层的映射和委托逻辑而不依赖 Room。
     */
    private class FakeCardDao : CardDao {
        val storedCards = mutableMapOf<String, CardEntity>()
        val activeCardsFlow = MutableStateFlow<List<CardEntity>>(emptyList())
        var activeCardsList: List<CardEntity> = emptyList()
        val summaryRowsFlow = MutableStateFlow<List<CardSummaryRow>>(emptyList())
        val archivedSummaryRowsFlow = MutableStateFlow<List<ArchivedCardSummaryRow>>(emptyList())
        val archivedCalls = mutableListOf<ArchivedCall>()
        val deletedIds = mutableListOf<String>()

        data class ArchivedCall(val cardId: String, val archived: Boolean, val updatedAt: Long)

        override suspend fun upsert(card: CardEntity): Long {
            storedCards[card.id] = card
            return 1L
        }

        override fun observeActiveCards(deckId: String): Flow<List<CardEntity>> = activeCardsFlow

        override suspend fun listActiveCards(deckId: String): List<CardEntity> = activeCardsList

        override suspend fun listAll(): List<CardEntity> = storedCards.values.toList()

        override fun observeActiveCardSummaries(
            deckId: String,
            activeStatus: String,
            nowEpochMillis: Long
        ): Flow<List<CardSummaryRow>> = summaryRowsFlow

        override fun observeArchivedCardSummaries(
            activeStatus: String,
            nowEpochMillis: Long
        ): Flow<List<ArchivedCardSummaryRow>> = archivedSummaryRowsFlow

        override suspend fun findById(cardId: String): CardEntity? = storedCards[cardId]

        override suspend fun upsertAll(cards: List<CardEntity>): List<Long> {
            cards.forEach { storedCards[it.id] = it }
            return cards.map { 1L }
        }

        override suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long): Int {
            archivedCalls.add(ArchivedCall(cardId, archived, updatedAt))
            return 1
        }

        override suspend fun deleteById(cardId: String): Int {
            deletedIds.add(cardId)
            storedCards.remove(cardId)
            return 1
        }

        override suspend fun clearAll(): Int {
            val count = storedCards.size
            storedCards.clear()
            return count
        }
    }
}

