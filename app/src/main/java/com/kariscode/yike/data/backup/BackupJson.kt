package com.kariscode.yike.data.backup

import com.kariscode.yike.core.domain.time.toInstant
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json

/**
 * 备份 JSON 编解码与时间格式化集中在一处，可避免不同路径对时间字符串和容错策略理解不一致。
 */
object BackupJson {
    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 统一写出带时区偏移的时间字符串，能让备份文件既可读又能在恢复时稳定回到 epoch millis。
     */
    fun formatEpochMillis(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        formatter.format(epochMillis.toInstant().atZone(zoneId))

    /**
     * 统一解析时间字符串，可把“时间字段非法”交给校验器集中处理而不是在各个恢复分支里分散 try/catch。
     */
    fun parseEpochMillis(value: String): Long =
        Instant.from(formatter.parse(value)).toEpochMilli()
}

