package com.kariscode.yike.testsupport

import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ArchivedCardSummary
import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import com.kariscode.yike.domain.model.DeckMasterySummarySnapshot
import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import com.kariscode.yike.domain.model.QuestionEditorDraftLoadResult
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot
import com.kariscode.yike.domain.model.PracticeSessionArgs
import com.kariscode.yike.domain.model.Question
import com.kariscode.yike.domain.model.QuestionContext
import com.kariscode.yike.domain.model.QuestionQueryFilters
import com.kariscode.yike.domain.model.ReviewRating
import com.kariscode.yike.domain.model.ReviewAnalyticsSnapshot
import com.kariscode.yike.domain.model.ReviewRecord
import com.kariscode.yike.domain.model.ReviewSubmission
import com.kariscode.yike.domain.model.StageAgainRatioSnapshot
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.domain.model.TodayReviewSummary
import com.kariscode.yike.domain.repository.AppSettingsRepository
import com.kariscode.yike.domain.repository.CardRepository
import com.kariscode.yike.domain.repository.DeckRepository
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import com.kariscode.yike.domain.repository.PracticeRepository
import com.kariscode.yike.domain.repository.QuestionRepository
import com.kariscode.yike.domain.repository.ReviewRepository
import com.kariscode.yike.domain.repository.StudyInsightsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 固定时间实现集中到测试支撑层，是为了让多个 ViewModel 测试围绕同一时点断言而不重复复制匿名类。
 */
class FixedTimeProvider(
    private val nowEpochMillis: Long
) : TimeProvider {
    /**
     * 测试里返回固定时间点，能让 dueAt 和时间范围断言稳定可复现。
     */
    override fun nowEpochMillis(): Long = nowEpochMillis
}

/**
 * 编辑草稿假仓储把“是否存在草稿”“是否遇到损坏文件”和“最近一次保存内容”都做成可观察内存状态，
 * 是为了让编辑页测试聚焦恢复语义而不必接入真实文件系统。
 */
open class FakeQuestionEditorDraftRepository : QuestionEditorDraftRepository {
    val draftsByCardId = linkedMapOf<String, QuestionEditorDraftSnapshot>()
    val savedDrafts = mutableListOf<QuestionEditorDraftSnapshot>()
    val deletedCardIds = mutableListOf<String>()
    val corruptedCardIds = linkedSetOf<String>()

    /**
     * 读取时按卡片维度返回假草稿，是为了让单卡编辑测试直接控制“有草稿/无草稿/损坏”三种入口态。
     */
    override suspend fun loadDraft(cardId: String): QuestionEditorDraftLoadResult {
        val wasCorrupted = corruptedCardIds.remove(cardId)
        if (wasCorrupted) {
            draftsByCardId.remove(cardId)
        }
        return QuestionEditorDraftLoadResult(
            draft = draftsByCardId[cardId],
            wasCorrupted = wasCorrupted
        )
    }

    /**
     * 保存时同时记录调用历史和最新快照，是为了让自动保存与手动保存的断言可以覆盖“保存次数”和“最终内容”两层语义。
     */
    override suspend fun saveDraft(snapshot: QuestionEditorDraftSnapshot) {
        savedDrafts += snapshot
        draftsByCardId[snapshot.cardId] = snapshot
    }

    /**
     * 删除调用记录目标 cardId，是为了让正式保存成功和主动放弃草稿两条路径都能被明确验证。
     */
    override suspend fun deleteDraft(cardId: String) {
        deletedCardIds += cardId
        draftsByCardId.remove(cardId)
    }
}

/**
 * 设置仓储假实现只维护一份内存快照和观察流，便于页面层测试验证读写与提醒配置变化。
 */
open class FakeAppSettingsRepository(
    initialSettings: AppSettings = defaultAppSettings()
) : AppSettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)

    /**
     * 设置更新统一走同一入口，是为了让各个 setter 继续保持真实仓储那种“只改目标字段”的语义，
     * 同时避免测试支撑层因为字段扩展而在多处复制 `copy(...)` 模板。
     */
    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsFlow.value = transform(settingsFlow.value)
    }

    /**
     * 观察流始终回放最新设置，是为了让依赖首帧订阅的 ViewModel 行为与真实仓储保持一致。
     */
    override fun observeSettings(): Flow<AppSettings> = settingsFlow

    /**
     * 快照读取直接返回内存状态，足以支撑页面层和调度层的主机测试。
     */
    override suspend fun getSettings(): AppSettings = settingsFlow.value

    /**
     * 单独开关写入保留真实仓储语义，便于测试“是否只改一个字段”。
     */
    override suspend fun setDailyReminderEnabled(enabled: Boolean) {
        updateSettings { settings ->
            settings.copy(dailyReminderEnabled = enabled)
        }
    }

    /**
     * 时间更新集中改写 hour/minute，便于断言提醒调度是否拿到最新值。
     */
    override suspend fun setDailyReminderTime(hour: Int, minute: Int) {
        updateSettings { settings ->
            settings.copy(
                dailyReminderHour = hour,
                dailyReminderMinute = minute
            )
        }
    }

    /**
     * 整份设置替换用于模拟备份恢复与同步回放后的快照写入。
     */
    override suspend fun setSettings(settings: AppSettings) {
        settingsFlow.value = settings
    }

    /**
     * schemaVersion 的最小可变语义要保留，避免迁移相关测试不得不引入真实 DataStore。
     */
    override suspend fun setSchemaVersion(schemaVersion: Int) {
        updateSettings { settings ->
            settings.copy(schemaVersion = schemaVersion)
        }
    }

    /**
     * 最近备份时间允许写空，是为了让备份页测试能覆盖“未知/未备份”的状态。
     */
    override suspend fun setBackupLastAt(epochMillis: Long?) {
        updateSettings { settings ->
            settings.copy(backupLastAt = epochMillis)
        }
    }

    /**
     * 主题写入保留独立入口，便于统计和设置页测试共享同一份假仓储。
     */
    override suspend fun setThemeMode(mode: ThemeMode) {
        updateSettings { settings ->
            settings.copy(themeMode = mode)
        }
    }
}

/**
 * QuestionRepository 假实现聚焦编辑页和提醒页关心的读写路径，避免为页面测试引入数据库样板。
 */
open class FakeQuestionRepository : QuestionRepository {
    val questionsByCard = linkedMapOf<String, MutableList<Question>>()
    var summary: TodayReviewSummary = TodayReviewSummary(dueCardCount = 0, dueQuestionCount = 0)
    val upsertCalls = mutableListOf<List<Question>>()
    val deleteCalls = mutableListOf<String>()
    val deleteAllCalls = mutableListOf<List<String>>()
    val requestedSummaryNow = mutableListOf<Long>()

    /**
     * 观察接口在页面测试中只需回放当前快照，因此直接用内存列表包成 flow 即可。
     */
    override fun observeQuestionsByCard(cardId: String): Flow<List<Question>> =
        MutableStateFlow(questionsByCard[cardId].orEmpty().toList())

    /**
     * 单对象查询允许编辑页按 id 回填旧题数据。
     */
    override suspend fun findById(questionId: String): Question? =
        questionsByCard.values.flatten().firstOrNull { question -> question.id == questionId }

    /**
     * 编辑页重新加载依赖一次性快照读取，这里直接返回当前卡片下的问题副本。
     */
    override suspend fun listByCard(cardId: String): List<Question> =
        questionsByCard[cardId].orEmpty().toList()

    /**
     * 批量 upsert 既记录调用，也回写到内存快照，便于测试保存后的 reload 行为。
     */
    override suspend fun upsertAll(questions: List<Question>) {
        upsertCalls += questions
        questions.groupBy(Question::cardId).forEach { (cardId, groupedQuestions) ->
            val current = questionsByCard.getOrPut(cardId) { mutableListOf() }
            groupedQuestions.forEach { incoming ->
                val index = current.indexOfFirst { it.id == incoming.id }
                if (index >= 0) current[index] = incoming else current += incoming
            }
        }
    }

    /**
     * 到期查询在当前这批页面测试里不是重点，因此默认返回空列表即可。
     */
    override suspend fun listDueQuestions(nowEpochMillis: Long): List<Question> = emptyList()

    /**
     * 下一张到期卡片在本组页面测试中不参与断言，因此返回空即可。
     */
    override suspend fun findNextDueCardId(nowEpochMillis: Long): String? = null

    /**
     * 首页与提醒的概览查询会记录请求时间点，便于测试是否真的发生过查询。
     */
    override suspend fun getTodayReviewSummary(nowEpochMillis: Long): TodayReviewSummary {
        requestedSummaryNow += nowEpochMillis
        return summary
    }

    /**
     * 单条删除既记录调用，也同步从内存快照里移除对应问题。
     */
    override suspend fun delete(questionId: String) {
        deleteCalls += questionId
        questionsByCard.values.forEach { questions ->
            questions.removeAll { question -> question.id == questionId }
        }
    }

    /**
     * 批量删除会保持与真实仓储一致的“按 id 集合删除”语义。
     */
    override suspend fun deleteAll(questionIds: Collection<String>) {
        deleteAllCalls += questionIds.toList()
        questionsByCard.values.forEach { questions ->
            questions.removeAll { question -> question.id in questionIds }
        }
    }
}

/**
 * StudyInsightsRepository 假实现把搜索、标签和统计数据拆成可直接配置的字段，便于页面测试聚焦状态编排。
 */
open class FakeStudyInsightsRepository : StudyInsightsRepository {
    var searchResults: List<QuestionContext> = emptyList()
    var availableTags: List<String> = emptyList()
    var analytics: ReviewAnalyticsSnapshot = ReviewAnalyticsSnapshot(
        totalReviews = 0,
        againCount = 0,
        hardCount = 0,
        goodCount = 0,
        easyCount = 0,
        averageResponseTimeMs = null,
        forgettingRate = 0f,
        deckBreakdowns = emptyList()
    )
    var reviewTimestamps: List<Long> = emptyList()
    var stageAgainRatios: List<StageAgainRatioSnapshot> = emptyList()
    var upcomingDueAts: List<Long> = emptyList()
    var deckMasterySummary: DeckMasterySummarySnapshot = DeckMasterySummarySnapshot(
        totalQuestions = 0,
        newCount = 0,
        learningCount = 0,
        familiarCount = 0,
        masteredCount = 0
    )
    var searchError: Throwable? = null
    var analyticsError: Throwable? = null
    val searchFilters = mutableListOf<QuestionQueryFilters>()
    val deckMasteryRequests = mutableListOf<String>()
    val analyticsRequests = mutableListOf<Long?>()
    val dueForecastRequests = mutableListOf<DueForecastRequest>()

    /**
     * 搜索调用会记录筛选条件，便于断言 ViewModel 是否把状态正确映射成查询参数。
     */
    override suspend fun searchQuestionContexts(filters: QuestionQueryFilters): List<QuestionContext> {
        searchFilters += filters
        searchError?.let { throw it }
        return searchResults
    }

    /**
     * 今日预览不在当前页面测试覆盖范围内，因此默认返回空列表。
     */
    override suspend fun listDueQuestionContexts(nowEpochMillis: Long): List<QuestionContext> = emptyList()

    /**
     * 标签候选直接返回预设值，足以验证搜索页的元数据刷新逻辑。
     */
    override suspend fun listAvailableTags(limit: Int): List<String> = availableTags.take(limit)

    /**
     * 卡组熟练度摘要默认回放预设值，是为了让卡片页测试聚焦状态编排而不是重复拼装题目集合。
     */
    override suspend fun getDeckMasterySummary(deckId: String): DeckMasterySummarySnapshot {
        deckMasteryRequests += deckId
        return deckMasterySummary
    }

    /**
     * 统计页会记录时间范围请求，便于测试 range 切换是否真的触发了重算。
     */
    override suspend fun getReviewAnalytics(startEpochMillis: Long?): ReviewAnalyticsSnapshot {
        analyticsRequests += startEpochMillis
        analyticsError?.let { throw it }
        return analytics
    }

    /**
     * 连续学习天数依赖原始时间戳列表，因此假实现直接回放预设集合。
     */
    override suspend fun listReviewTimestamps(startEpochMillis: Long?): List<Long> = reviewTimestamps
        .filter { timestamp -> startEpochMillis == null || timestamp >= startEpochMillis }

    /**
     * 遗忘曲线聚合在测试里通常只需要回放预设快照，足以断言 ViewModel 是否正确请求与映射。
     */
    override suspend fun listStageAgainRatios(startEpochMillis: Long?): List<StageAgainRatioSnapshot> = stageAgainRatios

    /**
     * 未来到期预测会记录请求窗口，便于断言 ViewModel 是否用“从今天开始的 7 天”这类稳定口径。
     */
    override suspend fun listUpcomingDueAts(startEpochMillis: Long, endEpochMillis: Long): List<Long> {
        dueForecastRequests += DueForecastRequest(startEpochMillis = startEpochMillis, endEpochMillis = endEpochMillis)
        return upcomingDueAts.filter { dueAt -> dueAt in startEpochMillis until endEpochMillis }
    }

    /**
     * 预测窗口记录单独建模，是为了让断言不需要依赖 Pair 下标，提升可读性。
     */
    data class DueForecastRequest(
        val startEpochMillis: Long,
        val endEpochMillis: Long
    )
}

/**
 * PracticeRepository 假实现把最近一次练习范围记录下来，
 * 是为了让设置页和会话页测试能直接断言只读查询是否收到正确的缩圈参数。
 */
open class FakePracticeRepository : PracticeRepository {
    var questionContexts: List<QuestionContext> = emptyList()
    var error: Throwable? = null
    val requests = mutableListOf<PracticeSessionArgs>()

    /**
     * 假仓储记录传入参数后直接回放预设结果，足以覆盖练习模式的范围裁剪与会话构建测试。
     */
    override suspend fun listPracticeQuestionContexts(args: PracticeSessionArgs): List<QuestionContext> {
        requests += args
        error?.let { throwable -> throw throwable }
        return questionContexts
    }
}

/**
 * ReviewRepository 假实现既记录评分提交，也允许测试预设不同卡片的到期题集合，
 * 是为了让网页后台学习流测试能在不接入真实数据库的前提下锁住正式复习语义复用是否正确。
 */
open class FakeReviewRepository : ReviewRepository {
    val dueQuestionsByCardId = linkedMapOf<String, List<Question>>()
    val listDueRequests = mutableListOf<Pair<String, Long>>()
    val submitRequests = mutableListOf<SubmittedReviewRating>()
    var submitResultFactory: ((SubmittedReviewRating) -> ReviewSubmission)? = null

    /**
     * 到期题读取会记录 cardId 与时间点，
     * 是为了让测试可以直接断言网页复习是否围绕正式 due 查询工作。
     */
    override suspend fun listDueQuestionsByCard(cardId: String, nowEpochMillis: Long): List<Question> {
        listDueRequests += cardId to nowEpochMillis
        return dueQuestionsByCardId[cardId].orEmpty()
    }

    /**
     * 评分提交默认回放一个最小成功结果，
     * 是为了让页面测试既能断言调用参数，也能在需要时替换成更具体的提交结果。
     */
    override suspend fun submitRating(
        questionId: String,
        rating: ReviewRating,
        reviewedAtEpochMillis: Long,
        responseTimeMs: Long?
    ): ReviewSubmission {
        val request = SubmittedReviewRating(
            questionId = questionId,
            rating = rating,
            reviewedAtEpochMillis = reviewedAtEpochMillis,
            responseTimeMs = responseTimeMs
        )
        submitRequests += request
        return submitResultFactory?.invoke(request) ?: ReviewSubmission(
            updatedQuestion = Question(
                id = questionId,
                cardId = "card",
                prompt = "prompt",
                answer = "answer",
                tags = emptyList(),
                status = com.kariscode.yike.domain.model.QuestionStatus.ACTIVE,
                stageIndex = 1,
                dueAt = reviewedAtEpochMillis,
                lastReviewedAt = reviewedAtEpochMillis,
                reviewCount = 1,
                lapseCount = if (rating == ReviewRating.AGAIN) 1 else 0,
                createdAt = 0L,
                updatedAt = reviewedAtEpochMillis
            ),
            reviewRecord = ReviewRecord(
                id = "record_$questionId",
                questionId = questionId,
                rating = rating,
                oldStageIndex = 0,
                newStageIndex = 1,
                oldDueAt = 0L,
                newDueAt = reviewedAtEpochMillis,
                reviewedAt = reviewedAtEpochMillis,
                responseTimeMs = responseTimeMs,
                note = ""
            )
        )
    }
}

/**
 * 评分提交请求单独成结构体，
 * 是为了让测试在断言评分档位、时间点和响应时长时保持清晰可读。
 */
data class SubmittedReviewRating(
    val questionId: String,
    val rating: ReviewRating,
    val reviewedAtEpochMillis: Long,
    val responseTimeMs: Long?
)

/**
 * DeckRepository 假实现同时支持活动列表与回收站列表，便于内容管理和回收站测试共享。
 */
open class FakeDeckRepository : DeckRepository {
    val activeSummariesFlow = MutableStateFlow<List<DeckSummary>>(emptyList())
    val archivedSummariesFlow = MutableStateFlow<List<DeckSummary>>(emptyList())
    var activeDecks: List<Deck> = emptyList()
    var deckById = linkedMapOf<String, Deck>()
    val upsertedDecks = mutableListOf<Deck>()
    val setArchivedCalls = mutableListOf<Triple<String, Boolean, Long>>()
    val deletedDeckIds = mutableListOf<String>()

    /**
     * 活动卡组快照统一从索引回算，是为了让 upsert 后的测试读取始终和当前内存索引保持一致。
     */
    private fun refreshActiveDecks() {
        activeDecks = deckById.values.filterNot(Deck::archived)
    }

    /**
     * 活动卡组流直接回放内存状态，足以覆盖列表类页面的订阅行为。
     */
    override fun observeActiveDecks(): Flow<List<Deck>> = MutableStateFlow(activeDecks)

    /**
     * 搜索页等一次性快照场景直接复用活动卡组列表。
     */
    override suspend fun listActiveDecks(): List<Deck> = activeDecks

    /**
     * 列表页活动摘要通过独立 flow 供测试手动推进状态变化。
     */
    override fun observeActiveDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = activeSummariesFlow

    /**
     * 回收站摘要同样走可控 flow，便于测试恢复与删除后的页面回写。
     */
    override fun observeArchivedDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>> = archivedSummariesFlow

    /**
     * 最近卡组在当前页面测试里只需返回活动摘要截断结果。
     */
    override suspend fun listRecentActiveDeckSummaries(nowEpochMillis: Long, limit: Int): List<DeckSummary> =
        activeSummariesFlow.value.take(limit)

    /**
     * 单对象查询从内存索引中读取，便于卡片页和编辑页根据 id 载入所属卡组。
     */
    override suspend fun findById(deckId: String): Deck? = deckById[deckId]

    /**
     * upsert 会更新内存索引，便于测试保存后再读取的行为。
     */
    override suspend fun upsert(deck: Deck) {
        upsertedDecks += deck
        deckById[deck.id] = deck
        refreshActiveDecks()
    }

    /**
     * 归档操作只记录调用参数，由具体测试决定是否推进 flow 模拟数据变化。
     */
    override suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long) {
        setArchivedCalls += Triple(deckId, archived, updatedAt)
    }

    /**
     * 物理删除只记录 id，用于断言高风险操作是否真的被触发。
     */
    override suspend fun delete(deckId: String) {
        deletedDeckIds += deckId
    }
}

/**
 * CardRepository 假实现同时覆盖搜索、卡片页和回收站的基础读写路径，减少页面测试样板。
 */
open class FakeCardRepository : CardRepository {
    val activeCardsByDeck = linkedMapOf<String, List<Card>>()
    val activeSummariesFlow = MutableStateFlow<List<CardSummary>>(emptyList())
    val archivedSummariesFlow = MutableStateFlow<List<ArchivedCardSummary>>(emptyList())
    var cardById = linkedMapOf<String, Card>()
    val upsertedCards = mutableListOf<Card>()
    val setArchivedCalls = mutableListOf<Triple<String, Boolean, Long>>()
    val deletedCardIds = mutableListOf<String>()

    /**
     * 单卡组活动卡片快照统一从索引重建，是为了让保存后的读取结果始终对齐当前假仓储状态。
     */
    private fun refreshActiveCards(deckId: String) {
        activeCardsByDeck[deckId] = cardById.values.filter { candidate ->
            candidate.deckId == deckId && !candidate.archived
        }
    }

    /**
     * 卡片活动流回放当前卡组列表，便于列表页观察更新。
     */
    override fun observeActiveCards(deckId: String): Flow<List<Card>> =
        MutableStateFlow(activeCardsByDeck[deckId].orEmpty())

    /**
     * 搜索页切卡组时会读取活动卡片快照，这里直接回放预设列表。
     */
    override suspend fun listActiveCards(deckId: String): List<Card> = activeCardsByDeck[deckId].orEmpty()

    /**
     * 卡片摘要流供卡片页手动推进聚合状态。
     */
    override fun observeActiveCardSummaries(deckId: String, nowEpochMillis: Long): Flow<List<CardSummary>> =
        activeSummariesFlow

    /**
     * 回收站摘要流供删除与恢复测试复用。
     */
    override fun observeArchivedCardSummaries(nowEpochMillis: Long): Flow<List<ArchivedCardSummary>> =
        archivedSummariesFlow

    /**
     * 单对象查询允许编辑与回收站等页面按 id 读取当前卡片。
     */
    override suspend fun findById(cardId: String): Card? = cardById[cardId]

    /**
     * upsert 记录后回写索引，便于测试保存成功后的二次读取。
     */
    override suspend fun upsert(card: Card) {
        upsertedCards += card
        cardById[card.id] = card
        refreshActiveCards(card.deckId)
    }

    /**
     * 归档调用保留参数记录，具体页面测试自行决定何时推进 flow。
     */
    override suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long) {
        setArchivedCalls += Triple(cardId, archived, updatedAt)
    }

    /**
     * 物理删除只记录目标 id，便于断言确认删除分支是否真正执行。
     */
    override suspend fun delete(cardId: String) {
        deletedCardIds += cardId
    }
}

/**
 * 默认设置构造函数集中在测试支撑层，是为了让多个测试类共享同一份稳定初始配置。
 */
fun defaultAppSettings(): AppSettings = AppSettings(
    dailyReminderEnabled = true,
    dailyReminderHour = 20,
    dailyReminderMinute = 30,
    schemaVersion = 4,
    backupLastAt = null,
    themeMode = ThemeMode.SYSTEM
)

