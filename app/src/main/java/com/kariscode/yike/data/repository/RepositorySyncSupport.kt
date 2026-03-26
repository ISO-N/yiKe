package com.kariscode.yike.data.repository

import com.kariscode.yike.data.sync.LanSyncChangeRecorder
import com.kariscode.yike.domain.model.SyncEntityType

/**
 * 同步感知写操作的共性样板集中在仓储 support 中，是为了让各个离线仓储继续保留各自查询口径，
 * 但不必重复实现“基于当前快照记录同步 journal”这套固定骨架。
 */
internal object RepositorySyncSupport {
    /**
     * 归档类更新通常都要基于更新前快照重建新的领域对象，收成 helper 后可以减少仓储里的重复空判模板。
     */
    suspend fun <Model> recordUpdatedSnapshot(
        current: Model?,
        buildUpdated: (Model) -> Model,
        recordUpsert: suspend (Model) -> Unit
    ) {
        current?.let { model ->
            recordUpsert(buildUpdated(model))
        }
    }

    /**
     * 删除 tombstone 总是依赖“当前实体若存在则取摘要，否则退回兜底值”，
     * 统一后可以让不同仓储继续只声明摘要与时间的来源。
     */
    suspend fun <Model> recordDeleteFromSnapshot(
        syncChangeRecorder: LanSyncChangeRecorder,
        entityType: SyncEntityType,
        entityId: String,
        current: Model?,
        fallbackSummary: String,
        fallbackModifiedAt: Long,
        summaryOf: (Model) -> String,
        modifiedAtOf: (Model) -> Long
    ) {
        syncChangeRecorder.recordDelete(
            entityType = entityType,
            entityId = entityId,
            summary = current?.let(summaryOf) ?: fallbackSummary,
            modifiedAt = current?.let(modifiedAtOf) ?: fallbackModifiedAt
        )
    }

    /**
     * 批量删除通常发生在“上层已经决定这批 ID 就该消失”的场景（例如编辑页批量删除、整卡片清空问题等），
     * 此时仓储往往并不需要（也不应该）为了生成 tombstone 再额外读取实体快照；
     * 因此提供批量入口让调用方只传一次 entityType 与统一的 modifiedAt，避免循环重复填充 `current = null`。
     */
    suspend fun recordBatchDelete(
        syncChangeRecorder: LanSyncChangeRecorder,
        entityType: SyncEntityType,
        entityIds: Collection<String>,
        fallbackModifiedAt: Long
    ) {
        entityIds.forEach { entityId ->
            syncChangeRecorder.recordDelete(
                entityType = entityType,
                entityId = entityId,
                summary = entityId,
                modifiedAt = fallbackModifiedAt
            )
        }
    }
}
