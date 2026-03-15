package com.kariscode.yike.feature.debug

/**
 * 调试页把执行中、结果和错误统一收敛到单一状态模型，
 * 是为了让开发者在重复生成测试数据时始终看到可追踪反馈，而不是只依赖一次性提示。
 */
data class DebugUiState(
    val isGenerating: Boolean = false,
    val statusMessage: String = "准备生成随机测试数据。",
    val createdDeckCount: Int = 0,
    val createdCardCount: Int = 0,
    val createdQuestionCount: Int = 0,
    val errorMessage: String? = null
)
