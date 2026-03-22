package com.kariscode.yike.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * OfflinePracticeRepository 测试直接验证 Room 查询口径，
 * 是为了守住练习模式“只读、忽略 due、仅包含有效内容”的核心边界。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflinePracticeRepositoryTest {

    private lateinit var database: YikeDatabase
    private lateinit var repository: OfflinePracticeRepository

    @Before
    fun setUp() {
        val dispatcher = UnconfinedTestDispatcher()
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            YikeDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = OfflinePracticeRepository(
            questionDao = database.questionDao(),
            dispatchers = object : AppDispatchers {
                override val main: CoroutineDispatcher = dispatcher
                override val io: CoroutineDispatcher = dispatcher
                override val default: CoroutineDispatcher = dispatcher
            }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 练习模式必须忽略 due 过滤，
     * 否则用户在考前回顾时仍只能看到今天到期的内容，违背主动练习目标。
     */
    @Test
    fun listPracticeQuestionContexts_ignoresDueAtButKeepsVisibleActiveQuestions() = runTest {
        seedHierarchy(deckId = "deck_math", cardId = "card_limit")
        seedQuestion(id = "q_due", cardId = "card_limit", dueAt = 1_000L)
        seedQuestion(id = "q_future", cardId = "card_limit", dueAt = 99_000L)

        val results = repository.listPracticeQuestionContexts(
            PracticeSessionArgs(orderMode = PracticeOrderMode.SEQUENTIAL)
        )

        assertEquals(listOf("q_due", "q_future"), results.map { context -> context.question.id })
    }

    /**
     * 练习范围查询必须排除已归档层级和非 active 题目，
     * 否则设置页与会话页会把用户已经隐藏掉的内容重新带回来。
     */
    @Test
    fun listPracticeQuestionContexts_excludesArchivedAndInactiveContent() = runTest {
        seedHierarchy(deckId = "deck_active", cardId = "card_active")
        seedHierarchy(deckId = "deck_archived", cardId = "card_archived", deckArchived = true)
        seedHierarchy(deckId = "deck_other", cardId = "card_other")
        seedQuestion(id = "q_valid", cardId = "card_active")
        seedQuestion(id = "q_archived_deck", cardId = "card_archived")
        seedQuestion(id = "q_inactive", cardId = "card_active", status = QuestionEntity.STATUS_ARCHIVED)
        seedQuestion(id = "q_other", cardId = "card_other")

        val results = repository.listPracticeQuestionContexts(
            PracticeSessionArgs(deckIds = listOf("deck_active"))
        )

        assertEquals(listOf("q_valid"), results.map { context -> context.question.id })
    }

    /**
     * 只读查询前后数据库快照必须一致，
     * 这样才能明确证明练习模式不会悄悄改动调度字段或生成正式复习记录。
     */
    @Test
    fun listPracticeQuestionContexts_doesNotWriteReviewRecordOrChangeQuestionSchedule() = runTest {
        seedHierarchy(deckId = "deck_focus", cardId = "card_focus")
        seedQuestion(
            id = "q_focus",
            cardId = "card_focus",
            stageIndex = 4,
            dueAt = 88_000L,
            lastReviewedAt = 66_000L,
            reviewCount = 7,
            lapseCount = 2
        )

        repository.listPracticeQuestionContexts(
            PracticeSessionArgs(questionIds = listOf("q_focus"))
        )

        val question = database.questionDao().findById("q_focus")!!
        val reviewRecords = database.reviewRecordDao().listAll()

        assertEquals(4, question.stageIndex)
        assertEquals(88_000L, question.dueAt)
        assertEquals(66_000L, question.lastReviewedAt)
        assertEquals(7, question.reviewCount)
        assertEquals(2, question.lapseCount)
        assertEquals(0, reviewRecords.size)
        assertNull(reviewRecords.firstOrNull())
    }

    /**
     * 当会话显式带入 `questionIds` 时，最终结果必须被严格裁剪，
     * 这样搜索结果或卡片页带入的局部题集才不会在练习中被悄悄放大。
     */
    @Test
    fun listPracticeQuestionContexts_questionIdsStrictlyTrimResults() = runTest {
        seedHierarchy(deckId = "deck_local", cardId = "card_local")
        seedQuestion(id = "q_1", cardId = "card_local")
        seedQuestion(id = "q_2", cardId = "card_local")
        seedQuestion(id = "q_3", cardId = "card_local")

        val results = repository.listPracticeQuestionContexts(
            PracticeSessionArgs(questionIds = listOf("q_1", "q_3"))
        )

        assertEquals(listOf("q_1", "q_3"), results.map { context -> context.question.id })
    }

    /**
     * 层级辅助方法统一写入最小合法数据，是为了让每条断言聚焦在练习口径而不是建表样板。
     */
    private suspend fun seedHierarchy(
        deckId: String,
        cardId: String,
        deckArchived: Boolean = false,
        cardArchived: Boolean = false
    ) {
        database.deckDao().upsert(
            DeckEntity(
                id = deckId,
                name = deckId,
                description = "",
                tagsJson = "[]",
                intervalStepCount = 8,
                archived = deckArchived,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.cardDao().upsert(
            CardEntity(
                id = cardId,
                deckId = deckId,
                title = cardId,
                description = "",
                archived = cardArchived,
                sortOrder = 0,
                createdAt = 1L,
                updatedAt = 1L
            )
        )
    }

    /**
     * 题目辅助方法允许覆盖调度字段，是为了让只读边界测试能直接对比调用前后的完整快照。
     */
    private suspend fun seedQuestion(
        id: String,
        cardId: String,
        stageIndex: Int = 0,
        dueAt: Long = 1_000L,
        lastReviewedAt: Long? = null,
        reviewCount: Int = 0,
        lapseCount: Int = 0,
        status: String = QuestionEntity.STATUS_ACTIVE
    ) {
        database.questionDao().upsertAll(
            listOf(
                QuestionEntity(
                    id = id,
                    cardId = cardId,
                    prompt = id,
                    answer = "",
                    tagsJson = "[]",
                    status = status,
                    stageIndex = stageIndex,
                    dueAt = dueAt,
                    lastReviewedAt = lastReviewedAt,
                    reviewCount = reviewCount,
                    lapseCount = lapseCount,
                    createdAt = 1L,
                    updatedAt = 1L
                )
            )
        )
    }
}
