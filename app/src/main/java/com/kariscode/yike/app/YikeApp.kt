package com.kariscode.yike.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kariscode.yike.data.settings.SettingsConstants
import com.kariscode.yike.domain.model.AppSettings
import com.kariscode.yike.domain.model.ThemeMode
import com.kariscode.yike.navigation.YikeNavGraph
import com.kariscode.yike.ui.theme.LocalYikeAdaptiveLayout
import com.kariscode.yike.ui.theme.YikeTheme
import com.kariscode.yike.ui.theme.yikeAdaptiveLayoutFor

/**
 * 将导航与页面根节点集中在一个 Composable 中，可以让 Activity 保持“只负责承载”的职责，
 * 从而在首版就形成稳定的 UI 入口，降低后续引入多窗口/深链路时的改动面。
 */
@Composable
fun YikeApp(
    container: AppContainer,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val settings = container.appSettingsRepository.observeSettings().collectAsStateWithLifecycle(
        initialValue = AppSettings(
            dailyReminderEnabled = false,
            dailyReminderHour = 20,
            dailyReminderMinute = 0,
            schemaVersion = SettingsConstants.SCHEMA_VERSION,
            backupLastAt = null,
            themeMode = ThemeMode.LIGHT
        )
    )
    YikeTheme(themeMode = settings.value.themeMode) {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalYikeAdaptiveLayout provides yikeAdaptiveLayoutFor(windowSizeClass.widthSizeClass)
        ) {
            YikeNavGraph(
                navController = navController,
                modifier = modifier
            )
        }
    }
}
