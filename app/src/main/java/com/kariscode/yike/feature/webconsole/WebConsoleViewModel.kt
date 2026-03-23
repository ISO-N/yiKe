package com.kariscode.yike.feature.webconsole

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kariscode.yike.core.viewmodel.typedViewModelFactory
import com.kariscode.yike.domain.model.WebConsoleState
import com.kariscode.yike.domain.repository.WebConsoleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 网页后台页状态单独包一层，是为了给页面保留稳定的展示结构，
 * 后续即使仓储状态增加字段，也不必让界面直接暴露底层形状。
 */
data class WebConsoleUiState(
    val state: WebConsoleState
)

/**
 * 网页后台页面 ViewModel 只负责订阅状态，是为了把服务启动与停止继续交给前台服务承载，
 * 从而避免页面退出时把锁屏可访问能力一起带走。
 */
class WebConsoleViewModel(
    webConsoleRepository: WebConsoleRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        WebConsoleUiState(
            state = WebConsoleState(
                isRunning = false,
                isStarting = false,
                port = null,
                addresses = emptyList(),
                accessCode = null,
                accessCodeIssuedAt = null,
                activeSessionCount = 0,
                lastStartedAt = null,
                lastError = null
            )
        )
    )
    val uiState: StateFlow<WebConsoleUiState> = _uiState.asStateFlow()

    init {
        /**
         * 页面直接订阅仓储状态，是为了让服务在锁屏后继续运行时，页面回到前台也能拿到最新访问码和地址。
         */
        viewModelScope.launch {
            webConsoleRepository.observeState().collect { state ->
                _uiState.update { current ->
                    current.copy(state = state)
                }
            }
        }
    }

    companion object {
        /**
         * 工厂显式注入仓储，是为了保持 ViewModel 可测试且不依赖全局静态对象。
         */
        fun factory(
            webConsoleRepository: WebConsoleRepository
        ): ViewModelProvider.Factory = typedViewModelFactory {
            WebConsoleViewModel(webConsoleRepository = webConsoleRepository)
        }
    }
}
