package com.kariscode.yike.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.core.ui.viewmodel.typedViewModelFactory
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.local.db.entity.CardEntity
import com.kariscode.yike.data.local.db.entity.DeckEntity
import com.kariscode.yike.data.local.db.entity.QuestionEntity
import com.kariscode.yike.data.local.db.entity.ReviewRecordEntity
import com.kariscode.yike.data.mapper.toDomain
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
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
private val QUESTION_PATTERNS: List<String> = listOf(
    "为什么 {topic} 很重要？",
    "{topic} 的核心定义是什么？",
    "如何区分 {topic} 与相近概念？",
    "{topic} 在实际场景里如何使用？",
    "{topic} 最容易出错的地方是什么？"
)
private val ANSWER_PATTERNS: List<String> = listOf(
    "{topic} 主要用于帮助快速建立记忆锚点。",
    "回答时应先说出定义，再补一个最常见的例子。",
    "如果混淆了 {topic}，通常是因为忽略了适用前提。",
    "这类题更适合用短句回答，先抓关键词再展开。",
    "{topic} 和相近概念的边界是调试页面重点观察的地方。"
)
private val TAG_POOL: List<String> = listOf("debug", "generated", "核心", "易错", "定义", "例句", "应用", "对比")

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
    private val timeProvider: TimeProvider,
    private val syncChangeRecorder: LanSyncChangeRecorder
) : ViewModel() {
    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    /**
     * 生成入口显式锁定并发点击，目的是防止开发中重复触发导致一次操作写入多份测试数据。
     */
    fun generateRandomData() {
        if (_uiState.value.isGenerating || _uiState.value.isClearing) return

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
                        isClearing = false,
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
                        isClearing = false,
                        statusMessage = "生成失败，请检查日志后重试。",
                        errorMessage = throwable.message ?: "未知错误"
                    )
                }
            }
        }
    }

    /**
     * 清除入口与造数入口互斥，是为了避免调试时一边写入一边清库导致数据库状态不可预测。
     */
    fun clearDebugData() {
        if (_uiState.value.isGenerating || _uiState.value.isClearing) return

        _uiState.update {
            it.copy(
                isClearing = true,
                statusMessage = "正在清除调试数据…",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            runCatching {
                clearAllData()
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isClearing = false,
                        statusMessage = "调试数据已清空，首页、题库和统计页现在会回到空状态。",
                        createdDeckCount = 0,
                        createdCardCount = 0,
                        createdQuestionCount = 0,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        isClearing = false,
                        statusMessage = "清除失败，请稍后重试。",
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
            val reviewRecordDao = database.reviewRecordDao()

            val decks = mutableListOf<DeckEntity>()
            val cards = mutableListOf<CardEntity>()
            val questions = mutableListOf<QuestionEntity>()
            val reviewRecords = mutableListOf<ReviewRecordEntity>()

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

            questions.forEach { question ->
                reviewRecords += createReviewRecordsForQuestion(question = question, nowEpochMillis = nowEpochMillis)
            }

            database.withTransaction {
                deckDao.upsertAll(decks)
                cardDao.upsertAll(cards)
                questionDao.upsertAll(questions)
                reviewRecordDao.insertAll(reviewRecords)
                recordGeneratedSyncChanges(
                    decks = decks,
                    cards = cards,
                    questions = questions,
                    reviewRecords = reviewRecords
                )
            }

            DebugGenerationSummary(
                deckCount = decks.size,
                cardCount = cards.size,
                questionCount = questions.size
            )
        }

    /**
     * 按层级自底向上清空业务表，是为了让外键约束始终由数据库自然维护，而不是依赖调用侧猜测顺序。
     */
    private suspend fun clearAllData() = withContext(dispatchers.io) {
        val deckDao = database.deckDao()
        val cardDao = database.cardDao()
        val questionDao = database.questionDao()
        database.withTransaction {
            val existingDecks = deckDao.listAll()
            val existingCards = cardDao.listAll()
            val existingQuestions = questionDao.listAll()
            database.reviewRecordDao().clearAll()
            questionDao.clearAll()
            cardDao.clearAll()
            deckDao.clearAll()
            recordClearedSyncChanges(
                decks = existingDecks,
                cards = existingCards,
                questions = existingQuestions
            )
        }
    }

    /**
     * 调试造数补写同步 journal，是为了让 debug 页面产生的数据与正式编辑路径共享同一套增量同步语义。
     */
    private suspend fun recordGeneratedSyncChanges(
        decks: List<DeckEntity>,
        cards: List<CardEntity>,
        questions: List<QuestionEntity>,
        reviewRecords: List<ReviewRecordEntity>
    ) {
        decks.forEach { deck ->
            syncChangeRecorder.recordDeckUpsert(deck.toDomain())
        }
        cards.forEach { card ->
            syncChangeRecorder.recordCardUpsert(card.toDomain())
        }
        questions.forEach { question ->
            syncChangeRecorder.recordQuestionUpsert(question.toDomain())
        }
        reviewRecords.forEach { reviewRecord ->
            syncChangeRecorder.recordReviewRecordInsert(reviewRecord.toDomain())
        }
    }

    /**
     * 清空调试数据后补齐删除 tombstone，是为了让另一台设备也能沿用既有级联删除规则收敛到相同空态。
     */
    private suspend fun recordClearedSyncChanges(
        decks: List<DeckEntity>,
        cards: List<CardEntity>,
        questions: List<QuestionEntity>
    ) {
        questions.forEach { question ->
            syncChangeRecorder.recordDelete(
                entityType = SyncEntityType.QUESTION,
                entityId = question.id,
                summary = question.prompt.take(MAX_SUMMARY_LENGTH),
                modifiedAt = question.updatedAt
            )
        }
        cards.forEach { card ->
            syncChangeRecorder.recordDelete(
                entityType = SyncEntityType.CARD,
                entityId = card.id,
                summary = card.title,
                modifiedAt = card.updatedAt
            )
        }
        decks.forEach { deck ->
            syncChangeRecorder.recordDelete(
                entityType = SyncEntityType.DECK,
                entityId = deck.id,
                summary = deck.name,
                modifiedAt = deck.updatedAt
            )
        }
    }

    /**
     * 卡组命名带主题前缀是为了让生成后的测试数据更易辨认，避免开发者面对一批无语义的“示例1/示例2”。
     */
    private fun createDeck(deckIndex: Int, nowEpochMillis: Long): DeckEntity {
        val topic = DECK_TOPICS[deckIndex % DECK_TOPICS.size]
        val tags = listOf(
            "debug",
            topic,
            TAG_POOL[(deckIndex + 1) % TAG_POOL.size]
        )
        return DeckEntity(
            id = "deck_${UUID.randomUUID()}",
            name = "调试卡组 ${deckIndex + 1} · $topic",
            description = "用于快速验证随机复习数据与列表展示。",
            tagsJson = tags.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\""),
            intervalStepCount = ReviewSchedulerV1.DEFAULT_INTERVAL_STEP_COUNT,
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
        val reviewCount = Random.nextInt(from = 0, until = 7)
        val stageIndex = if (reviewCount == 0) 0 else Random.nextInt(from = 0, until = 7)
        val dueOffsetDays = if (dueToday) 0 else Random.nextInt(from = 1, until = 8)
        val lapseCount = if (reviewCount == 0) 0 else Random.nextInt(from = 0, until = minOf(3, reviewCount + 1))
        val topic = "${card.title} ${CARD_TOPICS[(cardPosition + questionIndex) % CARD_TOPICS.size]}"
        val promptTemplate = QUESTION_PATTERNS.random()
        val answerTemplate = ANSWER_PATTERNS.random()
        val tags = buildList {
            add("debug")
            add("generated")
            add(TAG_POOL[(cardPosition + questionIndex) % TAG_POOL.size])
            add(TAG_POOL[(cardPosition + questionIndex + 2) % TAG_POOL.size])
        }.distinct()
        return QuestionEntity(
            id = "q_${UUID.randomUUID()}",
            cardId = card.id,
            prompt = promptTemplate.replace("{topic}", topic),
            answer = answerTemplate.replace("{topic}", topic),
            tagsJson = tags.joinToString(prefix = "[\"", postfix = "\"]", separator = "\",\""),
            status = QuestionEntity.STATUS_ACTIVE,
            stageIndex = stageIndex,
            dueAt = nowEpochMillis + dueOffsetDays * ONE_DAY_MILLIS,
            lastReviewedAt = if (reviewCount == 0) null else nowEpochMillis - Random.nextLong(from = 1L, until = 21L) * ONE_DAY_MILLIS,
            reviewCount = reviewCount,
            lapseCount = lapseCount,
            createdAt = nowEpochMillis,
            updatedAt = nowEpochMillis
        )
    }

    /**
     * 补充随机复习记录，是为了让统计页和今日预估时长真正能反映 AGAIN 比例与响应时间分布。
     */
    private fun createReviewRecordsForQuestion(
        question: QuestionEntity,
        nowEpochMillis: Long
    ): List<ReviewRecordEntity> {
        if (question.reviewCount <= 0) return emptyList()
        return List(question.reviewCount) { reviewIndex ->
            val rating = listOf(
                ReviewRating.AGAIN,
                ReviewRating.HARD,
                ReviewRating.GOOD,
                ReviewRating.EASY
            ).random()
            val reviewedAt = nowEpochMillis - (reviewIndex + 1L) * Random.nextLong(from = 1L, until = 6L) * ONE_DAY_MILLIS
            ReviewRecordEntity(
                id = "review_${UUID.randomUUID()}",
                questionId = question.id,
                rating = rating.name,
                oldStageIndex = (question.stageIndex - 1).coerceAtLeast(0),
                newStageIndex = question.stageIndex,
                oldDueAt = question.dueAt - ONE_DAY_MILLIS,
                newDueAt = question.dueAt,
                reviewedAt = reviewedAt,
                responseTimeMs = Random.nextLong(from = 2_500L, until = 32_000L),
                note = if (rating == ReviewRating.AGAIN) "调试样本：再次遗忘" else ""
            )
        }
    }

    companion object {
        private const val MAX_SUMMARY_LENGTH: Int = 48

        /**
         * 调试页仍通过工厂拿依赖，原因是这样可以沿用现有页面的装配方式并保持可测试性。
         */
        fun factory(
            database: YikeDatabase,
            dispatchers: AppDispatchers,
            timeProvider: TimeProvider,
            syncChangeRecorder: LanSyncChangeRecorder
        ): ViewModelProvider.Factory = typedViewModelFactory {
            DebugViewModel(
                database = database,
                dispatchers = dispatchers,
                timeProvider = timeProvider,
                syncChangeRecorder = syncChangeRecorder
            )
        }
    }
}

