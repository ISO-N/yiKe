package com.kariscode.yike.ui.theme

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.kariscode.yike.domain.model.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * 主题切换测试直接锁定系统栏图标明暗，是为了防止后续只顾着改 Compose 配色，
 * 却再次漏掉边到边窗口真正显示在最上层的状态栏外观。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class YikeThemeSystemBarAppearanceTest {

    /**
     * 深色主题必须关闭 light status bar 外观，
     * 否则深色图标会落到深色背景上，用户会误以为状态栏消失。
     */
    @Test
    fun yikeTheme_darkModeDisablesLightStatusBarAppearance() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.setContent {
            YikeTheme(themeMode = ThemeMode.DARK) {}
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertFalse(insetsController.isAppearanceLightStatusBars)
    }

    /**
     * 浅色主题必须启用 light status bar 外观，
     * 这样系统才能使用深色图标维持与浅色顶部背景的对比度。
     */
    @Test
    fun yikeTheme_lightModeEnablesLightStatusBarAppearance() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        activity.setContent {
            YikeTheme(themeMode = ThemeMode.LIGHT) {}
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertTrue(insetsController.isAppearanceLightStatusBars)
    }
}
