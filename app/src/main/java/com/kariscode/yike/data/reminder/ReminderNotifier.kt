package com.kariscode.yike.data.reminder

/**
 * Worker 只依赖提醒通知接口，而不直接绑定具体 Android 通知实现，
 * 这样测试可以专注验证“何时通知”而不是被 Context 细节牵制。
 */
interface ReminderNotifier {
    /**
     * 每日提醒正文由通知实现负责渲染，业务层只关心数量语义。
     */
    fun showDailyReminder(dueCardCount: Int, dueQuestionCount: Int)
}
