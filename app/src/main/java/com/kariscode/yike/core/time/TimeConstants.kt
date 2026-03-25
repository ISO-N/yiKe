package com.kariscode.yike.core.domain.time

/**
 * 统一管理时间相关的常量，
 * 避免在多处硬编码导致维护困难和计算错误。
 */
object TimeConstants {
    const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    const val WEEK_MILLIS = 7L * DAY_MILLIS
}

