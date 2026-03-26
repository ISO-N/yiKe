package com.kariscode.yike.feature.card

import com.kariscode.yike.domain.model.CardSummary
import kotlinx.coroutines.Job

/**
 * 熟练度摘要刷新策略抽成 delegate，是为了把“何时重算/如何去抖/如何取消上一轮”从 ViewModel 主流程中拿掉，
 * 避免列表流频繁发射时摘要计算逻辑继续侵入页面其它状态机分支。
 */
internal class MasterySummaryDelegate {
    private var refreshJob: Job? = null
    private var lastSignatureHash: Long? = null

    /**
     * 列表重试时必须清空历史签名与任务句柄，是为了保证恢复后的首次发射一定会触发一次摘要刷新。
     */
    fun reset() {
        refreshJob?.cancel()
        refreshJob = null
        lastSignatureHash = null
    }

    /**
     * 列表发射后仅在“影响熟练度分布”的字段发生变化时触发刷新，
     * 这样既能避免编辑标题造成无意义查询，也能确保复习评分改变 due 状态时摘要及时更新。
     */
    fun onItemsChanged(
        items: List<CardSummary>,
        launchRefresh: () -> Job
    ) {
        val currentHash = buildSignatureHash(items)
        if (currentHash == lastSignatureHash) {
            return
        }
        lastSignatureHash = currentHash
        refreshJob?.cancel()
        refreshJob = launchRefresh()
    }

    /**
     * 签名只保留会影响熟练度统计的字段，是为了让“是否需要重算”保持轻量；
     * 用 hash 而不是字符串拼接，是为了减少短生命周期的中间对象分配。
     */
    private fun buildSignatureHash(items: List<CardSummary>): Long {
        var hash = 1125899906842597L
        items.forEach { summary ->
            hash = hash * 31 + summary.card.id.hashCode()
            hash = hash * 31 + summary.questionCount
            hash = hash * 31 + summary.dueQuestionCount
        }
        return hash
    }
}

