package com.kariscode.yike.feature.common

/**
 * 表单类草稿往往需要承载一次性的校验反馈，
 * 抽成统一接口是为了让不同编辑弹窗在“何时清理旧提示/如何回写新提示”上保持一致语义，
 * 同时避免每个草稿类型都各自发明一套命名与调用习惯。
 */
interface ValidationMessageDraft<Self> {
    val validationMessage: String?

    /**
     * 校验消息由单点回填，是为了让不同入口（创建/编辑）都复用同一条提示口径。
     */
    fun withValidationMessage(message: String?): Self
}

