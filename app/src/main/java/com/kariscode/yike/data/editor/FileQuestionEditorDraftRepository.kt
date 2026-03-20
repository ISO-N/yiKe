package com.kariscode.yike.data.editor

import android.content.Context
import com.kariscode.yike.domain.model.QuestionEditorDraftItemSnapshot
import com.kariscode.yike.domain.model.QuestionEditorDraftLoadResult
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot
import com.kariscode.yike.domain.repository.QuestionEditorDraftRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 编辑草稿落到应用私有文件目录，是为了在不引入 Room 迁移和同步语义的前提下，
 * 提供进程外可恢复的本地临时状态。
 */
class FileQuestionEditorDraftRepository(
    private val context: Context
) : QuestionEditorDraftRepository {
    /**
     * 统一的 JSON 配置只保留前向兼容和稳定序列化能力，
     * 是为了让草稿文件演进时尽量少受字段扩展影响。
     */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 草稿目录集中在单一子路径下，是为了后续排除系统自动备份和清理旧文件时能精确定位。
     */
    private val draftDirectory: File
        get() = File(context.filesDir, DRAFT_DIRECTORY_NAME)

    /**
     * 读取走 IO 线程是为了避免较大的草稿文件在页面初始化时阻塞主线程首帧。
     */
    override suspend fun loadDraft(cardId: String): QuestionEditorDraftLoadResult = withContext(Dispatchers.IO) {
        val targetFile = draftFile(cardId)
        if (!targetFile.exists()) {
            return@withContext QuestionEditorDraftLoadResult(draft = null, wasCorrupted = false)
        }
        val rawText = targetFile.readText()
        try {
            val fileSnapshot = json.decodeFromString<QuestionEditorDraftFileSnapshot>(rawText)
            QuestionEditorDraftLoadResult(
                draft = fileSnapshot.toDomain(cardId = cardId),
                wasCorrupted = false
            )
        } catch (_: SerializationException) {
            targetFile.delete()
            QuestionEditorDraftLoadResult(draft = null, wasCorrupted = true)
        } catch (_: IllegalArgumentException) {
            targetFile.delete()
            QuestionEditorDraftLoadResult(draft = null, wasCorrupted = true)
        }
    }

    /**
     * 保存时先写临时文件再替换目标文件，是为了尽量避免应用中断时留下半份草稿导致后续恢复异常。
     */
    override suspend fun saveDraft(snapshot: QuestionEditorDraftSnapshot) = withContext(Dispatchers.IO) {
        ensureDraftDirectory()
        val targetFile = draftFile(snapshot.cardId)
        val tempFile = File(draftDirectory, "${snapshot.cardId}.tmp")
        tempFile.writeText(json.encodeToString(snapshot.toFileSnapshot()))
        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("无法替换旧草稿文件。")
        }
        if (!tempFile.renameTo(targetFile)) {
            tempFile.delete()
            throw IOException("无法写入草稿文件。")
        }
    }

    /**
     * 删除草稿显式忽略“不存在”的情况，是为了让正式保存和放弃草稿两条路径都保持幂等。
     */
    override suspend fun deleteDraft(cardId: String) = withContext(Dispatchers.IO) {
        val targetFile = draftFile(cardId)
        if (targetFile.exists()) {
            targetFile.delete()
        }
    }

    /**
     * 草稿文件名按 cardId 一一对应，是为了避免同一张卡片的多次编辑产生多份并存快照。
     */
    private fun draftFile(cardId: String): File = File(draftDirectory, "$cardId.json")

    /**
     * 目录按需创建而不是初始化时强建，是为了让未使用草稿能力的安装保持最小文件落地面。
     */
    private fun ensureDraftDirectory() {
        if (!draftDirectory.exists() && !draftDirectory.mkdirs()) {
            throw IOException("无法创建草稿目录。")
        }
    }

    /**
     * 文件结构保留显式版本号，是为了以后扩展字段时可以在仓储层做定向兼容，而不是让页面猜文件格式。
     */
    @Serializable
    private data class QuestionEditorDraftFileSnapshot(
        val version: Int,
        val title: String,
        val description: String,
        val questions: List<QuestionEditorDraftFileItem>,
        val deletedQuestionIds: List<String>,
        val savedAt: Long
    )

    /**
     * 草稿项单独建模后，临时 ID 和“是否新题”这类恢复必需信息就不需要塞进拼接字符串中曲线保存。
     */
    @Serializable
    private data class QuestionEditorDraftFileItem(
        val id: String,
        val prompt: String,
        val answer: String,
        val isNew: Boolean
    )

    /**
     * 仓储内部序列化结构转回领域快照时补入 cardId，是为了让磁盘文件不必重复保存路径已表达的主键。
     */
    private fun QuestionEditorDraftFileSnapshot.toDomain(cardId: String): QuestionEditorDraftSnapshot =
        QuestionEditorDraftSnapshot(
            cardId = cardId,
            title = title,
            description = description,
            questions = questions.map { item ->
                QuestionEditorDraftItemSnapshot(
                    id = item.id,
                    prompt = item.prompt,
                    answer = item.answer,
                    isNew = item.isNew
                )
            },
            deletedQuestionIds = deletedQuestionIds,
            savedAt = savedAt
        )

    /**
     * 领域快照写回文件结构时补入版本号，是为了把未来兼容决策固定在仓储内部而不是泄漏给调用方。
     */
    private fun QuestionEditorDraftSnapshot.toFileSnapshot(): QuestionEditorDraftFileSnapshot =
        QuestionEditorDraftFileSnapshot(
            version = FILE_VERSION,
            title = title,
            description = description,
            questions = questions.map { item ->
                QuestionEditorDraftFileItem(
                    id = item.id,
                    prompt = item.prompt,
                    answer = item.answer,
                    isNew = item.isNew
                )
            },
            deletedQuestionIds = deletedQuestionIds,
            savedAt = savedAt
        )

    private companion object {
        const val DRAFT_DIRECTORY_NAME = "question_editor_drafts"
        const val FILE_VERSION = 1
    }
}
