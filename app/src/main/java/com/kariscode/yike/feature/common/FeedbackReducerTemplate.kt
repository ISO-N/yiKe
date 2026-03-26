package com.kariscode.yike.feature.common

/**
 * 反馈字段（message/errorMessage）的更新模板集中在这里，是为了让不同列表页的 reducer 复用同一套语义：
 * 成功与失败互斥、写入新提示前先清空旧提示。
 */
internal object FeedbackReducerTemplate {
    /**
     * 写入错误提示时清空旧成功提示，是为了避免用户看到互相冲突的反馈。
     */
    fun <Self> withError(state: FeedbackState<Self>, errorMessage: String): Self =
        state.withFeedback(message = null, errorMessage = errorMessage)

    /**
     * 写入成功提示时清空旧错误提示，是为了避免“成功了但仍显示失败”的体验抖动。
     */
    fun <Self> withMessage(state: FeedbackState<Self>, message: String): Self =
        state.withFeedback(message = message, errorMessage = null)
}

