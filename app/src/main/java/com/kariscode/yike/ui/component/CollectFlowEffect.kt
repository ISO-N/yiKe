package com.kariscode.yike.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow

/**
 * 一次性 effect 的收集模板收口到这里，
 * 是为了让页面层只表达“收到 effect 后要做什么”，而不是反复维护相同的协程样板。
 */
@Composable
fun <T> CollectFlowEffect(
    effectFlow: Flow<T>,
    onEffect: suspend (T) -> Unit
) {
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effectFlow) {
        effectFlow.collect { effect ->
            currentOnEffect(effect)
        }
    }
}
