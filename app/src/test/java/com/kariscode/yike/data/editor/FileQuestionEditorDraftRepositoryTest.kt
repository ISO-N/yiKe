package com.kariscode.yike.data.editor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.kariscode.yike.domain.model.QuestionEditorDraftItemSnapshot
import com.kariscode.yike.domain.model.QuestionEditorDraftSnapshot
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 文件草稿仓储测试用于守住“正常往返”和“损坏文件自愈”两条最关键的持久化边界，
 * 避免真实设备上只能靠手动杀进程来发现恢复问题。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FileQuestionEditorDraftRepositoryTest {

    /**
     * 正常保存和读取必须无损往返，是为了确保新增题临时 ID 与删除列表不会在恢复时悄悄丢失。
     */
    @Test
    fun saveDraft_loadDraftReturnsSameSnapshot() = runTest {
        val context = createIsolatedContext("draft_round_trip")
        val repository = FileQuestionEditorDraftRepository(context = context)
        val snapshot = sampleSnapshot(savedAt = 1_700_000_000_123L)

        repository.saveDraft(snapshot)
        val result = repository.loadDraft(cardId = "card_1")

        assertFalse(result.wasCorrupted)
        assertEquals(snapshot, result.draft)
    }

    /**
     * 损坏 JSON 应该被删除并安全回退为空草稿，
     * 否则单个坏文件就会持续打断这张卡片的编辑入口。
     */
    @Test
    fun loadDraft_corruptedFileDeletesFileAndReportsCorruption() = runTest {
        val context = createIsolatedContext("draft_corrupted")
        val repository = FileQuestionEditorDraftRepository(context = context)
        val corruptedFile = File(context.filesDir, "question_editor_drafts/card_1.json").apply {
            parentFile?.mkdirs()
            writeText("{not-json")
        }

        val result = repository.loadDraft(cardId = "card_1")

        assertTrue(result.wasCorrupted)
        assertNull(result.draft)
        assertFalse(corruptedFile.exists())
    }

    /**
     * 删除草稿保持幂等，是为了让正式保存和主动放弃草稿两条路径都不用额外判断文件是否存在。
     */
    @Test
    fun deleteDraft_removesPersistedFile() = runTest {
        val context = createIsolatedContext("draft_delete")
        val repository = FileQuestionEditorDraftRepository(context = context)
        repository.saveDraft(sampleSnapshot(savedAt = 1_700_000_000_456L))

        repository.deleteDraft(cardId = "card_1")
        val result = repository.loadDraft(cardId = "card_1")

        assertFalse(result.wasCorrupted)
        assertNull(result.draft)
    }

    /**
     * 测试使用独立 files 目录，是为了让仓储在主机测试里也能覆盖真实的相对路径与替换逻辑。
     */
    private fun createIsolatedContext(folderName: String): Context {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val rootDirectory = File(applicationContext.cacheDir, "question_editor_draft_tests/$folderName").apply {
            deleteRecursively()
            mkdirs()
        }
        return object : android.content.ContextWrapper(applicationContext) {
            /**
             * 仅重写 filesDir 是为了让仓储继续走真实实现，同时避免污染应用默认文件目录。
             */
            override fun getFilesDir(): File = rootDirectory
        }
    }

    /**
     * 样例草稿集中构造后，文件仓储测试就能把注意力放在持久化语义而不是对象样板上。
     */
    private fun sampleSnapshot(savedAt: Long): QuestionEditorDraftSnapshot = QuestionEditorDraftSnapshot(
        cardId = "card_1",
        title = "极限",
        description = "草稿说明",
        questions = listOf(
            QuestionEditorDraftItemSnapshot(
                id = "temp_question_1",
                prompt = "什么是极限",
                answer = "描述趋近行为",
                isNew = true
            )
        ),
        deletedQuestionIds = listOf("question_old"),
        savedAt = savedAt
    )
}
