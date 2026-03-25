package com.kariscode.yike.data.webconsole

import com.kariscode.yike.core.domain.time.TimeProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网页后台安全相关测试集中校验访问码、会话和来源守卫，
 * 是为了在后续补页面与 API 时继续锁住最关键的局域网安全边界。
 */
class WebConsoleSecurityTest {
    /**
     * 访问码必须保持固定长度和纯数字，才能同时满足人工输入成本与服务端登录校验约束。
     */
    @Test
    fun generateAccessCode_returnsSixDigits() {
        val code = WebConsoleAccessCodeGenerator.generate()

        assertEquals(6, code.length)
        assertTrue(code.all(Char::isDigit))
    }

    /**
     * 刷新访问码时同步清空旧会话，是为了避免已经登录的浏览器在访问码轮换后仍能继续操作。
     */
    @Test
    fun rotateAccessCode_clearsSessions() {
        val runtime = WebConsoleRuntime(
            timeProvider = object : TimeProvider {
                override fun nowEpochMillis(): Long = 1_000L
            }
        )
        runtime.activate(
            port = 9440,
            addresses = emptyList()
        )
        val initialCode = runtime.state.value.accessCode
        val sessionId = runtime.createSession()

        runtime.rotateAccessCode()

        assertNotEquals(initialCode, runtime.state.value.accessCode)
        assertEquals(0, runtime.state.value.activeSessionCount)
        assertFalse(runtime.touchSession(sessionId))
    }

    /**
     * 访问码轮换必须连同浏览器学习会话一起失效，
     * 否则用户即使被踢回登录页，旧标签页仍可能错误恢复上一轮学习上下文。
     */
    @Test
    fun rotateAccessCode_clearsStudySessions() {
        val runtime = WebConsoleRuntime(
            timeProvider = object : TimeProvider {
                override fun nowEpochMillis(): Long = 1_000L
            }
        )
        runtime.activate(
            port = 9440,
            addresses = emptyList()
        )
        val sessionId = runtime.createSession()
        runtime.putStudySession(
            sessionId = sessionId,
            session = WebConsolePracticeStudySession(
                orderMode = com.kariscode.yike.domain.model.PracticeOrderMode.SEQUENTIAL,
                sessionSeed = null,
                questions = listOf(
                    WebConsolePracticeQuestionSnapshot(
                        questionId = "question_1",
                        deckName = "词汇",
                        cardTitle = "基础",
                        prompt = "hello",
                        answerText = "你好"
                    )
                ),
                currentIndex = 0,
                answerVisible = false,
                updatedAt = 1_000L
            )
        )

        runtime.rotateAccessCode()

        assertEquals(0, runtime.state.value.activeSessionCount)
        assertEquals(null, runtime.getStudySession(sessionId))
    }

    /**
     * 来源守卫需要接受常见局域网地址并拒绝公网地址，才能维持“只在本地网络可访问”的产品边界。
     */
    @Test
    fun isAllowedLocalNetworkHost_onlyAcceptsLocalAddresses() {
        assertTrue("192.168.31.8".isAllowedLocalNetworkHost())
        assertTrue("10.0.0.5".isAllowedLocalNetworkHost())
        assertTrue("127.0.0.1".isAllowedLocalNetworkHost())
        assertFalse("8.8.8.8".isAllowedLocalNetworkHost())
    }
}

