package com.kariscode.yike.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * 调试源集仍有少量页面需要在不接入 Koin 的前提下创建 ViewModel，
 * 保留这个 helper 能把那部分样板压缩在单点，而不把主业务层重新拉回双注入体系。
 */
inline fun <reified T : ViewModel> typedViewModelFactory(
    crossinline creator: () -> T
): ViewModelProvider.Factory = viewModelFactory {
    initializer { creator() }
}
