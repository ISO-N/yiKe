package com.kariscode.yike.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.component.YikeTopAppBar

/**
 * 首版首页先作为“总入口”存在，原因是导航与分层落地阶段更需要稳定的路径结构，
 * 而不是立刻在首页堆叠复杂展示；后续再把待复习统计和空/错状态接入这里。
 */
@Composable
fun HomeScreen(
    onStartReview: () -> Unit,
    onOpenDeckList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val viewModel = viewModel<HomeViewModel>(
        factory = HomeViewModel.factory(
            questionRepository = container.questionRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { YikeTopAppBar(title = "忆刻") },
        modifier = modifier
    ) { padding ->
        HomeContent(
            uiState = uiState,
            onRetry = viewModel::refresh,
            onStartReview = onStartReview,
            onOpenDeckList = onOpenDeckList,
            onOpenSettings = onOpenSettings,
            onOpenDebug = onOpenDebug,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }
}

/**
 * 首页主体抽成独立展示层，是为了让 UI 测试能直接覆盖空状态、错误态和成功态文案，
 * 而不依赖真实 ViewModel 与数据库。
 */
@Composable
fun HomeContent(
    uiState: HomeUiState,
    onRetry: () -> Unit,
    onStartReview: () -> Unit,
    onOpenDeckList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            uiState.isLoading -> Text("加载中…")
            uiState.errorMessage != null -> {
                Text(uiState.errorMessage ?: "加载失败")
                Button(onClick = onRetry) { Text("重试") }
            }
            else -> {
                val summary = uiState.summary
                val dueQuestions = summary?.dueQuestionCount ?: 0
                val dueCards = summary?.dueCardCount ?: 0
                if (dueQuestions <= 0) {
                    Text("今日暂无待复习")
                    Text("可以先去录入一些内容。")
                    Button(
                        onClick = onOpenDeckList,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("进入卡组")
                    }
                } else {
                    Text("今日待复习：$dueCards 张卡片 / $dueQuestions 个问题")
                    Button(
                        onClick = onStartReview,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("开始复习")
                    }
                }
            }
        }
        Button(
            onClick = onOpenDeckList,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("卡组管理")
        }
        Button(
            onClick = onOpenSettings,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("设置")
        }
        HomeDebugEntry(onOpenDebug = onOpenDebug)
    }
}
