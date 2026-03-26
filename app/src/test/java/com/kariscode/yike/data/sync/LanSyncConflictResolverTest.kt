package com.kariscode.yike.data.sync

import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 冲突解析测试锁定“同实体变更只在真正不同步时才提示冲突”的规则，
 * 这样性能优化（减少中间集合）不会悄悄改变冲突判定语义。
 */
class LanSyncConflictResolverTest {
    /**
     * 双端都删除同一对象时不应生成冲突，
     * 否则用户会被迫在“本就一致的决议”上做一次无意义选择。
     */
    @Test
    fun buildConflicts_bothDeleteReturnsEmpty() {
        val resolver = LanSyncConflictResolver()
        val local = listOf(
            change(
                seq = 1L,
                entityType = SyncEntityType.QUESTION,
                entityId = "q_1",
                operation = SyncChangeOperation.DELETE,
                payloadHash = "hash_a"
            )
        )
        val remote = listOf(
            change(
                seq = 2L,
                entityType = SyncEntityType.QUESTION,
                entityId = "q_1",
                operation = SyncChangeOperation.DELETE,
                payloadHash = "hash_b"
            )
        )

        val conflicts = resolver.buildConflicts(localChanges = local, remoteChanges = remote)

        assertTrue(conflicts.isEmpty())
    }

    /**
     * 双端 payload 与操作完全一致时不应生成冲突，
     * 否则同一份变更被重复同步会反复要求用户确认。
     */
    @Test
    fun buildConflicts_samePayloadReturnsEmpty() {
        val resolver = LanSyncConflictResolver()
        val local = listOf(
            change(
                seq = 1L,
                entityType = SyncEntityType.CARD,
                entityId = "card_1",
                operation = SyncChangeOperation.UPSERT,
                payloadHash = "hash_same"
            )
        )
        val remote = listOf(
            change(
                seq = 2L,
                entityType = SyncEntityType.CARD,
                entityId = "card_1",
                operation = SyncChangeOperation.UPSERT,
                payloadHash = "hash_same"
            )
        )

        val conflicts = resolver.buildConflicts(localChanges = local, remoteChanges = remote)

        assertTrue(conflicts.isEmpty())
    }

    /**
     * 双端都修改同一实体但 payload 不同必须生成冲突，
     * 这样用户才能在执行前明确选择“以本机为准”或“以远端为准”。
     */
    @Test
    fun buildConflicts_differentPayloadReturnsConflict() {
        val resolver = LanSyncConflictResolver()
        val local = listOf(
            change(
                seq = 1L,
                entityType = SyncEntityType.DECK,
                entityId = "deck_1",
                operation = SyncChangeOperation.UPSERT,
                payloadHash = "hash_local",
                summary = "数学"
            )
        )
        val remote = listOf(
            change(
                seq = 2L,
                entityType = SyncEntityType.DECK,
                entityId = "deck_1",
                operation = SyncChangeOperation.UPSERT,
                payloadHash = "hash_remote",
                summary = "数学"
            )
        )

        val conflicts = resolver.buildConflicts(localChanges = local, remoteChanges = remote)

        assertEquals(1, conflicts.size)
        assertEquals(SyncEntityType.DECK, conflicts.single().entityType)
        assertEquals("deck_1", conflicts.single().entityId)
    }

    /**
     * 变更工厂把与测试无关字段收口在默认值里，是为了让断言只锁定冲突检测的关键语义。
     */
    private fun change(
        seq: Long,
        entityType: SyncEntityType,
        entityId: String,
        operation: SyncChangeOperation,
        payloadHash: String,
        summary: String = entityId
    ): SyncChangePayload = SyncChangePayload(
        seq = seq,
        entityType = entityType.name,
        entityId = entityId,
        operation = operation.name,
        summary = summary,
        payloadJson = "{}",
        payloadHash = payloadHash,
        modifiedAt = 1_000L
    )
}

