package com.kariscode.yike.core.ui.message

/**
 * 异常 message 的可读性和可用性在不同实现里差异很大，
 * 因此把“优先使用异常 message，否则回退到业务兜底文案”的策略集中在一处，
 * 让各个 ViewModel 只表达“我希望用户看到什么兜底信息”，而不反复维护同一段空值判断。
 */
fun Throwable.userMessageOr(fallback: String): String =
    message?.takeIf { it.isNotBlank() }
        ?: cause?.message?.takeIf { it.isNotBlank() }
        ?: fallback


