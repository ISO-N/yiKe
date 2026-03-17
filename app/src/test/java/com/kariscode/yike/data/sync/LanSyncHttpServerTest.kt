package com.kariscode.yike.data.sync

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LanSyncHttpServer 测试锁定路由和请求反序列化行为，
 * 避免协议端点在调整时出现“客户端还能发，服务端已不再接”的隐性断裂。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LanSyncHttpServerTest {

    /**
     * hello 路由必须原样返回处理器给出的设备资料，
     * 否则发现页会在最早阶段就拿到错误的版本或设备标识。
     */
    @Test
    fun helloRoute_returnsHandlerPayload() = testApplication {
        application {
            configureLanSyncRoutes(
                onHello = {
                    LanSyncHelloResponse(
                        deviceId = "peer_1",
                        displayName = "Peer",
                        shortDeviceId = "peer_1",
                        protocolVersion = 2,
                        pairingNonce = "nonce"
                    )
                },
                onPairInit = { error("unused") },
                onPing = { error("unused") },
                onPullChanges = { error("unused") },
                onPushChanges = { error("unused") },
                onAck = { error("unused") }
            )
        }

        val response = client.get("/lan-sync/v2/hello")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            "Peer",
            LanSyncJson.json.decodeFromString(
                LanSyncHelloResponse.serializer(),
                response.bodyAsText()
            ).displayName
        )
    }

    /**
     * 受保护 push 路由必须完整反序列化请求体并把处理器响应再编码回去，
     * 否则仓储虽然调用了传输端点，真正的提交语义会在服务端入口处丢失。
     */
    @Test
    fun pushRoute_deserializesRequestAndReturnsHandlerResponse() = testApplication {
        var receivedRequest: LanSyncProtectedRequest? = null
        application {
            configureLanSyncRoutes(
                onHello = { error("unused") },
                onPairInit = { error("unused") },
                onPing = { error("unused") },
                onPullChanges = { error("unused") },
                onPushChanges = { request ->
                    receivedRequest = request
                    LanSyncProtectedResponse(
                        payload = LanSyncEncryptedEnvelope(
                            iv = "iv_1",
                            cipherText = "cipher_1"
                        )
                    )
                },
                onAck = { error("unused") }
            )
        }

        val request = LanSyncProtectedRequest(
            requesterDeviceId = "local_device",
            payload = LanSyncEncryptedEnvelope(
                iv = "iv_request",
                cipherText = "cipher_request"
            )
        )
        val response = client.post("/lan-sync/v2/changes/push") {
            contentType(ContentType.Application.Json)
            setBody(
                LanSyncJson.json.encodeToString(
                    LanSyncProtectedRequest.serializer(),
                    request
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("local_device", receivedRequest?.requesterDeviceId)
        assertEquals("iv_request", receivedRequest?.payload?.iv)
        assertEquals(
            "cipher_1",
            LanSyncJson.json.decodeFromString(
                LanSyncProtectedResponse.serializer(),
                response.bodyAsText()
            ).payload.cipherText
        )
    }

    /**
     * 未支持路径必须显式返回 404，
     * 否则客户端误配端点时只会得到模糊失败，排障成本会明显放大。
     */
    @Test
    fun unsupportedRoute_returnsNotFound() = testApplication {
        application {
            configureLanSyncRoutes(
                onHello = { error("unused") },
                onPairInit = { error("unused") },
                onPing = { error("unused") },
                onPullChanges = { error("unused") },
                onPushChanges = { error("unused") },
                onAck = { error("unused") }
            )
        }

        val response = client.get("/lan-sync/v2/unknown")
        val body = response.body<String>()

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertTrue(body.contains("Unsupported path"))
        assertFalse(body.isBlank())
    }
}
