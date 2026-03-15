package com.kariscode.yike.core.result

/**
 * 用结构化结果替代直接抛异常/返回 null，目的是让 domain 到 ui 的错误路径可控，
 * 并为“用户可理解的错误提示”和“开发可定位的错误信息”留出明确承载位置。
 */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/**
 * 统一错误模型能避免 UI 依赖底层异常类型，从而保持 data 层的实现细节可替换。
 */
data class AppError(
    val message: String,
    val cause: Throwable? = null
)

