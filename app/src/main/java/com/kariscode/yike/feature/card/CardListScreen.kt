package com.kariscode.yike.feature.card

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
 * 卡片列表页作为内容管理层级的一部分，先固定“deckId -> 卡片列表 -> 编辑页”的导航关系，
 * 能保证后续在数据层实现级联与归档策略时，UI 不需要改路由就能逐步替换实现。
 */
@Composable
fun CardListScreen(
    deckId: String,
    onBack: () -> Unit,
    onEditCard: (cardId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("卡片列表") },
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
            Text("deckId: $deckId")
            Text("卡片列表（占位）：后续接入新建/编辑/归档/删除与统计信息。")
            Button(onClick = { onEditCard("card_new") }) {
                Text("编辑示例卡片/问题")
            }
        }
    }
}

