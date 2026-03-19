package com.kariscode.yike.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Android NSD 适配器只负责类型转换与回调桥接，是为了把业务判断继续留在 LanSyncNsdService，
 * 让“过滤自发现、解析缓存、生命周期收口”等高风险分支可以在主机测试里稳定回归。
 */
internal class AndroidLanSyncNsdPlatform(
    context: Context
) : LanSyncNsdPlatform {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val registrationListeners = mutableMapOf<LanSyncRegistrationListener, NsdManager.RegistrationListener>()
    private val discoveryListeners = mutableMapOf<LanSyncDiscoveryListener, NsdManager.DiscoveryListener>()

    /**
     * 注册时缓存 framework listener，是为了后续注销仍能找到系统侧句柄。
     */
    override fun registerService(service: LanSyncPlatformServiceInfo, listener: LanSyncRegistrationListener) {
        val serviceInfo = service.toAndroidServiceInfo()
        val androidListener = object : NsdManager.RegistrationListener {
            /**
             * 系统回传最终服务名后交给服务层处理，避免适配器私自保留业务状态。
             */
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                listener.onServiceRegistered(serviceInfo.toPlatformServiceInfo())
            }

            /**
             * 注册失败继续透传完整服务信息，是为了让业务层保留统一的日志与兜底策略。
             */
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                listener.onRegistrationFailed(serviceInfo.toPlatformServiceInfo(), errorCode)
            }

            /**
             * 注销成功只做事件转发，是为了保持适配器无状态。
             */
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                listener.onServiceUnregistered(serviceInfo.toPlatformServiceInfo())
            }

            /**
             * 注销失败仍原样转发，是为了让服务层自己决定如何收口本地状态。
             */
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                listener.onUnregistrationFailed(serviceInfo.toPlatformServiceInfo(), errorCode)
            }
        }
        registrationListeners[listener] = androidListener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, androidListener)
    }

    /**
     * 注销时移除缓存 listener，是为了避免重复进入同步页后继续持有旧会话引用。
     */
    override fun unregisterService(listener: LanSyncRegistrationListener) {
        registrationListeners.remove(listener)?.let(nsdManager::unregisterService)
    }

    /**
     * 发现监听缓存下来后，停止时才能精确对应到同一次 discover 调用。
     */
    override fun startDiscovery(serviceType: String, listener: LanSyncDiscoveryListener) {
        val androidListener = object : NsdManager.DiscoveryListener {
            /**
             * 启动失败直接透传，是为了让服务层以统一方式收口资源。
             */
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onStartDiscoveryFailed(serviceType, errorCode)
            }

            /**
             * 停止失败继续透传，是为了让缓存清理逻辑集中留在服务层。
             */
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onStopDiscoveryFailed(serviceType, errorCode)
            }

            /**
             * 成功启动仅映射生命周期，不在适配层产生额外副作用。
             */
            override fun onDiscoveryStarted(serviceType: String) {
                listener.onDiscoveryStarted(serviceType)
            }

            /**
             * 成功停止同样只做事件映射，保证适配层行为可预测。
             */
            override fun onDiscoveryStopped(serviceType: String) {
                listener.onDiscoveryStopped(serviceType)
            }

            /**
             * 发现服务后统一转换成平台值对象，避免上层直接依赖 framework 类型。
             */
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                listener.onServiceFound(serviceInfo.toPlatformServiceInfo())
            }

            /**
             * 服务丢失也统一转成最小值对象，是为了让服务层缓存逻辑稳定复用。
             */
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                listener.onServiceLost(serviceInfo.toPlatformServiceInfo())
            }
        }
        discoveryListeners[listener] = androidListener
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, androidListener)
    }

    /**
     * 停止发现时同步移除缓存 listener，是为了避免后续重复 stop 触碰失效句柄。
     */
    override fun stopDiscovery(listener: LanSyncDiscoveryListener) {
        discoveryListeners.remove(listener)?.let(nsdManager::stopServiceDiscovery)
    }

    /**
     * 解析结果继续转换成平台值对象，是为了让服务层只面对稳定的 host/port 数据。
     */
    @Suppress("DEPRECATION")
    override fun resolveService(service: LanSyncPlatformServiceInfo, listener: LanSyncResolveListener) {
        nsdManager.resolveService(
            service.toAndroidServiceInfo(),
            object : NsdManager.ResolveListener {
                /**
                 * 解析失败继续透传，是为了让上层保留统一日志口径。
                 */
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    listener.onResolveFailed(serviceInfo.toPlatformServiceInfo(), errorCode)
                }

                /**
                 * 解析成功后带上 hostAddress，服务层才能决定是否写入发现结果。
                 */
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    listener.onServiceResolved(serviceInfo.toPlatformServiceInfo())
                }
            }
        )
    }
}

