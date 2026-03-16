package com.kariscode.yike.feature.deck

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DeckListViewModel 测试锁定卡组页的关键管理语义，
 * 避免卡组页在收敛为单一归档入口后又退回到多套重复动作。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    /**
     * 卡组页只保留归档入口后，点击归档应直接写入归档状态，
     * 这样列表页就不会再维护一套和归档等价的重复删除语义。
     */
    @Test
    fun onToggleArchiveClick_archivesDeckAndKeepsPhysicalDeleteUnused() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = FakeDeckRepository()
            val item = createDeckSummary(deckId = "deck_1")
            repository.archivedDecksFlow.value = listOf(item)
            val viewModel = DeckListViewModel(
                deckRepository = repository,
                timeProvider = object : TimeProvider {
                    override fun nowEpochMillis(): Long = 321L
                }
            )

            viewModel.onToggleArchiveClick(item)
            advanceUntilIdle()

            assertEquals(1, repository.setArchivedCalls.size)
            assertEquals("deck_1", repository.setArchivedCalls.single().deckId)
            assertEquals(true, repository.setArchivedCalls.single().archived)
            assertEquals(321L, repository.setArchivedCalls.single().updatedAt)
            assertEquals(0, repository.deletedDeckIds.size)
            assertEquals("已归档，可在已归档内容中恢复", viewModel.uiState.value.message)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * 测试数据显式保留聚合字段，是为了让归档动作在真实列表项上下文里执行。
     */
    private fun createDeckSummary(deckId: String): DeckSummary = DeckSummary(
        deck = Deck(
            id = deckId,
            name = "英语",
            description = "",
            archived = false,
            sortOrder = 0,
            createdAt = 1L,
            updatedAt = 1L
        ),
        cardCount = 2,
        questionCount = 5,
        dueQuestionCount = 1
    )

    /**
     * FakeDeckRepository 只记录 ViewModel 本次关心的写路径，
     * 这样测试可以聚焦“卡组页是否只剩归档语义”这一条行为。
     */
    private class FakeDeckRepository : DeckRepository {
        val archivedDecksFlow = MutableStateFlow<List<DeckSummary>>(emptyList())
        val setArchivedCalls = mutableListOf<SetArchivedCall>()
        val deletedDeckIds = mutableListOf<String>()

        data class SetArchivedCall(val deckId: String, val archived: Boolean, val updatedAt: Long)

        override fun observeActiveDecks(): Flow<List<Deck>> = MutableStateFlow(emptyList())

        override suspend fun listActiveDecks(): List<Deck> = emptyList()

        override fun observeActiveDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = archivedDecksFlow

        override fun observeArchivedDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = MutableStateFlow(emptyList())

        override suspend fun listRecentActiveDeckSummaries(nowEpochMillis: Long, limit: Int): List<DeckSummary> = emptyList()

        override suspend fun findById(deckId: String): Deck? = null

        override suspend fun upsert(deck: Deck) = Unit

        override suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long) {
            setArchivedCalls.add(SetArchivedCall(deckId, archived, updatedAt))
        }

        override suspend fun delete(deckId: String) {
            deletedDeckIds.add(deckId)
        }
    }
}
