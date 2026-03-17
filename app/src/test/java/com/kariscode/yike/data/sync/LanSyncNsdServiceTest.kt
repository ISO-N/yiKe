package com.kariscode.yike.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanSyncNsdService 测试锁定 NSD 生命周期、自发现过滤和解析缓存更新，
 * 避免这段平台边界逻辑长期只能依赖真机手点回归。
 */
class LanSyncNsdServiceTest {

    /**
     * 系统若把本机广播名改写，发现流程仍必须按最终名字过滤自己，
     * 否则同步页会把本机错误显示成可配对设备。
     */
    @Test
    fun startDiscovery_filtersLocalServiceAndPublishesResolvedRemotePeer() {
        val platform = FakeNsdPlatform()
        val lockFactory = FakeMulticastLockFactory()
        val service = LanSyncNsdService(
            nsdPlatform = platform,
            multicastLockFactory = lockFactory
        )

        service.registerService(serviceName = "yike-local", port = 9420)
        service.startDiscovery()
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "yike-local(2)",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9420
            )
        )
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "peer-b",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9521
            )
        )
        platform.resolveWithHost(
            serviceName = "peer-b",
            hostAddress = "192.168.1.11"
        )

        val discovered = service.services.value

        assertEquals(1, platform.registerRequests.size)
        assertEquals(1, platform.startDiscoveryRequests.size)
        assertTrue(lockFactory.lock.acquireCalls == 1)
        assertTrue(lockFactory.lock.referenceCounted == false)
        assertEquals(listOf("peer-b"), discovered.map(LanSyncDiscoveredService::serviceName))
        assertEquals("192.168.1.11", discovered.single().hostAddress)
    }

    /**
     * 重复解析同名服务时必须按 serviceName 做 upsert，
     * 否则局域网广播抖动会让设备列表不断出现重复项。
     */
    @Test
    fun resolvedService_upsertsByServiceNameAndSortsAlphabetically() {
        val platform = FakeNsdPlatform()
        val service = LanSyncNsdService(
            nsdPlatform = platform,
            multicastLockFactory = FakeMulticastLockFactory()
        )

        service.startDiscovery()
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "peer-b",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9521
            )
        )
        platform.resolveWithHost(serviceName = "peer-b", hostAddress = "192.168.1.11")
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "peer-a",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9520
            )
        )
        platform.resolveWithHost(serviceName = "peer-a", hostAddress = "192.168.1.10")
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "peer-b",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9522
            )
        )
        platform.resolveWithHost(serviceName = "peer-b", hostAddress = "192.168.1.12")

        val discovered = service.services.value

        assertEquals(listOf("peer-a", "peer-b"), discovered.map(LanSyncDiscoveredService::serviceName))
        assertEquals("192.168.1.12", discovered.last().hostAddress)
        assertEquals(9522, discovered.last().port)
    }

    /**
     * 停止发现时必须清空缓存并释放 multicast lock，
     * 否则退出同步页后仍会残留过期设备列表和系统资源占用。
     */
    @Test
    fun stopDiscovery_clearsCacheAndReleasesMulticastLock() {
        val platform = FakeNsdPlatform()
        val lockFactory = FakeMulticastLockFactory()
        val service = LanSyncNsdService(
            nsdPlatform = platform,
            multicastLockFactory = lockFactory
        )

        service.startDiscovery()
        platform.dispatchServiceFound(
            LanSyncPlatformServiceInfo(
                serviceName = "peer-b",
                serviceType = LanSyncConfig.SERVICE_TYPE,
                port = 9521
            )
        )
        platform.resolveWithHost(serviceName = "peer-b", hostAddress = "192.168.1.11")

        service.stopDiscovery()

        assertTrue(service.services.value.isEmpty())
        assertEquals(1, platform.stopDiscoveryRequests)
        assertEquals(1, lockFactory.lock.releaseCalls)
        assertFalse(lockFactory.lock.isHeld)
    }

    /**
     * 卸载广播时只要请求已发出即可证明生命周期被正确收口，
     * 这样会话结束后就不会继续对外暴露旧的本机服务。
     */
    @Test
    fun unregisterService_forwardsToPlatformAndResetsRegistration() {
        val platform = FakeNsdPlatform()
        val service = LanSyncNsdService(
            nsdPlatform = platform,
            multicastLockFactory = FakeMulticastLockFactory()
        )

        service.registerService(serviceName = "yike-local", port = 9420)
        service.unregisterService()

        assertEquals(1, platform.unregisterRequests)
    }

    /**
     * 假平台把发现/解析回调显式暴露出来，是为了让测试直接编排 NSD 生命周期而不依赖真机网络。
     */
    private class FakeNsdPlatform : LanSyncNsdPlatform {
        val registerRequests = mutableListOf<LanSyncPlatformServiceInfo>()
        val resolveRequests = mutableListOf<LanSyncPlatformServiceInfo>()
        val startDiscoveryRequests = mutableListOf<String>()
        var unregisterRequests: Int = 0
        var stopDiscoveryRequests: Int = 0
        private var registrationListener: LanSyncRegistrationListener? = null
        private var discoveryListener: LanSyncDiscoveryListener? = null
        private val resolveListeners = linkedMapOf<String, LanSyncResolveListener>()

        /**
         * 注册时立即回放系统改名，是为了验证服务层会按最终广播名过滤自发现结果。
         */
        override fun registerService(
            service: LanSyncPlatformServiceInfo,
            listener: LanSyncRegistrationListener
        ) {
            registerRequests += service
            registrationListener = listener
            listener.onServiceRegistered(service.copy(serviceName = "${service.serviceName}(2)"))
        }

        /**
         * 注销只计数即可，足以证明服务层有把生命周期收口下推到平台。
         */
        override fun unregisterService(listener: LanSyncRegistrationListener) {
            unregisterRequests += 1
            registrationListener = null
        }

        /**
         * 启动发现后缓存监听，是为了让测试能主动投递发现与丢失事件。
         */
        override fun startDiscovery(serviceType: String, listener: LanSyncDiscoveryListener) {
            startDiscoveryRequests += serviceType
            discoveryListener = listener
            listener.onDiscoveryStarted(serviceType)
        }

        /**
         * 停止发现只要清空监听即可模拟系统收口，避免测试保留旧会话回调。
         */
        override fun stopDiscovery(listener: LanSyncDiscoveryListener) {
            stopDiscoveryRequests += 1
            discoveryListener = null
        }

        /**
         * 解析请求缓存起来后，测试就能在需要的时刻回放成功或失败结果。
         */
        override fun resolveService(service: LanSyncPlatformServiceInfo, listener: LanSyncResolveListener) {
            resolveRequests += service
            resolveListeners[service.serviceName] = listener
        }

        /**
         * 发现事件由测试手动分发，是为了把自发现、重复广播和排序场景都跑成稳定回归。
         */
        fun dispatchServiceFound(service: LanSyncPlatformServiceInfo) {
            discoveryListener?.onServiceFound(service)
        }

        /**
         * 解析成功回放 hostAddress，是为了模拟系统 resolve 后的最小可连接结果。
         */
        fun resolveWithHost(serviceName: String, hostAddress: String) {
            val original = resolveRequests.last { request -> request.serviceName == serviceName }
            resolveListeners[serviceName]?.onServiceResolved(original.copy(hostAddress = hostAddress))
        }
    }

    /**
     * 假锁集中记录获取与释放次数，是为了让测试直接验证发现生命周期是否正确管理系统资源。
     */
    private class FakeMulticastLockFactory : LanSyncMulticastLockFactory {
        val lock = FakeMulticastLock()

        /**
         * 始终返回同一把假锁，是为了让断言只聚焦当前服务实例的资源收放行为。
         */
        override fun create(tag: String): LanSyncMulticastLock = lock
    }

    /**
     * 假锁只保留服务层真正关心的状态，是为了让测试不受平台实现细节影响。
     */
    private class FakeMulticastLock : LanSyncMulticastLock {
        var referenceCounted: Boolean? = null
        var acquireCalls: Int = 0
        var releaseCalls: Int = 0
        override var isHeld: Boolean = false
            private set

        /**
         * 记录引用计数配置，是为了验证服务仍沿用“不计引用次数”的原始资源策略。
         */
        override fun setReferenceCounted(referenceCounted: Boolean) {
            this.referenceCounted = referenceCounted
        }

        /**
         * 获取锁时更新 held 状态，便于测试验证 stopDiscovery 是否真的完成释放。
         */
        override fun acquire() {
            acquireCalls += 1
            isHeld = true
        }

        /**
         * 释放锁后清理 held 状态，是为了模拟真实系统资源回收语义。
         */
        override fun release() {
            releaseCalls += 1
            isHeld = false
        }
    }
}
