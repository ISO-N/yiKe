package com.kariscode.yike.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.kariscode.yike.feature.backup.BackupRestoreScreen
import com.kariscode.yike.feature.card.CardListScreen
import com.kariscode.yike.feature.deck.DeckListScreen
import com.kariscode.yike.feature.editor.QuestionEditorScreen
import com.kariscode.yike.feature.home.HomeScreen
import com.kariscode.yike.feature.review.ReviewCardScreen
import com.kariscode.yike.feature.review.ReviewQueueScreen
import com.kariscode.yike.feature.settings.SettingsScreen

/**
 * 将导航图独立出来，能避免在 Activity 或某个页面中堆叠路由逻辑，
 * 同时也让后续为导航加测试或深链路支持更容易。
 */
@Composable
fun YikeNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = YikeDestination.HOME,
        modifier = modifier
    ) {
        composable(route = YikeDestination.HOME) {
            HomeScreen(
                onStartReview = { navController.navigate(YikeDestination.REVIEW_QUEUE) },
                onOpenDeckList = { navController.navigate(YikeDestination.DECK_LIST) },
                onOpenSettings = { navController.navigate(YikeDestination.SETTINGS) },
                onOpenDebug = { navController.navigate(YikeDestination.DEBUG) }
            )
        }

        composable(route = YikeDestination.DECK_LIST) {
            DeckListScreen(
                onBack = { navController.popBackStack() },
                onOpenDeck = { deckId -> navController.navigate(YikeDestination.cardList(deckId)) }
            )
        }

        composable(
            route = YikeDestination.CARD_LIST,
            arguments = listOf(navArgument("deckId") { type = NavType.StringType })
        ) { entry ->
            val deckId = entry.arguments?.getString("deckId").orEmpty()
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
                navArgument("cardId") { type = NavType.StringType },
                navArgument("deckId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            val cardId = entry.arguments?.getString("cardId").orEmpty()
            val deckId = entry.arguments?.getString("deckId")
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
            arguments = listOf(navArgument("cardId") { type = NavType.StringType })
        ) { entry ->
            val cardId = entry.arguments?.getString("cardId").orEmpty()
            ReviewCardScreen(
                cardId = cardId,
                onExit = { navController.popBackStack(route = YikeDestination.HOME, inclusive = false) },
                onNextCard = { navController.navigate(YikeDestination.REVIEW_QUEUE) }
            )
        }

        composable(route = YikeDestination.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
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
}
