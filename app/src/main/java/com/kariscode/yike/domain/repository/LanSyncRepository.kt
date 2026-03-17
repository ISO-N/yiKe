package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.LanSyncSnapshot
import com.kariscode.yike.domain.model.LocalSyncSnapshot
import com.kariscode.yike.domain.model.SyncDevice
import kotlinx.coroutines.flow.Flow

/**
 * 局域网同步仓储隔离了 NSD、HTTP 与备份恢复细节，
 * 这样同步页只表达“发现设备、查看摘要、执行覆盖同步”的业务意图。
 */
interface LanSyncRepository {
    /**
     * 已发现设备通过 Flow 暴露，是为了让局域网广播变化能直接反映到同步页列表而无需手动刷新。
     */
    fun observeDevices(): Flow<List<SyncDevice>>

    /**
     * 本机展示名称由仓储集中提供，是为了保证服务注册名、页面文案和远端清单使用同一设备语义。
     */
    fun getLocalDeviceName(): String

    /**
     * 进入同步页后才启动发现与服务注册，是为了把网络暴露窗口限制在用户主动使用该功能时。
     */
    suspend fun start()

    /**
     * 离开页面后及时关闭发现与服务注册，可以减少电量开销并避免后台持续广播带来的惊扰。
     */
    suspend fun stop()

    /**
     * 覆盖同步前需要先拿到本机规模摘要，
     * 这样页面才能判断是否应先弹出确认提示而不是直接写入本地。
     */
    suspend fun getLocalSnapshot(): LocalSyncSnapshot

    /**
     * 远端摘要单独获取后，同步页可以在真正传输大文件前先完成冲突判断与风险提示。
     */
    suspend fun fetchRemoteSnapshot(device: SyncDevice): LanSyncSnapshot

    /**
     * 真正同步时直接拉取远端完整备份并恢复到本机，
     * 是为了复用现有备份恢复链路而不是再维护第二套写库协议。
     */
    suspend fun importFromDevice(device: SyncDevice)
}
