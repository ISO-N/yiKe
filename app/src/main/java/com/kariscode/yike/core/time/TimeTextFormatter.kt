package com.kariscode.yike.core.time

/**
 * 小时分钟文本在备份、设置页和提醒说明里都必须保持同一口径，
 * 因此抽到 core 层可以避免不同模块各自维护 `%02d:%02d` 模板后逐渐漂移。
 */
object TimeTextFormatter {
    /**
     * 固定使用两位数输出，是为了让备份文本稳定可读，也让设置页展示与解析规则天然对齐。
     */
    fun formatHourMinute(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)
}
