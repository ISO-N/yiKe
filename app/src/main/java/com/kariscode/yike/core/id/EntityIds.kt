package com.kariscode.yike.core.domain.id

import com.kariscode.yike.core.domain.constant.EntityIdPrefixes
import java.util.UUID

/**
 * 统一收敛实体 ID 的生成策略，目的是把“前缀约定 + 随机性来源”固定在单点，
 * 避免未来需要调整 ID 形态（例如切换到更可读或可排序的方案）时全仓逐个替换。
 */
object EntityIds {
    /**
     * 卡组 ID 前缀集中管理，是为了让数据表、备份格式和导航参数在同一约定下演进。
     */
    fun newDeckId(): String = EntityIdPrefixes.DECK + UUID.randomUUID().toString()

    /**
     * 卡片 ID 的生成放到统一入口，是为了避免不同页面以不同前缀或不同随机源创建导致不可预测的数据形态。
     */
    fun newCardId(): String = EntityIdPrefixes.CARD + UUID.randomUUID().toString()

    /**
     * 题目 ID 的生成与前缀约定绑定，是为了让后续按前缀快速判定实体类型成为可依赖的规则。
     */
    fun newQuestionId(): String = EntityIdPrefixes.QUESTION + UUID.randomUUID().toString()

    /**
     * 复习记录属于历史流水，因此单独使用 review_ 前缀，是为了把“内容实体”和“行为事件”在日志层分开。
     */
    fun newReviewRecordId(): String = "review_" + UUID.randomUUID().toString()

    /**
     * 临时草稿 ID 只在内存态使用，显式加 temp_ 前缀是为了避免误把草稿 ID 当作可持久化实体 ID。
     */
    fun newTempDraftId(): String = "temp_" + UUID.randomUUID().toString()
}


