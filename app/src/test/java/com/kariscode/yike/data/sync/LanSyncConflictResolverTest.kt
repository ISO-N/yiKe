package com.kariscode.yike.data.sync

import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanSyncConflictResolver 测试锁定冲突检测、裁剪与压缩规则，
 * 避免同步协议未来演进时把预览结果和真正执行结果带偏。
 */
class LanSyncConflictResolverTest {
    private val resolver = LanSyncConflictResolver()

    /**
     * 同一实体在两端都修改且 payload 不同时必须产出冲突，
     * 否则预览阶段会把真实覆盖风险静默吞掉。
     */
    @Test
    fun buildConflicts_mutualDifferentUpdates_returnsConflict() {
        val local = listOf(
            change(
                seq = 1L,
                entityType = SyncEntityType.QUESTION,
                entityId = "q_1",
                operation = SyncChangeOperation.UPSERT,
                summary = "本地题目",
                payloadHash = "local-hash"
            )
        )
        val remote = listOf(
            change(
                seq = 2L,
                entityType = SyncEntityType.QUESTION,
                entityId = "q_1",
                operation = SyncChangeOperation.UPSERT,
                summary = "远端题目",
                payloadHash = "remote-hash"
            )
        )

        val conflicts = resolver.buildConflicts(localChanges = local, remoteChanges = remote)

        assertEquals(1, conflicts.size)
        assertEquals(SyncEntityType.QUESTION, conflicts.single().entityType)
        assertEquals("q_1", conflicts.single().entityId)
        assertEquals("两端都修改了同一对象", conflicts.single().reason)
    }

    /**
     * 双方都删除或 payload 完全一致时不应提示冲突，
     * 否则预览会制造不必要的用户负担。
     */
    @Test
    fun buildConflicts_samePayloadOrBothDelete_returnsEmpty() {
        val sameUpsert = listOf(
            change(
                seq = 1L,
                entityType = SyncEntityType.CARD,
                entityId = "card_1",
                operation = SyncChangeOperation.UPSERT,
                payloadHash = "same-hash"
            )
        )
        val bothDelete = listOf(
            change(
                seq = 2L,
                entityType = SyncEntityType.CARD,
                entityId = "card_2",
                operation = SyncChangeOperation.DELETE,
                payloadHash = "delete-hash"
            )
        )

        val noConflictFromSamePayload = resolver.buildConflicts(sameUpsert, sameUpsert)
        val noConflictFromBothDelete = resolver.buildConflicts(bothDelete, bothDelete)

        assertTrue(noConflictFromSamePayload.isEmpty())
        assertTrue(noConflictFromBothDelete.isEmpty())
    }

    /**
     * 冲突决议为 KEEP_LOCAL / KEEP_REMOTE / SKIP 时必须分别裁剪对应方向的变更，
     * 否则执行阶段会违背用户在预览弹窗里的明确选择。
     */
    @Test
    fun applyConflictResolution_filtersLocalAndRemoteByChoice() {
        val localChanges = listOf(
            change(seq = 1L, entityType = SyncEntityType.DECK, entityId = "deck_keep_local", payloadHash = "1"),
            change(seq = 2L, entityType = SyncEntityType.DECK, entityId = "deck_keep_remote", payloadHash = "2"),
            change(seq = 3L, entityType = SyncEntityType.DECK, entityId = "deck_skip", payloadHash = "3")
        )
        val remoteChanges = listOf(
            change(seq = 4L, entityType = SyncEntityType.DECK, entityId = "deck_keep_local", payloadHash = "4"),
            change(seq = 5L, entityType = SyncEntityType.DECK, entityId = "deck_keep_remote", payloadHash = "5"),
            change(seq = 6L, entityType = SyncEntityType.DECK, entityId = "deck_skip", payloadHash = "6")
        )
        val resolutions = listOf(
            resolution(entityId = "deck_keep_local", choice = LanSyncConflictChoice.KEEP_LOCAL),
            resolution(entityId = "deck_keep_remote", choice = LanSyncConflictChoice.KEEP_REMOTE),
            resolution(entityId = "deck_skip", choice = LanSyncConflictChoice.SKIP)
        )

        val (filteredLocal, filteredRemote) = resolver.applyConflictResolution(
            localChanges = localChanges,
            remoteChanges = remoteChanges,
            resolutions = resolutions
        )

        assertEquals(listOf("deck_keep_local"), filteredLocal.map { it.entityId })
        assertEquals(listOf("deck_keep_remote"), filteredRemote.map { it.entityId })
    }

    /**
     * 可变实体压缩后只保留最新一条，而 ReviewRecord 作为追加事件必须完整保留，
     * 否则预览计数和真正应用的历史记录都会失真。
     */
    @Test
    fun compressChanges_keepsLatestMutableAndAllReviewRecords() {
        val changes = listOf(
            change(seq = 1L, entityType = SyncEntityType.QUESTION, entityId = "q_1", payloadHash = "old"),
            change(seq = 2L, entityType = SyncEntityType.REVIEW_RECORD, entityId = "rr_1", payloadHash = "rr1"),
            change(seq = 3L, entityType = SyncEntityType.QUESTION, entityId = "q_1", payloadHash = "new"),
            change(seq = 4L, entityType = SyncEntityType.REVIEW_RECORD, entityId = "rr_2", payloadHash = "rr2")
        )

        val compressed = resolver.compressChanges(changes)

        assertEquals(listOf(2L, 3L, 4L), compressed.map { it.seq })
        assertEquals(listOf("rr_1", "q_1", "rr_2"), compressed.map { it.entityId })
    }

    private fun change(
        seq: Long,
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncChangeOperation = SyncChangeOperation.UPSERT,
        summary: String = entityId,
        payloadHash: String
    ): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = entityType.name,
        entityId = entityId,
        operation = operation.name,
        summary = summary,
        payloadJson = """{"id":"$entityId"}""",
        payloadHash = payloadHash,
        modifiedAt = seq * 1_000L
    )

    private fun resolution(
        entityId: String,
        choice: LanSyncConflictChoice
    ): LanSyncConflictResolution = LanSyncConflictResolution(
        entityType = SyncEntityType.DECK,
        entityId = entityId,
        choice = choice
    )
}
