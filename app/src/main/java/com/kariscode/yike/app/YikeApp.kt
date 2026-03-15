package com.kariscode.yike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.kariscode.yike.navigation.YikeNavGraph

/**
 * 将导航与页面根节点集中在一个 Composable 中，可以让 Activity 保持“只负责承载”的职责，
 * 从而在首版就形成稳定的 UI 入口，降低后续引入多窗口/深链路时的改动面。
 */
@Composable
fun YikeApp(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    CompositionLocalProvider(LocalAppContainer provides container) {
        YikeNavGraph(
            navController = navController,
            modifier = modifier
        )
    }
}
