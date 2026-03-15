package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.Deck
import com.kariscode.yike.domain.model.DeckSummary
import kotlinx.coroutines.flow.Flow

/**
 * DeckRepository 定义“卡组语义”的数据访问边界，
 * 这样内容管理 UI 与复习统计都只依赖统一接口，不会直接耦合 Room 查询细节。
 */
interface DeckRepository {
    /**
     * 使用 Flow 能让列表在本地数据变更时自动更新，减少 UI 端手动刷新导致的状态错位。
     */
    fun observeActiveDecks(): Flow<List<Deck>>

    /**
     * 为列表提供带统计的聚合流，可以把统计口径集中在 data 层实现，
     * 并避免上层用例/页面引入 N+1 查询。
     */
    fun observeActiveDeckSummaries(nowEpochMillis: Long): Flow<List<DeckSummary>>

    /**
     * 首页只展示少量最近卡组，提供限量快照查询可以避免先订阅全量流再在上层截断。
     */
    suspend fun listRecentActiveDeckSummaries(nowEpochMillis: Long, limit: Int): List<DeckSummary>

    /**
     * 以 ID 获取单个对象是为了配合导航参数与进程重建，
     * 页面只需要持有 ID 就能重新加载所需信息。
     */
    suspend fun findById(deckId: String): Deck?

    /**
     * Upsert 能减少“新建/编辑”分支，从而让用例更聚焦于业务校验而不是存储细节。
     */
    suspend fun upsert(deck: Deck)

    /**
     * 归档优先于删除是为了降低误删风险，并让历史内容可恢复。
     */
    suspend fun setArchived(deckId: String, archived: Boolean, updatedAt: Long)

    /**
     * 物理删除仅用于用户明确确认的高风险操作，并依赖级联约束清理下层数据。
     */
    suspend fun delete(deckId: String)
}
