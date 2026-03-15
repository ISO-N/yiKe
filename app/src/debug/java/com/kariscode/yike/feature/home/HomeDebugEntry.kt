package com.kariscode.yike.feature.home

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kariscode.yike.BuildConfig

/**
 * 调试入口放在 debug source set，是为了让首页只在开发构建里暴露造数能力，
 * 同时避免 release 产物误带内部工具入口。
 */
@Composable
fun HomeDebugEntry(onOpenDebug: () -> Unit) {
    if (!BuildConfig.DEBUG) return

    Button(onClick = onOpenDebug) {
        Text("调试工具")
    }
}
