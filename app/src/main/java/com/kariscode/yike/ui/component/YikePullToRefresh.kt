package com.kariscode.yike.ui.component

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 下拉刷新能力抽成统一壳层，是为了让不同页面在接入 Material3 手势时复用同一套品牌化指示器样式，
 * 并确保刷新逻辑始终落到各自 ViewModel 的 refresh() 入口，避免出现重复数据源或状态抖动。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YikePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberPullToRefreshState()
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

