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
 * 复习页将来会承载“问题 -> 显示答案 -> 评分 -> 下一题”的流程状态机；
 * 先落页面壳能让后续把状态机集中在 ViewModel/UseCase 中，而不是在 Composable 中拼流程分支。
 */
@Composable
fun ReviewCardScreen(
    cardId: String,
    onExit: () -> Unit,
    onNextCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习") },
                navigationIcon = { IconButton(onClick = onExit) { Text("退出") } }
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
            Text("cardId: $cardId")
            Text("复习页（占位）：后续接入逐题流程、答案展示与四档评分。")
            Button(onClick = onNextCard) {
                Text("继续下一张")
            }
        }
    }
}

