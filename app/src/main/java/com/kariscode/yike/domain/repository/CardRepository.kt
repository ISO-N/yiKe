package com.kariscode.yike.domain.repository

import com.kariscode.yike.domain.model.Card
import com.kariscode.yike.domain.model.CardSummary
import kotlinx.coroutines.flow.Flow

/**
 * CardRepository 把“卡片属于某卡组”的层级关系封装到 domain 接口中，
 * 避免 UI 直接理解外键与查询条件，从而保持层级规则一致。
 */
interface CardRepository {
    /**
     * 以 deckId 维度观察卡片列表能让页面在内容变更后自动刷新，
     * 同时保持“按路由参数加载”的原则，便于进程重建恢复。
     */
    fun observeActiveCards(deckId: String): Flow<List<Card>>

    /**
     * 卡片列表需要的聚合信息（例如题目数量）通过专门的流提供，
     * 以避免页面层逐条查询造成性能问题。
     */
    fun observeActiveCardSummaries(deckId: String): Flow<List<CardSummary>>

    /**
     * 单对象读取用于编辑页根据 cardId 重建表单状态，避免跨页面传对象。
     */
    suspend fun findById(cardId: String): Card?

    /**
     * Upsert 可降低创建/编辑分支复杂度，使上层更聚焦校验与调度初始化规则。
     */
    suspend fun upsert(card: Card)

    /**
     * 归档用于默认列表过滤，避免误删；updatedAt 显式传入便于统一时间策略。
     */
    suspend fun setArchived(cardId: String, archived: Boolean, updatedAt: Long)

    /**
     * 物理删除需要用户确认，并依赖级联约束清理问题与复习记录。
     */
    suspend fun delete(cardId: String)
}
