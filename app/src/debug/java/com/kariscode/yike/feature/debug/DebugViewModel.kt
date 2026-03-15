package com.kariscode.yike.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kariscode.yike.core.dispatchers.AppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DEFAULT_DECK_COUNT: Int = 2
private const val MIN_CARD_COUNT: Int = 3
private const val MAX_CARD_COUNT_EXCLUSIVE: Int = 6
private const val MIN_QUESTION_COUNT: Int = 2
private const val MAX_QUESTION_COUNT_EXCLUSIVE: Int = 4
private const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

private val DECK_TOPICS: List<String> = listOf("旅行英语", "历史时间线", "前端术语", "日常口语", "算法基础")
private val CARD_TOPICS: List<String> = listOf("概念", "例句", "易错点", "回顾", "比较")

/**
 * 生成结果单独建模是为了让 ViewModel 在更新 UI 时只暴露稳定摘要，
 * 避免 Composable 反向依赖生成实现中的中间集合。
 */
private data class DebugGenerationSummary(
    val deckCount: Int,
    val cardCount: Int,
    val questionCount: Int
)

/**
 * 随机数据生成逻辑放在 ViewModel 中，是为了复用容器里的数据库与时间策略，
 * 同时避免把事务边界和随机调度规则散落到 Composable。
 */
class DebugViewModel(
    private val database: YikeDatabase,
    private val dispatchers: AppDispatchers,
    private val timeProvider: TimeProvider
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    /**
     * 生成入口显式锁定并发点击，目的是防止开发中重复触发导致一次操作写入多份测试数据。
     */
    fun generateRandomData() {
        if (_uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                isGenerating = true,
                statusMessage = "正在生成随机测试数据…",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                createRandomData(nowEpochMillis = timeProvider.nowEpochMillis())
            }.onSuccess { summary ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        statusMessage = "已生成 ${summary.deckCount} 个卡组、${summary.cardCount} 张卡片、${summary.questionCount} 个问题。",
                        createdDeckCount = summary.deckCount,
                        createdCardCount = summary.cardCount,
                        createdQuestionCount = summary.questionCount,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        statusMessage = "生成失败，请检查日志后重试。",
                        errorMessage = throwable.message ?: "未知错误"
                    )
                }
            }
        }
    }

    /**
     * 通过单次事务批量写入三层实体，是为了保证任何一步失败时数据库都不会出现半成功层级。
     */
    private suspend fun createRandomData(nowEpochMillis: Long): DebugGenerationSummary =
        withContext(dispatchers.io) {
            val deckDao = database.deckDao()
            val cardDao = database.cardDao()
            val questionDao = database.questionDao()

            val decks = mutableListOf<DeckEntity>()
            val cards = mutableListOf<CardEntity>()
            val questions = mutableListOf<QuestionEntity>()

            repeat(DEFAULT_DECK_COUNT) { deckIndex ->
                val deck = createDeck(deckIndex = deckIndex, nowEpochMillis = nowEpochMillis)
                decks += deck

                val cardCount = Random.nextInt(from = MIN_CARD_COUNT, until = MAX_CARD_COUNT_EXCLUSIVE)
                repeat(cardCount) { cardIndex ->
                    val card = createCard(
                        deckId = deck.id,
                        deckIndex = deckIndex,
                        cardIndex = cardIndex,
                        sortOrder = cardIndex,
                        nowEpochMillis = nowEpochMillis
                    )
                    cards += card
                }
            }

            val plannedQuestionCount = cards.sumOf {
                Random.nextInt(from = MIN_QUESTION_COUNT, until = MAX_QUESTION_COUNT_EXCLUSIVE)
            }
            var dueTodayRemaining = maxOf(1, (plannedQuestionCount + 4) / 5)
            var questionsRemaining = plannedQuestionCount
            var dueAssignmentRemaining = plannedQuestionCount

            cards.forEachIndexed { cardPosition, card ->
                val questionCount = if (cardPosition == cards.lastIndex) {
                    questionsRemaining
                } else {
                    val remainingCards = cards.size - cardPosition - 1
                    val minForRemaining = remainingCards * MIN_QUESTION_COUNT
                    val maxForCurrent = minOf(
                        MAX_QUESTION_COUNT_EXCLUSIVE - 1,
                        questionsRemaining - minForRemaining
                    )
                    Random.nextInt(from = MIN_QUESTION_COUNT, until = maxForCurrent + 1)
                }
                questionsRemaining -= questionCount

                repeat(questionCount) { questionIndex ->
                    val mustAssignToday = dueTodayRemaining == dueAssignmentRemaining
                    val shouldAssignToday = mustAssignToday || (
                        dueTodayRemaining > 0 &&
                            Random.nextDouble() < dueTodayRemaining.toDouble() / dueAssignmentRemaining
                        )
                    if (shouldAssignToday) {
                        dueTodayRemaining -= 1
                    }
                    questions += createQuestion(
                        card = card,
                        cardPosition = cardPosition,
                        questionIndex = questionIndex,
                        dueToday = shouldAssignToday,
                        nowEpochMillis = nowEpochMillis
                    )
                    dueAssignmentRemaining -= 1
                }
            }

            database.withTransaction {
                deckDao.upsertAll(decks)
                cardDao.upsertAll(cards)
                questionDao.upsertAll(questions)
            }

            DebugGenerationSummary(
                deckCount = decks.size,
                cardCount = cards.size,
                questionCount = questions.size
            )
        }

    /**
     * 卡组命名带主题前缀是为了让生成后的测试数据更易辨认，避免开发者面对一批无语义的“示例1/示例2”。
     */
    private fun createDeck(deckIndex: Int, nowEpochMillis: Long): DeckEntity {
        val topic = DECK_TOPICS[deckIndex % DECK_TOPICS.size]
        return DeckEntity(
            id = "deck_${UUID.randomUUID()}",
            name = "调试卡组 ${deckIndex + 1} · $topic",
            description = "用于快速验证随机复习数据与列表展示。",
            archived = false,
            sortOrder = deckIndex,
            createdAt = nowEpochMillis,
            updatedAt = nowEpochMillis
        )
    }

    /**
     * 卡片标题保留层级信息，是为了生成后在列表中能快速看出它属于哪一个调试卡组与测试主题。
     */
    private fun createCard(
        deckId: String,
        deckIndex: Int,
        cardIndex: Int,
        sortOrder: Int,
        nowEpochMillis: Long
    ): CardEntity {
        val topic = CARD_TOPICS[(deckIndex + cardIndex) % CARD_TOPICS.size]
        return CardEntity(
            id = "card_${UUID.randomUUID()}",
            deckId = deckId,
            title = "调试卡片 ${cardIndex + 1} · $topic",
            description = "用于验证复习入口、统计刷新和问题聚合。",
            archived = false,
            sortOrder = sortOrder,
            createdAt = nowEpochMillis,
            updatedAt = nowEpochMillis
        )
    }

    /**
     * 问题生成时显式控制 due 与 stage 分布，是为了让首页、复习流和未来时间视图都能拿到更接近真实使用的数据。
     */
    private fun createQuestion(
        card: CardEntity,
        cardPosition: Int,
        questionIndex: Int,
        dueToday: Boolean,
        nowEpochMillis: Long
    ): QuestionEntity {
        val stageIndex = Random.nextInt(from = 0, until = 4)
        val dueOffsetDays = if (dueToday) 0 else Random.nextInt(from = 1, until = 8)
        return QuestionEntity(
            id = "q_${UUID.randomUUID()}",
            cardId = card.id,
            prompt = "请回忆卡片 ${cardPosition + 1} 的测试问题 ${questionIndex + 1}。",
            answer = "这是用于开发验证的参考答案 ${questionIndex + 1}。",
            tagsJson = """["debug","generated","card_${cardPosition + 1}"]""",
            status = QuestionEntity.STATUS_ACTIVE,
            stageIndex = stageIndex,
            dueAt = nowEpochMillis + dueOffsetDays * ONE_DAY_MILLIS,
            lastReviewedAt = if (stageIndex == 0) null else nowEpochMillis - stageIndex * ONE_DAY_MILLIS,
            reviewCount = stageIndex,
            lapseCount = if (stageIndex == 0) 0 else Random.nextInt(from = 0, until = 2),
            createdAt = nowEpochMillis,
            updatedAt = nowEpochMillis
        )
    }

    companion object {
        /**
         * 调试页仍通过工厂拿依赖，原因是这样可以沿用现有页面的装配方式并保持可测试性。
         */
        fun factory(
            database: YikeDatabase,
            dispatchers: AppDispatchers,
            timeProvider: TimeProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return DebugViewModel(
                    database = database,
                    dispatchers = dispatchers,
                    timeProvider = timeProvider
                ) as T
            }
        }
    }
}
