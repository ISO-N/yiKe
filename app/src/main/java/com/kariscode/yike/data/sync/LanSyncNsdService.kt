package com.kariscode.yike.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * NSD 服务只负责把局域网里的候选地址发现出来，
 * 是为了让设备身份、协议能力和信任状态统一由后续 hello/配对流程决定。
 */
class LanSyncNsdService internal constructor(
    private val nsdPlatform: LanSyncNsdPlatform,
    private val multicastLockFactory: LanSyncMulticastLockFactory
) : LanSyncDiscoveryService {
    constructor(context: Context) : this(
        nsdPlatform = AndroidLanSyncNsdPlatform(context.applicationContext),
        multicastLockFactory = AndroidLanSyncMulticastLockFactory(context.applicationContext)
    )

    private val _services = MutableStateFlow<List<LanSyncDiscoveredService>>(emptyList())
    private var discoveryListener: LanSyncDiscoveryListener? = null
    private var registrationListener: LanSyncRegistrationListener? = null
    private var multicastLock: LanSyncMulticastLock? = null
    private var localServiceName: String? = null

    /**
     * 发现结果以只读 StateFlow 暴露，是为了让上层能消费网络变化但不能绕过发现服务直接篡改缓存。
     */
    override val services: StateFlow<List<LanSyncDiscoveredService>> = _services.asStateFlow()

    /**
     * 本机广播名单独注册，是为了把“可被发现”和“主动发现别人”两个生命周期动作解耦。
     */
    override fun registerService(serviceName: String, port: Int) {
        if (registrationListener != null) {
            return
        }
        registrationListener = object : LanSyncRegistrationListener {
            /**
             * 记录系统最终采用的广播名，是为了在发现回调里准确过滤掉自己，避免把本机当成远端设备。
             */
            override fun onServiceRegistered(service: LanSyncPlatformServiceInfo) {
                localServiceName = service.serviceName
            }

            /**
             * 注册失败要写日志，是为了在端口冲突或系统拒绝时留下真实错误，而不是页面只看到抽象失败提示。
             */
            override fun onRegistrationFailed(service: LanSyncPlatformServiceInfo, errorCode: Int) {
                localServiceName = serviceName
                LanSyncLogger.e("NSD register failed: $errorCode for ${service.serviceName}")
            }

            /**
             * 注销成功后清空本机广播名，是为了让后续重新进入同步页时不会拿着过期名字错误过滤结果。
             */
            override fun onServiceUnregistered(service: LanSyncPlatformServiceInfo) {
                localServiceName = null
            }

            /**
             * 注销失败虽然不影响 stop 调用继续返回，但仍要清空本地缓存，以免会话状态和系统状态进一步漂移。
             */
            override fun onUnregistrationFailed(service: LanSyncPlatformServiceInfo, errorCode: Int) {
                LanSyncLogger.e("NSD unregister failed: $errorCode for ${service.serviceName}")
                localServiceName = null
            }
        }.also { listener ->
            nsdPlatform.registerService(
                service = LanSyncPlatformServiceInfo(
                    serviceName = serviceName,
                    serviceType = LanSyncConfig.SERVICE_TYPE,
                    port = port
                ),
                listener = listener
            )
        }
    }

    /**
     * 结束广播时统一收口注册监听，是为了让页面退出后不再继续向局域网暴露本机服务。
     */
    override fun unregisterService() {
        registrationListener?.let { listener ->
            runCatching { nsdPlatform.unregisterService(listener) }
                .onFailure { throwable -> LanSyncLogger.e("NSD unregister exception", throwable) }
        }
        registrationListener = null
        localServiceName = null
    }

    /**
     * 发现流程开启时顺带申请 multicast lock，是为了提升局域网 Wi-Fi 场景下的服务可见性。
     */
    override fun startDiscovery() {
        if (discoveryListener != null) {
            return
        }
        acquireMulticastLock()
        discoveryListener = object : LanSyncDiscoveryListener {
            /**
             * 启动发现失败时记录日志并主动停掉当前会话资源，是为了避免 discoveryListener 留在半初始化状态。
             */
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                LanSyncLogger.e("NSD discovery start failed: $errorCode for $serviceType")
                stopDiscovery()
            }

            /**
             * 停止发现失败依然执行本地收口，是为了防止页面退出后继续保留过期的发现缓存。
             */
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                LanSyncLogger.e("NSD discovery stop failed: $errorCode for $serviceType")
                stopDiscovery()
            }

            /**
             * 启动发现本身不改变业务状态，因此只保留空实现即可。
             */
            override fun onDiscoveryStarted(serviceType: String) = Unit

            /**
             * 结束发现不单独维护 UI 状态，是为了让 stopDiscovery 成为唯一收口入口。
             */
            override fun onDiscoveryStopped(serviceType: String) = Unit

            /**
             * 发现到候选服务后立即做地址解析，是为了让上层拿到的结果已经具备可连接的 host/port 信息。
             */
            override fun onServiceFound(service: LanSyncPlatformServiceInfo) {
                if (service.serviceType != LanSyncConfig.SERVICE_TYPE) {
                    return
                }
                if (service.serviceName == localServiceName) {
                    return
                }
                resolveService(service)
            }

            /**
             * 服务丢失时按 serviceName 移除，是为了在对端退出同步页后尽快把设备列表收敛回真实可用状态。
             */
            override fun onServiceLost(service: LanSyncPlatformServiceInfo) {
                _services.update { services ->
                    services.filterNot { current -> current.serviceName == service.serviceName }
                }
            }
        }.also { listener ->
            nsdPlatform.startDiscovery(
                serviceType = LanSyncConfig.SERVICE_TYPE,
                listener = listener
            )
        }
    }

    /**
     * 停止发现时同时释放 multicast lock 并清空缓存，是为了让同步页关闭后立刻回到完全离线的默认状态。
     */
    override fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdPlatform.stopDiscovery(listener) }
                .onFailure { throwable -> LanSyncLogger.e("NSD stop discovery exception", throwable) }
        }
        discoveryListener = null
        releaseMulticastLock()
        _services.value = emptyList()
    }

    /**
     * 解析成功后只保留连接所需最小字段，是为了让 NSD 层不承担协议能力和可信设备判断职责。
     */
    private fun resolveService(service: LanSyncPlatformServiceInfo) {
        nsdPlatform.resolveService(
            service = service,
            listener = object : LanSyncResolveListener {
                /**
                 * 解析失败必须写日志，是为了保留设备不可见或地址异常时的底层原因。
                 */
                override fun onResolveFailed(service: LanSyncPlatformServiceInfo, errorCode: Int) {
                    LanSyncLogger.e("NSD resolve failed: $errorCode for ${service.serviceName}")
                }

                /**
                 * 解析到 host/port 后以 serviceName 为键 upsert，可以避免局域网重复广播造成列表不断追加重复项。
                 */
                override fun onServiceResolved(service: LanSyncPlatformServiceInfo) {
                    val hostAddress = service.hostAddress ?: return
                    upsertService(
                        LanSyncDiscoveredService(
                            serviceName = service.serviceName,
                            hostAddress = hostAddress,
                            port = service.port
                        )
                    )
                }
            }
        )
    }

    /**
     * serviceName 作为发现层唯一键足够稳定，是为了把更高层的 deviceId/fingerprint 判断留给 hello 和配对流程。
     */
    private fun upsertService(service: LanSyncDiscoveredService) {
        _services.update { services ->
            (services.filterNot { current -> current.serviceName == service.serviceName } + service)
                .sortedBy { current -> current.serviceName.lowercase() }
        }
    }

    /**
     * Multicast lock 统一维护在单点后，多次打开关闭同步页也不会遗留系统资源。
     */
    private fun acquireMulticastLock() {
        if (multicastLock != null) {
            return
        }
        multicastLock = multicastLockFactory.create("yike-lan-sync").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    /**
     * 发现关闭即释放 lock，是为了避免同步页退出后仍持续占用 Wi-Fi 组播能力。
     */
    private fun releaseMulticastLock() {
        multicastLock?.takeIf(LanSyncMulticastLock::isHeld)?.release()
        multicastLock = null
    }
}

/**
 * 平台服务信息统一抽成值对象，是为了让发现逻辑测试不必直接依赖 Android NSD 对象。
 */
internal data class LanSyncPlatformServiceInfo(
    val serviceName: String,
    val serviceType: String,
    val port: Int,
    val hostAddress: String? = null
)

/**
 * 注册监听抽象出来后，主机测试就能覆盖“系统改名、自清理、失败兜底”这些高风险分支。
 */
internal interface LanSyncRegistrationListener {
    /**
     * 系统接受注册后回传最终广播名，是为了让发现层据此过滤自发现结果。
     */
    fun onServiceRegistered(service: LanSyncPlatformServiceInfo)

    /**
     * 注册失败时保留服务信息，是为了把真实失败上下文带回日志与测试断言。
     */
    fun onRegistrationFailed(service: LanSyncPlatformServiceInfo, errorCode: Int)

    /**
     * 注销成功单独回调，是为了让本地缓存和系统状态同步收口。
     */
    fun onServiceUnregistered(service: LanSyncPlatformServiceInfo)

    /**
     * 注销失败也显式暴露，是为了测试异常路径下的本地兜底行为。
     */
    fun onUnregistrationFailed(service: LanSyncPlatformServiceInfo, errorCode: Int)
}

/**
 * 发现监听接口把生命周期事件与服务事件分开，是为了让测试只关心业务相关分支而不依赖 Android 回调类。
 */
internal interface LanSyncDiscoveryListener {
    /**
     * 启动失败回调独立保留，是为了让服务能在半初始化状态下主动收口。
     */
    fun onStartDiscoveryFailed(serviceType: String, errorCode: Int)

    /**
     * 停止失败也需要被观察，是为了验证服务退出时不会残留缓存与锁。
     */
    fun onStopDiscoveryFailed(serviceType: String, errorCode: Int)

    /**
     * 启动成功事件虽然当前不驱动状态，但保留接口可以保持平台适配语义完整。
     */
    fun onDiscoveryStarted(serviceType: String)

    /**
     * 停止成功事件保留出来，是为了让适配器仍然完整映射底层生命周期。
     */
    fun onDiscoveryStopped(serviceType: String)

    /**
     * 候选服务发现事件单独暴露，是为了让服务层自己决定过滤与解析策略。
     */
    fun onServiceFound(service: LanSyncPlatformServiceInfo)

    /**
     * 服务丢失事件直接带最小信息，是为了让缓存移除逻辑在主机侧也能稳定回归。
     */
    fun onServiceLost(service: LanSyncPlatformServiceInfo)
}

/**
 * 解析监听抽象出来后，解析成功与失败都能在主机测试里精确回放。
 */
internal interface LanSyncResolveListener {
    /**
     * 解析失败保留服务信息，是为了让日志和测试都能定位具体是哪一个广播项异常。
     */
    fun onResolveFailed(service: LanSyncPlatformServiceInfo, errorCode: Int)

    /**
     * 解析成功返回 host/port 后，服务层才能把结果写入可连接列表。
     */
    fun onServiceResolved(service: LanSyncPlatformServiceInfo)
}

/**
 * NSD 平台适配层隔离 Android Framework，是为了把服务逻辑回归从系统对象里解耦出来。
 */
internal interface LanSyncNsdPlatform {
    /**
     * 注册服务时统一走适配层，是为了让主机测试能直接回放注册回调。
     */
    fun registerService(service: LanSyncPlatformServiceInfo, listener: LanSyncRegistrationListener)

    /**
     * 注销服务保持单独入口，是为了让服务层继续以监听器为生命周期句柄。
     */
    fun unregisterService(listener: LanSyncRegistrationListener)

    /**
     * 启动发现时只关心服务类型，是为了把协议常量集中留在服务层。
     */
    fun startDiscovery(serviceType: String, listener: LanSyncDiscoveryListener)

    /**
     * 停止发现继续按监听器收口，是为了和 Android NSD 的资源模型保持一致。
     */
    fun stopDiscovery(listener: LanSyncDiscoveryListener)

    /**
     * 解析动作单独抽象，是为了让测试直接构造 host/port 结果而不依赖真实网络。
     */
    fun resolveService(service: LanSyncPlatformServiceInfo, listener: LanSyncResolveListener)
}

/**
 * Multicast lock 抽象出来后，发现生命周期测试就能直接断言资源有没有正确收放。
 */
internal interface LanSyncMulticastLock {
    /**
     * 关闭引用计数能避免页面重复进入时留下难以推理的锁状态。
     */
    fun setReferenceCounted(referenceCounted: Boolean)

    /**
     * 启动发现时获取锁，是为了维持 Wi-Fi 组播可见性。
     */
    fun acquire()

    /**
     * 结束发现时释放锁，是为了防止同步页退出后继续占用系统资源。
     */
    fun release()

    /**
     * 只暴露持有状态即可满足释放判断，避免测试或服务层接触更多平台细节。
     */
    val isHeld: Boolean
}

/**
 * 锁工厂独立出来后，服务层就不需要知道 WifiManager 的具体构造细节。
 */
internal interface LanSyncMulticastLockFactory {
    /**
     * 统一用 tag 创建锁，是为了让日志和系统调试里保留稳定标识。
     */
    fun create(tag: String): LanSyncMulticastLock
}

/**
 * Android NSD 适配器只负责类型转换，是为了把业务判断继续留在 LanSyncNsdService。
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

/**
 * Android Wi-Fi 锁工厂保持在适配层，是为了让服务逻辑完全不依赖 WifiManager API。
 */
internal class AndroidLanSyncMulticastLockFactory(
    context: Context
) : LanSyncMulticastLockFactory {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

    /**
     * 每次创建都返回统一包装器，是为了把平台锁状态隐藏在最小接口之后。
     */
    override fun create(tag: String): LanSyncMulticastLock =
        AndroidLanSyncMulticastLock(wifiManager.createMulticastLock(tag))
}

/**
 * Android 锁包装器只暴露服务层真正需要的操作，是为了减少测试与业务代码对 framework API 的耦合。
 */
internal class AndroidLanSyncMulticastLock(
    private val delegate: WifiManager.MulticastLock
) : LanSyncMulticastLock {
    /**
     * 关闭引用计数透传给系统锁，是为了保持线上行为和原始实现一致。
     */
    override fun setReferenceCounted(referenceCounted: Boolean) {
        delegate.setReferenceCounted(referenceCounted)
    }

    /**
     * 获取锁时直接委托系统实现，是为了保留 Wi-Fi 组播可见性的真实语义。
     */
    override fun acquire() {
        delegate.acquire()
    }

    /**
     * 释放锁继续走系统实现，是为了把资源生命周期交还给平台。
     */
    override fun release() {
        delegate.release()
    }

    /**
     * 暴露只读持有状态，是为了让服务层决定是否需要释放。
     */
    override val isHeld: Boolean
        get() = delegate.isHeld
}

/**
 * 平台值对象到 Android NSD 类型的转换集中在单点，是为了避免字段映射散落在多个回调里。
 */
private fun LanSyncPlatformServiceInfo.toAndroidServiceInfo(): NsdServiceInfo = NsdServiceInfo().apply {
    serviceName = this@toAndroidServiceInfo.serviceName
    serviceType = this@toAndroidServiceInfo.serviceType
    port = this@toAndroidServiceInfo.port
}

/**
 * Android NSD 类型回转成平台值对象，是为了让上层始终面对同一套稳定的数据结构。
 */
private fun NsdServiceInfo.toPlatformServiceInfo(): LanSyncPlatformServiceInfo = LanSyncPlatformServiceInfo(
    serviceName = serviceName,
    serviceType = serviceType,
    port = port,
    hostAddress = host?.hostAddress
)
