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
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.kariscode.yike.feature.review.ReviewCardScreen
import com.kariscode.yike.feature.review.ReviewQueueScreen
import com.kariscode.yike.feature.settings.SettingsScreen
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

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = YikeDestination.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { primaryDestinationEnterTransition() },
            exitTransition = { primaryDestinationExitTransition() },
            popEnterTransition = { primaryDestinationEnterTransition() },
            popExitTransition = { primaryDestinationExitTransition() }
        ) {
            composable(route = YikeDestination.HOME) {
                HomeScreen(
                    onStartReview = { navController.navigate(YikeDestination.REVIEW_QUEUE) },
                    onOpenDeckList = { navController.navigatePrimaryDestination(YikeDestination.DECK_LIST) },
                    onOpenSettings = { navController.navigatePrimaryDestination(YikeDestination.SETTINGS) },
                    onOpenDebug = { navController.navigate(YikeDestination.DEBUG) }
                )
            }

            composable(route = YikeDestination.DECK_LIST) {
                DeckListScreen(
                    onOpenDeck = { deckId -> navController.navigate(YikeDestination.cardList(deckId)) }
                )
            }

            composable(
                route = YikeDestination.CARD_LIST,
                arguments = listOf(navArgument(NavArguments.DECK_ID) { type = NavType.StringType })
            ) { entry ->
                val deckId = entry.requireStringArg(NavArguments.DECK_ID)
                CardListScreen(
                    deckId = deckId,
                    onBack = { navController.popBackStack() },
                    onEditCard = { cardId ->
                        navController.navigate(YikeDestination.questionEditor(cardId = cardId, deckId = deckId))
                    }
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
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = YikeDestination.REVIEW_QUEUE) {
                ReviewQueueScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNextCard = { cardId -> navController.navigate(YikeDestination.reviewCard(cardId)) },
                    onBackToHome = {
                        navController.popBackStack(route = YikeDestination.HOME, inclusive = false)
                    }
                )
            }

            composable(
                route = YikeDestination.REVIEW_CARD,
                arguments = listOf(navArgument(NavArguments.CARD_ID) { type = NavType.StringType })
            ) { entry ->
                val cardId = entry.requireStringArg(NavArguments.CARD_ID)
                ReviewCardScreen(
                    cardId = cardId,
                    onExit = { navController.popBackStack(route = YikeDestination.HOME, inclusive = false) },
                    onNextCard = { navController.navigate(YikeDestination.REVIEW_QUEUE) }
                )
            }

            composable(route = YikeDestination.SETTINGS) {
                SettingsScreen(
                    onOpenBackupRestore = { navController.navigate(YikeDestination.BACKUP_RESTORE) }
                )
            }

            composable(route = YikeDestination.BACKUP_RESTORE) {
                BackupRestoreScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            addDebugDestination(onBack = { navController.popBackStack() })
        }

        currentPrimaryDestination?.let { destination ->
            YikePrimaryNavigationChrome(
                currentDestination = destination,
                onNavigate = { primaryDestination ->
                    navController.navigatePrimaryDestination(primaryDestination.route)
                }
            )
        }
    }
}

/**
 * 一级入口切换统一走单一导航策略，是为了避免首页、卡组和设置在快速连点时
 * 一部分走 push、一部分走 pop，最终把转场节奏打散成“偶尔直接展开”的状态。
 */
private fun NavHostController.navigatePrimaryDestination(
    route: String
) {
    if (currentDestination?.route == route) {
        return
    }
    navigate(route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.findStartDestination().id) {
            saveState = true
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
 * 进入动画只在一级入口之间启用，是为了让主导航保持桌面式滑动反馈，
 * 同时避免把流内页面也误伤成同一套横滑动画。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryDestinationEnterTransition(): EnterTransition {
    val initialOrder = primaryDestinationOrder(initialState.destination.route)
    val targetOrder = primaryDestinationOrder(targetState.destination.route)
    if (initialOrder == null || targetOrder == null || initialOrder == targetOrder) {
        return EnterTransition.None
    }
    val direction = if (targetOrder > initialOrder) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideIntoContainer(
        towards = direction,
        animationSpec = tween(durationMillis = 380)
    ) + fadeIn(animationSpec = tween(durationMillis = 280))
}

/**
 * 退出动画与进入方向保持镜像，是为了让一级入口像桌面页一样形成连续的“推开/滑入”关系，
 * 而不是前页和目标页各自独立地淡入淡出。
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.primaryDestinationExitTransition(): ExitTransition {
    val initialOrder = primaryDestinationOrder(initialState.destination.route)
    val targetOrder = primaryDestinationOrder(targetState.destination.route)
    if (initialOrder == null || targetOrder == null || initialOrder == targetOrder) {
        return ExitTransition.None
    }
    val direction = if (targetOrder > initialOrder) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
    return slideOutOfContainer(
        towards = direction,
        animationSpec = tween(durationMillis = 380)
    ) + fadeOut(animationSpec = tween(durationMillis = 280))
}
