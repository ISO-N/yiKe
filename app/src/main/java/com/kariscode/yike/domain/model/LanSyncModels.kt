package com.kariscode.yike.domain.model

/**
 * 已发现设备显式保留地址与端口，是为了让同步页在展示列表时无需再理解 NSD 解析结果细节。
 */
data class SyncDevice(
    val id: String,
    val deviceName: String,
    val hostAddress: String,
    val port: Int,
    val lastSeenAt: Long
)

/**
 * 远端同步快照只保留同步前必须展示的摘要，是为了让局域网确认弹窗聚焦在“将覆盖多少内容”这一决策信息。
 */
data class LanSyncSnapshot(
    val deviceId: String,
    val deviceName: String,
    val exportedAt: Long,
    val deckCount: Int,
    val cardCount: Int,
    val questionCount: Int
)

/**
 * 本机快照把本地内容规模与最近备份时间放在一起，是为了在覆盖前给用户足够的风险判断依据。
 */
data class LocalSyncSnapshot(
    val deckCount: Int,
    val cardCount: Int,
    val questionCount: Int,
    val lastBackupAt: Long?
)

/**
 * 冲突信息显式携带本地与远端摘要，是为了让确认弹窗不必临时拼接多份状态并避免展示口径漂移。
 */
data class SyncConflict(
    val device: SyncDevice,
    val remoteSnapshot: LanSyncSnapshot,
    val localSnapshot: LocalSyncSnapshot,
    val reason: String
)
