package com.kariscode.yike.data.backup

import com.kariscode.yike.core.time.TimeTextFormatter

/**
 * 备份里的提醒时间需要同时被导出、校验和恢复复用，
 * 因此把 `HH:mm` 规则收口到单点，能避免不同路径各自实现后逐渐漂移。
 */
object BackupReminderTimeCodec {
    /**
     * 导出时固定输出两位数时间文本，可让备份结构稳定且更利于人工检查。
     */
    fun format(hour: Int, minute: Int): String = TimeTextFormatter.formatHourMinute(hour, minute)

    /**
     * 恢复与校验共用同一解析入口，能确保格式错误在所有路径上得到一致处理。
     */
    fun parse(value: String): Pair<Int, Int> {
        val parts = value.split(":")
        require(parts.size == 2) { "备份文件无效或版本不兼容" }
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()
        require(hour in 0..23 && minute in 0..59) { "备份文件无效或版本不兼容" }
        return hour to minute
    }
}
