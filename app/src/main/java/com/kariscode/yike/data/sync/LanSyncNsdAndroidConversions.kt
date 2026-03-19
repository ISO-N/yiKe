package com.kariscode.yike.data.sync

import android.net.nsd.NsdServiceInfo

/**
 * NSD 值对象与 Android framework 类型的转换集中到单文件，是为了让平台适配层可以复用同一份字段映射，
 * 同时避免 LanSyncNsdService 因为 Android 类型而膨胀。
 */

/**
 * 平台值对象到 Android NSD 类型的转换集中在单点，是为了避免字段映射散落在多个回调里。
 */
internal fun LanSyncPlatformServiceInfo.toAndroidServiceInfo(): NsdServiceInfo = NsdServiceInfo().apply {
    serviceName = this@toAndroidServiceInfo.serviceName
    serviceType = this@toAndroidServiceInfo.serviceType
    port = this@toAndroidServiceInfo.port
}

/**
 * Android NSD 类型回转成平台值对象，是为了让上层始终面对同一套稳定的数据结构。
 * 当 serviceType 为 null 时使用空字符串，是为了让服务发现流程能继续而不崩溃。
 */
@Suppress("DEPRECATION")
internal fun NsdServiceInfo.toPlatformServiceInfo(): LanSyncPlatformServiceInfo = LanSyncPlatformServiceInfo(
    serviceName = serviceName ?: "",
    serviceType = serviceType ?: "",
    port = port,
    hostAddress = host?.hostAddress
)
