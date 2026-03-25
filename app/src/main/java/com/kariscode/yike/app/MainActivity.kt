package com.kariscode.yike.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 以单 Activity 作为入口能把导航、依赖装配和系统初始化收敛到一个稳定边界，
 * 避免首版在页面层提前引入多 Activity 复杂度导致分层失控。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    /**
     * Shortcuts / Widget 都会通过 Intent 重新唤起 Activity；把最新 Intent 提升为 Compose state，
     * 是为了让单 Activity 架构下的导航仍能响应“运行中收到新入口”的场景，而不需要引入额外 Activity。
     */
    private var latestIntent: Intent? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        latestIntent = intent
        setContent {
            val container = (application as YikeApplication).container
            YikeApp(
                container = container,
                windowSizeClass = calculateWindowSizeClass(this),
                launchIntent = latestIntent
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent = intent
    }
}
