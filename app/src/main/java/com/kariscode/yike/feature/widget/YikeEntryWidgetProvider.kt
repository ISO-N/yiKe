package com.kariscode.yike.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.kariscode.yike.R
import com.kariscode.yike.app.MainActivity
import com.kariscode.yike.navigation.YikeAppLinks

/**
 * Widget 以“桌面入口”的方式存在，是为了让用户不必先打开应用再寻找入口，
 * 在碎片时间也能快速进入复习或开始补内容，从而降低使用门槛。
 */
class YikeEntryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateAppWidget(context = context, appWidgetManager = appWidgetManager, appWidgetId = appWidgetId)
        }
    }

    companion object {
        /**
         * 更新 RemoteViews 的逻辑集中到 companion，是为了让 onUpdate 与后续可能的刷新入口复用同一实现，
         * 避免 Widget 行为在不同回调里分叉。
         */
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_yike_entry)
            views.setOnClickPendingIntent(
                R.id.widget_action_review,
                buildLaunchPendingIntent(context = context, dataIntent = Intent(Intent.ACTION_VIEW, YikeAppLinks.shortcutReviewUri()))
            )
            views.setOnClickPendingIntent(
                R.id.widget_action_new_card,
                buildLaunchPendingIntent(context = context, dataIntent = Intent(Intent.ACTION_VIEW, YikeAppLinks.shortcutNewCardUri()))
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        /**
         * PendingIntent 显式指向 MainActivity 并承载 Uri，是为了让入口协议统一走同一套解析逻辑，
         * 并规避 RemoteViews 无法稳定携带 extras 的限制。
         */
        private fun buildLaunchPendingIntent(context: Context, dataIntent: Intent): PendingIntent {
            val intent = Intent(dataIntent.action, dataIntent.data, context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
            return PendingIntent.getActivity(
                context,
                intent.dataString.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag
            )
        }
    }
}

