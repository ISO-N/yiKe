package com.kariscode.yike.feature.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 调试页独立成屏幕，是为了把“快速造数”与正式用户路径彻底分离，
 * 避免首页在生产语境里混入只服务开发的说明和风险提示。
 */
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<DebugViewModel>(
        factory = DebugViewModel.factory(
            database = container.database,
            dispatchers = container.dispatchers,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            YikeTopAppBar(
                title = "调试工具",
                navigationAction = NavigationAction(label = "返回", onClick = onBack)
            )
        },
        modifier = modifier
    ) { padding ->
        DebugContent(
            uiState = uiState,
            onGenerate = viewModel::generateRandomData,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * 页面内容单独拆分后，后续无论补充更多调试动作还是加 UI 测试，都不需要耦合导航壳。
 */
@Composable
private fun DebugContent(
    uiState: DebugUiState,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("默认会生成 2 个卡组，每个卡组 3-5 张卡片，每张卡片 2-3 个问题。")
        Text("至少 20% 的问题会在今天到期，方便直接验证首页统计与复习入口。")
        Button(onClick = onGenerate, enabled = !uiState.isGenerating) {
            Text(if (uiState.isGenerating) "生成中…" else "生成随机数据")
        }
        Text(uiState.statusMessage)
        if (uiState.createdQuestionCount > 0) {
            Text(
                "最近一次结果：${uiState.createdDeckCount} 个卡组 / " +
                    "${uiState.createdCardCount} 张卡片 / ${uiState.createdQuestionCount} 个问题"
            )
        }
        if (uiState.errorMessage != null) {
            Text("错误：${uiState.errorMessage}")
        }
    }
}
