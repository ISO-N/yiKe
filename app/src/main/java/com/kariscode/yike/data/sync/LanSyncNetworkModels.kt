package com.kariscode.yike.data.sync

import kotlinx.serialization.Serializable

/**
 * 网络传输模型只保留跨设备协商所需字段，是为了让同步接口稳定复用现有备份格式而不泄漏内部实体结构。
 */
@Serializable
data class LanSyncManifestPayload(
    val deviceId: String,
    val deviceName: String,
    val exportedAt: Long,
    val deckCount: Int,
    val cardCount: Int,
    val questionCount: Int
)
