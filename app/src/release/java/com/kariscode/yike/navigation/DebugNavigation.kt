package com.kariscode.yike.navigation

import androidx.navigation.NavGraphBuilder

/**
 * release 版本不注册调试路由，原因是仅隐藏按钮并不足以阻止其他路径误触达调试页面。
 */
@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.addDebugDestination(onBack: () -> Unit) {
}
