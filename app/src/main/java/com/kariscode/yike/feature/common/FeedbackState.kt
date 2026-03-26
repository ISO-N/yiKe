package com.kariscode.yike.feature.common

/**
 * 列表类页面通常都需要承载一次性的 message/errorMessage，
 * 抽成统一接口是为了让 reducer 层能复用同一套“清旧提示、回写新提示”的模板，避免各处 copy(...) 逐渐漂移。
 */
interface FeedbackState<Self> {
    val message: String?
    val errorMessage: String?

    /**
     * 明确用一个入口更新反馈字段，是为了让“成功提示与错误提示互斥”的约束只维护一处。
     */
    fun withFeedback(message: String?, errorMessage: String?): Self
}

