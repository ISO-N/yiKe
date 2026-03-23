package com.kariscode.yike.feature.webconsole

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kariscode.yike.app.LocalAppContainer
import com.kariscode.yike.app.WebConsoleForegroundService
import com.kariscode.yike.domain.model.WebConsoleState
import com.kariscode.yike.navigation.YikeNavigator
import com.kariscode.yike.ui.component.YikeBadge
import com.kariscode.yike.ui.component.YikeFlowScaffold
import com.kariscode.yike.ui.component.YikeListItemCard
import com.kariscode.yike.ui.component.YikeOperationFeedback
import com.kariscode.yike.ui.component.YikePrimaryButton
import com.kariscode.yike.ui.component.YikeScrollableColumn
import com.kariscode.yike.ui.component.YikeSecondaryButton
import com.kariscode.yike.ui.component.YikeStateBanner
import com.kariscode.yike.ui.component.YikeWarningCard
import com.kariscode.yike.ui.component.backNavigationAction
import com.kariscode.yike.ui.format.formatLocalDateTime
import com.kariscode.yike.ui.theme.LocalYikeSpacing

/**
 * 网页后台页独立成单独流程页，是为了把“对局域网暴露服务”这种高风险能力与普通设置项隔离开。
 */
@Composable
fun WebConsoleScreen(
    navigator: YikeNavigator,
    modifier: Modifier = Modifier
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel = viewModel<WebConsoleViewModel>(
        factory = WebConsoleViewModel.factory(
            webConsoleRepository = container.webConsoleRepository
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    YikeFlowScaffold(
        title = "网页后台",
        subtitle = "让同一局域网或热点下的其他设备通过 IP:端口访问网页版控制台。",
        navigationAction = backNavigationAction(onClick = navigator::back)
    ) { padding ->
        WebConsoleContent(
            state = uiState.state,
            onStart = { WebConsoleForegroundService.start(context) },
            onStop = { WebConsoleForegroundService.stop(context) },
            onRefreshAccessCode = { WebConsoleForegroundService.refreshAccessCode(context) },
            modifier = modifier,
            contentPadding = padding
        )
    }
}

/**
 * 页面主体把风险提示、控制按钮和地址列表集中展示，是为了让用户在同一处完成“启动、记地址、给别人登录”。
 */
@Composable
private fun WebConsoleContent(
    state: WebConsoleState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefreshAccessCode: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalYikeSpacing.current
    YikeScrollableColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        YikeStateBanner(
            title = when {
                state.isStarting -> "网页后台准备中"
                state.isRunning -> "网页后台运行中"
                else -> "网页后台已停止"
            },
            description = when {
                state.isStarting -> "正在准备端口、地址和访问码。"
                state.isRunning -> "当前已开放局域网访问，请把访问码告诉需要登录后台的设备。"
                else -> "启动后，其他设备可以通过手机显示的 IP:端口 登录后台网页。"
            },
            trailing = {
                YikeBadge(
                    text = if (state.isRunning) "运行中" else "未运行"
                )
            }
        )

        YikeWarningCard(
            title = "使用边界",
            description = "仅在你主动启动后才会对局域网开放；访问码刷新后，旧浏览器会立即失效。"
        )

        YikeOperationFeedback(
            successMessage = if (state.isRunning && !state.isStarting) "网页后台已可访问" else null,
            errorMessage = state.lastError,
            successTitle = "服务已启动",
            errorTitle = "服务状态异常"
        )

        YikeListItemCard(
            title = "服务控制",
            summary = state.port?.let { "端口 $it" } ?: "尚未绑定端口",
            supporting = state.lastStartedAt?.let { "最近启动：${formatLocalDateTime(it)}" } ?: "启动后会显示推荐访问地址。"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (state.isRunning || state.isStarting) {
                    YikePrimaryButton(
                        text = "停止服务",
                        onClick = onStop,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    YikePrimaryButton(
                        text = "启动服务",
                        onClick = onStart,
                        modifier = Modifier.weight(1f)
                    )
                }
                YikeSecondaryButton(
                    text = "刷新访问码",
                    onClick = onRefreshAccessCode,
                    modifier = Modifier.weight(1f),
                    enabled = state.isRunning
                )
            }
        }

        YikeListItemCard(
            title = "一次性访问码",
            summary = state.accessCode ?: "------",
            supporting = "浏览器首次进入后台时需要先输入这个 6 位访问码。"
        ) {
            Text("在线会话数：${state.activeSessionCount}")
        }

        if (state.addresses.isEmpty()) {
            YikeListItemCard(
                title = "访问地址",
                summary = "暂无可用地址",
                supporting = "请确认 Wi‑Fi 或热点已开启，然后重新启动服务。"
            )
        } else {
            state.addresses.forEach { address ->
                YikeListItemCard(
                    title = address.label,
                    summary = address.url,
                    supporting = "${address.host}:${address.port}"
                ) {
                    Text(if (address.isRecommended) "推荐优先使用这个地址" else "备用地址")
                }
            }
        }
    }
}
