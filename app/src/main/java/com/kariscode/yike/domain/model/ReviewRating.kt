package com.kariscode.yike.domain.model

/**
 * 评分枚举是调度规则的核心输入之一，必须放在 domain 层以避免 UI 或 data 层各自定义导致含义漂移；
 * 这样调度器、备份校验与 UI 文案都能以同一枚举为准。
 */
enum class ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY
}

