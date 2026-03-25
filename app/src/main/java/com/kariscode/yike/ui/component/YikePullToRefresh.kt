package com.kariscode.yike.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 下拉刷新能力抽成统一壳层，是为了让不同页面在接入 Material3 手势时复用同一套品牌化指示器样式，
 * 并确保刷新逻辑始终落到各自 ViewModel 的 refresh() 入口，避免出现重复数据源或状态抖动。
 * 刷新完成后显式重建一次状态，是为了兜住 Material3 在“快速刷新且数据未变化”场景下
 * 偶发保留旧位移的情况，避免指示器停在页面顶部不消失。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YikePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var stateResetVersion by remember { mutableIntStateOf(0) }
    var previousRefreshing by remember { mutableStateOf(isRefreshing) }
    val state = remember(stateResetVersion) { PullToRefreshState() }

    LaunchedEffect(isRefreshing) {
        if (previousRefreshing && !isRefreshing) {
            stateResetVersion += 1
        }
        previousRefreshing = isRefreshing
    }

    PullToRefreshBox(
        modifier = modifier,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                color = MaterialTheme.colorScheme.primary
            )
        },
        content = { content() }
    )
}

