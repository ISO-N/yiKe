package com.kariscode.yike.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/**
 * ViewModel 工厂统一走同一个 helper，是为了把样板化的强转与匿名对象收敛到单点，
 * 这样后续新增页面时更容易保持注入方式一致，也能减少重复代码带来的维护噪音。
 */
inline fun <reified T : ViewModel> typedViewModelFactory(
    crossinline creator: () -> T
): ViewModelProvider.Factory = viewModelFactory {
    initializer { creator() }
}

