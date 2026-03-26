package com.kariscode.yike.data.sync

import com.kariscode.yike.domain.model.LanSyncConflictChoice
import com.kariscode.yike.domain.model.LanSyncConflictItem
import com.kariscode.yike.domain.model.LanSyncConflictResolution
import com.kariscode.yike.domain.model.SyncChangeOperation
import com.kariscode.yike.domain.model.SyncEntityType

/**
 * 同步冲突检测与裁剪逻辑保持纯函数化，
 * 这样预览和执行阶段就能共享同一规则并独立于网络与数据库测试。
 */
class LanSyncConflictResolver {
    /**
     * 冲突只比较同一实体最新一条可变变更，以减少中间态噪音。
     */
    fun buildConflicts(
        localChanges: List<SyncChangePayload>,
        remoteChanges: List<SyncChangePayload>
    ): List<LanSyncConflictItem> {
        val localByKey = latestMutableChanges(localChanges)
        val remoteByKey = latestMutableChanges(remoteChanges)
        val smallerIsLocal = localByKey.size <= remoteByKey.size
        val smaller = if (smallerIsLocal) localByKey else remoteByKey
        val larger = if (smallerIsLocal) remoteByKey else localByKey

        val conflicts = ArrayList<LanSyncConflictItem>(smaller.size.coerceAtMost(larger.size))
        smaller.forEach { (key, oneSide) ->
            val otherSide = larger[key] ?: return@forEach
            val local = if (smallerIsLocal) oneSide else otherSide
            val remote = if (smallerIsLocal) otherSide else oneSide

            val bothDelete = local.operation == SyncChangeOperation.DELETE.name &&
                remote.operation == SyncChangeOperation.DELETE.name
            val samePayload = local.payloadHash == remote.payloadHash && local.operation == remote.operation
            if (bothDelete || samePayload) {
                return@forEach
            }

            val reason = when {
                local.operation == SyncChangeOperation.DELETE.name || remote.operation == SyncChangeOperation.DELETE.name ->
                    "一端删除了该对象，另一端仍有修改"
                else -> "两端都修改了同一对象"
            }
            conflicts.add(
                LanSyncConflictItem(
                    entityType = SyncEntityType.valueOf(local.entityType),
                    entityId = local.entityId,
                    summary = local.summary.ifBlank { remote.summary },
                    localSummary = local.summary,
                    remoteSummary = remote.summary,
                    reason = reason
                )
            )
        }

        return conflicts.sortedBy { conflict -> "${conflict.entityType.name}:${conflict.summary}" }
    }

    /**
     * 根据冲突决议分别裁剪本地上传与远端应用集合，避免执行阶段再理解 UI 选择。
     */
    fun applyConflictResolution(
        localChanges: List<SyncChangePayload>,
        remoteChanges: List<SyncChangePayload>,
        resolutions: List<LanSyncConflictResolution>
    ): Pair<List<SyncChangePayload>, List<SyncChangePayload>> {
        val resolutionMap = resolutions.associateBy { resolution ->
            "${resolution.entityType.name}:${resolution.entityId}"
        }
        val filteredLocal = localChanges.filter { change ->
            when (resolutionMap["${change.entityType}:${change.entityId}"]?.choice) {
                LanSyncConflictChoice.KEEP_REMOTE, LanSyncConflictChoice.SKIP -> false
                else -> true
            }
        }
        val filteredRemote = remoteChanges.filter { change ->
            when (resolutionMap["${change.entityType}:${change.entityId}"]?.choice) {
                LanSyncConflictChoice.KEEP_LOCAL, LanSyncConflictChoice.SKIP -> false
                else -> true
            }
        }
        return filteredLocal to filteredRemote
    }

    /**
     * 一个同步窗口内可变实体只保留最新一条，追加型 ReviewRecord 则完整保留。
     */
    fun compressChanges(changes: List<SyncChangePayload>): List<SyncChangePayload> {
        val mutableLatest = latestMutableChanges(changes).values.toList()
        val reviewRecords = changes
            .filter { change -> change.entityType == SyncEntityType.REVIEW_RECORD.name }
            .sortedBy { change -> change.seq }
        return (mutableLatest + reviewRecords).sortedBy { change -> change.seq }
    }

    /**
     * 最新可变实体统一按 entityType + entityId 聚合，确保 preview 与 apply 口径一致。
     */
    private fun latestMutableChanges(changes: List<SyncChangePayload>): Map<String, SyncChangePayload> =
        changes
            .filter { change -> change.entityType != SyncEntityType.REVIEW_RECORD.name }
            .groupBy { change -> "${change.entityType}:${change.entityId}" }
            .mapValues { (_, groupedChanges) -> groupedChanges.maxBy { change -> change.seq } }
}
