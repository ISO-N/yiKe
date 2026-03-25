package com.kariscode.yike.feature.debug

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kariscode.yike.core.domain.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.core.domain.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.data.sync.LanSyncCrypto
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import com.kariscode.yike.domain.scheduler.ReviewSchedulerV1
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 调试造数测试直接覆盖 ViewModel 与真实 Room，是为了确认“能点按钮”之外，
 * 这次新增的随机数据确实满足首页统计和复习入口依赖的最小数据约束。
 */
@RunWith(AndroidJUnit4::class)
class DebugViewModelIntegrationTest {
    private lateinit var database: YikeDatabase
    private lateinit var syncChangeRecorder: LanSyncChangeRecorder

    /**
     * 使用内存数据库可以把断言聚焦在生成规则本身，避免污染设备上的真实开发数据。
     */
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncChangeRecorder = LanSyncChangeRecorder(
            syncChangeDao = database.syncChangeDao(),
            crypto = LanSyncCrypto()
        )
    }

    /**
     * 关闭数据库能确保每条用例都从空白状态开始，防止随机数据跨测试互相干扰。
     */
    @After
    fun tearDown() {
        database.close()
    }

    /**
     * 生成后的数据量和 due 分布必须满足规格，
     * 否则首页待复习统计与调试验证路径会失去参考价值。
     */
    @Test
    fun generateRandomData_persistsHierarchyAndDueDistribution() = runBlocking {
        val fixedNow = 1_000_000L
        val viewModel = DebugViewModel(
            database = database,
            dispatchers = DefaultAppDispatchers(),
            timeProvider = FixedTimeProvider(nowEpochMillis = fixedNow),
            syncChangeRecorder = syncChangeRecorder
        )

        viewModel.generateRandomData()
        waitForIdle(viewModel = viewModel)

        val decks = database.deckDao().listAll()
        val cards = database.cardDao().listAll()
        val questions = database.questionDao().listAll()
        val dueTodayCount = questions.count { it.dueAt == fixedNow }

        assertTrue("应固定生成 2 个卡组。", decks.size == 2)
        assertTrue("卡片总数应落在 6 到 10 之间。", cards.size in 6..10)
        assertTrue("问题总数应落在 12 到 30 之间。", questions.size in 12..30)
        assertTrue("至少 20% 的问题应在今天到期。", dueTodayCount * 5 >= questions.size)
        assertTrue(
            "stageIndex 应落在当前调度器支持的范围内。",
            questions.all { it.stageIndex in 0..ReviewSchedulerV1.DEFAULT_INTERVAL_DAYS_BY_STAGE.lastIndex }
        )
    }

    /**
     * 调试页写入和清空都必须补 journal，
     * 否则两台 debug 构建设备即使配对成功，也无法通过局域网同步收敛到同一批测试数据。
     */
    @Test
    fun generateRandomData_andClearDebugData_recordSyncJournal() = runBlocking {
        val viewModel = DebugViewModel(
            database = database,
            dispatchers = DefaultAppDispatchers(),
            timeProvider = FixedTimeProvider(nowEpochMillis = 1_000_000L),
            syncChangeRecorder = syncChangeRecorder
        )

        viewModel.generateRandomData()
        waitForIdle(viewModel = viewModel)

        val decks = database.deckDao().listAll()
        val cards = database.cardDao().listAll()
        val questions = database.questionDao().listAll()
        val reviewRecords = database.reviewRecordDao().listAll()
        val generatedChanges = database.syncChangeDao().listAfter(afterSeq = 0L)

        assertEquals(decks.size, generatedChanges.countChange(SyncEntityType.DECK, SyncChangeOperation.UPSERT))
        assertEquals(cards.size, generatedChanges.countChange(SyncEntityType.CARD, SyncChangeOperation.UPSERT))
        assertEquals(questions.size, generatedChanges.countChange(SyncEntityType.QUESTION, SyncChangeOperation.UPSERT))
        assertEquals(
            reviewRecords.size,
            generatedChanges.countChange(SyncEntityType.REVIEW_RECORD, SyncChangeOperation.UPSERT)
        )

        viewModel.clearDebugData()
        waitForIdle(viewModel = viewModel)

        val allChanges = database.syncChangeDao().listAfter(afterSeq = 0L)

        assertEquals(0, database.deckDao().listAll().size)
        assertEquals(0, database.cardDao().listAll().size)
        assertEquals(0, database.questionDao().listAll().size)
        assertEquals(0, database.reviewRecordDao().listAll().size)
        assertEquals(questions.size, allChanges.countChange(SyncEntityType.QUESTION, SyncChangeOperation.DELETE))
        assertEquals(cards.size, allChanges.countChange(SyncEntityType.CARD, SyncChangeOperation.DELETE))
        assertEquals(decks.size, allChanges.countChange(SyncEntityType.DECK, SyncChangeOperation.DELETE))
    }

    /**
     * 轮询等待空闲态，可以让测试只依赖最终状态，
     * 而不是和 ViewModel 内部协程调度细节强耦合。
     */
    private suspend fun waitForIdle(viewModel: DebugViewModel) {
        repeat(50) {
            if (!viewModel.uiState.value.isGenerating && !viewModel.uiState.value.isClearing) return
            delay(100)
        }
        error("等待调试数据操作完成超时。")
    }
}

/**
 * 变更统计辅助函数收在测试文件内，是为了让断言保留“按实体类型和操作验证 journal”的意图而不是淹没在筛选样板里。
 */
private fun List<com.kariscode.yike.data.local.db.entity.SyncChangeEntity>.countChange(
    entityType: SyncEntityType,
    operation: SyncChangeOperation
): Int = count { change ->
    change.entityType == entityType.name && change.operation == operation.name
}

/**
 * 固定时间实现能把 dueAt 的断言稳定在单一时间基准上，
 * 避免设备当前时钟让“今天到期”统计变成偶发失败。
 */
private class FixedTimeProvider(
    private val nowEpochMillis: Long
) : TimeProvider {
    /**
     * 返回固定时间，是为了让随机造数测试始终围绕同一批预期 dueAt 值断言。
     */
    override fun nowEpochMillis(): Long = nowEpochMillis
}

