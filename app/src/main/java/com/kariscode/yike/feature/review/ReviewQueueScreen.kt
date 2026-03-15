package com.kariscode.yike.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 复习队列路由的存在是为了把“选择下一张待复习卡片”的路由逻辑从具体页面中剥离出来，
 * 这样复习页只需关心本卡内逐题流程，避免单个 ViewModel 同时承担队列与流程的双重复杂度。
 */
@Composable
fun ReviewQueueScreen(
    onBack: () -> Unit,
    onOpenNextCard: (cardId: String) -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习队列") },
                navigationIcon = { IconButton(onClick = onBack) { Text("返回") } }
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("复习队列（占位）：后续自动路由到下一张待复习卡片或返回首页完成态。")
            Button(onClick = { onOpenNextCard("card_demo") }) {
                Text("进入示例复习卡片")
            }
            Button(onClick = onBackToHome) {
                Text("返回首页")
            }
        }
    }
}

