package com.kariscode.yike.data.repository

import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.DeckDao
import com.kariscode.yike.data.local.db.dao.DeckSummaryRow
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.sync.FixedTimeProvider
import com.kariscode.yike.data.sync.InMemorySyncChangeDao
import com.kariscode.yike.data.sync.createInspectableTestSyncRecorder
import com.kariscode.yike.data.sync.storageValue
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * OfflineDeckRepository 测试聚焦在聚合映射、归档/删除委托和同步 journal 记录，
 * 避免内容管理层最上游的卡组状态在仓储层悄悄漂移。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineDeckRepositoryTest {
    private lateinit var fakeDao: FakeDeckDao
    private lateinit var repository: OfflineDeckRepository
    private lateinit var syncChangeDao: InMemorySyncChangeDao

    /**
     * 仓储使用统一测试 dispatcher 和可检查 journal 的 recorder，
     * 这样每条用例都能同时验证 DAO 委托和同步记录语义。
     */
    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testSyncRecorder = createInspectableTestSyncRecorder()
        fakeDao = FakeDeckDao()
        syncChangeDao = testSyncRecorder.syncChangeDao
        repository = OfflineDeckRepository(
            deckDao = fakeDao,
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            },
            timeProvider = FixedTimeProvider(now = 9_999L),
            syncChangeRecorder = testSyncRecorder.recorder
        )
    }

    /**
     * 活动卡组订阅必须把 Entity 准确映射成领域模型，
     * 否则列表页会在最外层就拿到错位的卡组数据。
     */
    @Test
    fun observeActiveDecks_mapsEntitiesToDomainModels() = runTest {
        fakeDao.activeDecksFlow.value = listOf(
            createDeckEntity(id = "deck_math", name = "数学")
        )

        val decks = repository.observeActiveDecks().first()

        assertEquals(1, decks.size)
        assertEquals("deck_math", decks.first().id)
        assertEquals("数学", decks.first().name)
    }

    /**
     * 摘要订阅必须保留统计字段和卡组元数据，
     * 否则首页和回收站看到的数字会和实际数据口径脱节。
     */
    @Test
    fun observeArchivedDeckSummaries_mapsRowsToDomainModels() = runTest {
        fakeDao.archivedSummaryRowsFlow.value = listOf(
            DeckSummaryRow(
                id = "deck_archive",
                name = "已归档卡组",
                description = "",
                tagsJson = """["历史"]""",
                intervalStepCount = 4,
                archived = true,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 2L,
                cardCount = 2,
                questionCount = 8,
                dueQuestionCount = 3
            )
        )

        val summaries = repository.observeArchivedDeckSummaries(nowEpochMillis = 5_000L).first()

        assertEquals(1, summaries.size)
        assertEquals("deck_archive", summaries.first().deck.id)
        assertEquals(listOf("历史"), summaries.first().deck.tags)
        assertEquals(2, summaries.first().cardCount)
        assertEquals(8, summaries.first().questionCount)
        assertEquals(3, summaries.first().dueQuestionCount)
    }

    /**
     * 单对象读取存在时必须返回完整映射结果，
     * 这样编辑和归档恢复场景才能依赖同一仓储快照。
     */
    @Test
    fun findById_existingDeck_returnsMappedDomainModel() = runTest {
        fakeDao.storedDecks["deck_1"] = createDeckEntity(id = "deck_1", name = "算法")

        val deck = repository.findById("deck_1")

        assertNotNull(deck)
        assertEquals("deck_1", deck!!.id)
        assertEquals("算法", deck.name)
        assertEquals(8, deck.intervalStepCount)
    }

    /**
     * 不存在的卡组必须返回 null，避免页面把缺失数据误认为空白卡组。
     */
    @Test
    fun findById_missingDeck_returnsNull() = runTest {
        val deck = repository.findById("missing")

        assertNull(deck)
    }

    /**
     * upsert 必须同时写 DAO 和同步 journal，
     * 否则本地内容虽然保存成功，后续局域网同步却看不到这次变更。
     */
    @Test
    fun upsert_persistsDeckAndRecordsJournal() = runTest {
        val deck = Deck(
            id = "deck_new",
            name = "英语",
            description = "语法",
            tags = listOf("高频"),
            intervalStepCount = 6,
            archived = false,
            sortOrder = 1,
            createdAt = 10L,
            updatedAt = 20L
        )

        repository.upsert(deck)

        val stored = fakeDao.storedDecks["deck_new"]
        val changes = syncChangeDao.listAfter(afterSeq = 0L)

        assertNotNull(stored)
        assertEquals("英语", stored!!.name)
        assertEquals("""["高频"]""", stored.tagsJson)
        assertEquals(1, changes.size)
        assertEquals(SyncEntityType.DECK.storageValue(), changes.single().entityType)
        assertEquals(SyncChangeOperation.UPSERT.storageValue(), changes.single().operation)
        assertEquals("deck_new", changes.single().entityId)
    }

    /**
     * 归档时必须基于当前快照生成新的同步记录，
     * 否则另一台设备会看不到“只是归档，不是删除”的状态变化。
     */
    @Test
    fun setArchived_existingDeck_recordsUpdatedDeckJournal() = runTest {
        fakeDao.storedDecks["deck_archive"] = createDeckEntity(
            id = "deck_archive",
            name = "历史",
            archived = false,
            updatedAt = 30L
        )

        repository.setArchived(deckId = "deck_archive", archived = true, updatedAt = 88L)

        val call = fakeDao.archivedCalls.single()
        val changes = syncChangeDao.listAfter(afterSeq = 0L)

        assertEquals("deck_archive", call.deckId)
        assertEquals(true, call.archived)
        assertEquals(88L, call.updatedAt)
        assertEquals(1, changes.size)
        assertEquals(SyncChangeOperation.UPSERT.storageValue(), changes.single().operation)
        assertEquals(88L, changes.single().modifiedAt)
    }

    /**
     * 删除时必须写 tombstone，
     * 否则远端设备无法知道这张卡组已经被本地显式删除。
     */
    @Test
    fun delete_recordsDeleteJournalWithCurrentDeckSummary() = runTest {
        fakeDao.storedDecks["deck_delete"] = createDeckEntity(
            id = "deck_delete",
            name = "待删除卡组",
            updatedAt = 77L
        )

        repository.delete("deck_delete")

        val changes = syncChangeDao.listAfter(afterSeq = 0L)

        assertTrue(fakeDao.deletedDeckIds.contains("deck_delete"))
        assertEquals(1, changes.size)
        assertEquals(SyncChangeOperation.DELETE.storageValue(), changes.single().operation)
        assertEquals("待删除卡组", changes.single().summary)
        assertEquals(77L, changes.single().modifiedAt)
    }

    /**
     * 缺失快照时删除仍应记录最小 tombstone，
     * 这样数据库里没有实体也不会让同步层完全失去删除语义。
     */
    @Test
    fun delete_missingDeck_fallsBackToIdAndCurrentTime() = runTest {
        repository.delete("deck_missing")

        val changes = syncChangeDao.listAfter(afterSeq = 0L)

        assertEquals(1, changes.size)
        assertEquals("deck_missing", changes.single().summary)
        assertEquals(9_999L, changes.single().modifiedAt)
    }

    /**
     * FakeDeckDao 记录仓储的读写轨迹，是为了让测试只验证仓储职责而不引入 Room 样板。
     */
    private class FakeDeckDao : DeckDao {
        val storedDecks = linkedMapOf<String, DeckEntity>()
        val activeDecksFlow = MutableStateFlow<List<DeckEntity>>(emptyList())
        val activeSummaryRowsFlow = MutableStateFlow<List<DeckSummaryRow>>(emptyList())
        val archivedSummaryRowsFlow = MutableStateFlow<List<DeckSummaryRow>>(emptyList())
        var activeDecksList: List<DeckEntity> = emptyList()
        var recentSummaryRows: List<DeckSummaryRow> = emptyList()
        val archivedCalls = mutableListOf<ArchivedCall>()
        val deletedDeckIds = mutableListOf<String>()

        /**
         * 归档调用单独结构化记录后，断言不必依赖多个并行列表保持索引一致。
         */
        data class ArchivedCall(
            val deckId: String,
            val archived: Boolean,
            val updatedAt: Long
        )

        override suspend fun upsert(deck: DeckEntity): Long {
            storedDecks[deck.id] = deck
            return 1L
        }

        override fun observeActiveDecks(): Flow<List<DeckEntity>> = activeDecksFlow

        override suspend fun listActiveDecks(): List<DeckEntity> = activeDecksList

        override suspend fun listAll(): List<DeckEntity> = storedDecks.values.toList()

        override fun observeActiveDeckSummaries(
            activeStatus: String,
            nowEpochMillis: Long
        ): Flow<List<DeckSummaryRow>> = activeSummaryRowsFlow

        override fun observeArchivedDeckSummaries(
            activeStatus: String,
            nowEpochMillis: Long
        ): Flow<List<DeckSummaryRow>> = archivedSummaryRowsFlow

        override suspend fun listRecentActiveDeckSummaries(
            activeStatus: String,
            nowEpochMillis: Long,
            limit: Int
        ): List<DeckSummaryRow> = recentSummaryRows.take(limit)

        override suspend fun findById(deckId: String): DeckEntity? = storedDecks[deckId]

        override suspend fun upsertAll(decks: List<DeckEntity>): List<Long> {
            decks.forEach { deck -> storedDecks[deck.id] = deck }
            return decks.map { 1L }
        }

        override suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long): Int {
            archivedCalls += ArchivedCall(deckId = deckId, archived = archived, updatedAt = updatedAt)
            return 1
        }

        override suspend fun deleteById(deckId: String): Int {
            deletedDeckIds += deckId
            storedDecks.remove(deckId)
            return 1
        }

        override suspend fun clearAll(): Int {
            val count = storedDecks.size
            storedDecks.clear()
            return count
        }
    }

    /**
     * 最小合法卡组实体构造器集中在单处，避免每条用例都重复拼装无关字段。
     */
    private fun createDeckEntity(
        id: String,
        name: String,
        archived: Boolean = false,
        updatedAt: Long = 1L
    ): DeckEntity = DeckEntity(
        id = id,
        name = name,
        description = "",
        tagsJson = "[]",
        intervalStepCount = 8,
        archived = archived,
        sortOrder = 0,
        createdAt = 1L,
        updatedAt = updatedAt
    )
}
