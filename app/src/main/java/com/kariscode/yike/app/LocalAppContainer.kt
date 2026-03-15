package com.kariscode.yike.app

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 通过 CompositionLocal 提供容器，可以在不引入 DI 框架的前提下让各页面就近获取依赖，
 * 并避免把 container 参数层层透传导致导航与 UI 函数签名膨胀。
 */
val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer 未注入，请在 YikeApp 中提供 LocalAppContainer。")
}

