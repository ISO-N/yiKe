package com.kariscode.yike.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.kariscode.yike.core.time.TimeProvider
import com.kariscode.yike.domain.model.SyncDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val LAN_SYNC_SERVICE_TYPE: String = "_yike._tcp."

/**
 * NSD 服务同时承担本机广播与远端发现，是为了让局域网同步继续依赖 Android 原生能力而不额外引入中间服务。
 */
class LanSyncNsdService(
    context: Context,
    private val timeProvider: TimeProvider
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _devices = MutableStateFlow<List<SyncDevice>>(emptyList())
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var localServiceName: String? = null

    /**
     * 发现结果通过只读 StateFlow 暴露，是为了让上层只能消费设备列表而不能篡改底层发现状态。
     */
    val devices: StateFlow<List<SyncDevice>> = _devices.asStateFlow()

    /**
     * 服务注册需要单独启动，是为了把“本机可被发现”的时机严格限定在同步页使用期间。
     */
    fun registerService(serviceName: String, port: Int) {
        if (registrationListener != null) {
            return
        }
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = LAN_SYNC_SERVICE_TYPE
            this.port = port
        }
        registrationListener = object : NsdManager.RegistrationListener {
            /**
             * 记录系统最终采用的服务名，是为了在后续发现列表中准确过滤掉本机广播。
             */
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                localServiceName = serviceInfo.serviceName
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                localServiceName = serviceName
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                localServiceName = null
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                localServiceName = null
            }
        }.also { listener ->
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    /**
     * 停止广播能让同步能力退出后不再暴露本机服务，保持离线优先的默认边界。
     */
    fun unregisterService() {
        registrationListener?.let { listener ->
            runCatching { nsdManager.unregisterService(listener) }
        }
        registrationListener = null
        localServiceName = null
    }

    /**
     * 发现流程开启时顺带申请 multicast lock，是为了提高局域网内 NSD 广播在 Wi-Fi 场景下的可见性。
     */
    fun startDiscovery() {
        if (discoveryListener != null) {
            return
        }
        acquireMulticastLock()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                stopDiscovery()
            }

            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            /**
             * 发现到服务后立即解析地址，是为了让列表项能直接提供可连接的主机信息。
             */
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != LAN_SYNC_SERVICE_TYPE) {
                    return
                }
                if (serviceInfo.serviceName == localServiceName) {
                    return
                }
                resolveService(serviceInfo)
            }

            /**
             * 服务丢失时立即从列表移除，可以避免用户点击到已经离线的旧设备。
             */
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                _devices.update { devices ->
                    devices.filterNot { device -> device.id == serviceInfo.serviceName }
                }
            }
        }.also { listener ->
            nsdManager.discoverServices(LAN_SYNC_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    /**
     * 结束发现时同时释放 multicast lock 并清空列表，是为了避免页面退出后继续展示过期局域网状态。
     */
    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        releaseMulticastLock()
        _devices.value = emptyList()
    }

    /**
     * 服务解析成功后统一转换成领域模型，是为了让上层不必处理 NSD 的平台对象和时序细节。
     */
    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(
            serviceInfo,
            object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val hostAddress = serviceInfo.host?.hostAddress ?: return
                    upsertDevice(
                        SyncDevice(
                            id = serviceInfo.serviceName,
                            deviceName = serviceInfo.serviceName,
                            hostAddress = hostAddress,
                            port = serviceInfo.port,
                            lastSeenAt = timeProvider.nowEpochMillis()
                        )
                    )
                }
            }
        )
    }

    /**
     * 发现结果以 serviceName 为主键更新，是为了在设备周期性广播时只刷新最后一次出现时间而不是不断追加重复项。
     */
    private fun upsertDevice(device: SyncDevice) {
        _devices.update { devices ->
            (devices.filterNot { existing -> existing.id == device.id } + device)
                .sortedBy { it.deviceName.lowercase() }
        }
    }

    /**
     * Multicast lock 显式维护在单点后，就不会在多次开始/停止发现时遗漏释放导致系统资源滞留。
     */
    private fun acquireMulticastLock() {
        if (multicastLock != null) {
            return
        }
        multicastLock = wifiManager.createMulticastLock("yike-lan-sync").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    /**
     * 发现结束就释放 lock，可以避免同步页离开后仍持续占用 Wi-Fi 组播能力。
     */
    private fun releaseMulticastLock() {
        multicastLock?.takeIf { it.isHeld }?.release()
        multicastLock = null
    }
}
