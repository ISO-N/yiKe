package com.kariscode.yike.feature.home

import androidx.compose.runtime.Composable

/**
 * release 版本显式提供空实现，是为了让主源码保持统一调用点，
 * 同时确保生产包内没有任何调试入口可见。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeDebugEntry(onOpenDebug: () -> Unit) {
}
