package com.kariscode.yike.navigation

import androidx.navigation.NavBackStackEntry
import com.kariscode.yike.domain.model.PracticeOrderMode
import com.kariscode.yike.domain.model.PracticeSessionArgs

/**
 * 路由参数名集中定义，是为了让声明与读取走同一份标识，避免字符串字面量在导航图里散落复制。
 */
object NavArguments {
    const val DECK_ID = "deckId"
    const val CARD_ID = "cardId"
    const val TAG = "tag"
    const val DECK_IDS = "deckIds"
    const val CARD_IDS = "cardIds"
    const val QUESTION_IDS = "questionIds"
    const val ORDER_MODE = "orderMode"
    const val CREATE_CARD = "createCard"
}

/**
 * 必填路由参数在导航层尽早失败，是为了把路由拼接错误定位在入口处，
 * 避免把空字符串继续传给页面后才在更深层暴露成难定位的问题。
 */
fun NavBackStackEntry.requireStringArg(name: String): String =
    arguments?.getString(name)
        ?: error("缺少必填导航参数: $name")

/**
 * 可选路由参数统一经由同一入口读取，是为了让“允许缺省”和“必须存在”的语义边界保持清晰。
 */
fun NavBackStackEntry.optionalStringArg(name: String): String? =
    arguments?.getString(name)

/**
 * 练习模式的多选参数需要从 query 字符串恢复集合，因此统一解析可以避免每个入口各自 split。
 */
fun NavBackStackEntry.optionalStringListArg(name: String): List<String> =
    optionalStringArg(name)
        ?.split(",")
        ?.map(String::trim)
        ?.filter(String::isNotBlank)
        ?.distinct()
        .orEmpty()

/**
 * 练习路由参数恢复到结构化对象后，设置页与会话页就能共享同一份选择协议。
 */
fun NavBackStackEntry.toPracticeSessionArgs(): PracticeSessionArgs = PracticeSessionArgs(
    deckIds = optionalStringListArg(NavArguments.DECK_IDS),
    cardIds = optionalStringListArg(NavArguments.CARD_IDS),
    questionIds = optionalStringListArg(NavArguments.QUESTION_IDS),
    orderMode = PracticeOrderMode.fromStorageValue(optionalStringArg(NavArguments.ORDER_MODE))
).normalized()
