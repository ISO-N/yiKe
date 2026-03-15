package com.kariscode.yike.domain.model

/**
 * 评分提交流水同时返回更新后的题目与新增的复习记录，
 * 是为了让调用方在一次成功结果里拿到完整上下文，而不是再发起额外查询扩大事务外不一致窗口。
 */
data class ReviewSubmission(
    val updatedQuestion: Question,
    val reviewRecord: ReviewRecord
)
