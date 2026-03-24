package com.kariscode.yike.data.sync

import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.data.local.db.dao.SyncChangeDao
import com.kariscode.yike.data.local.db.entity.SyncChangeEntity

/**
 * 固定时间实现集中放在测试支撑里，是为了让多个仓储测试共用同一套稳定时钟而不重复复制匿名类。
 */
class FixedTimeProvider(
    private val now: Long
) : TimeProvider {
    /**
     * 测试里固定返回同一时间点，才能让删除 tombstone 和设置 journal 的断言保持可复现。
     */
    override fun nowEpochMillis(): Long = now
}

/**
 * 内存版 journal DAO 只保留顺序追加和简单读取，是为了让仓储测试验证同步记录行为而不依赖 Room。
 */
class InMemorySyncChangeDao : SyncChangeDao {
    private val changes = mutableListOf<SyncChangeEntity>()
    private var nextSeq: Long = 1L

    /**
     * 追加时显式分配递增 seq，是为了模拟真实数据库里“本机变更顺序”这一关键语义。
     */
    override suspend fun insert(change: SyncChangeEntity): Long {
        changes += change.copy(seq = nextSeq)
        return nextSeq++
    }

    /**
     * 按游标读取的能力保留下来，是为了让依赖 recorder 的测试仍然围绕真实增量接口组织。
     */
    override suspend fun listAfter(afterSeq: Long): List<SyncChangeEntity> =
        changes.filter { change -> change.seq > afterSeq }

    /**
     * 受限读取复用同一份内存流水，是为了保证 limit 语义和完整读取保持一致。
     */
    override suspend fun listAfterLimited(afterSeq: Long, limit: Int): List<SyncChangeEntity> =
        listAfter(afterSeq).take(limit)

    /**
     * 备份增量导出按修改时间读取流水，是为了让单测里的 journal 假实现继续覆盖同一接口契约。
     */
    override suspend fun listModifiedAfter(afterModifiedAt: Long): List<SyncChangeEntity> =
        changes.filter { change -> change.modifiedAt > afterModifiedAt }

    /**
     * 最新游标直接从内存流水推导，是为了让测试不必维护第二份冗余状态。
     */
    override suspend fun findLatestSeq(): Long = changes.maxOfOrNull(SyncChangeEntity::seq) ?: 0L
}

/**
 * 测试专用 recorder 构建入口集中在一处，是为了让仓储测试聚焦业务行为而不是同步基础设施的装配细节。
 */
fun createTestSyncChangeRecorder(): LanSyncChangeRecorder = LanSyncChangeRecorder(
    syncChangeDao = InMemorySyncChangeDao(),
    crypto = LanSyncCrypto()
)

/**
 * 仓储测试有时既要写 journal，又要在断言阶段读取 journal，
 * 因此提供 recorder 与 DAO 成组返回的构建入口，避免测试各自拼装样板。
 */
data class TestSyncRecorder(
    val recorder: LanSyncChangeRecorder,
    val syncChangeDao: InMemorySyncChangeDao
)

/**
 * 组合构建入口把 recorder 与内存 DAO 绑在一起，便于测试直接读取写入后的增量流水。
 */
fun createInspectableTestSyncRecorder(): TestSyncRecorder {
    val syncChangeDao = InMemorySyncChangeDao()
    return TestSyncRecorder(
        recorder = LanSyncChangeRecorder(
            syncChangeDao = syncChangeDao,
            crypto = LanSyncCrypto()
        ),
        syncChangeDao = syncChangeDao
    )
}
