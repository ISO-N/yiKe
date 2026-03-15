package com.kariscode.yike.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kariscode.yike.app.YikeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备重启、时间变化与时区变化后都需要重建提醒，
 * 否则“每日固定时间”的语义会因为系统状态变化而长期失效。
 */
class ReminderRestoreReceiver : BroadcastReceiver() {
    /**
     * 广播接收器只做轻量调度恢复，不直接承载业务逻辑，
     * 这样能降低广播执行窗口内的复杂度并避免 ANR 风险。
     */
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching {
                val application = context.applicationContext as YikeApplication
                application.container.reminderScheduler.syncReminderFromRepository()
            }
            pendingResult.finish()
        }
    }
}
