package com.kariscode.yike.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.kariscode.yike.feature.debug.DebugScreen

/**
 * 调试页面注册放进 debug source set，是为了让 release 构建在路由图层就彻底失去该入口。
 */
fun NavGraphBuilder.addDebugDestination(onBack: () -> Unit) {
    composable(route = YikeDestination.DEBUG) {
        DebugScreen(onBack = onBack)
    }
}
