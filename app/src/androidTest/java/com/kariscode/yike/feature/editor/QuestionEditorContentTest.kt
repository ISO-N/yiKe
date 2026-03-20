package com.kariscode.yike.feature.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.kariscode.yike.ui.theme.YikeTheme
import org.junit.Rule
import org.junit.Test

/**
 * 编辑页内容测试优先守住草稿恢复与正式保存的双轨提示，
 * 这样 UI 结构继续迭代时也不容易把最关键的恢复入口和提交出口改丢。
 */
class QuestionEditorContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * 检测到草稿时必须先给出恢复或丢弃的明确选择，
     * 否则旧草稿会在用户毫不知情的情况下覆盖正式内容。
     */
    @Test
    fun restoreDraftDialog_showsRecoveryChoices() {
        composeRule.setContent {
            YikeTheme {
                QuestionEditorRestoreDraftDialog(
                    info = QuestionEditorRestoreDraftInfo(
                        savedAt = 1_700_000_000_000L,
                        questionCount = 3,
                        deletedQuestionCount = 1
                    ),
                    onRestore = {},
                    onDiscard = {}
                )
            }
        }

        composeRule.onNodeWithText("发现未提交草稿").assertIsDisplayed()
        composeRule.onNodeWithText("恢复草稿").assertIsDisplayed()
        composeRule.onNodeWithText("丢弃草稿").assertIsDisplayed()
        composeRule.onNodeWithText("包含 3 条问题", substring = true).assertIsDisplayed()
    }

    /**
     * 草稿已保存到本机后，页面仍需明确提示“还没正式生效”，
     * 这样用户才知道下一步应该点击“保存修改”而不是误以为已经入库。
     */
    @Test
    fun questionEditorContent_savedDraftStillShowsOfficialSaveAction() {
        composeRule.setContent {
            YikeTheme {
                QuestionEditorContent(
                    uiState = QuestionEditorUiState(
                        cardId = "card_1",
                        deckId = "deck_1",
                        isLoading = false,
                        title = "第一章 极限",
                        description = "聚焦极限定义",
                        questions = listOf(
                            QuestionDraft(
                                id = "question_1",
                                prompt = "什么是极限？",
                                answer = "描述趋近行为",
                                isNew = false
                            )
                        ),
                        hasUnsavedChanges = true,
                        hasPendingDraftChanges = false,
                        isSaving = false,
                        isDraftSaving = false,
                        lastDraftSavedAt = 1_700_000_000_000L,
                        restoreDraftDialogVisible = false,
                        restoreDraftInfo = null,
                        message = null,
                        errorMessage = null
                    ),
                    onTitleChange = {},
                    onDescriptionChange = {},
                    onAddQuestion = {},
                    onSave = {},
                    onPromptChange = { _, _ -> },
                    onAnswerChange = { _, _ -> },
                    onDeleteQuestion = {}
                )
            }
        }

        composeRule.onNodeWithText("草稿已保存到本机").assertIsDisplayed()
        composeRule.onNodeWithText("保存修改").assertIsDisplayed()
        composeRule.onNodeWithText("添加问题").assertIsDisplayed()
    }
}
