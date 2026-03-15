package com.kariscode.yike.feature.debug

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kariscode.yike.core.dispatchers.DefaultAppDispatchers
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.YikeDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
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

    /**
     * 使用内存数据库可以把断言聚焦在生成规则本身，避免污染设备上的真实开发数据。
     */
    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, YikeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
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
            timeProvider = FixedTimeProvider(nowEpochMillis = fixedNow)
        )

        viewModel.generateRandomData()
        waitForGeneration(viewModel = viewModel)

        val decks = database.deckDao().listAll()
        val cards = database.cardDao().listAll()
        val questions = database.questionDao().listAll()
        val dueTodayCount = questions.count { it.dueAt == fixedNow }

        assertTrue("应固定生成 2 个卡组。", decks.size == 2)
        assertTrue("卡片总数应落在 6 到 10 之间。", cards.size in 6..10)
        assertTrue("问题总数应落在 12 到 30 之间。", questions.size in 12..30)
        assertTrue("至少 20% 的问题应在今天到期。", dueTodayCount * 5 >= questions.size)
        assertTrue("stageIndex 应只分布在 0 到 3。", questions.all { it.stageIndex in 0..3 })
    }

    /**
     * 轮询等待生成完成，可以让测试只依赖最终状态，
     * 而不是和 ViewModel 内部协程调度细节强耦合。
     */
    private suspend fun waitForGeneration(viewModel: DebugViewModel) {
        repeat(50) {
            if (!viewModel.uiState.value.isGenerating) return
            delay(100)
        }
        error("等待随机数据生成超时。")
    }
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
