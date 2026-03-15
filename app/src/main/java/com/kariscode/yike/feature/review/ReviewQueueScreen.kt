package com.kariscode.yike.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.ui.component.NavigationAction
import com.kariscode.yike.ui.component.YikeTopAppBar

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
    val container = LocalAppContainer.current
    val viewModel = viewModel<ReviewQueueViewModel>(
        factory = ReviewQueueViewModel.factory(
            questionRepository = container.questionRepository,
            timeProvider = container.timeProvider
        )
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReviewQueueEffect.NavigateToCard -> onOpenNextCard(effect.cardId)
                ReviewQueueEffect.BackToHomeCompleted -> onBackToHome()
            }
        }
    }

    Scaffold(
        topBar = { YikeTopAppBar(title = "复习队列", navigationAction = NavigationAction(label = "返回", onClick = onBack)) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                uiState.isLoading -> Text("正在选择下一张卡片…")
                uiState.errorMessage != null -> {
                    Text(uiState.errorMessage ?: "加载失败")
                    Button(onClick = viewModel::loadNext) { Text("重试") }
                }
                else -> {
                    Text("正在跳转…")
                    Button(onClick = onBackToHome) { Text("返回首页") }
                }
            }
        }
    }
}
