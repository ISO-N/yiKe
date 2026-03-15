package com.kariscode.yike.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kariscode.yike.ui.theme.YikeTheme

/**
 * 以单 Activity 作为入口能把导航、依赖装配和系统初始化收敛到一个稳定边界，
 * 避免首版在页面层提前引入多 Activity 复杂度导致分层失控。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YikeTheme {
                val container = (application as YikeApplication).container
                YikeApp(container)
            }
        }
    }
}
