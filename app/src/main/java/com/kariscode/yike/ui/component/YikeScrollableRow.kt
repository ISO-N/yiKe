package com.kariscode.yike.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 横向可滚动行统一沉淀到共享组件，是为了让筛选芯片、标签和轻量徽标在窄屏下保持一致的浏览节奏，
 * 避免滚动行为和间距策略散落在多个页面里逐渐漂移。
 */
@Composable
fun YikeScrollableRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(LocalYikeSpacing.current.sm),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}
