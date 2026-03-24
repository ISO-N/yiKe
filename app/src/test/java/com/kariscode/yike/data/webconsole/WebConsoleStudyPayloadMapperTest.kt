package com.kariscode.yike.data.webconsole

import com.kariscode.yike.domain.model.PracticeOrderMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 学习 payload 映射测试锁住新的会话 DTO 边界，是为了让服务拆分后前端学习工作区仍能收到稳定契约。
 */
class WebConsoleStudyPayloadMapperTest {
    private val mapper = WebConsoleStudyPayloadMapper()

    /**
     * 复习完成态必须继续映射为明确的完成摘要，
     * 否则桌面工作区会在结构重构后退回到“完成了但下一步不清楚”的模糊状态。
     */
    @Test
    fun toStudySessionPayload_reviewSessionCompleted_returnsCompletionSummary() {
        val payload = mapper.toStudySessionPayload(
            WebConsoleReviewStudySession(
                cards = listOf(
                    WebConsoleReviewCardSnapshot(
                        deckName = "英语",
                        cardId = "card_1",
                        cardTitle = "基础短语",
                        questions = listOf(
                            WebConsoleReviewQuestionSnapshot(
                                questionId = "question_1",
                                prompt = "hello",
                                answerText = "你好",
                                stageIndex = 0
                            )
                        )
                    )
                ),
                currentCardIndex = 1,
                currentQuestionIndex = 0,
                questionPresentedAt = null,
                answerVisible = false,
                updatedAt = 1_000L
            )
        )

        assertEquals(WebConsoleStudySessionTypes.REVIEW, payload.type)
        assertTrue(payload.summary.contains("全部处理完成"))
        assertTrue(payload.review?.isSessionCompleted == true)
    }

    /**
     * 练习摘要必须继续携带顺序模式文案，
     * 否则从其他工作区恢复练习时用户无法理解本次题序组织方式。
     */
    @Test
    fun toStudySessionSummaryPayload_practiceSession_containsOrderModeLabel() {
        val payload = mapper.toStudySessionSummaryPayload(
            WebConsolePracticeStudySession(
                orderMode = PracticeOrderMode.RANDOM,
                sessionSeed = 42L,
                questions = listOf(
                    WebConsolePracticeQuestionSnapshot(
                        questionId = "question_1",
                        deckName = "英语",
                        cardTitle = "基础短语",
                        prompt = "hello",
                        answerText = "你好"
                    )
                ),
                currentIndex = 0,
                answerVisible = false,
                updatedAt = 1_000L
            )
        )

        assertEquals(WebConsoleStudySessionTypes.PRACTICE, payload.type)
        assertTrue(payload.detail.contains("随机"))
        assertEquals("恢复练习", payload.actionLabel)
    }
}
