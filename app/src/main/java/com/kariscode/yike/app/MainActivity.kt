package com.kariscode.yike.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

/**
 * 以单 Activity 作为入口能把导航、依赖装配和系统初始化收敛到一个稳定边界，
 * 避免首版在页面层提前引入多 Activity 复杂度导致分层失控。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as YikeApplication).container
            YikeApp(
                container = container,
                windowSizeClass = calculateWindowSizeClass(this)
            )
        }
    }
}
