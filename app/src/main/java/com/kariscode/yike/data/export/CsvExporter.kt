package com.kariscode.yike.data.export

import android.app.Application
import android.net.Uri
import com.kariscode.yike.core.domain.dispatchers.AppDispatchers
import com.kariscode.yike.core.domain.time.toLocalDate
import com.kariscode.yike.data.local.db.dao.QuestionDao
import com.kariscode.yike.data.mapper.decodeTags
import com.kariscode.yike.domain.model.QuestionStatus
import java.io.OutputStreamWriter
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.withContext

/**
 * CSV 导出器负责把活跃题目按稳定字段顺序导出为表格友好的文本，
 * 选择单独实现是为了让备份 JSON 与“给外部工具看的表格导出”保持各自清晰边界，避免互相污染格式语义。
 */
class CsvExporter(
    private val application: Application,
    private val questionDao: QuestionDao,
    private val dispatchers: AppDispatchers,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    /**
     * 导出入口以 Uri 作为目标，是为了复用 SAF 的权限模型，
     * 避免应用自行处理外部存储路径在不同 Android 版本上的权限差异。
     */
    suspend fun exportActiveQuestionsToUri(uri: Uri) {
        withContext(dispatchers.io) {
            val rows = questionDao.listCsvExportRows(activeStatus = QuestionStatus.ACTIVE.storageValue)
            val outputStream = application.contentResolver.openOutputStream(uri)
                ?: error("无法打开导出目标：$uri")
            outputStream.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).buffered().use { writer ->
                    writer.appendLine(CSV_HEADER)
                    rows.forEach { row ->
                        writer.appendLine(
                            listOf(
                                row.deckName,
                                row.cardTitle,
                                row.cardDescription,
                                row.prompt,
                                row.answer,
                                decodeTags(row.tagsJson).joinToString(separator = ","),
                                row.stageIndex.toString(),
                                formatDueDate(row.dueAt)
                            ).joinToString(separator = ",") { value -> escapeCsvField(value) }
                        )
                    }
                }
            }
        }
    }

    /**
     * due_date 使用 ISO 本地日期格式，是为了让 Excel/Numbers/Google Sheets 这类工具能直接识别日期列，
     * 同时保持跨语言环境下的可预测性。
     */
    private fun formatDueDate(epochMillis: Long): String =
        epochMillis.toLocalDate(zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE)

    /**
     * CSV 转义采用标准双引号包裹并对引号做双写，
     * 这样逗号、换行和双引号都不会破坏列结构。
     */
    private fun escapeCsvField(raw: String): String {
        val value = raw.trimEnd('\u0000')
        val mustQuote = value.any { ch -> ch == ',' || ch == '"' || ch == '\n' || ch == '\r' }
        if (!mustQuote) {
            return value
        }
        return buildString {
            append('"')
            value.forEach { ch ->
                if (ch == '"') append("\"\"") else append(ch)
            }
            append('"')
        }
    }

    private companion object {
        const val CSV_HEADER: String = "deck_name,card_title,card_description,prompt,answer,tags,stage,due_date"
    }
}

