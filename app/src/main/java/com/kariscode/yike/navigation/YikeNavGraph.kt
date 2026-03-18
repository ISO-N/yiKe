package com.kariscode.yike.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kariscode.yike.feature.backup.BackupRestoreScreen
import com.kariscode.yike.feature.card.CardListScreen
import com.kariscode.yike.feature.deck.DeckListScreen
import com.kariscode.yike.feature.editor.QuestionEditorScreen
import com.kariscode.yike.feature.home.HomeScreen
import com.kariscode.yike.feature.analytics.AnalyticsScreen
import com.kariscode.yike.feature.preview.TodayPreviewScreen
import com.kariscode.yike.feature.recyclebin.RecycleBinScreen
import com.kariscode.yike.feature.review.ReviewCardScreen
import com.kariscode.yike.feature.review.ReviewQueueScreen
import com.kariscode.yike.feature.search.QuestionSearchScreen
import com.kariscode.yike.feature.settings.SettingsScreen
import com.kariscode.yike.feature.sync.LanSyncScreen
import com.kariscode.yike.ui.component.YikePrimaryDestination
import com.kariscode.yike.ui.component.YikePrimaryNavigationChrome

private val primaryDestinationMetadata = listOf(
    YikeDestination.HOME to YikePrimaryDestination.HOME,
    YikeDestination.DECK_LIST to YikePrimaryDestination.DECKS,
    YikeDestination.SETTINGS to YikePrimaryDestination.SETTINGS
)

/**
 * 将导航图独立出来，能避免在 Activity 或某个页面中堆叠路由逻辑，
 * 同时也让后续为导航加测试或深链路支持更容易。
 */
@Composable
fun YikeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentPrimaryDestination = primaryDestinationForRoute(currentBackStackEntry?.destination?.route)
    val navigator = rememberYikeNavigator(navController)

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = YikeDestination.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { appEnterTransition() },
            exitTransition = { appExitTransition() },
            popEnterTransition = { appPopEnterTransition() },
            popExitTransition = { appPopExitTransition() }
        ) {
            composable(route = YikeDestination.HOME) {
                HomeScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.DECK_LIST) {
                DeckListScreen(
                    navigator = navigator
                )
            }

            composable(
                route = YikeDestination.CARD_LIST,
                arguments = listOf(navArgument(NavArguments.DECK_ID) { type = NavType.StringType })
            ) { entry ->
                val deckId = entry.requireStringArg(NavArguments.DECK_ID)
                CardListScreen(
                    deckId = deckId,
                    navigator = navigator
                )
            }

            composable(
                route = YikeDestination.QUESTION_EDITOR,
                arguments = listOf(
                    navArgument(NavArguments.CARD_ID) { type = NavType.StringType },
                    navArgument(NavArguments.DECK_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val cardId = entry.requireStringArg(NavArguments.CARD_ID)
                val deckId = entry.optionalStringArg(NavArguments.DECK_ID)
                QuestionEditorScreen(
                    cardId = cardId,
                    deckId = deckId,
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.REVIEW_QUEUE) {
                ReviewQueueScreen(
                    navigator = navigator
                )
            }

            composable(
                route = YikeDestination.REVIEW_CARD,
                arguments = listOf(navArgument(NavArguments.CARD_ID) { type = NavType.StringType })
            ) { entry ->
                val cardId = entry.requireStringArg(NavArguments.CARD_ID)
                ReviewCardScreen(
                    cardId = cardId,
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.SETTINGS) {
                SettingsScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.BACKUP_RESTORE) {
                BackupRestoreScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.LAN_SYNC) {
                LanSyncScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.RECYCLE_BIN) {
                RecycleBinScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.TODAY_PREVIEW) {
                TodayPreviewScreen(
                    navigator = navigator
                )
            }

            composable(route = YikeDestination.REVIEW_ANALYTICS) {
                AnalyticsScreen(
                    navigator = navigator
                )
            }

            composable(
                route = YikeDestination.QUESTION_SEARCH_ROUTE,
                arguments = listOf(
                    navArgument(NavArguments.DECK_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(NavArguments.CARD_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                QuestionSearchScreen(
                    initialDeckId = entry.optionalStringArg(NavArguments.DECK_ID),
                    initialCardId = entry.optionalStringArg(NavArguments.CARD_ID),
                    navigator = navigator,
                    deckIdForEditor = entry.optionalStringArg(NavArguments.DECK_ID)
                )
            }

            addDebugDestination(onBack = navigator::back)
        }

        currentPrimaryDestination?.let { destination ->
            YikePrimaryNavigationChrome(
                currentDestination = destination,
                onNavigate = { primaryDestination ->
                    navigator.openPrimary(primaryDestination.route)
                }
            )
        }
    }
}


/**
 * 一级入口的顺序被单独抽出来，是为了让首页、卡组和设置能够稳定复用同一套左右切换方向，
 * 让连续点击时的动画仍然像 Android 桌面页那样有明确的空间感。
 */
private fun primaryDestinationOrder(
    route: String?
): Int? = primaryDestinationMetadata.indexOfFirst { (candidateRoute, _) -> candidateRoute == route }
    .takeIf { index -> index >= 0 }

/**
 * 一级目标映射单独集中，是为了让共享导航壳层能够只根据当前 route 判断自身状态，
 * 不需要在 UI 层再硬编码一份首页/卡组/设置的判定逻辑。
 */
private fun primaryDestinationForRoute(
    route: String?
): YikePrimaryDestination? = primaryDestinationMetadata
    .firstOrNull { (candidateRoute, _) -> candidateRoute == route }
    ?.second

/**
 * 一级入口切换是否成立统一由同一入口判断，是为了让一级导航动画和普通流内动画共享一份边界定义。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPrimaryDestinationTransition(): Boolean =
    primaryDestinationOrder(initialState.destination.route) != null &&
        primaryDestinationOrder(targetState.destination.route) != null

/**
 * 一级入口切换方向集中计算后，进入、退出和返回动画都能共享同一套空间方向判断。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryTransitionDirection():
    AnimatedContentTransitionScope.SlideDirection? {
    val initialOrder = primaryDestinationOrder(initialState.destination.route)
    val targetOrder = primaryDestinationOrder(targetState.destination.route)
    if (initialOrder == null || targetOrder == null || initialOrder == targetOrder) {
        return null
    }
    return if (targetOrder > initialOrder) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

/**
 * 进入转场统一由共享的 slide+fade 组合生成，是为了让不同场景只需要表达方向和时长，而不是复制整段动画拼接。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideFadeEnter(
    direction: AnimatedContentTransitionScope.SlideDirection,
    slideDurationMillis: Int,
    fadeDurationMillis: Int
): EnterTransition = slideIntoContainer(
    towards = direction,
    animationSpec = tween(durationMillis = slideDurationMillis)
) + fadeIn(animationSpec = tween(durationMillis = fadeDurationMillis))

/**
 * 退出转场同样走共享组合，是为了让 push/pop 与一级入口切换在收敛后仍保持镜像关系。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideFadeExit(
    direction: AnimatedContentTransitionScope.SlideDirection,
    slideDurationMillis: Int,
    fadeDurationMillis: Int
): ExitTransition = slideOutOfContainer(
    towards = direction,
    animationSpec = tween(durationMillis = slideDurationMillis)
) + fadeOut(animationSpec = tween(durationMillis = fadeDurationMillis))

/**
 * 进入动画只在一级入口之间启用，是为了让主导航保持桌面式滑动反馈，
 * 同时避免把流内页面也误伤成同一套横滑动画。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryDestinationEnterTransition(): EnterTransition {
    val direction = primaryTransitionDirection() ?: return EnterTransition.None
    return slideFadeEnter(
        direction = direction,
        slideDurationMillis = 380,
        fadeDurationMillis = 280
    )
}

/**
 * 退出动画与进入方向保持镜像，是为了让一级入口像桌面页一样形成连续的“推开/滑入”关系，
 * 而不是前页和目标页各自独立地淡入淡出。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryDestinationExitTransition(): ExitTransition {
    val direction = primaryTransitionDirection() ?: return ExitTransition.None
    return slideFadeExit(
        direction = direction,
        slideDurationMillis = 380,
        fadeDurationMillis = 280
    )
}

/**
 * 应用级进入动画统一先判断是否为一级入口切换，若不是则回退到流内页面的轻量横向推进，
 * 这样既保留主导航的桌面式空间感，也让编辑、预览、统计和搜索页面不再像“瞬间硬切”。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.appEnterTransition(): EnterTransition {
    if (isPrimaryDestinationTransition()) {
        return primaryDestinationEnterTransition()
    }
    return slideFadeEnter(
        direction = AnimatedContentTransitionScope.SlideDirection.Left,
        slideDurationMillis = 300,
        fadeDurationMillis = 220
    )
}

/**
 * 普通 push 场景下的退出动画采用与进入同向的轻量推开，是为了让流内页面切换更接近原生任务流节奏。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.appExitTransition(): ExitTransition {
    if (isPrimaryDestinationTransition()) {
        return primaryDestinationExitTransition()
    }
    return slideFadeExit(
        direction = AnimatedContentTransitionScope.SlideDirection.Left,
        slideDurationMillis = 300,
        fadeDurationMillis = 220
    )
}

/**
 * pop 进入动画与 push 方向镜像，是为了让用户在返回时获得明确的“回到上一层”空间反馈。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopEnterTransition(): EnterTransition {
    if (isPrimaryDestinationTransition()) {
        return primaryDestinationEnterTransition()
    }
    return slideFadeEnter(
        direction = AnimatedContentTransitionScope.SlideDirection.Right,
        slideDurationMillis = 300,
        fadeDurationMillis = 220
    )
}

/**
 * pop 退出动画与 pop 进入保持镜像，是为了让返回链路不再只剩淡出，而是具有稳定层级感。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.appPopExitTransition(): ExitTransition {
    if (isPrimaryDestinationTransition()) {
        return primaryDestinationExitTransition()
    }
    return slideFadeExit(
        direction = AnimatedContentTransitionScope.SlideDirection.Right,
        slideDurationMillis = 300,
        fadeDurationMillis = 220
    )
}
