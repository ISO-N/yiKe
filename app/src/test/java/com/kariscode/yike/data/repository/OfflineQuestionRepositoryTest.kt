package com.kariscode.yike.data.repository

import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.dao.DeckMasterySummaryRow
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.local.db.dao.TodayReviewSummaryRow
import com.kariscode.yike.data.local.db.dao.QuestionContextRow
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.sync.FixedTimeProvider
import com.kariscode.yike.data.sync.createTestSyncChangeRecorder
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionStatus
import com.kariscode.yike.testsupport.testQuestionEntity
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
 * OfflineQuestionRepository 测试验证仓储层的映射和委托逻辑，
 * 确保 DAO 结果正确转换为领域模型，以及写入操作正确委托到 DAO。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineQuestionRepositoryTest {

    private lateinit var fakeDao: FakeQuestionDao
    private lateinit var repository: OfflineQuestionRepository

    @Before
    fun setUp() {
        val testDispatcher = UnconfinedTestDispatcher()
        fakeDao = FakeQuestionDao()
        repository = OfflineQuestionRepository(
            questionDao = fakeDao,
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = testDispatcher
                override val io: CoroutineDispatcher = testDispatcher
                override val default: CoroutineDispatcher = testDispatcher
            },
            timeProvider = FixedTimeProvider(now = 999L),
            syncChangeRecorder = createTestSyncChangeRecorder()
        )
    }

    // ---- observeQuestionsByCard ----

    /**
     * 观察式查询必须将 Entity 正确映射为领域模型，
     * 否则编辑页会收到未转换的数据或字段错位。
     */
    @Test
    fun observeQuestionsByCard_mapsEntitiesToDomainModels() = runTest {
        val entity = testQuestionEntity(id = "q_1", cardId = "card_1")
        fakeDao.questionsByCardFlow.value = listOf(entity)

        val questions = repository.observeQuestionsByCard("card_1").first()

        assertEquals(1, questions.size)
        val q = questions.first()
        assertEquals("q_1", q.id)
        assertEquals("card_1", q.cardId)
        assertEquals("q_1", q.prompt)
        assertEquals(QuestionStatus.ACTIVE, q.status)
    }

    /**
     * 空列表场景必须正常传递，避免映射逻辑对空集合抛异常。
     */
    @Test
    fun observeQuestionsByCard_emptyList_returnsEmptyFlow() = runTest {
        fakeDao.questionsByCardFlow.value = emptyList()

        val questions = repository.observeQuestionsByCard("card_1").first()

        assertTrue(questions.isEmpty())
    }

    /**
     * 多条问题需要保持顺序和全部正确映射。
     */
    @Test
    fun observeQuestionsByCard_multipleEntities_mapsAllCorrectly() = runTest {
        fakeDao.questionsByCardFlow.value = listOf(
            testQuestionEntity(id = "q_1", cardId = "card_1"),
            testQuestionEntity(id = "q_2", cardId = "card_1")
        )

        val questions = repository.observeQuestionsByCard("card_1").first()

        assertEquals(2, questions.size)
        assertEquals("q_1", questions[0].id)
        assertEquals("q_2", questions[1].id)
    }

    // ---- findById ----

    /**
     * 按 id 查找存在的问题必须返回正确映射的领域模型，
     * 评分提交依赖此方法精确定位待评分的问题。
     */
    @Test
    fun findById_existingQuestion_returnsMappedDomainModel() = runTest {
        val entity = testQuestionEntity(id = "q_1", cardId = "card_1")
        fakeDao.storedQuestions["q_1"] = entity

        val question = repository.findById("q_1")

        assertNotNull(question)
        assertEquals("q_1", question!!.id)
        assertEquals("card_1", question.cardId)
        assertEquals("q_1", question.prompt)
        assertEquals(QuestionStatus.ACTIVE, question.status)
        assertEquals(0, question.stageIndex)
        assertEquals(1_000L, question.dueAt)
    }

    /**
     * 查找不存在的问题必须返回 null，避免上层因非空断言崩溃。
     */
    @Test
    fun findById_nonExistentQuestion_returnsNull() = runTest {
        val question = repository.findById("missing_q")

        assertNull(question)
    }

    /**
     * tags JSON 必须被正确解码为 List<String>。
     */
    @Test
    fun findById_decodesTagsJsonCorrectly() = runTest {
        val entity = testQuestionEntity(id = "q_tags", cardId = "card_1")
            .copy(tagsJson = """["kotlin","android"]""")
        fakeDao.storedQuestions["q_tags"] = entity

        val question = repository.findById("q_tags")

        assertNotNull(question)
        assertEquals(listOf("kotlin", "android"), question!!.tags)
    }

    // ---- listByCard ----

    /**
     * 快照查询需要与观察查询保持相同的映射口径，
     * 否则编辑页载入和保存后会出现不一致的数据。
     */
    @Test
    fun listByCard_mapsEntitiesToDomainModels() = runTest {
        fakeDao.listByCardResult = listOf(
            testQuestionEntity(id = "q_a", cardId = "card_1"),
            testQuestionEntity(id = "q_b", cardId = "card_1")
        )

        val questions = repository.listByCard("card_1")

        assertEquals(2, questions.size)
        assertEquals("q_a", questions[0].id)
        assertEquals("q_b", questions[1].id)
    }

    @Test
    fun listByCard_emptyCard_returnsEmptyList() = runTest {
        fakeDao.listByCardResult = emptyList()

        val questions = repository.listByCard("card_nonexistent")

        assertTrue(questions.isEmpty())
    }

    // ---- upsertAll ----

    /**
     * 批量写入必须将领域模型正确转换为 Entity 写入 DAO，
     * 否则编辑页一次保存后持久化的数据与用户输入不一致。
     */
    @Test
    fun upsertAll_delegatesToDaoWithCorrectEntities() = runTest {
        val questions = listOf(
            Question(
                id = "q_new_1",
                cardId = "card_1",
                prompt = "什么是 Kotlin",
                answer = "一种编程语言",
                tags = listOf("语言", "JVM"),
                status = QuestionStatus.ACTIVE,
                stageIndex = 0,
                dueAt = 5_000L,
                lastReviewedAt = null,
                reviewCount = 0,
                lapseCount = 0,
                createdAt = 100L,
                updatedAt = 200L
            ),
            Question(
                id = "q_new_2",
                cardId = "card_1",
                prompt = "什么是 Compose",
                answer = "声明式 UI 框架",
                tags = emptyList(),
                status = QuestionStatus.ACTIVE,
                stageIndex = 1,
                dueAt = 6_000L,
                lastReviewedAt = 3_000L,
                reviewCount = 1,
                lapseCount = 0,
                createdAt = 100L,
                updatedAt = 300L
            )
        )

        repository.upsertAll(questions)

        assertEquals(2, fakeDao.storedQuestions.size)
        val saved1 = fakeDao.storedQuestions["q_new_1"]!!
        assertEquals("什么是 Kotlin", saved1.prompt)
        assertEquals("一种编程语言", saved1.answer)
        assertEquals("""["语言","JVM"]""", saved1.tagsJson)
        assertEquals(QuestionEntity.STATUS_ACTIVE, saved1.status)
        assertEquals(0, saved1.stageIndex)

        val saved2 = fakeDao.storedQuestions["q_new_2"]!!
        assertEquals("什么是 Compose", saved2.prompt)
        assertEquals(1, saved2.stageIndex)
        assertEquals(3_000L, saved2.lastReviewedAt)
    }

    /**
     * 空列表 upsert 不应抛异常。
     */
    @Test
    fun upsertAll_emptyList_doesNotThrow() = runTest {
        repository.upsertAll(emptyList())

        assertTrue(fakeDao.storedQuestions.isEmpty())
    }

    /**
     * archived 状态的 Question 必须被正确编码为 Entity 的 status 字段。
     */
    @Test
    fun upsertAll_archivedStatus_encodedCorrectly() = runTest {
        val archivedQuestion = Question(
            id = "q_archived",
            cardId = "card_1",
            prompt = "已归档",
            answer = "",
            tags = emptyList(),
            status = QuestionStatus.ARCHIVED,
            stageIndex = 0,
            dueAt = 1_000L,
            lastReviewedAt = null,
            reviewCount = 0,
            lapseCount = 0,
            createdAt = 1L,
            updatedAt = 1L
        )

        repository.upsertAll(listOf(archivedQuestion))

        val saved = fakeDao.storedQuestions["q_archived"]!!
        assertEquals(QuestionEntity.STATUS_ARCHIVED, saved.status)
    }

    // ---- listDueQuestions ----

    /**
     * due 查询必须传递正确的 activeStatus 参数并正确映射返回结果。
     */
    @Test
    fun listDueQuestions_mapsDueEntitiesToDomainModels() = runTest {
        fakeDao.dueQuestions = listOf(
            testQuestionEntity(id = "q_due_1", cardId = "card_1"),
            testQuestionEntity(id = "q_due_2", cardId = "card_2")
        )

        val questions = repository.listDueQuestions(nowEpochMillis = 2_000L)

        assertEquals(2, questions.size)
        assertEquals("q_due_1", questions[0].id)
        assertEquals("q_due_2", questions[1].id)
    }

    /**
     * 没有到期问题时必须返回空列表。
     */
    @Test
    fun listDueQuestions_noDueQuestions_returnsEmptyList() = runTest {
        fakeDao.dueQuestions = emptyList()

        val questions = repository.listDueQuestions(nowEpochMillis = 2_000L)

        assertTrue(questions.isEmpty())
    }

    /**
     * 必须使用 STATUS_ACTIVE 作为 activeStatus 参数调用 DAO。
     */
    @Test
    fun listDueQuestions_passesActiveStatusToDao() = runTest {
        fakeDao.dueQuestions = emptyList()

        repository.listDueQuestions(nowEpochMillis = 5_000L)

        assertEquals(QuestionEntity.STATUS_ACTIVE, fakeDao.lastDueActiveStatus)
        assertEquals(5_000L, fakeDao.lastDueNowEpochMillis)
    }

    // ---- findNextDueCardId ----

    /**
     * 下一卡片查询存在到期问题时应返回对应的 cardId。
     */
    @Test
    fun findNextDueCardId_hasDueQuestions_returnsCardId() = runTest {
        fakeDao.nextDueCardId = "card_next"

        val cardId = repository.findNextDueCardId(nowEpochMillis = 2_000L)

        assertEquals("card_next", cardId)
    }

    /**
     * 没有到期问题时必须返回 null。
     */
    @Test
    fun findNextDueCardId_noDueQuestions_returnsNull() = runTest {
        fakeDao.nextDueCardId = null

        val cardId = repository.findNextDueCardId(nowEpochMillis = 2_000L)

        assertNull(cardId)
    }

    /**
     * 必须使用 STATUS_ACTIVE 作为 activeStatus 参数调用 DAO。
     */
    @Test
    fun findNextDueCardId_passesActiveStatusToDao() = runTest {
        fakeDao.nextDueCardId = null

        repository.findNextDueCardId(nowEpochMillis = 8_000L)

        assertEquals(QuestionEntity.STATUS_ACTIVE, fakeDao.lastNextDueActiveStatus)
        assertEquals(8_000L, fakeDao.lastNextDueNowEpochMillis)
    }

    // ---- getTodayReviewSummary ----

    /**
     * 统计查询必须正确映射 DAO 返回的聚合行为领域模型，
     * 首页概览与提醒依赖此方法展示正确的待复习数量。
     */
    @Test
    fun getTodayReviewSummary_mapsSummaryRowToDomainModel() = runTest {
        fakeDao.todayReviewSummaryRow = TodayReviewSummaryRow(
            dueCardCount = 3,
            dueQuestionCount = 10
        )

        val summary = repository.getTodayReviewSummary(nowEpochMillis = 5_000L)

        assertEquals(3, summary.dueCardCount)
        assertEquals(10, summary.dueQuestionCount)
    }

    /**
     * 没有到期数据时返回零计数。
     */
    @Test
    fun getTodayReviewSummary_noDueData_returnsZeroCounts() = runTest {
        fakeDao.todayReviewSummaryRow = TodayReviewSummaryRow(
            dueCardCount = 0,
            dueQuestionCount = 0
        )

        val summary = repository.getTodayReviewSummary(nowEpochMillis = 5_000L)

        assertEquals(0, summary.dueCardCount)
        assertEquals(0, summary.dueQuestionCount)
    }

    /**
     * 必须使用 STATUS_ACTIVE 作为 activeStatus 参数调用 DAO。
     */
    @Test
    fun getTodayReviewSummary_passesActiveStatusToDao() = runTest {
        fakeDao.todayReviewSummaryRow = TodayReviewSummaryRow(0, 0)

        repository.getTodayReviewSummary(nowEpochMillis = 7_000L)

        assertEquals(QuestionEntity.STATUS_ACTIVE, fakeDao.lastSummaryActiveStatus)
        assertEquals(7_000L, fakeDao.lastSummaryNowEpochMillis)
    }

    // ---- delete ----

    /**
     * 删除操作必须将正确的 questionId 传递给 DAO。
     */
    @Test
    fun delete_delegatesToDaoWithCorrectId() = runTest {
        fakeDao.storedQuestions["q_1"] = testQuestionEntity(id = "q_1", cardId = "card_1")

        repository.delete("q_1")

        assertTrue(fakeDao.deletedIds.contains("q_1"))
    }

    @Test
    fun delete_nonExistentId_doesNotThrow() = runTest {
        repository.delete("missing_q")

        assertTrue(fakeDao.deletedIds.contains("missing_q"))
    }

    // ---- deleteAll ----

    /**
     * 批量删除必须将完整的 id 集合传递给 DAO。
     */
    @Test
    fun deleteAll_delegatesToDaoWithCorrectIds() = runTest {
        val ids = listOf("q_1", "q_2", "q_3")

        repository.deleteAll(ids)

        assertEquals(ids, fakeDao.batchDeletedIds)
    }

    /**
     * 空集合不应触发 DAO 调用，避免无意义的数据库往返。
     */
    @Test
    fun deleteAll_emptyCollection_skipsDao() = runTest {
        repository.deleteAll(emptyList())

        assertNull(fakeDao.batchDeletedIds)
    }

    /**
     * 单元素集合也应正确委托。
     */
    @Test
    fun deleteAll_singleElement_delegatesCorrectly() = runTest {
        repository.deleteAll(listOf("q_only"))

        assertEquals(listOf("q_only"), fakeDao.batchDeletedIds)
    }

    /**
     * FakeQuestionDao 记录写入操作并返回预设数据，
     * 使测试可以验证 Repository 层的映射和委托逻辑而不依赖 Room。
     */
    private class FakeQuestionDao : QuestionDao {
        val storedQuestions = mutableMapOf<String, QuestionEntity>()
        val questionsByCardFlow = MutableStateFlow<List<QuestionEntity>>(emptyList())
        var listByCardResult: List<QuestionEntity> = emptyList()
        var dueQuestions: List<QuestionEntity> = emptyList()
        var nextDueCardId: String? = null
        var todayReviewSummaryRow = TodayReviewSummaryRow(0, 0)
        val deletedIds = mutableListOf<String>()
        var batchDeletedIds: List<String>? = null

        var lastDueActiveStatus: String? = null
        var lastDueNowEpochMillis: Long? = null
        var lastNextDueActiveStatus: String? = null
        var lastNextDueNowEpochMillis: Long? = null
        var lastSummaryActiveStatus: String? = null
        var lastSummaryNowEpochMillis: Long? = null

        override suspend fun upsertAll(questions: List<QuestionEntity>): List<Long> {
            questions.forEach { storedQuestions[it.id] = it }
            return questions.map { 1L }
        }

        override fun observeQuestionsByCard(cardId: String): Flow<List<QuestionEntity>> =
            questionsByCardFlow

        override suspend fun listByCard(cardId: String): List<QuestionEntity> = listByCardResult

        override suspend fun listByIds(questionIds: List<String>): List<QuestionEntity> =
            questionIds.mapNotNull(storedQuestions::get)

        override suspend fun findById(questionId: String): QuestionEntity? =
            storedQuestions[questionId]

        override suspend fun findDeckIntervalStepCountByQuestionId(questionId: String): Int? = null

        override suspend fun listQuestionContexts(
            keyword: String?,
            tagKeyword: String?,
            status: String?,
            deckId: String?,
            cardId: String?,
            maxDueAt: Long?,
            includeAllQuestionIds: Boolean,
            questionIds: List<String>
        ): List<QuestionContextRow> = emptyList()

        override suspend fun listTagsJson(activeStatus: String): List<String> = emptyList()

        override suspend fun listCsvExportRows(activeStatus: String): List<com.kariscode.yike.data.local.db.dao.CsvQuestionExportRow> =
            emptyList()

        override suspend fun listUpcomingDueAts(
            activeStatus: String,
            startEpochMillis: Long,
            endEpochMillis: Long
        ): List<Long> = emptyList()

        /**
         * 练习模式查询不在当前仓储测试关注范围内，这里返回空列表即可满足接口约束。
         */
        override suspend fun listPracticeQuestionContexts(
            activeStatus: String,
            includeAllDecks: Boolean,
            deckIds: List<String>,
            includeAllCards: Boolean,
            cardIds: List<String>,
            includeAllQuestions: Boolean,
            questionIds: List<String>
        ): List<QuestionContextRow> = emptyList()

        override suspend fun getDeckMasterySummary(
            deckId: String,
            activeStatus: String
        ): DeckMasterySummaryRow = DeckMasterySummaryRow(
            totalQuestions = 0,
            newCount = 0,
            learningCount = 0,
            familiarCount = 0,
            masteredCount = 0
        )

        override suspend fun listDueQuestionsByCard(
            cardId: String,
            activeStatus: String,
            nowEpochMillis: Long
        ): List<QuestionEntity> = emptyList()

        override suspend fun listDueQuestions(
            activeStatus: String,
            nowEpochMillis: Long
        ): List<QuestionEntity> {
            lastDueActiveStatus = activeStatus
            lastDueNowEpochMillis = nowEpochMillis
            return dueQuestions
        }

        override suspend fun findNextDueCardId(
            activeStatus: String,
            nowEpochMillis: Long
        ): String? {
            lastNextDueActiveStatus = activeStatus
            lastNextDueNowEpochMillis = nowEpochMillis
            return nextDueCardId
        }

        override suspend fun getTodayReviewSummary(
            activeStatus: String,
            nowEpochMillis: Long
        ): TodayReviewSummaryRow {
            lastSummaryActiveStatus = activeStatus
            lastSummaryNowEpochMillis = nowEpochMillis
            return todayReviewSummaryRow
        }

        override suspend fun listAll(): List<QuestionEntity> = storedQuestions.values.toList()

        /**
         * 启动分批重建索引只依赖稳定分页结果，这里直接基于已存数据切片，
         * 能让仓储测试在不引入额外状态的前提下满足最新 DAO 契约。
         */
        override suspend fun listPage(limit: Int, offset: Int): List<QuestionEntity> =
            storedQuestions.values
                .sortedBy(QuestionEntity::createdAt)
                .drop(offset)
                .take(limit)

        override suspend fun deleteById(questionId: String): Int {
            deletedIds.add(questionId)
            storedQuestions.remove(questionId)
            return 1
        }

        override suspend fun deleteByIds(questionIds: Collection<String>): Int {
            batchDeletedIds = questionIds.toList()
            questionIds.forEach { storedQuestions.remove(it) }
            return questionIds.size
        }

        override suspend fun clearAll(): Int {
            val count = storedQuestions.size
            storedQuestions.clear()
            return count
        }
    }
}

