package com.kariscode.yike.domain.model

/**
 * Question 的状态需要显式建模以支撑“归档后不进入默认列表/待复习”的规则，
 * 否则状态语义容易在查询条件里被硬编码并在多个层次出现偏差。
 */
enum class QuestionStatus {
    ACTIVE,
    ARCHIVED
}

